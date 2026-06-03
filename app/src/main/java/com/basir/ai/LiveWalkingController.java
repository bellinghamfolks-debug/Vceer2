package com.basir.ai;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v3.1 — live walking mode for blind users.
 *
 * Why a custom controller (not Gemini's Live API)
 * ───────────────────────────────────────────────
 *   Gemini's bidirectional Multimodal Live API would need a WebSocket
 *   client, an audio capture / playback pipeline, and a streaming
 *   protocol — easily 2,000+ lines plus an external library that
 *   would break Basir's "zero dependencies, 160 KB APK" architecture.
 *   For blind walking guidance the critical metric is "accurate
 *   warning of the next hazard," not "frames per second of narration."
 *   A 2-second capture cadence with structured-JSON output and
 *   priority-based speech delivery hits the same UX goal at ~5% of
 *   the implementation cost.
 *
 * What it does
 * ────────────
 *   1. Opens the back camera via Camera2 and configures an ImageReader
 *      for JPEG capture at 1280×960. No preview surface — a blind user
 *      doesn't need one, and skipping the TextureView setup keeps the
 *      code compact and the battery cooler.
 *   2. Captures one frame every CAPTURE_INTERVAL_MS, gated by whether
 *      the previous frame's AI call is still in flight (no concurrent
 *      calls — keeps cost bounded and prevents out-of-order speech).
 *   3. Sends each JPEG to Gemini Flash with a tight prompt + JSON
 *      schema asking for {hazard, path, scene} only. Flash is forced
 *      regardless of the doc-quality preset because navigation needs
 *      <1 s response latency, not Pro-level fidelity.
 *   4. Maps the structured response into:
 *        • haptic feedback proportional to hazard.level (stop = long
 *          triple, caution = double short, none = silent)
 *        • TTS announcement using a strict priority: hazard always,
 *          path only when it CHANGED from the previous frame, scene
 *          only every SCENE_REPEAT_MS to avoid verbosity overload.
 *   5. Maintains a rolling 3-frame summary so the prompt instructs
 *      Gemini "do not repeat the previous descriptions" — this is the
 *      key to avoiding the "narrate every wall, every step, every
 *      object every two seconds" failure mode that earlier walking-
 *      assistant attempts hit.
 *
 * Lifecycle
 * ─────────
 *   The controller is bound to the calling Activity. {@link #start}
 *   opens the camera + AI loop. {@link #stop} stops the loop, closes
 *   the camera, cancels any pending speech, and is safe to call any
 *   number of times (idempotent).
 *
 * Threading
 * ─────────
 *   Camera callbacks run on a dedicated HandlerThread.
 *   AI calls run on a single-thread ExecutorService.
 *   TTS speak() + listener callbacks dispatch to the main thread so
 *   the calling Activity can update UI / vibrate / etc. without extra
 *   handler hops.
 */
public final class LiveWalkingController {

    public interface Listener {
        /** State transition callback — for UI labels like "scanning",
         *  "speaking", "paused". */
        void onStatusText(String text);
        /** A line ready for TTS. Already filtered by priority/repeat
         *  rules. Empty when there is nothing new to say. */
        void onSpoken(String text);
        /** Last announced hazard level ("stop" | "caution" | "none"). */
        void onHazard(String level, String description);
        /** Hard error — caller should stop the controller. */
        void onError(String message);
    }

    // ───── Tuning constants ─────

    /** Time between successive captures. 2 seconds is the sweet spot:
     *  fast enough for walking pace, slow enough to keep cost under
     *  ~$1/min on Flash and to avoid speech-clobbering the user. */
    private static final long CAPTURE_INTERVAL_MS = 2_000L;

    /** How often "scene" lines are spoken even when nothing changed.
     *  Hazards always speak. Path speaks on change. Scene is the
     *  ambient line — kept rare to avoid verbosity overload. */
    private static final long SCENE_REPEAT_MS = 12_000L;

    /** Capture size sent to Gemini. 1280×960 balances Flash latency
     *  (smaller = faster) with detail (large enough to see a curb
     *  edge ~3 m ahead). */
    private static final int CAPTURE_W = 1280;
    private static final int CAPTURE_H = 960;

    // ───── Dependencies (passed in) ─────

    private final Context appCtx;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final boolean arabic;
    /** v3.1.1 — when true, the controller fetches one location reading
     *  at start() and includes a "you are near X" hint in each prompt
     *  so Gemini can interpret visual cues (street signs, landmarks,
     *  shop names) against the right city / neighborhood. */
    private final boolean useGps;
    /** Reverse-geocoded label like "Near King Fahd Road, Riyadh". Null
     *  when GPS is off, denied, or the fetch failed. */
    private volatile String locationLabel;

    // ───── State ─────

    private CameraManager cameraMgr;
    private CameraDevice cameraDevice;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private HandlerThread bgThread;
    private Handler bgHandler;
    private Handler mainHandler;
    private ExecutorService aiExec;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean aiBusy = new AtomicBoolean(false);
    private long lastCaptureScheduledAt;
    /** Rolling 3-frame summary so the prompt can say "don't repeat". */
    private final Deque<String> recentSummaries = new ArrayDeque<>();
    private String lastSpokenPath = "";
    private String lastHazardLevel = "none";
    private long lastSceneSpokenAt = 0L;

    public LiveWalkingController(Context ctx,
                                  SharedPreferences prefs,
                                  boolean arabic,
                                  boolean useGps,
                                  Listener listener) {
        this.appCtx = ctx.getApplicationContext();
        this.prefs = prefs;
        this.arabic = arabic;
        this.useGps = useGps;
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /** Back-compat constructor (default no-GPS) for callers that haven't
     *  been updated yet. */
    public LiveWalkingController(Context ctx,
                                  SharedPreferences prefs,
                                  boolean arabic,
                                  Listener listener) {
        this(ctx, prefs, arabic, false, listener);
    }

    // ───── Public API ─────

    /** Start the live walking loop. Requires CAMERA permission already
     *  granted (the caller checks first). Idempotent. */
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        if (!hasCameraPermission()) {
            running.set(false);
            postError(arabic
                    ? "إذن الكاميرا مطلوب لوضع البث المباشر."
                    : "Camera permission is required for live mode.");
            return;
        }
        if (!AiClient.isConfigured(prefs)) {
            running.set(false);
            postError(arabic
                    ? "يجب إعداد Gemini أولاً."
                    : "Gemini must be set up first.");
            return;
        }
        bgThread = new HandlerThread("BasirLiveWalking");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());
        aiExec = Executors.newSingleThreadExecutor();
        // v3.1.1 — kick off a one-shot location fetch in parallel with
        // the camera open. We do NOT wait on it: the first frame is
        // sent without a location hint; from the second frame onward
        // (if GPS resolved) the hint is included.
        if (useGps) bgHandler.post(this::fetchLocationOnce);
        postStatus(arabic ? "جاري فتح الكاميرا..." : "Opening camera...");
        bgHandler.post(this::openCamera);
    }

    /** Stop the loop. Closes the camera, cancels pending AI calls,
     *  resets all rolling state. Idempotent. */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        postStatus(arabic ? "إيقاف..." : "Stopping...");
        try {
            if (session != null) {
                try { session.close(); } catch (Throwable ignore) {}
                session = null;
            }
            if (cameraDevice != null) {
                try { cameraDevice.close(); } catch (Throwable ignore) {}
                cameraDevice = null;
            }
            if (imageReader != null) {
                try { imageReader.close(); } catch (Throwable ignore) {}
                imageReader = null;
            }
            if (aiExec != null) {
                aiExec.shutdownNow();
                aiExec = null;
            }
            if (bgThread != null) {
                bgThread.quitSafely();
                bgThread = null;
                bgHandler = null;
            }
        } finally {
            recentSummaries.clear();
            lastSpokenPath = "";
            lastHazardLevel = "none";
            lastSceneSpokenAt = 0L;
            postStatus(arabic ? "متوقف." : "Stopped.");
        }
    }

    public boolean isRunning() { return running.get(); }

    // ───── Camera2 setup ─────

    private void openCamera() {
        try {
            cameraMgr = (CameraManager) appCtx.getSystemService(Context.CAMERA_SERVICE);
            String backId = pickBackCameraId();
            if (backId == null) {
                postError(arabic
                        ? "لا توجد كاميرا خلفية على هذا الجهاز."
                        : "No back camera on this device.");
                running.set(false);
                return;
            }
            imageReader = ImageReader.newInstance(CAPTURE_W, CAPTURE_H,
                    ImageFormat.JPEG, /*maxImages*/ 2);
            imageReader.setOnImageAvailableListener(this::onImageReady, bgHandler);

            // SuppressLint("MissingPermission"): we explicitly checked
            // hasCameraPermission() in start().
            cameraMgr.openCamera(backId, deviceCallback, bgHandler);
        } catch (CameraAccessException | SecurityException e) {
            postError((arabic ? "تعذر فتح الكاميرا: " : "Could not open camera: ")
                    + (e.getMessage() == null ? "" : e.getMessage()));
            running.set(false);
        }
    }

    private String pickBackCameraId() throws CameraAccessException {
        for (String id : cameraMgr.getCameraIdList()) {
            CameraCharacteristics ch = cameraMgr.getCameraCharacteristics(id);
            Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        // Fall back to whatever camera the device exposes (front-only
        // tablets) so the feature degrades instead of failing outright.
        String[] ids = cameraMgr.getCameraIdList();
        return ids.length > 0 ? ids[0] : null;
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice device) {
            cameraDevice = device;
            createSession();
        }
        @Override public void onDisconnected(CameraDevice device) {
            try { device.close(); } catch (Throwable ignore) {}
            cameraDevice = null;
        }
        @Override public void onError(CameraDevice device, int error) {
            try { device.close(); } catch (Throwable ignore) {}
            cameraDevice = null;
            postError((arabic ? "خطأ في الكاميرا، الرمز: "
                              : "Camera error code: ") + error);
            running.set(false);
        }
    };

    private void createSession() {
        try {
            cameraDevice.createCaptureSession(
                    Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            postStatus(arabic ? "بدأ الفحص..." : "Scanning started...");
                            scheduleNextCapture(0);
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            postError(arabic ? "فشل تهيئة الكاميرا."
                                              : "Camera session config failed.");
                            running.set(false);
                        }
                    }, bgHandler);
        } catch (CameraAccessException e) {
            postError(e.getMessage());
            running.set(false);
        }
    }

    // ───── Capture loop ─────

    private void scheduleNextCapture(long delayMs) {
        if (!running.get() || bgHandler == null) return;
        lastCaptureScheduledAt = System.currentTimeMillis();
        bgHandler.postDelayed(this::triggerCapture, delayMs);
    }

    private void triggerCapture() {
        if (!running.get() || session == null || cameraDevice == null) return;
        // Skip this tick if the previous frame's AI call is still in
        // flight. Concurrent calls would compete for TTS attention and
        // could deliver speech out-of-order.
        if (aiBusy.get()) {
            scheduleNextCapture(CAPTURE_INTERVAL_MS);
            return;
        }
        try {
            CaptureRequest.Builder req = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE);
            req.addTarget(imageReader.getSurface());
            req.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            req.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
            req.set(CaptureRequest.JPEG_QUALITY, (byte) 85);
            // Most phones have a sensor orientation that doesn't match
            // portrait; let the JPEG carry the rotation EXIF tag.
            req.set(CaptureRequest.JPEG_ORIENTATION, 90);
            session.capture(req.build(), null, bgHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            // Session may have been closed concurrently with stop().
            // Don't propagate as an error in that case.
            if (running.get()) postError(e.getMessage());
        }
    }

    private void onImageReady(ImageReader reader) {
        if (!running.get()) return;
        byte[] jpeg = null;
        try (Image img = reader.acquireLatestImage()) {
            if (img == null) {
                scheduleNextCapture(CAPTURE_INTERVAL_MS);
                return;
            }
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            jpeg = new byte[buf.remaining()];
            buf.get(jpeg);
        } catch (Throwable t) {
            if (running.get()) postError(t.getMessage());
            scheduleNextCapture(CAPTURE_INTERVAL_MS);
            return;
        }
        sendToAi(jpeg);
    }

    // ───── AI request ─────

    private void sendToAi(final byte[] jpeg) {
        if (!aiBusy.compareAndSet(false, true)) {
            scheduleNextCapture(CAPTURE_INTERVAL_MS);
            return;
        }
        final ExecutorService exec = aiExec;
        if (exec == null) { aiBusy.set(false); return; }
        exec.execute(() -> {
            try {
                String b64 = AiClient.encodeBase64(jpeg);
                String key = SecurePrefs.getGeminiKey(prefs);
                // Flash forced — navigation latency beats Pro fidelity.
                String model = AiClient.modelForQuality(prefs, AiClient.QUALITY_BALANCED);
                String prompt = AiClient.liveWalkingPrompt(arabic,
                        snapshotRecentSummariesAsText(),
                        locationLabel);  // null when GPS off / unresolved
                JSONObject schema = AiClient.liveWalkingSchema();
                JSONObject response = GeminiDirectClient.generateJsonWithImage(
                        key, model,
                        "You are Basir, an assistant for blind and low-vision users.",
                        prompt, b64, "image/jpeg", schema, /*maxOutputTokens*/ 1024);
                handleResponse(response);
            } catch (Throwable t) {
                postStatus(arabic
                        ? "تخطّي إطار: " + safeShort(t.getMessage())
                        : "Skipped frame: " + safeShort(t.getMessage()));
            } finally {
                aiBusy.set(false);
                scheduleNextCapture(CAPTURE_INTERVAL_MS);
            }
        });
    }

    /**
     * v3.1.1 — best-effort one-shot location resolution on the
     * background handler. Uses LocationManager.getLastKnownLocation
     * (no continuous listening; we don't want a battery drain) and
     * Geocoder.getFromLocation for a human-readable label.
     *
     * Failure modes are silent: if permission is missing, no provider
     * has a cached fix, the Geocoder throws, or we hit an IOException,
     * locationLabel stays null and the prompt simply omits the hint.
     * Blind walking guidance must never block on a slow GPS provider.
     */
    private void fetchLocationOnce() {
        try {
            if (!hasLocationPermission()) return;
            LocationManager lm = (LocationManager)
                    appCtx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            Location best = null;
            try {
                Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (gps != null && net != null) {
                    best = gps.getTime() > net.getTime() ? gps : net;
                } else {
                    best = gps != null ? gps : net;
                }
            } catch (SecurityException ignore) {
                return;
            }
            if (best == null) return;

            String label = reverseGeocode(best);
            if (label != null && !label.isEmpty()) {
                locationLabel = label;
                postStatus(arabic
                        ? "تم تحديد الموقع: " + label
                        : "Location set: " + label);
            } else {
                // Even bare coordinates help Gemini disambiguate
                // (knows whether you're in Riyadh vs Cairo etc).
                locationLabel = String.format(java.util.Locale.US,
                        "%.4f,%.4f", best.getLatitude(), best.getLongitude());
            }
        } catch (Throwable ignore) {
            // Defensive: location is a nice-to-have, never a blocker.
        }
    }

    private boolean hasLocationPermission() {
        return appCtx.checkSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            || appCtx.checkSelfPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private String reverseGeocode(Location loc) {
        if (!Geocoder.isPresent()) return null;
        try {
            Geocoder gc = new Geocoder(appCtx,
                    arabic ? new java.util.Locale("ar", "SA")
                           : java.util.Locale.US);
            java.util.List<Address> list = gc.getFromLocation(
                    loc.getLatitude(), loc.getLongitude(), 1);
            if (list == null || list.isEmpty()) return null;
            Address a = list.get(0);
            // Build a "<thoroughfare>, <locality>, <country>" label —
            // skipping segments the geocoder didn't resolve. Streets +
            // neighborhood are the most useful disambiguators; country
            // ensures the city is unambiguous.
            StringBuilder sb = new StringBuilder();
            String street = a.getThoroughfare();
            String subLoc = a.getSubLocality();
            String loc1 = a.getLocality();
            String country = a.getCountryName();
            if (street != null && !street.isEmpty()) sb.append(street);
            if (subLoc != null && !subLoc.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(subLoc);
            }
            if (loc1 != null && !loc1.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(loc1);
            }
            if (country != null && !country.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(country);
            }
            return sb.toString();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private String snapshotRecentSummariesAsText() {
        synchronized (recentSummaries) {
            if (recentSummaries.isEmpty()) return "(none)";
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (String s : recentSummaries) {
                sb.append(i++).append(") ").append(s).append('\n');
            }
            return sb.toString().trim();
        }
    }

    private void handleResponse(JSONObject resp) {
        JSONObject hazard = resp.optJSONObject("hazard");
        String level = hazard != null ? hazard.optString("level", "none") : "none";
        String hazardDesc = hazard != null ? hazard.optString("description", "") : "";
        String path = resp.optString("path", "").trim();
        String scene = resp.optString("scene", "").trim();

        // ── Update rolling summary ──
        String summary = "hazard=" + level
                + " path=\"" + path + "\""
                + (scene.isEmpty() ? "" : " scene=\"" + scene + "\"");
        synchronized (recentSummaries) {
            recentSummaries.addLast(summary);
            while (recentSummaries.size() > 3) recentSummaries.removeFirst();
        }

        // ── Vibration mapping ──
        vibrateForLevel(level);

        // ── Priority-based speech ──
        String toSpeak = null;
        boolean hazardActive = ("stop".equalsIgnoreCase(level)
                || "caution".equalsIgnoreCase(level))
                && !hazardDesc.isEmpty();
        if (hazardActive) {
            toSpeak = hazardDesc;
        } else if (!path.isEmpty() && !path.equalsIgnoreCase(lastSpokenPath)) {
            toSpeak = path;
            lastSpokenPath = path;
        } else if (!scene.isEmpty()
                && System.currentTimeMillis() - lastSceneSpokenAt > SCENE_REPEAT_MS) {
            toSpeak = scene;
            lastSceneSpokenAt = System.currentTimeMillis();
        }

        final String finalSpoken = toSpeak;
        final String finalLevel = level;
        final String finalHazardDesc = hazardDesc;
        mainHandler.post(() -> {
            listener.onHazard(finalLevel, finalHazardDesc);
            if (finalSpoken != null) listener.onSpoken(finalSpoken);
            String status = arabic
                    ? "آخر مسح: " + (path.isEmpty() ? "—" : path)
                    : "Last scan: " + (path.isEmpty() ? "—" : path);
            listener.onStatusText(status);
        });
        lastHazardLevel = level;
    }

    private void vibrateForLevel(String level) {
        boolean enabled = prefs.getBoolean("vibration_enabled", true);
        if (!enabled) return;
        Vibrator v = (Vibrator) appCtx.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        long[] pattern;
        if ("stop".equalsIgnoreCase(level)) {
            // Three long pulses — "stop / hazard imminent"
            pattern = new long[]{ 0, 300, 150, 300, 150, 300 };
        } else if ("caution".equalsIgnoreCase(level)) {
            // Two short pulses — "needs attention"
            pattern = new long[]{ 0, 120, 100, 120 };
        } else {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        } catch (Throwable ignore) {}
    }

    // ───── Helpers ─────

    private boolean hasCameraPermission() {
        return appCtx.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void postError(String msg) {
        final String m = msg == null ? "" : msg;
        mainHandler.post(() -> listener.onError(m));
    }

    private void postStatus(String text) {
        final String t = text == null ? "" : text;
        mainHandler.post(() -> listener.onStatusText(t));
    }

    private static String safeShort(String s) {
        if (s == null) return "";
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
