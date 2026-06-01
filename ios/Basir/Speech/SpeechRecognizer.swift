// SpeechRecognizer.swift
// SFSpeechRecognizer wrapper. Equivalent to Android's VoiceController.
//
// Responsibilities
// ────────────────
//   - Request the two iOS permissions needed for live dictation:
//       1. NSSpeechRecognitionUsageDescription   (Apple's speech API)
//       2. NSMicrophoneUsageDescription          (audio capture)
//   - Run a single-shot dictation session: open mic, recognise, stop
//     after the user pauses, return final text.
//   - Coexist with SpeechSynthesizer: when TTS is mid-speech we hold
//     off opening the mic so the recogniser doesn't try to transcribe
//     our own voice.
//
// What it deliberately does NOT do
//   - Continuous dictation (background streaming). For the voice
//     conversation mode we wrap this in a loop driven by the TTS
//     "did finish" signal, not by holding the mic open indefinitely.

import Foundation
import Speech
import AVFoundation

@MainActor
final class SpeechRecognizer: ObservableObject {
    static let shared = SpeechRecognizer()

    enum AuthorizationState { case undetermined, denied, granted, restricted }

    @Published private(set) var isRecording: Bool = false
    @Published private(set) var transcript: String = ""
    @Published private(set) var authorization: AuthorizationState = .undetermined

    private let audioEngine = AVAudioEngine()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var recognizer: SFSpeechRecognizer?

    private init() {}

    /// Asks the user for both microphone and speech-recognition
    /// permission. Call once when entering any voice screen.
    func requestAuthorization() async -> AuthorizationState {
        // 1) Speech-recognition authorisation
        let speechStatus = await withCheckedContinuation { (cont: CheckedContinuation<SFSpeechRecognizerAuthorizationStatus, Never>) in
            SFSpeechRecognizer.requestAuthorization { status in
                cont.resume(returning: status)
            }
        }
        guard speechStatus == .authorized else {
            authorization = .denied
            return .denied
        }

        // 2) Microphone authorisation
        let micGranted = await withCheckedContinuation { (cont: CheckedContinuation<Bool, Never>) in
            AVAudioApplication.requestRecordPermission { granted in
                cont.resume(returning: granted)
            }
        }
        authorization = micGranted ? .granted : .denied
        return authorization
    }

    /// Start a one-shot dictation. The callback fires once with the
    /// final recognised text when the user pauses (or when stop() is
    /// called manually). Returns false if recognition is unavailable
    /// (no Google-style fallback on iOS — SFSpeechRecognizer is the
    /// only option Apple ships).
    @discardableResult
    func startDictation(language: AppLanguage,
                         onFinal: @escaping (String) -> Void) -> Bool {
        // Initialise recogniser for the right locale.
        let locale = language == .arabic
            ? Locale(identifier: "ar-SA")
            : Locale(identifier: "en-US")
        recognizer = SFSpeechRecognizer(locale: locale)
        guard let recognizer, recognizer.isAvailable else {
            return false
        }

        // Tear down any previous session.
        stop()
        transcript = ""

        do {
            // Audio session configured for recording while TTS may
            // still be active in the background. .duckOthers means
            // Apple Music will quiet down for us.
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord,
                                     mode: .measurement,
                                     options: [.duckOthers, .defaultToSpeaker])
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            recognitionRequest = request

            let inputNode = audioEngine.inputNode
            let format = inputNode.outputFormat(forBus: 0)
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                request.append(buffer)
            }

            audioEngine.prepare()
            try audioEngine.start()
            isRecording = true

            recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
                guard let self else { return }
                Task { @MainActor in
                    if let result {
                        self.transcript = result.bestTranscription.formattedString
                        if result.isFinal {
                            let text = self.transcript
                            self.stop()
                            onFinal(text)
                        }
                    }
                    if let _ = error {
                        let text = self.transcript
                        self.stop()
                        if !text.isEmpty { onFinal(text) }
                    }
                }
            }
            return true
        } catch {
            stop()
            return false
        }
    }

    func stop() {
        recognitionTask?.cancel()
        recognitionTask = nil

        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }

        recognitionRequest?.endAudio()
        recognitionRequest = nil
        isRecording = false

        try? AVAudioSession.sharedInstance().setActive(false,
                                                       options: .notifyOthersOnDeactivation)
    }
}
