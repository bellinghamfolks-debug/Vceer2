package com.basir.ai.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.basir.ai.AiClient;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v2.1 — OCR-on-touch accessibility service.
 *
 * Once enabled in Settings → Accessibility, the system shows our
 * accessibility shortcut button. Tapping it captures the current screen,
 * sends the screenshot to Gemini for OCR, and reads the extracted text
 * aloud via TTS. This works on top of ANY app — the user can hear text
 * from images in WhatsApp, captchas in a browser, scanned receipts inside
 * a PDF viewer, etc.
 *
 * Two code paths:
 *   - Android 11+ (API 30): use AccessibilityService.takeScreenshot(),
 *     which is the cleanest API — no MediaProjection popup, no extra
 *     permissions, just one method call.
 *   - Older Android: fall back to walking the accessibility tree of the
 *     foreground window and reading any visible text directly. We lose
 *     image OCR (no screenshot) but still solve 80% of real-world use
 *     (chat messages, app UI, web pages).
 */
public class BasirOcrService extends AccessibilityService {

    private static final String TAG = "BasirOcrService";
    private static final long DEBOUNCE_MS = 1500L;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private long lastTriggerMs = 0L;
    private volatile boolean busy = false;

    private static BasirOcrService instance;
    public static BasirOcrService getInstance() { return instance; }
    public static boolean isEnabled() { return instance != null; }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                applyTtsLocale();
            }
        });
        // v2.1 fix: AccessibilityService doesn't expose
        // onAccessibilityButtonClicked() as an overridable method
        // (despite a common assumption — even the Android docs phrase
        // it ambiguously). The correct API is to grab the
        // AccessibilityButtonController and REGISTER a callback on it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                android.accessibilityservice.AccessibilityButtonController ctl =
                        getAccessibilityButtonController();
                if (ctl != null) {
                    ctl.registerAccessibilityButtonCallback(
                        new android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                            @Override
                            public void onClicked(android.accessibilityservice.AccessibilityButtonController c) {
                                runOcrTrigger();
                            }
                        });
                }
            } catch (Throwable ignore) {}
        }
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Throwable ignore) {}
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally a no-op. We act only when the user taps the
        // accessibility shortcut button.
    }

    @Override
    public void onInterrupt() { /* no-op */ }

    /** Public entry point so MainActivity can also kick OCR off without
     *  the user tapping the system shortcut (e.g. a settings test button). */
    public void runOcrTrigger() {
        long now = System.currentTimeMillis();
        if (now - lastTriggerMs < DEBOUNCE_MS) return;
        lastTriggerMs = now;
        if (busy) {
            speak(arabic() ? "جاري قراءة الشاشة، يرجى الانتظار."
                           : "Reading the screen, please wait.");
            return;
        }
        busy = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureAndOcr();
        } else {
            readVisibleTextFallback();
        }
    }

    /** Android 11+ path: full-screen capture via the accessibility API,
     *  then Gemini OCR. */
    private void captureAndOcr() {
        try {
            speak(arabic() ? "جاري قراءة الشاشة..." : "Reading the screen...");
            takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(android.accessibilityservice.AccessibilityService.ScreenshotResult screenshot) {
                            io.execute(() -> processScreenshot(screenshot));
                        }
                        @Override
                        public void onFailure(int errorCode) {
                            busy = false;
                            speak(arabic()
                                    ? "تعذر التقاط الشاشة. حاول مرة أخرى."
                                    : "Could not capture the screen. Try again.");
                        }
                    });
        } catch (Throwable t) {
            busy = false;
            speak(arabic() ? "تعذر التقاط الشاشة." : "Could not capture the screen.");
        }
    }

    /** Decode the bitmap on the IO thread, then OCR via Gemini. We use
     *  the fully-qualified type because {@code ScreenshotResult} is a
     *  static nested class of {@link android.accessibilityservice.AccessibilityService}
     *  — addressing it via {@code TakeScreenshotCallback.ScreenshotResult}
     *  (the obvious-looking guess) does not compile. */
    private void processScreenshot(android.accessibilityservice.AccessibilityService.ScreenshotResult screenshot) {
        Bitmap bm = null;
        try {
            android.hardware.HardwareBuffer hwBuffer = screenshot.getHardwareBuffer();
            android.graphics.ColorSpace colorSpace = screenshot.getColorSpace();
            bm = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace);
            if (bm == null) {
                throw new Exception("wrapHardwareBuffer returned null");
            }
            // Copy to a regular ARGB_8888 bitmap so we can compress it.
            Bitmap copy = bm.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) throw new Exception("Bitmap.copy returned null");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // JPEG at quality 75 is enough for OCR and keeps the upload small.
            copy.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            byte[] bytes = baos.toByteArray();
            copy.recycle();

            String text = ocrViaGemini(bytes, "image/jpeg");
            announceOcrResult(text);
        } catch (Throwable t) {
            speak(arabic() ? "تعذر التعرف على النص. " : "Could not extract text. ");
        } finally {
            if (bm != null) try { bm.recycle(); } catch (Throwable ignore) {}
            try { screenshot.getHardwareBuffer().close(); } catch (Throwable ignore) {}
            busy = false;
        }
    }

    /** Older-Android path: walk the foreground window's accessibility tree
     *  and read any visible text directly. No image OCR but still useful
     *  for chats, web pages, and most app UI. */
    private void readVisibleTextFallback() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                busy = false;
                speak(arabic() ? "لا يمكن قراءة هذه الشاشة." : "Cannot read this screen.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            collectVisibleText(root, sb, 0);
            try { root.recycle(); } catch (Throwable ignore) {}
            String text = sb.toString().trim();
            if (text.isEmpty()) {
                busy = false;
                speak(arabic() ? "لم أعثر على نص في الشاشة."
                               : "No readable text found on this screen.");
                return;
            }
            announceOcrResult(text);
        } catch (Throwable t) {
            speak(arabic() ? "تعذرت قراءة الشاشة." : "Could not read the screen.");
        } finally {
            busy = false;
        }
    }

    private static final int MAX_NODES = 5000;
    private int nodesVisited = 0;

    private void collectVisibleText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 25) return;
        if (++nodesVisited > MAX_NODES) return;
        if (node.isVisibleToUser()) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                out.append(text.toString().trim()).append('\n');
            } else {
                CharSequence desc = node.getContentDescription();
                if (desc != null && desc.length() > 0) {
                    out.append(desc.toString().trim()).append('\n');
                }
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = null;
            try { child = node.getChild(i); } catch (Throwable ignore) {}
            if (child != null) {
                collectVisibleText(child, out, depth + 1);
                try { child.recycle(); } catch (Throwable ignore) {}
            }
        }
    }

    /** Send a screenshot to Gemini and return the extracted text. The prompt
     *  asks for a clean, TTS-ready transcription (no descriptions of layout,
     *  no markdown). */
    private String ocrViaGemini(byte[] imageBytes, String mimeType) throws Exception {
        if (!AiClient.isConfigured(prefs)) {
            return arabic()
                ? "يجب إعداد Gemini أولًا من إعدادات تطبيق بصير."
                : "Please set up Gemini first from the Basir app settings.";
        }
        String b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        String instruction = arabic()
            ? "أنت بصير. التُقطت صورة كاملة لشاشة الجهاز. اقرأ كل النص الظاهر فيها بترتيبه " +
              "الطبيعي للقراءة (من أعلى لأسفل، ومن اليمين لليسار للعربية). اذكر النصوص فقط، " +
              "بدون وصف للواجهة أو ذكر مواقع الأزرار. إذا كان هناك صورة تحتوي على نص، اقرأ النص " +
              "الذي بداخلها. لا تستخدم Markdown أو قوائم. الإجابة مخصصة للقراءة الصوتية بـ TTS."
            : "You are Basir. The image is a full screenshot of the device. Read out ALL " +
              "the visible text in natural reading order (top to bottom, left to right for " +
              "English, right to left for Arabic). Output text only — no UI descriptions, no " +
              "button locations. If an image inside the screenshot contains text, read THAT " +
              "text. No markdown, no bullets. The output is read aloud by TTS.";
        return AiClient.ask(prefs, "ocr_screen",
                "Extract and read the screen text.",
                instruction,
                arabic() ? "ar" : "en",
                b64, mimeType);
    }

    private void announceOcrResult(String text) {
        if (text == null) text = "";
        text = text.trim();
        if (text.isEmpty()) {
            speak(arabic() ? "لا يوجد نص مقروء." : "No readable text.");
            return;
        }
        // Cap to a sensible TTS budget so the user isn't stuck listening to
        // a 5-minute monologue from a single tap. They can tap again on a
        // scrolled view if they need more.
        final int MAX_CHARS = 1200;
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS)
                    + (arabic() ? "... النص أطول من المتاح." : "... text continues.");
        }
        speak(text);
    }

    // ---------- helpers ----------

    private boolean arabic() {
        if (prefs == null) return true;
        String lang = prefs.getString("language", "ar");
        return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("ar");
    }

    private void applyTtsLocale() {
        if (tts == null || !ttsReady) return;
        try {
            tts.setLanguage(arabic() ? new Locale("ar", "SA") : Locale.US);
            tts.setSpeechRate(prefs == null ? 0.95f : prefs.getFloat("tts_rate", 0.95f));
        } catch (Throwable ignore) {}
    }

    private void speak(final String s) {
        if (s == null) return;
        ui.post(() -> {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) am.requestAudioFocus(null,
                        AudioManager.STREAM_NOTIFICATION,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } catch (Throwable ignore) {}
            if (tts != null && ttsReady) {
                applyTtsLocale();
                tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "ocr-" + System.currentTimeMillis());
            }
        });
    }
}
