// LiveSceneGuidanceController.swift
//
// iOS counterpart to Android's LiveWalkingController +
// LiveWalkingService (v3.2). Captures one JPEG every 2 seconds from
// the back camera via AVCaptureSession, sends it to Gemini in JSON
// mode with the live scene guidance prompt, and turns the response
// into:
//
//   1. Haptic feedback proportional to hazard.level
//        - stop     → 3 long pulses (notification .error)
//        - caution  → 2 short pulses (notification .warning)
//        - none     → silent
//   2. TTS announcement using a strict priority:
//        - hazard always speaks (never filtered)
//        - path speaks only on change and only when it is NOT a
//          near-duplicate of the last 3 spoken paths (Levenshtein
//          similarity ≥ 0.75 → suppress)
//        - scene speaks at most every 12 seconds
//   3. Rolling 3-frame summary fed back into the next prompt so
//      Gemini stops re-narrating the same corridor.
//
// Why no foreground "service" equivalent
// ──────────────────────────────────────
// iOS prohibits camera access from the background. The session is
// torn down automatically when the app backgrounds (the user's
// blind-walking session ends at the lock-screen, by design).
// We don't fight this — we surface a clear "Stopped" status when
// the app enters the background.
//
// Threading
// ─────────
// AVCaptureSession startup + frame capture happens on a dedicated
// session queue. Gemini calls run via async/await on the cooperative
// pool. UI bindings (@Published) are written on the main actor so
// SwiftUI can observe without dispatch hops.

#if canImport(UIKit)
import AVFoundation
import Combine
import CoreLocation
import Foundation
import UIKit

@MainActor
final class LiveSceneGuidanceController: NSObject, ObservableObject {

    // ───── Published UI state ─────

    @Published private(set) var isRunning = false
    @Published private(set) var lastStatus: String = ""
    @Published private(set) var lastLine: String = ""
    @Published private(set) var hazardLevel: String = "none"
    @Published private(set) var errorMessage: String?

    // ───── Tuning constants (match Android) ─────

    private let captureInterval: TimeInterval = 2.0
    private let sceneRepeatInterval: TimeInterval = 12.0
    private let pathHistorySize = 3
    private let pathSimilarityThreshold = 0.75

    // ───── Capture pipeline ─────

    private let captureSession = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private let sessionQueue = DispatchQueue(label: "basir.live_scene.session")
    private var captureTimer: Timer?
    private var aiBusy = false
    private var sessionConfigured = false
    private var photoDelegate: PhotoDelegate?

    // ───── Inputs (mutable so the View can re-apply user settings at
    //   each Start without having to rebuild the @StateObject). ─────

    private var arabic: Bool
    private var useGps: Bool

    /// Re-apply user settings before a fresh start(). Has no effect
    /// while a session is already running.
    func configure(arabic: Bool, useGps: Bool) {
        guard !isRunning else { return }
        self.arabic = arabic
        self.useGps = useGps
    }

    // ───── Rolling state ─────

    private var recentSummaries: [String] = []
    private var recentSpokenPaths: [String] = []
    private var lastSpokenPath = ""
    private var lastSceneSpokenAt: Date = .distantPast
    private var locationLabel: String?

    init(arabic: Bool, useGps: Bool) {
        self.arabic = arabic
        self.useGps = useGps
        super.init()
    }

    // ───── Public API ─────

    /// Start the guidance loop. Idempotent. Posts an error to
    /// errorMessage when the camera or Gemini key is unusable.
    func start() {
        guard !isRunning else { return }
        let key = KeychainStore.geminiKey()
        guard !key.isEmpty else {
            errorMessage = arabic
                ? "يجب إعداد مفتاح Gemini قبل تشغيل الوصف المباشر."
                : "A Gemini key must be configured before live scene guidance can start."
            return
        }
        isRunning = true
        errorMessage = nil
        lastStatus = arabic ? "جارٍ تهيئة الكاميرا..." : "Preparing camera..."
        if useGps { Task { await self.fetchLocationOnce() } }
        sessionQueue.async { [weak self] in self?.configureAndStartSession() }
    }

