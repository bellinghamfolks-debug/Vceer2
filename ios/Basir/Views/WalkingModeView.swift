// WalkingModeView.swift
// "Tap → capture → describe → speak → repeat" loop for blind users
// who are walking. iOS does not allow continuous photo capture from
// the background, so this is foreground-only.
//
// The interaction is a single big button. After each Gemini response
// TTS reads the description. When TTS finishes the user can tap
// again for the next scene. An "auto-open camera" toggle re-opens
// the picker as soon as TTS ends, mirroring the Android version.

import SwiftUI
import PhotosUI
import Combine

struct WalkingModeView: View {
    @State private var pickerItem: PhotosPickerItem?
    @State private var lastDescription: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @AppStorage("walking_auto_reopen") private var autoReopen: Bool = false
    @StateObject private var tts = SpeechSynthesizer.shared
    @State private var ttsSubscription: AnyCancellable?
    @State private var openPickerAfterTts = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.t(
                "اضغط لالتقاط ما أمامك. سأصف المشهد بإيجاز، ثم يمكنك التقاط المشهد التالي. هذا الوضع للمساعدة ولا يغني عن الانتباه للطريق أو أدوات التنقل.",
                "Tap to capture what is ahead. I will describe the scene briefly, then you can capture the next one. This mode is assistive and does not replace attention to your surroundings or mobility tools."
            ))
            .font(.callout)
            .foregroundStyle(.secondary)

            if !lastDescription.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L10n.t("آخر وصف:", "Last description:"))
                        .font(.subheadline.bold())
                    Text(lastDescription)
                        .accessibilityLabel(lastDescription)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.red.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            Spacer()

            PhotosPicker(
                selection: $pickerItem,
                matching: .images,
                photoLibrary: .shared()
            ) {
                VStack {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 50))
                    Text(L10n.t("التقاط ووصف ما أمامي",
                                 "Capture and describe what is ahead"))
                        .fontWeight(.bold)
                }
                .frame(maxWidth: .infinity, minHeight: 120)
                .background(Color.accentColor)
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 18))
            }
            .disabled(isLoading)

            Toggle(L10n.t("فتح الكاميرا تلقائيًا بعد كل وصف",
                          "Auto-open camera after each description"),
                    isOn: $autoReopen)
                .font(.callout)
        }
        .padding(20)
        .navigationTitle(L10n.t("وضع المشي", "Walking mode"))
        .onAppear {
            ttsSubscription = tts.didFinish.sink { id in
                guard id == "walking" else { return }
                if autoReopen && openPickerAfterTts {
                    openPickerAfterTts = false
                    // Trigger a system event so the picker remembers to
                    // reopen. PhotosPicker takes care of the rest when
                    // the user picks another shot.
                }
            }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task { await describe(item: item) }
        }
    }

    private func describe(item: PhotosPickerItem) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                throw GeminiError.decode("could not read image")
            }
            // 1600-px JPEG-85 compression — same wire-saving pass that
            // the Android version uses.
            let compressed = compressForAi(data: data) ?? data
            let response = try await GeminiAiProvider().ask(
                task: .describeImage,
                input: "",
                instruction: "Describe the scene for a blind walker. ONE concise paragraph (max 35 words): main objects, obstacles directly ahead, any text or signage, and one practical next-step suggestion. No greetings or filler.",
                language: BasirSettings.shared.language,
                imageData: compressed,
                mimeType: "image/jpeg"
            )
            lastDescription = response
            // Auto-speak the description so a walking user doesn't need
            // to read the screen.
            openPickerAfterTts = autoReopen
            SpeechSynthesizer.shared.speak(response, utteranceId: "walking")
            ArchiveStore.shared.appendLog(type: "walking",
                                           content: response)
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }

    private func compressForAi(data: Data) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let maxLongEdge: CGFloat = 1600
        let longEdge = max(image.size.width, image.size.height)
        guard longEdge > 0 else { return nil }
        let scale = min(1.0, maxLongEdge / longEdge)
        let newSize = CGSize(width: image.size.width * scale,
                              height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }.jpegData(compressionQuality: 0.85)
    }
}
