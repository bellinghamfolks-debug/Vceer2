// SpeechSynthesizer.swift
// AVSpeechSynthesizer wrapper. Equivalent to Android's TtsController.
//
// Responsibilities
// ────────────────
//   - Speak any string in the current app language at the user's
//     preferred rate.
//   - Configure the audio session so a system-wide media play
//     (e.g. music) ducks while Basir speaks, instead of fighting it.
//   - Track when a speak completes so the conversation loop knows
//     when to open the microphone next.
//
// Why Apple's AVSpeechSynthesizer (not third-party)
//   Same reasoning as the Android side: zero external dependencies,
//   ships with the OS, supports Arabic (Maged) and English (Samantha
//   / system default) out of the box.

import AVFoundation
import Combine

@MainActor
final class SpeechSynthesizer: NSObject, ObservableObject {
    static let shared = SpeechSynthesizer()

    @Published var isSpeaking: Bool = false

    /// Fired when a speak completes successfully. Listeners (e.g. the
    /// continuous conversation loop) subscribe to drive the next step.
    let didFinish = PassthroughSubject<String, Never>()

    private let engine = AVSpeechSynthesizer()
    /// utteranceId → string mapping, so didFinish can carry the same
    /// "what just finished" hint the Android TtsController.Host gets.
    private var pendingIds: [AVSpeechUtterance: String] = [:]

    override private init() {
        super.init()
        engine.delegate = self
        configureAudioSession()
    }

    /// Configure the audio session so Basir's TTS plays in a way that
    /// gracefully co-exists with other audio (Spotify ducking, calls
    /// taking priority). .playback + .duckOthers is the right category
    /// for an assistive app whose voice should be heard over music
    /// but pause for phone calls.
    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback,
                                 mode: .spokenAudio,
                                 options: [.duckOthers, .mixWithOthers])
        try? session.setActive(true)
    }

    /// Speak the text in the user-selected language. No-op when the
    /// "speech_enabled" preference is off (matches Android's gating).
    func speak(_ text: String, utteranceId: String = "basir") {
        guard BasirSettings.shared.speechEnabled else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        // Cancel anything currently mid-flight; speak() should never
        // queue. Last call wins.
        if engine.isSpeaking {
            engine.stopSpeaking(at: .immediate)
        }

        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = bestVoice(for: BasirSettings.shared.language)
        // iOS rate range is 0.0 ... 1.0 with 0.5 being normal.
        // Our setting is 0.5 ... 1.5 where 1.0 is normal; map it.
        let userRate = BasirSettings.shared.ttsRate
        let mapped = AVSpeechUtteranceDefaultSpeechRate * Float(userRate)
        utterance.rate = max(AVSpeechUtteranceMinimumSpeechRate,
                              min(AVSpeechUtteranceMaximumSpeechRate, mapped))
        utterance.pitchMultiplier = 1.0
        utterance.preUtteranceDelay = 0
        utterance.postUtteranceDelay = 0.05

        pendingIds[utterance] = utteranceId
        isSpeaking = true
        engine.speak(utterance)
    }

    func stop() {
        guard engine.isSpeaking else { return }
        engine.stopSpeaking(at: .immediate)
        pendingIds.removeAll()
        isSpeaking = false
    }

    /// Pick the best installed voice for a given language. iOS has
    /// multiple voices per language; the "premium" voice (when
    /// installed) is much better than the default compact one for
    /// Arabic in particular.
    private func bestVoice(for language: AppLanguage) -> AVSpeechSynthesisVoice? {
        let preferredCode = language.bcp47 == "ar" ? "ar-SA" : "en-US"
        let voices = AVSpeechSynthesisVoice.speechVoices()
        // 1) try exact locale + premium quality
        if let premium = voices.first(where: {
            $0.language == preferredCode && $0.quality == .premium
        }) { return premium }
        // 2) try exact locale + enhanced
        if let enhanced = voices.first(where: {
            $0.language == preferredCode && $0.quality == .enhanced
        }) { return enhanced }
        // 3) exact locale, any quality
        if let exact = voices.first(where: { $0.language == preferredCode }) {
            return exact
        }
        // 4) same primary language, different region
        if let same = voices.first(where: {
            $0.language.hasPrefix(language.bcp47)
        }) { return same }
        // 5) system default
        return AVSpeechSynthesisVoice(language: preferredCode)
    }
}

extension SpeechSynthesizer: AVSpeechSynthesizerDelegate {
    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer,
                                        didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            let id = self.pendingIds.removeValue(forKey: utterance) ?? "basir"
            self.isSpeaking = self.engine.isSpeaking
            self.didFinish.send(id)
        }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer,
                                        didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.pendingIds.removeValue(forKey: utterance)
            self.isSpeaking = self.engine.isSpeaking
        }
    }
}
