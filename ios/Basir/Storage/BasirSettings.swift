// BasirSettings.swift
// Centralised user-preference store. Wraps UserDefaults for everything
// EXCEPT the Gemini API key (which goes into Keychain via
// KeychainStore.swift).
//
// Observable so SwiftUI views can react to settings changes.

import Foundation
import SwiftUI

@MainActor
final class BasirSettings: ObservableObject {
    static let shared = BasirSettings()

    // MARK: - Language
    @AppStorage("language_raw") private var languageRaw: String = AppLanguage.arabic.rawValue

    var language: AppLanguage {
        get { AppLanguage(rawValue: languageRaw) ?? .arabic }
        set { languageRaw = newValue.rawValue; objectWillChange.send() }
    }

    // MARK: - Speech / accessibility
    @AppStorage("speech_enabled") var speechEnabled: Bool = true
    @AppStorage("vibration_enabled") var vibrationEnabled: Bool = true
    @AppStorage("tts_rate") var ttsRate: Double = 1.0       // 0.5 .. 1.5
    @AppStorage("font_step") var fontStep: Int = 0          // 0 .. 5

    // MARK: - Privacy
    @AppStorage("privacy_mode") var privacyMode: Bool = false
    @AppStorage("auto_save_results") var autoSaveResults: Bool = true

    // MARK: - AI mode + quality presets
    @AppStorage("ai_mode") var aiMode: String = "direct"    // "direct" | "proxy"
    @AppStorage("quick_quality") var quickQuality: String = "balanced"
    @AppStorage("doc_quality") var docQuality: String = "best"
    @AppStorage("ai_server_url") var proxyURL: String = ""
    @AppStorage("ai_app_token") var proxyToken: String = ""

    // MARK: - Translation
    @AppStorage("translate_src") var translateSource: String = "auto"
    @AppStorage("translate_tgt") var translateTarget: String = "en"

    // MARK: - Emergency
    @AppStorage("emergency_contact") var emergencyContact: String = ""

    // Note: the Gemini API key is NOT here. Use:
    //   KeychainStore.geminiKey()
    //   KeychainStore.setGeminiKey(_:)

    private init() {}

    var isConfigured: Bool {
        if aiMode == "direct" {
            return !KeychainStore.geminiKey().isEmpty
        }
        return proxyURL.hasPrefix("https://") || proxyURL.hasPrefix("http://")
    }

    /// Map a quality preset to the actual Gemini model ID. Matches
    /// AiClient.modelForQuality on Android.
    func modelForQuality(_ quality: String) -> String {
        switch quality {
        case "fast":     return "gemini-2.5-flash-lite"
        case "best":     return "gemini-2.5-pro"
        case "balanced": fallthrough
        default:         return "gemini-2.5-flash"
        }
    }

    func modelFor(task: TaskKind) -> String {
        // Live scene guidance ALWAYS uses Flash regardless of the user's
        // selected quality — navigation latency beats Pro-level fidelity
        // (matches Android LiveWalkingController's QUALITY_BALANCED pick).
        if task == .liveScene { return modelForQuality("balanced") }
        let quick: Set<TaskKind> = [.ask, .translate, .reply, .quick, .health,
                                     .medicalText, .legalText, .tableRead]
        let preset = quick.contains(task) ? quickQuality : docQuality
        return modelForQuality(preset)
    }
}

enum TaskKind: String {
    case ask, translate, reply, quick, health
    case describeImage = "describe_image"
    case altText = "alt_text"
    case screenshot
    case currencyOrReceipt = "currency_or_receipt"
    case medicalText = "medical_text"
    case legalText = "legal_text"
    case tableRead = "table_read"
    case mathExtract = "math_extract"
    case liveScene = "live_scene"
    case convert
    case askDocument = "ask_document"
}
