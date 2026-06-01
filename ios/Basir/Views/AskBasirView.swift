// AskBasirView.swift
// Conversational Q&A. Type a question, get an answer back from Gemini.

import SwiftUI

struct AskBasirView: View {
    @State private var question: String = ""
    @State private var answer: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @FocusState private var inputFocused: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.t("اكتب سؤالك، أو استخدم الإملاء الصوتي.",
                             "Type your question, or use voice dictation."))
                    .font(.callout)
                    .foregroundStyle(.secondary)

                TextEditor(text: $question)
                    .focused($inputFocused)
                    .frame(minHeight: 140)
                    .padding(8)
                    .background(Color(.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12).stroke(.tertiary)
                    )
                    .accessibilityLabel(L10n.t("اكتب سؤالك هنا",
                                                "Type your question here"))

                Button {
                    Task { await submit() }
                } label: {
                    HStack {
                        if isLoading { ProgressView().tint(.white) }
                        Text(isLoading
                             ? L10n.t("جارٍ الاتصال بـ Gemini...",
                                       "Connecting to Gemini...")
                             : L10n.t("إرسال", "Send"))
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .disabled(isLoading || question.trimmingCharacters(in: .whitespaces).isEmpty)

                Button {
                    Task { await dictate() }
                } label: {
                    Label(L10n.t("إملاء صوتي", "Voice dictation"),
                          systemImage: "mic.fill")
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.bordered)
                .disabled(isLoading)

                if !answer.isEmpty {
                    Divider().padding(.vertical, 8)
                    Text(L10n.t("إجابة بصير", "Basir's answer"))
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    Text(answer)
                        .textSelection(.enabled)
                        .accessibilityLabel(answer)
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
        .navigationTitle(L10n.t("اسأل بصير", "Ask Basir"))
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                inputFocused = true
            }
        }
    }

    private func submit() async {
        let q = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        answer = ""
        defer { isLoading = false }

        do {
            let response = try await GeminiAiProvider().ask(
                task: .ask,
                input: q,
                instruction: "Answer as Basir, screen-reader friendly and practical.",
                language: BasirSettings.shared.language,
                imageData: nil,
                mimeType: nil
            )
            answer = response
            // Announce completion to VoiceOver. Equivalent to the Android
            // announceForAccessibility call.
            UIAccessibility.post(notification: .announcement,
                                  argument: L10n.t("اكتمل التحليل.", "Analysis complete."))
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }

    /// One-shot voice dictation. Fills the question field with the
    /// recognised text so the user can review before sending.
    private func dictate() async {
        let auth = await SpeechRecognizer.shared.requestAuthorization()
        guard auth == .granted else {
            errorMessage = L10n.t(
                "يجب السماح بإذن الميكروفون والتعرّف الصوتي من إعدادات iOS.",
                "Microphone and speech-recognition permission are required in iOS Settings."
            )
            return
        }
        SpeechRecognizer.shared.startDictation(
            language: BasirSettings.shared.language
        ) { final in
            question = final
            inputFocused = true
        }
    }
}
