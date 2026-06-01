// DocumentConvertView.swift
// Single-shot PDF → structured-text conversion. The user picks a PDF
// via UIDocumentPicker; we extract its text on-device with PDFKit,
// hand the text to Gemini wrapped in the standard convert prompt, and
// render the JSON sections into a plain-text result that lives in the
// archive + can be shared.
//
// "Single-shot" means we do NOT chunk + run for minutes in the
// background. iOS doesn't allow that. The trade-off: maximum 60
// pages per pass (PdfReader.maxPagesPerShot). For longer documents
// the user is told to split.

import SwiftUI
import UniformTypeIdentifiers

struct DocumentConvertView: View {
    @State private var pickedURL: URL?
    @State private var pageCount: Int = 0
    @State private var resultText: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showPicker = false

    /// The single mode the iOS port supports today. Translation is the
    /// next slice; non-translate "convert to structured Word" is too
    /// dependent on a DocxBuilder port that's still to come.
    @State private var translateTo: String = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                pickerCard

                if pickedURL != nil {
                    Section {
                        translationPicker
                        runButton
                    }
                }

                if isLoading {
                    HStack {
                        ProgressView()
                        Text(L10n.t("جارٍ المعالجة عبر Gemini...",
                                     "Processing via Gemini..."))
                    }
                }

                if !resultText.isEmpty {
                    Divider().padding(.vertical, 8)
                    HStack {
                        Text(L10n.t("النتيجة", "Result"))
                            .font(.headline)
                            .accessibilityAddTraits(.isHeader)
                        Spacer()
                        ShareLink(item: resultText) {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .accessibilityLabel(L10n.t("مشاركة", "Share"))
                    }
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
        .navigationTitle(L10n.t("تحويل وترجمة المستندات",
                                 "Convert and translate documents"))
        .fileImporter(
            isPresented: $showPicker,
            allowedContentTypes: [.pdf, .commaSeparatedText, .plainText],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                if let url = urls.first {
                    handlePicked(url: url)
                }
            case .failure(let error):
                errorMessage = UserFriendlyErrorMapper.map(error)
            }
        }
    }

    private var pickerCard: some View {
        Button {
            showPicker = true
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "doc.fill.badge.plus")
                        .font(.title)
                    Text(L10n.t("اختر ملف PDF", "Pick a PDF"))
                        .font(.title3.bold())
                }
                Text(L10n.t(
                    "حدّ أقصى 60 صفحة في الدفعة الواحدة. للملفات الأطول قسّم الملف أولاً.",
                    "Up to 60 pages per single-shot run. Split longer files first."
                ))
                .font(.caption)
                .foregroundStyle(.secondary)
                if let url = pickedURL {
                    Divider().padding(.vertical, 4)
                    Text(url.lastPathComponent)
                        .font(.callout)
                        .lineLimit(2)
                    if pageCount > 0 {
                        Text(L10n.t("عدد الصفحات: \(pageCount)",
                                     "Pages: \(pageCount)"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private var translationPicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.t("اختياري: ترجمة إلى لغة معيّنة",
                         "Optional: translate to a target language"))
                .font(.subheadline.bold())
            Picker(L10n.t("لغة الترجمة", "Translation target"),
                    selection: $translateTo) {
                Text(L10n.t("بدون ترجمة (تحليل فقط)",
                             "No translation (analyse only)")).tag("")
                ForEach(L10n.supportedTranslationLanguages.filter { $0.code != "auto" },
                         id: \.code) { lang in
                    Text(BasirSettings.shared.language == .arabic
                          ? lang.ar : lang.en).tag(lang.code)
                }
            }
            .pickerStyle(.menu)
        }
    }

    private var runButton: some View {
        Button {
            Task { await run() }
        } label: {
            HStack {
                if isLoading { ProgressView().tint(.white) }
                Text(isLoading
                     ? L10n.t("جارٍ المعالجة...", "Processing...")
                     : L10n.t("تشغيل", "Run"))
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(Color.accentColor)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(isLoading)
    }

    private func handlePicked(url: URL) {
        // iOS hands back a security-scoped URL — we have to start an
        // access session before we can read it, and stop it after.
        // For PDFKit's PDFDocument(url:) call this is required.
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        // Copy to our sandbox so subsequent reads don't need the scope.
        let dest = FileManager.default.temporaryDirectory
            .appendingPathComponent(url.lastPathComponent)
        try? FileManager.default.removeItem(at: dest)
        do {
            try FileManager.default.copyItem(at: url, to: dest)
            pickedURL = dest
            pageCount = PdfReader.pageCount(of: dest)
            resultText = ""
            errorMessage = nil
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }

    private func run() async {
        guard let url = pickedURL else { return }
        isLoading = true
        errorMessage = nil
        resultText = ""
        defer { isLoading = false }

        do {
            let extracted = try PdfReader.extractText(from: url)
            var instruction = "You are processing a document for a blind user. "
                + "Preserve heading levels, list items, and tables. "
                + "Output a clean readable text version optimized for screen-readers."
            if !translateTo.isEmpty {
                let tgtName = GeminiPrompts.bcp47Name(translateTo)
                instruction = "TRANSLATE the document into \(tgtName). "
                    + "Preserve structure — headings, lists, tables — exactly. "
                    + "Only the language of the text changes."
            }
            let response = try await GeminiAiProvider().ask(
                task: .convert,
                input: extracted,
                instruction: instruction,
                language: BasirSettings.shared.language,
                imageData: nil,
                mimeType: nil
            )
            resultText = response
            // Auto-save to archive when enabled in settings.
            ArchiveStore.shared.addResult(ArchivedResult(
                title: url.lastPathComponent,
                kind: translateTo.isEmpty ? "convert" : "translate_doc",
                text: response,
                summary: String(response.prefix(140))
            ))
            UIAccessibility.post(notification: .announcement,
                                  argument: L10n.t("اكتمل التحويل.",
                                                    "Conversion complete."))
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }
}
