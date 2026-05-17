package com.basir.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service that performs the (potentially long) PDF/PPTX → Word
 * conversion off the main thread, with a sticky notification so Android does
 * not kill the process while the user has the app in the background.
 *
 * Communication with the UI is done through {@link ConversionState}, a tiny
 * in-process singleton that the MainActivity observes via a listener.
 */
public class ConversionService extends Service {

    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_LANGUAGE   = "language";
    public static final String EXTRA_MODE       = "mode";

    private static final String CHANNEL_ID = "basir_convert";
    private static final int NOTIF_ID = 7001;

    private ExecutorService worker;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
        worker = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        boolean arabic = "ar".equalsIgnoreCase(intent.getStringExtra(EXTRA_LANGUAGE));
        startForeground(NOTIF_ID, buildNotification(
                arabic ? "جاري تحويل الملف" : "Converting file",
                arabic ? "جاري تجهيز الملف..." : "Preparing file...",
                0, 0));

        final Uri sourceUri = intent.getParcelableExtra(EXTRA_SOURCE_URI);
        final String lang = intent.getStringExtra(EXTRA_LANGUAGE);
        final String mode = intent.getStringExtra(EXTRA_MODE);

        if (sourceUri == null) {
            ConversionState.get().fail(arabic
                    ? "لم يتم تحديد ملف للتحويل."
                    : "No file was provided for conversion.");
            stopSelf();
            return START_NOT_STICKY;
        }

        ConversionState.get().start();

        worker.execute(() -> {
            File outFile = null;
            try {
                SharedPreferences prefs = getSharedPreferences("basir_settings", MODE_PRIVATE);
                outFile = new File(getCacheDir(),
                        "basir-tmp-" + System.currentTimeMillis() + ".docx");

                AiClient.convertToDocx(
                        ConversionService.this, prefs, sourceUri,
                        mode == null ? "full" : mode,
                        lang == null ? "ar"   : lang,
                        outFile,
                        (current, total, stage) -> {
                            ConversionState.get().updateProgress(current, total);
                            // Reuse the same notification id so the system
                            // replaces the live progress in-place.
                            updateNotification(arabic, current, total);
                        });

                ConversionState.get().success(outFile);
            } catch (Throwable e) {
                if (outFile != null && outFile.exists()) outFile.delete();
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                ConversionState.get().fail(msg);
            } finally {
                new Handler(Looper.getMainLooper()).post(() -> {
                    stopForeground(true);
                    stopSelf();
                });
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (worker != null) worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Notification helpers
    // ------------------------------------------------------------------

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "تحويل المستندات / Document conversion",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("إشعارات تقدم تحويل ملفات PDF و PowerPoint إلى Word.");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private void updateNotification(boolean arabic, int current, int total) {
        String title = arabic ? "جاري تحويل الملف" : "Converting file";
        String text;
        if (total > 0) {
            text = arabic
                    ? "الصفحة " + current + " من " + total
                    : "Page " + current + " of " + total;
        } else {
            text = arabic ? "جاري المعالجة..." : "Processing...";
        }
        Notification n = buildNotification(title, text, current, total);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }

    private Notification buildNotification(String title, String text, int current, int total) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle(title)
         .setContentText(text)
         .setSmallIcon(android.R.drawable.stat_sys_upload)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setContentIntent(pi);

        if (total > 0) {
            b.setProgress(total, current, false);
        } else {
            b.setProgress(0, 0, true); // indeterminate
        }
        return b.build();
    }
}
