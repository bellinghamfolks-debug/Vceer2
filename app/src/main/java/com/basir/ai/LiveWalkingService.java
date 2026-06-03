package com.basir.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * v3.2 — foreground service that owns the live walking session.
 *
 * Why a service (and not just an Activity-bound controller)
 * ─────────────────────────────────────────────────────────
 * The v3.1 implementation tied the camera + AI loop to the
 * Activity's lifecycle. The moment the user backgrounded the app or
 * the screen locked, {@code onPause} called {@code stop()} and the
 * camera released. Blind walkers can't keep the screen on while
 * walking — they shove the phone in a chest pocket, lanyard, or
 * sling rig — so the v3.1 behaviour was a regression dressed up as
 * a feature. v3.2 promotes the controller into a {@link Service}
 * with:
 *   - a persistent ongoing notification (required by Android 8+
 *     for any service that keeps running while the app is in the
 *     background, and Android 14+ for any camera-using service),
 *   - {@code foregroundServiceType="camera"} so we can keep the
 *     camera open after the Activity is gone,
 *   - a {@code PARTIAL_WAKE_LOCK} so the CPU keeps running even
 *     when the screen is off (no display wake lock — the user
 *     wants the screen off, they have no use for it),
 *   - a {@code Stop} action right on the notification so the user
 *     can shut the session down without unlocking the phone.
 *
 * Bind + start
 * ────────────
 * The service is started ({@link #ACTION_START}) so it survives
 * the Activity going away, and bound ({@link #onBind}) so the
 * Activity can attach a {@link LiveWalkingController.Listener} for
 * UI updates while it's in the foreground. When the Activity
 * unbinds the listener is detached but the session keeps running —
 * the user's notification + TTS announcements remain.
 *
 * Lifecycle
 * ─────────
 *   • {@link #ACTION_START} — first call: instantiates the
 *     controller and starts the loop. Subsequent calls with the
 *     same action are no-ops (the controller is already running).
 *   • {@link #ACTION_STOP}  — fired by the notification's Stop
 *     button or by the Activity. Stops the controller, releases
 *     the wake lock, and {@code stopSelf}.
 *   • {@code onTaskRemoved} — the user swiped Basir out of the
 *     recents list. We treat that as an explicit "stop everything"
 *     intent and tear down. We do NOT auto-restart with
 *     {@code START_STICKY} — a camera + Gemini session that
 *     resurrects itself without user action would burn quota and
 *     scare the user.
 */
public final class LiveWalkingService extends Service {

    public static final String ACTION_START = "com.basir.ai.action.START_LIVE_WALKING";
    public static final String ACTION_STOP  = "com.basir.ai.action.STOP_LIVE_WALKING";
    public static final String EXTRA_ARABIC  = "arabic";
    public static final String EXTRA_USE_GPS = "use_gps";

    private static final String CHANNEL_ID = "basir_live_walking";
    private static final int NOTIF_ID = 7101;
    private static final String WAKE_TAG = "basir:live_walking";

    private LiveWalkingController controller;
    private PowerManager.WakeLock wakeLock;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Live UI listener set by the bound Activity. Null while the
     *  Activity isn't bound — the session keeps running, the
     *  callbacks just go nowhere. */
    private volatile LiveWalkingController.Listener uiListener;

    /** Cached last status / last spoken line so a freshly-bound
     *  Activity can paint the screen with the in-flight state
     *  instead of an empty placeholder. */
    private volatile String lastStatus = "";
    private volatile String lastSpoken = "";
    private volatile String lastHazardLevel = "none";
    private volatile String lastHazardDesc = "";

    private boolean arabic = true;

    private final LiveWalkingController.Listener bridge = new LiveWalkingController.Listener() {
        @Override public void onStatusText(String text) {
            lastStatus = text == null ? "" : text;
            LiveWalkingController.Listener l = uiListener;
            if (l != null) main.post(() -> l.onStatusText(lastStatus));
            updateNotification(lastStatus);
        }
        @Override public void onSpoken(String text) {
            lastSpoken = text == null ? "" : text;
            LiveWalkingController.Listener l = uiListener;
            if (l != null) main.post(() -> l.onSpoken(lastSpoken));
        }
        @Override public void onHazard(String level, String description) {
            lastHazardLevel = level == null ? "none" : level;
            lastHazardDesc = description == null ? "" : description;
            LiveWalkingController.Listener l = uiListener;
            if (l != null) main.post(() -> l.onHazard(lastHazardLevel, lastHazardDesc));
        }
        @Override public void onError(String message) {
            LiveWalkingController.Listener l = uiListener;
            if (l != null) main.post(() -> l.onError(message == null ? "" : message));
            // A hard camera / AI failure stops the session.
            main.post(LiveWalkingService.this::stopSelfWithCleanup);
        }
    };

    public final class LocalBinder extends Binder {
        public LiveWalkingService getService() { return LiveWalkingService.this; }
    }
    private final IBinder binder = new LocalBinder();

    @Override public IBinder onBind(Intent intent) { return binder; }

    /** Returns true if the controller is currently running. */
    public boolean isRunning() {
        return controller != null && controller.isRunning();
    }

    /** Replay-on-rebind hooks for a freshly-bound Activity. */
    public String getLastStatus()       { return lastStatus; }
    public String getLastSpoken()       { return lastSpoken; }
    public String getLastHazardLevel()  { return lastHazardLevel; }
    public String getLastHazardDesc()   { return lastHazardDesc; }

    /** Attach / detach the Activity's UI listener. Passing null
     *  means "Activity went away, keep the session running but
     *  discard onStatus / onSpoken / onHazard callbacks." */
    public void setListener(LiveWalkingController.Listener l) {
        this.uiListener = l;
    }

    @Override public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelfWithCleanup();
            return START_NOT_STICKY;
        }

        arabic = intent == null || intent.getBooleanExtra(EXTRA_ARABIC, true);
        boolean useGps = intent != null && intent.getBooleanExtra(EXTRA_USE_GPS, false);

        // Promote to foreground BEFORE opening the camera. On Android
        // 14+ a camera-using service that isn't yet in foreground
        // status when CameraManager.openCamera fires is killed.
        startInForeground();

        if (controller == null) {
            SharedPreferences prefs = getSharedPreferences("basir_settings", MODE_PRIVATE);
            controller = new LiveWalkingController(this, prefs, arabic, useGps, bridge);
            controller.start();
        }
        acquireWakeLock();
        return START_NOT_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // User swiped the app out of recents — treat as explicit stop.
        stopSelfWithCleanup();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        stopController();
        releaseWakeLock();
        super.onDestroy();
    }

    private void stopController() {
        if (controller != null) {
            try { controller.stop(); } catch (Throwable ignore) {}
            controller = null;
        }
    }

    private void stopSelfWithCleanup() {
        stopController();
        releaseWakeLock();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable ignore) {}
        stopSelf();
    }

    // ───── Foreground / notification ─────

    private void startInForeground() {
        Notification n = buildNotification(
                arabic ? "البث المباشر للمشي" : "Live walking mode",
                arabic ? "جاري الفحص..."      : "Scanning...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(String status) {
        if (controller == null) return;
        Notification n = buildNotification(
                arabic ? "البث المباشر للمشي" : "Live walking mode",
                status == null || status.isEmpty()
                        ? (arabic ? "جاري الفحص..." : "Scanning...")
                        : status);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        String label = isDeviceArabic() ? "البث المباشر للمشي" : "Live walking mode";
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, label, NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(isDeviceArabic()
                ? "إشعار دائم أثناء جلسة الإرشاد المباشر للمكفوفين."
                : "Ongoing notification during a live blind-guidance session.");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String title, String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags);

        Intent stop = new Intent(this, LiveWalkingService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle(title)
         .setContentText(text)
         .setSmallIcon(android.R.drawable.ic_menu_camera)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setContentIntent(openPi)
         .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    arabic ? "إيقاف" : "Stop", stopPi);
        return b.build();
    }

    private boolean isDeviceArabic() {
        try {
            return java.util.Locale.getDefault().getLanguage().startsWith("ar");
        } catch (Throwable t) { return false; }
    }

    // ───── Wake lock ─────

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        // PARTIAL_WAKE_LOCK keeps the CPU running so our capture +
        // AI loop survives the screen turning off. We deliberately
        // do NOT acquire a screen-on / FULL_WAKE_LOCK — a blind
        // user wants the screen off for battery + privacy.
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
        wakeLock.setReferenceCounted(false);
        // 4 h ceiling: Doze + battery saver will already throttle us
        // long before this; the timeout is a defensive "if something
        // wedges and we never call release, the OS still reclaims."
        wakeLock.acquire(4L * 60L * 60L * 1000L);
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignore) {}
        wakeLock = null;
    }

    // ───── Static helpers for the calling Activity ─────

    public static Intent startIntent(Context ctx, boolean arabic, boolean useGps) {
        Intent i = new Intent(ctx, LiveWalkingService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_ARABIC, arabic);
        i.putExtra(EXTRA_USE_GPS, useGps);
        return i;
    }

    public static Intent stopIntent(Context ctx) {
        Intent i = new Intent(ctx, LiveWalkingService.class);
        i.setAction(ACTION_STOP);
        return i;
    }
}
