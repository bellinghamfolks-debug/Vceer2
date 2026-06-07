// TranslateView.swift
// Text translation between 20 languages. The full-file translation
// (PDF/DOCX/PPTX) entry is intentionally NOT in this scaffold because
// document conversion is deferred (see README).

import SwiftUI

struct TranslateView: View {
    @EnvironmentObject var settings: BasirSettings
    @State private var inputText: String = ""
    @State private var translation: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @FocusState private var inputFocused: Bool

    // The 20-language list lives in L10n.swift.
    private let allLanguages = L10n.supportedTranslationLanguages
    // Target spinner: drop the "auto" entry (you can't translate INTO auto).
    private var targetLanguages: [(code: String, ar: String, en: String)] {
        allLanguages.filter { $0.code != "auto" }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.t(
                    "اختر اللغة المصدر واللغة الهدف، ثم اكتب النص أو الصقه. راجع الأسماء والأرقام والمصطلحات المتخصصة قبل استخدام الترجمة.",
                    "Choose the source and target languages, then type or paste text. Check names, numbers, and specialized terms before using the translation."
                ))
                .font(.callout)
                .foregroundStyle(.secondary)

                // Source language
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.t("اللغة المصدر", "Source language"))
                        .font(.subheadline.bold())
                    Picker(L10n.t("اختر اللغة المصدر", "Choose source language"),
                            selection: $settings.translateSource) {
                        ForEach(allLanguages, id: \.code) { lang in
                            Text(BasirSettings.shared.language == .arabic ? lang.ar : lang.en)
                                .tag(lang.code)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityLabel(L10n.t(
                        "اختيار اللغة المصدر للترجمة",
                        "Select the source language for translation"
                    ))
                }

                // Target language
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.t("اللغة الهدف", "Target language"))
                        .font(.subheadline.bold())
                    Picker(L10n.t("اختر اللغة الهدف", "Choose target language"),
                            selection: $settings.translateTarget) {
                        ForEach(targetLanguages, id: \.code) { lang in
                            Text(BasirSettings.shared.language == .arabic ? lang.ar : lang.en)
                                .tag(lang.code)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityLabel(L10n.t(
                        "اختيار اللغة الهدف للترجمة",
                        "Select the target language for translation"
                    ))
                }

                // Swap languages
                Button {
                    let oldSrc = settings.translateSource
                    let oldTgt = settings.translateTarget
                    if oldSrc == "auto" {
                        UIAccessibility.post(notification: .announcement,
                                              argument: L10n.t(
                                                "لا يمكن تبديل اللغة مع الاكتشاف التلقائي.",
                                                "Cannot swap with auto-detect."
                                              ))
                        return
                    }
                    settings.translateSource = oldTgt
                    settings.translateTarget = oldSrc
                    UIAccessibility.post(notification: .announcement,
                                          argument: L10n.t("تم تبديل اللغتين.",
                                                            "Languages swapped."))
                } label: {
                    HStack {
                        Image(systemName: "arrow.up.arrow.down")
                        Text(L10n.t("تبديل اللغتين", "Swap languages"))
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.bordered)

                // Input
                TextEditor(text: $inputText)
                    .focused($inputFocused)
                    .frame(minHeight: 140)
                    .padding(8)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12).stroke(.tertiary)
                    )
                    .accessibilityLabel(L10n.t(
                        "اكتب النص أو الصقه للترجمة",
                        "Type or paste text to translate"
                    ))

                // Translate button
                Button {
                    Task { await translate() }
                } label: {
                    HStack {
                        if isLoading { ProgressView().tint(.white) }
                        Text(isLoading
                             ? L10n.t("جارٍ الترجمة...", "Translating...")
                             : L10n.t("ترجمة", "Translate"))
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .disabled(isLoading || inputText.trimmingCharacters(in: .whitespaces).isEmpty)

                // Output
                if !translation.isEmpty {
                    Divider().padding(.vertical, 8)
                    Text(L10n.t("الترجمة", "Translation"))
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    Text(translation)
                        .textSelection(.enabled)
                        .accessibilityLabel(translation)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.red.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("ترجمة وشرح", "Translate and explain"))
    }

    private func translate() async {
        let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        translation = ""
        defer { isLoading = false }

        let instruction = GeminiPrompts.translateInstruction(
            sourceCode: settings.translateSource,
            targetCode: settings.translateTarget
        )

        do {
            let response = try await AiProviderFactory.current().ask(
                task: .translate,
                input: text,
                instruction: instruction,
                language: BasirSettings.shared.language,
                imageData: nil,
                mimeType: nil
            )
            translation = response
            UIAccessibility.post(notification: .announcement,
                                  argument: L10n.t("اكتملت الترجمة.",
                                                    "Translation complete."))
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }
}
