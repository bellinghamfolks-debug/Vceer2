// DocumentConvertView.swift
// Single-shot PDF or plain-text processing. PDF text is extracted
// on-device with PDFKit; text and CSV files are read directly. The selected
// text is sent to Gemini and the result is presented as shareable plain text.
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
    /// File URL of the latest generated DOCX, or nil if none was
    /// produced for the current result. Reset on every new conversion.
    @State private var lastDocxURL: URL?

    /// Document types iOS now extracts on-device. DOCX and PPTX go
    /// through DocxReader / PptxReader (the iOS equivalents of
    /// Android's DocxExtractor / PptxExtractor); PDF goes through
    /// PdfReader; CSV / TXT are read as plain text.
    private static let allowedTypes: [UTType] = {
        var types: [UTType] = [.pdf, .commaSeparatedText, .plainText]
        // DOCX / PPTX are declared by their MIME types so we work
        // even on iOS releases that haven't promoted them to a
        // first-class UTType identifier.
        if let docx = UTType(mimeType:
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
            types.append(docx)
        }
        if let pptx = UTType(mimeType:
                "application/vnd.openxmlformats-officedocument.presentationml.presentation") {
            types.append(pptx)
        }
        return types
    }()

    /// Empty means organize the extracted text without translation.
    /// A selected language requests translation while preserving structure.
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
                        Text(L10n.t("جارٍ تنفيذ الطلب عبر Gemini...",
                                     "Processing the request with Gemini..."))
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
                        .accessibilityLabel(L10n.t("مشاركة كنص",
                                                     "Share as text"))
                    }
                    Text(resultText)
                        .textSelection(.enabled)
                        .accessibilityLabel(resultText)

                    // v3.2 — produce an actual .docx file the user can
                    // share, save to Files, or hand to Word. Mirrors
                    // the Android "convert to Word" pathway.
                    if let docxURL = lastDocxURL {
                        ShareLink(item: docxURL) {
                            HStack {
                                Image(systemName: "doc.fill")
                                Text(L10n.t("مشاركة كملف Word (DOCX)",
                                             "Share as Word file (DOCX)"))
                            }
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(Color.accentColor.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .accessibilityHint(L10n.t(
                            "ينشئ ملف Word ويفتح ورقة المشاركة لحفظه أو إرساله.",
                            "Builds a Word file and opens the share sheet to save or send it."))
                    } else {
                        Button {
                            buildDocxFile()
                        } label: {
                            HStack {
                                Image(systemName: "doc.badge.plus")
                                Text(L10n.t("إنشاء ملف Word (DOCX)",
                                             "Create a Word file (DOCX)"))
                            }
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(Color.accentColor.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .accessibilityHint(L10n.t(
                            "يحوّل النتيجة إلى ملف Word قابل للمشاركة.",
                            "Converts the result into a shareable Word file."))
                    }
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
        .navigationTitle(L10n.t("قراءة مستند وترجمته",
                                 "Read and translate a document"))
        .fileImporter(
            isPresented: $showPicker,
            allowedContentTypes: Self.allowedTypes,
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
                    Text(L10n.t("اختر مستندًا (PDF أو Word أو PowerPoint أو نص)",
                                  "Choose a document (PDF, Word, PowerPoint, or text)"))
                        .font(.title3.bold())
                }
                Text(L10n.t(
                    "يدعم PDF حتى 60 صفحة، وملفات Word (DOCX) وPowerPoint (PPTX) وTXT وCSV. يُستخرج النص محليًا على الجهاز، ثم يُرسل إلى Gemini لتنظيمه أو ترجمته. النتيجة نص قابل للنسخ والمشاركة وليست ملف Word.",
                    "Supports PDFs of up to 60 pages, Word (DOCX) and PowerPoint (PPTX) files, plus TXT and CSV. Text is extracted on-device then sent to Gemini for structuring or translation. The result is shareable text, not a Word file."
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
            Text(L10n.t("اختياري: ترجمة النص المستخرج",
                         "Optional: translate the extracted text"))
                .font(.subheadline.bold())
            Picker(L10n.t("لغة الترجمة", "Translation target"),
                    selection: $translateTo) {
                Text(L10n.t("تنظيم النص دون ترجمة",
                             "Structure the text without translation")).tag("")
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
                     : L10n.t("بدء المعالجة", "Start processing"))
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
            // Only PDF has a meaningful page count we can show
            // up-front. DOCX / PPTX page-equivalents are unknown
            // until we extract — the convert step will tell us if
            // they're empty.
            pageCount = dest.pathExtension.lowercased() == "pdf"
                ? PdfReader.pageCount(of: dest) : 0
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
        // Invalidate any previously-built DOCX so the user can't share
        // a file that belongs to an older Gemini run.
        lastDocxURL = nil
        defer { isLoading = false }

        do {
            let extracted: String
            switch url.pathExtension.lowercased() {
            case "pdf":
                extracted = try PdfReader.extractText(from: url)
            case "docx":
                extracted = try DocxReader.extractText(from: url)
            case "pptx":
                extracted = try PptxReader.extractText(from: url)
            default:
                extracted = try String(contentsOf: url, encoding: .utf8)
            }
            guard !extracted.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                throw NSError(domain: "BasirDocument", code: 1,
                              userInfo: [NSLocalizedDescriptionKey:
                                L10n.t("لم يُعثر على نص قابل للقراءة في الملف.",
                                       "No readable text was found in the file.")])
            }
            var instruction = "You are processing a document for a blind user. "
                + "Preserve heading levels, list items, and tables. "
                + "Output clean, readable plain text optimized for screen readers. "
                + "Do not claim that images or tables were read unless their content exists in the extracted text."
            if !translateTo.isEmpty {
                let tgtName = GeminiPrompts.bcp47Name(translateTo)
                instruction = "TRANSLATE the document into \(tgtName). "
                    + "Preserve structure — headings, lists, tables — exactly. "
                    + "Only the language of the text changes."
            }
            let response = try await AiProviderFactory.current().ask(
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
                title: L10n.t("معالجة: ", "Processed: ") + url.lastPathComponent,
                kind: translateTo.isEmpty ? "convert" : "translate_doc",
                text: response,
                summary: String(response.prefix(140))
            ))
            UIAccessibility.post(notification: .announcement,
                                  argument: L10n.t("اكتملت المعالجة. راجع النتيجة قبل استخدامها.",
                                                    "Processing is complete. Review the result before using it."))
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }

    /// Build a .docx file from the current resultText and stash its
    /// URL into lastDocxURL so the ShareLink shows up. Writes into the
    /// caches dir so the share sheet can read it without sandbox
    /// surprises; the OS reaps the file when caches are pruned.
    private func buildDocxFile() {
        guard !resultText.isEmpty else { return }
        let rtl = BasirSettings.shared.language == .arabic
        var writer = DocxWriter(rtl: rtl)
        writer.appendPlain(resultText)
        let baseName = pickedURL?
            .deletingPathExtension()
            .lastPathComponent ?? "Basir"
        let outURL = FileManager.default
            .temporaryDirectory
            .appendingPathComponent("\(baseName)-basir.docx")
        try? FileManager.default.removeItem(at: outURL)
        do {
            try writer.write(to: outURL)
            lastDocxURL = outURL
            UIAccessibility.post(notification: .announcement,
                                  argument: L10n.t(
                                      "تم إنشاء ملف Word. استخدم زر المشاركة لحفظه.",
                                      "Word file created. Use the share button to save it."))
        } catch {
            errorMessage = UserFriendlyErrorMapper.map(error)
        }
    }
}