    /// Stop the loop. Idempotent.
    func stop() {
        guard isRunning else { return }
        isRunning = false
        captureTimer?.invalidate()
        captureTimer = nil
        let captureSession = self.captureSession
        sessionQueue.async {
            if captureSession.isRunning {
                captureSession.stopRunning()
            }
        }
        recentSummaries.removeAll()
        recentSpokenPaths.removeAll()
        lastSpokenPath = ""
        lastSceneSpokenAt = .distantPast
        hazardLevel = "none"
        lastStatus = arabic ? "متوقف." : "Stopped."
    }

    // ───── AVCaptureSession setup ─────

    private func configureAndStartSession() {
        // Permission: the view is expected to have asked first. If
        // it's still .notDetermined we bounce through requestAccess
        // here so a misconfigured caller doesn't silently get nothing.
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        if status == .notDetermined {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    if granted { self.configureAndStartSession() }
                    else { self.fail("camera_denied") }
                }
            }
            return
        }
        if status == .denied || status == .restricted {
            fail("camera_denied"); return
        }

        let needConfig = !sessionConfigured
        let captureSession = self.captureSession
        let photoOutput = self.photoOutput
        sessionQueue.async { [weak self] in
            if needConfig {
                captureSession.beginConfiguration()
                captureSession.sessionPreset = .high
                // v3.2 parity — prefer the WIDEST back camera so the
                // ultra-wide field of view catches side-approaching
                // obstacles a 24-28mm "main" lens would crop out.
                let widest = AVCaptureDevice.default(.builtInUltraWideCamera,
                                                      for: .video, position: .back)
                    ?? AVCaptureDevice.default(.builtInWideAngleCamera,
                                                 for: .video, position: .back)
                    ?? AVCaptureDevice.default(for: .video)
                guard let camera = widest,
                      let input = try? AVCaptureDeviceInput(device: camera) else {
                    captureSession.commitConfiguration()
                    Task { @MainActor in self?.fail("camera_open_failed") }
                    return
                }
                if captureSession.canAddInput(input) { captureSession.addInput(input) }
                if captureSession.canAddOutput(photoOutput) { captureSession.addOutput(photoOutput) }
                photoOutput.isHighResolutionCaptureEnabled = false
                captureSession.commitConfiguration()
            }
            if !captureSession.isRunning { captureSession.startRunning() }
            Task { @MainActor [weak self] in
                self?.sessionConfigured = true
                self?.startCaptureTimer()
            }
        }
    }

    private func startCaptureTimer() {
        lastStatus = arabic ? "بدأ المسح..." : "Scanning started..."
        captureTimer?.invalidate()
        // Fire immediately, then every captureInterval.
        triggerCapture()
        captureTimer = Timer.scheduledTimer(
            withTimeInterval: captureInterval,
            repeats: true
        ) { [weak self] _ in
            Task { @MainActor in self?.triggerCapture() }
        }
    }

    private func triggerCapture() {
        guard isRunning else { return }
        // Skip when a previous frame's AI call is still in flight —
        // mirrors the aiBusy compareAndSet on Android.
        if aiBusy { return }
        let captureSession = self.captureSession
        let photoOutput = self.photoOutput
        sessionQueue.async { [weak self] in
            guard captureSession.isRunning else { return }
            let settings = AVCapturePhotoSettings(
                format: [AVVideoCodecKey: AVVideoCodecType.jpeg]
            )
            settings.isHighResolutionPhotoEnabled = false
            // Hold a strong reference for the duration of the capture
            // callback; AVCapturePhotoOutput only holds it weakly.
            let delegate = PhotoDelegate { data in
                Task { @MainActor in self?.onJpegReady(data) }
            }
            Task { @MainActor in self?.photoDelegate = delegate }
            photoOutput.capturePhoto(with: settings, delegate: delegate)
        }
    }

    private func onJpegReady(_ jpeg: Data?) {
        guard isRunning, let jpeg else { return }
        aiBusy = true
        Task { [weak self] in
            await self?.sendToAi(jpeg: jpeg)
            self?.aiBusy = false
        }
    }

    // ───── Gemini round trip ─────

    private func sendToAi(jpeg: Data) async {
        do {
            let prompt = GeminiPrompts.liveSceneGuidancePrompt(
                arabic: arabic,
                recentSummaries: snapshotRecentSummaries(),
                locationLabel: locationLabel)
            // Route through the configured provider so Proxy mode
            // works too. The Direct provider switches to JSON mode
            // internally when it sees task == .liveScene; the Proxy
            // provider passes the prompt through verbatim and the
            // upstream server / Gemini still returns a JSON string.
            let text = try await AiProviderFactory.current().ask(
                task: .liveScene,
                input: prompt,
                instruction: nil,
                language: arabic ? .arabic : .english,
                imageData: jpeg,
                mimeType: "image/jpeg")
            let trimmed = stripJsonFence(text)
            guard let data = trimmed.data(using: .utf8),
                  let obj = try? JSONSerialization.jsonObject(with: data)
                            as? [String: Any] else {
                throw GeminiError.decode("liveScene response was not JSON")
            }
            handleResponse(obj)
        } catch {
            // Silent skip: a single failed frame should not break the
            // session — the next one in 2 seconds usually succeeds.
            let mapped = UserFriendlyErrorMapper.map(error)
            lastStatus = (arabic
                          ? "تخطّي إطار: "
                          : "Skipped frame: ") + Self.shortError(mapped)
        }
    }

    /// Strip ```json fences when an upstream proxy doesn't honour
    /// JSON mode and Gemini wraps the object in markdown. The Direct
    /// path already returns bare JSON via responseMimeType, so this
    /// is a no-op there.
    private func stripJsonFence(_ s: String) -> String {
        var t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.hasPrefix("```") {
            if let firstNewline = t.firstIndex(of: "\n") {
                t = String(t[t.index(after: firstNewline)...])
            }
            if t.hasSuffix("```") { t = String(t.dropLast(3)) }
        }
        return t.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func handleResponse(_ resp: [String: Any]) {
        let hazardObj = resp["hazard"] as? [String: Any] ?? [:]
        let level = (hazardObj["level"] as? String) ?? "none"
        let hazardDesc = (hazardObj["description"] as? String) ?? ""
        let path = ((resp["path"] as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let scene = ((resp["scene"] as? String) ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        // Update rolling summary so the next prompt can say "don't repeat".
        let summary = "hazard=\(level) path=\"\(path)\""
            + (scene.isEmpty ? "" : " scene=\"\(scene)\"")
        recentSummaries.append(summary)
        if recentSummaries.count > 3 { recentSummaries.removeFirst() }

        // Haptics
        fireHaptic(forLevel: level)

        // Priority-based speech (hazard always; path filtered; scene rare).
        var toSpeak: String? = nil
        let hazardActive = (level.lowercased() == "stop"
                            || level.lowercased() == "caution")
                            && !hazardDesc.isEmpty
        if hazardActive {
            toSpeak = hazardDesc
        } else if !path.isEmpty
                  && path.lowercased() != lastSpokenPath.lowercased()
                  && !isNearDuplicatePath(path) {
            toSpeak = path
            lastSpokenPath = path
            recordSpokenPath(path)
        } else if !scene.isEmpty
                  && Date().timeIntervalSince(lastSceneSpokenAt) > sceneRepeatInterval {
            toSpeak = scene
            lastSceneSpokenAt = Date()
        }

        self.hazardLevel = level
        let statusText = arabic
            ? "آخر مسح: " + (path.isEmpty ? "—" : path)
            : "Last scan: " + (path.isEmpty ? "—" : path)
        self.lastStatus = statusText
        if let spoken = toSpeak {
            self.lastLine = spoken
            SpeechSynthesizer.shared.speak(spoken, utteranceId: "live_scene")
            ArchiveStore.shared.appendLog(type: "live_scene", content: spoken)
        }
    }

    // ───── Haptics (mirrors Android's vibrateForLevel) ─────

    private func fireHaptic(forLevel level: String) {
        switch level.lowercased() {
        case "stop":
            // Three discrete impacts ~120 ms apart so the user can
            // distinguish the count even through clothing / a pocket.
            let gen = UIImpactFeedbackGenerator(style: .heavy)
            gen.prepare()
            gen.impactOccurred()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) { gen.impactOccurred() }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.36) { gen.impactOccurred() }
        case "caution":
            // Two medium impacts.
            let gen = UIImpactFeedbackGenerator(style: .medium)
            gen.prepare()
            gen.impactOccurred()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.14) { gen.impactOccurred() }
        default:
            break
        }
    }

    // ───── Levenshtein-based dedup (mirrors Android v3.2) ─────

    private func recordSpokenPath(_ path: String) {
        recentSpokenPaths.append(path)
        while recentSpokenPaths.count > pathHistorySize {
            recentSpokenPaths.removeFirst()
        }
    }

    private func isNearDuplicatePath(_ candidate: String) -> Bool {
        let norm = normalise(candidate)
        guard !norm.isEmpty else { return false }
        for past in recentSpokenPaths {
            let pn = normalise(past)
            guard !pn.isEmpty else { continue }
            let dist = Self.levenshtein(norm, pn)
            let maxLen = max(norm.count, pn.count)
            let similarity = 1.0 - Double(dist) / Double(maxLen)
            if similarity >= pathSimilarityThreshold { return true }
        }
        return false
    }

    private func normalise(_ s: String) -> String {
        s.trimmingCharacters(in: .whitespacesAndNewlines)
         .lowercased()
         .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    }

    private static func levenshtein(_ a: String, _ b: String) -> Int {
        if a == b { return 0 }
        let aChars = Array(a); let bChars = Array(b)
        if aChars.isEmpty { return bChars.count }
        if bChars.isEmpty { return aChars.count }
        // Keep the inner buffer along the shorter side.
        let (s, t) = aChars.count <= bChars.count
            ? (aChars, bChars) : (bChars, aChars)
        let n = s.count; let m = t.count
        var prev = [Int](0...n)
        var curr = [Int](repeating: 0, count: n + 1)
        for i in 1...m {
            curr[0] = i
            let bi = t[i - 1]
            for j in 1...n {
                let cost = s[j - 1] == bi ? 0 : 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1),
                              prev[j - 1] + cost)
            }
            swap(&prev, &curr)
        }
        return prev[n]
    }

    // ───── GPS one-shot + reverse geocode ─────

    private func fetchLocationOnce() async {
        let loc = await LocationService.shared.fetchOnce()
        guard let loc else { return }
        let geocoder = CLGeocoder()
        let placemarks = try? await geocoder.reverseGeocodeLocation(loc)
        let label: String = {
            if let p = placemarks?.first {
                let joined = [p.thoroughfare, p.subLocality, p.locality, p.country]
                    .compactMap { $0 }
                    .filter { !$0.isEmpty }
                    .joined(separator: ", ")
                if !joined.isEmpty { return joined }
            }
            return String(format: "%.4f,%.4f",
                          loc.coordinate.latitude, loc.coordinate.longitude)
        }()
        locationLabel = label
        lastStatus = (arabic ? "تم تحديد الموقع: " : "Location set: ") + label
    }

    // ───── Helpers ─────

    private func snapshotRecentSummaries() -> String {
        if recentSummaries.isEmpty { return "(none)" }
        return recentSummaries.enumerated()
            .map { "\($0.offset + 1)) \($0.element)" }
            .joined(separator: "\n")
    }

    private func fail(_ code: String) {
        isRunning = false
        captureTimer?.invalidate()
        captureTimer = nil
        switch code {
        case "camera_denied":
            errorMessage = arabic
                ? "إذن الكاميرا مرفوض. فعّله من الإعدادات."
                : "Camera permission is denied. Enable it in Settings."
        case "camera_open_failed":
            errorMessage = arabic
                ? "تعذّر فتح الكاميرا على هذا الجهاز."
                : "Could not open the camera on this device."
        default:
            errorMessage = arabic ? "حدث خطأ غير معروف." : "Unknown error."
        }
        lastStatus = arabic ? "متوقف بسبب خطأ." : "Stopped due to an error."
    }

    private static func shortError(_ s: String) -> String {
        s.count > 80 ? String(s.prefix(80)) + "…" : s
    }
}

// MARK: - PhotoDelegate

/// AVCapturePhotoOutput only holds the delegate weakly, so we own
/// it from the controller for the duration of each capture.
private final class PhotoDelegate: NSObject, AVCapturePhotoCaptureDelegate {
    private let onJpeg: (Data?) -> Void
    init(onJpeg: @escaping (Data?) -> Void) { self.onJpeg = onJpeg }

    func photoOutput(_ output: AVCapturePhotoOutput,
                     didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {
        onJpeg(photo.fileDataRepresentation())
    }
}
#endif
