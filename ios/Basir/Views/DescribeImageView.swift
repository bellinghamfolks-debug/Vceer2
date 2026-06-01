// DescribeImageView.swift
// Reusable image task screen for: detailed description, alt text,
// screenshot reading, currency/receipt reading. The four modes share
// the same picker + Gemini round-trip; only the prompt differs.

import SwiftUI
import PhotosUI

enum DescribeImageMode {
    case detailed, altText, screenshot, currencyOrReceipt

    var title: String {
        switch self {
        case .detailed:           return L10n.t("وصف صورة أو مشهد", "Describe an image")
        case .altText:            return L10n.t("الوصف البديل", "Alt text")
        case .screenshot:         return L10n.t("قراءة لقطة شاشة", "Screenshot reading")
        case .currencyOrReceipt:  return L10n.t("قارئ العملات والفواتير",
                                                 "Currency and receipt reader")
        }
    }

    var task: TaskKind {
        switch self {
        case .detailed:           return .describeImage
        case .altText:            return .altText
        case .screenshot:         return .screenshot
        case .currencyOrReceipt:  return .currencyOrReceipt
        }
    }

    var instruction: String {
        switch self {
        case .detailed:
            return "Provide a detailed description suitable for a blind user. Start with a one-sentence summary, then objects, layout, visible text, and any practical notes."
        case .altText:
            return "Write precise alt text for a blind user: objects, spatial relationships, colors, visible text, practical relevance."
        case .screenshot:
            return "Explain the screenshot for a screen-reader user: page, buttons, messages, errors, and the next useful step."
        case .currencyOrReceipt:
            return "You are Basir, an assistant for blind and low-vision users. The image contains either banknotes/coins OR a paid receipt/invoice. BANKNOTES/COINS: state the currency and denomination in the FIRST sentence. RECEIPTS/INVOICES: state the grand total and the currency in the FIRST sentence. Keep the answer under 80 words, plain prose, no markdown."
        }
    }
}

struct DescribeImageView: View {
    let mode: DescribeImageMode
    @State private var pickerItem: PhotosPickerItem?
    @State private var resultText: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PhotosPicker(
                    selection: $pickerItem,
                    matching: .images,
                    photoLibrary: .shared()
                ) {
                    HStack {
                        Image(systemName: "photo.on.rectangle.angled")
                        Text(L10n.t("اختر أو التقط صورة", "Pick or take a photo"))
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }

                if isLoading {
                    HStack {
                        ProgressView()
                        Text(L10n.t("جارٍ التحليل عبر Gemini...",
                                     "Analyzing via Gemini..."))
                    }
                }

                if !resultText.isEmpty {
                    Divider().padding(.vertical, 8)
                    Text(L10n.t("النتيجة", "Result"))
                        .font(.headline)
                        .accessibilityAddTraits(.isHeader)
                    Text(resultText)
                        .textSelection(.enabled)
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
        .navigationTitle(mode.title)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task { await runDescribe(item: item) }
        }
    }

    private func runDescribe(item: PhotosPickerItem) async {
        isLoading = true
        errorMessage = nil
        resultText = ""
        defer { isLoading = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                throw GeminiError.decode("could not read image")
            }
            // Same 1600-px JPEG-85 compression used by MathExtractView.
            let compressed = UIImage(data: data).flatMap { img -> Data? in
                let maxLongEdge: CGFloat = 1600
                let longEdge = max(img.size.width, img.size.height)
                guard longEdge > 0 else { return nil }
                let scale = min(1.0, maxLongEdge / longEdge)
                let newSize = CGSize(width: img.size.width * scale,
                                     height: img.size.height * scale)
                let renderer = UIGraphicsImageRenderer(size: newSize)
                return renderer.image { _ in
                    img.draw(in: CGRect(origin: .zero, size: newSize))
                }.jpegData(compressionQuality: 0.85)
            } ?? data

            let response = try await GeminiAiProvider().ask(
                task: mode.task,
                input: "",
                instruction: mode.instruction,
                language: BasirSettings.shared.language,
                imageData: compressed,
                mimeType: "image/jpeg"
            )
            resultText = response
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }
}
