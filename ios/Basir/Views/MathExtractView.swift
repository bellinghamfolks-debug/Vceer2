// MathExtractView.swift
// Math extraction — the v2.9 feature. Photograph a textbook page, a
// whiteboard, or handwritten equations; Basir returns each expression
// in spoken Arabic / English followed by the LaTeX source.
//
// This uses the FULL mathExtractionInstruction prompt that ports
// directly from AiClient.mathExtractionInstruction on Android, with
// the same 8 worked examples + full Arabic / English vocabulary.

import SwiftUI
import PhotosUI

struct MathExtractView: View {
    @State private var pickerItem: PhotosPickerItem?
    @State private var resultText: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.t(
                    "صوّر معادلات أو سبورة أو صفحة كتاب. سيحاول بصير استخراج الصيغ بصيغة منطوقة مع LaTeX للمراجعة.",
                    "Photograph equations, a whiteboard, or a textbook page. Basir will attempt to extract spoken expressions with LaTeX for review."
                ))
                .font(.callout)
                .foregroundStyle(.secondary)

                PhotosPicker(
                    selection: $pickerItem,
                    matching: .images,
                    photoLibrary: .shared()
                ) {
                    HStack {
                        Image(systemName: "camera.fill")
                        Text(L10n.t("التقاط أو اختيار صورة",
                                     "Take or pick an image"))
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .accessibilityLabel(L10n.t(
                    "اختيار صورة لتحليل المعادلات",
                    "Pick an image to analyse math"
                ))

                if isLoading {
                    HStack {
                        ProgressView()
                        Text(L10n.t("جارٍ تحليل المعادلات...",
                                     "Analysing equations..."))
                    }
                    .padding(.top, 8)
                }

                if !resultText.isEmpty {
                    Divider().padding(.vertical, 8)
                    Text(L10n.t("الناتج", "Result"))
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    Text(resultText)
                        .textSelection(.enabled)
                        .font(.body)
                        .accessibilityLabel(resultText)
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
        .navigationTitle(L10n.t("تحليل ورقة رياضيات", "Analyze a math sheet"))
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task { await runExtraction(item: item) }
        }
    }

    private func runExtraction(item: PhotosPickerItem) async {
        isLoading = true
        errorMessage = nil
        resultText = ""
        defer { isLoading = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                throw GeminiError.decode(L10n.t(
                    "تعذّرت قراءة الصورة.",
                    "Could not read the image."
                ))
            }
            // Compress to ~1600px long edge + JPEG quality 85 — matches
            // the Android ImageCompressor and the same Gemini cost win.
            let compressed = compressForAi(data: data) ?? data
            let mime = "image/jpeg"
            let isEnglish = BasirSettings.shared.language == .english
            let instruction = GeminiPrompts.mathExtractionInstruction(english: isEnglish)
            let response = try await AiProviderFactory.current().ask(
                task: .mathExtract,
                input: "",
                instruction: instruction,
                language: BasirSettings.shared.language,
                imageData: compressed,
                mimeType: mime
            )
            resultText = response
            UIAccessibility.post(notification: .announcement,
                                  argument: L10n.t("اكتمل تحليل الرياضيات.",
                                                    "Math analysis complete."))
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }

    // MARK: - Image compression (port of Android ImageCompressor)

    /// Down-scales the long edge to 1600 px and re-encodes as JPEG 85.
    /// Returns nil if decoding fails (caller falls back to raw data).
    private func compressForAi(data: Data) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let maxLongEdge: CGFloat = 1600
        let w = image.size.width, h = image.size.height
        let longEdge = max(w, h)
        guard longEdge > 0 else { return nil }
        let scale = min(1.0, maxLongEdge / longEdge)
        let newSize = CGSize(width: w * scale, height: h * scale)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let scaled = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
        return scaled.jpegData(compressionQuality: 0.85)
    }
}
