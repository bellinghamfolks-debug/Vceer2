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
import java.util.concurrent.Future;

/**
 * Foreground service that performs the (potentially long) PDF/PPTX → Word
 * conversion off the main thread, with a sticky notification so Android does
 * not kill the process while the user has the app in the background.
 *
 * Supports cancellation via {@link #ACTION_CANCEL}: the worker thread is
 * interrupted, in-flight HTTP requests abort, and the partial output is
 * discarded.
 */
public class ConversionService extends Service {

    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_LANGUAGE   = "language";
    public static final String EXTRA_MODE       = "mode";
    /** v2.9.2 — when true, AiClient.directConvertToDocx reuses the
     *  uploaded file URI + chunk snapshot already in ConversionState
     *  instead of re-uploading the source. Set by MainActivity's
     *  retryFailedChunks(). */
    public static final String EXTRA_RESUME     = "resume";

    public static final String ACTION_CANCEL = "com.basir.ai.action.CANCEL_CONVERSION";

    private static final String CHANNEL_ID = "basir_convert";
    private static final int NOTIF_ID = 7001;

    private ExecutorService worker;
    private Future<?> currentJob;
    private boolean arabic = true;

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

        if (ACTION_CANCEL.equals(intent.getAction())) {
            ConversionState.get().requestCancel();
            if (currentJob != null) currentJob.cancel(true);
            return START_NOT_STICKY;
        }

        arabic = !"en".equalsIgnoreCase(intent.getStringExtra(EXTRA_LANGUAGE));
        startForeground(NOTIF_ID, buildNotification(
                arabic ? "جارٍ معالجة الملف" : "Processing document",
                arabic ? "جارٍ تجهيز الملف..." : "Preparing file...",
                0, 0, true));

        final boolean resume = intent.getBooleanExtra(EXTRA_RESUME, false);
        // For resume runs the original source Uri is no longer needed —
        // the file lives on Gemini's side already (uploadedFileUri in
        // ConversionState). For first-pass runs we still take it from
        // the intent. AiClient.directConvertToDocx branches on
        // ConversionState.hasRetainedSnapshot() to decide which path
        // to use, so passing a null sourceUri on resume is correct.
        final Uri sourceUri = resume ? null : intent.getParcelableExtra(EXTRA_SOURCE_URI);
        final String lang = intent.getStringExtra(EXTRA_LANGUAGE);
        final String mode = intent.getStringExtra(EXTRA_MODE);

        if (sourceUri == null && !resume) {
            ConversionState.get().fail(arabic
                    ? "لم يتم تحديد ملف للتحويل."
                    : "No file was provided for conversion.");
            stopSelf();
            return START_NOT_STICKY;
        }

        ConversionState.get().start();

        currentJob = worker.submit(() -> {
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
                            // Honour cancellation at every callback boundary so
                            // an in-flight long batch still gets interrupted
                            // on the next checkpoint.
                            if (ConversionState.get().isCancelRequested()) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Cancelled");
                            }
                            ConversionState.Stage s = stageFromString(stage);
                            ConversionState.get().updateProgress(current, total, s);
                            updateNotification(arabic, current, total, s);
                        });

                if (Thread.currentThread().isInterrupted() || ConversionState.get().isCancelRequested()) {
                    if (outFile.exists()) outFile.delete();
                    ConversionState.get().cancelled();
                } else {
                    ConversionState.get().success(outFile);
                }
            } catch (Throwable e) {
                if (outFile != null && outFile.exists()) outFile.delete();
                if (ConversionState.get().isCancelRequested()
                        || (e.getMessage() != null && e.getMessage().toLowerCase().contains("cancel"))) {
                    ConversionState.get().cancelled();
                } else {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    ConversionState.get().fail(msg);
                }
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
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        // Pick a locale-appropriate channel label so the system Settings UI
        // doesn't show a mixed Arabic / English string.
        String label = isDeviceArabic()
                ? "تحويل المستندات"
                : "Document conversion";
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, label, NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(isDeviceArabic()
                ? "إشعارات تقدم تحويل ملفات PDF و PowerPoint إلى Word."
                : "Progress notifications for PDF / PowerPoint → Word conversion.");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private boolean isDeviceArabic() {
        try {
            return java.util.Locale.getDefault().getLanguage().startsWith("ar");
        } catch (Throwable t) { return false; }
    }

    private void updateNotification(boolean arabic, int current, int total,
                                    ConversionState.Stage stage) {
        String title = arabic ? "جارٍ معالجة الملف" : "Processing document";
        String text;
        boolean indeterminate;

        switch (stage) {
            case PREPARING:
                text = arabic ? "جارٍ تجهيز الملف..." : "Preparing file...";
                indeterminate = true;
                break;
            case UPLOADING:
                text = arabic ? "جارٍ رفع الملف إلى خدمة المعالجة..." : "Uploading file...";
                indeterminate = true;
                break;
            case FINALISING:
                text = arabic ? "جارٍ إنشاء الملف الناتج..." : "Saving document...";
                indeterminate = true;
                break;
            case DONE:
                text = arabic ? "اكتملت معالجة المستند" : "Document processing complete";
                indeterminate = false;
                break;
            case PROCESSING:
            default:
                if (total > 0) {
                    text = arabic
                            ? "الصفحة " + current + " من " + total
                            : "Page " + current + " of " + total;
                    indeterminate = false;
                } else {
                    text = arabic ? "جارٍ تنفيذ المعالجة..." : "Processing...";
                    indeterminate = true;
                }
        }

        Notification n = buildNotification(title, text, current, total, indeterminate);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }

    private Notification buildNotification(String title, String text, int current, int total,
                                           boolean indeterminate) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, piFlags);

        Intent cancel = new Intent(this, ConversionService.class);
        cancel.setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 1, cancel, piFlags);

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
         .setContentIntent(openPi)
         .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    arabic ? "إلغاء" : "Cancel", cancelPi);

        if (indeterminate || total <= 0) {
            b.setProgress(0, 0, true);
        } else {
            b.setProgress(total, current, false);
        }
        return b.build();
    }

    private static ConversionState.Stage stageFromString(String s) {
        if (s == null) return ConversionState.Stage.PROCESSING;
        switch (s) {
            case "preparing":  return ConversionState.Stage.PREPARING;
            case "uploading":  return ConversionState.Stage.UPLOADING;
            case "finalising": return ConversionState.Stage.FINALISING;
            case "done":       return ConversionState.Stage.DONE;
            case "processing":
            default:           return ConversionState.Stage.PROCESSING;
        }
    }
}
