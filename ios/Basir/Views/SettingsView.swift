// SettingsView.swift
// All preferences in one Form. iOS Form gives us the native iOS Settings
// look for free, and VoiceOver navigates it cleanly without extra work.

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var settings: BasirSettings
    @State private var apiKey: String = ""
    @State private var showSavedToast = false

    var body: some View {
        Form {
            Section(L10n.t("اللغة", "Language")) {
                Picker(L10n.t("لغة التطبيق", "App language"),
                       selection: Binding(
                        get: { settings.language },
                        set: { settings.language = $0 }
                       )) {
                    ForEach(AppLanguage.allCases) { lang in
                        Text(lang.displayName).tag(lang)
                    }
                }
            }

            Section(L10n.t("الصوت والاهتزاز", "Voice and vibration")) {
                Toggle(L10n.t("النطق الصوتي", "Speech output"),
                       isOn: $settings.speechEnabled)
                Toggle(L10n.t("الاهتزاز", "Vibration"),
                       isOn: $settings.vibrationEnabled)
                HStack {
                    Text(L10n.t("سرعة النطق", "Speech rate"))
                    Slider(value: $settings.ttsRate, in: 0.5...1.5, step: 0.1)
                        .accessibilityLabel(L10n.t("سرعة النطق", "Speech rate"))
                    Text(String(format: "%.1f×", settings.ttsRate))
                        .monospacedDigit()
                }
            }

            Section(L10n.t("الخصوصية", "Privacy")) {
                Toggle(L10n.t("وضع الخصوصية", "Privacy mode"),
                       isOn: $settings.privacyMode)
                Toggle(L10n.t("الحفظ التلقائي للنتائج", "Auto-save results"),
                       isOn: $settings.autoSaveResults)
            }

            Section(L10n.t("إعداد Gemini", "Gemini setup")) {
                SecureField(L10n.t("مفتاح Gemini API", "Gemini API key"),
                            text: $apiKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityLabel(L10n.t(
                        "حقل مفتاح Gemini API. النص محمي.",
                        "Gemini API key field. Text is hidden."
                    ))
                Button(L10n.t("حفظ المفتاح", "Save key")) {
                    KeychainStore.setGeminiKey(apiKey)
                    showSavedToast = true
                    apiKey = ""
                }
                .disabled(apiKey.trimmingCharacters(in: .whitespaces).isEmpty)

                Picker(L10n.t("جودة المهام السريعة", "Quick tasks quality"),
                       selection: $settings.quickQuality) {
                    Text(L10n.t("سريع · Flash Lite", "Fast · Flash Lite")).tag("fast")
                    Text(L10n.t("متوازن · Flash", "Balanced · Flash")).tag("balanced")
                    Text(L10n.t("الأدق · Pro", "Most accurate · Pro")).tag("best")
                }
                Picker(L10n.t("جودة تحويل المستندات", "Document conversion quality"),
                       selection: $settings.docQuality) {
                    Text(L10n.t("سريع · Flash Lite", "Fast · Flash Lite")).tag("fast")
                    Text(L10n.t("متوازن · Flash", "Balanced · Flash")).tag("balanced")
                    Text(L10n.t("الأدق · Pro", "Most accurate · Pro")).tag("best")
                }
            }

            Section(L10n.t("الطوارئ", "Emergency")) {
                TextField(L10n.t("جهة الطوارئ (مثال: +9665XXXXXXXX)",
                                  "Emergency contact (e.g. +9665XXXXXXXX)"),
                          text: $settings.emergencyContact)
                    .keyboardType(.phonePad)
            }
        }
        .navigationTitle(L10n.t("الإعدادات", "Settings"))
        .toolbar(.hidden, for: .tabBar)
        .alert(L10n.t("تم الحفظ", "Saved"), isPresented: $showSavedToast) {
            Button(L10n.t("حسناً", "OK"), role: .cancel) {}
        }
    }
}
