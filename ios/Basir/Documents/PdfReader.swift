// PdfReader.swift
// On-device PDF text extraction via Apple's PDFKit framework.
// Equivalent to a small subset of what Android's PdfRenderer + Gemini
// Files API does — minus the cloud upload, since for translation /
// summarisation we ship extracted text to Gemini as plain text.
//
// Why on-device extraction
// ────────────────────────
//   Gemini's Files API DOES accept PDFs directly, but iOS does not
//   allow indefinite foreground processing the way Android does. By
//   reading the text locally first and then asking Gemini to process
//   the extracted text (single shot, ≤30 seconds), we keep the entire
//   pipeline inside what iOS allows without compromise. The downside:
//   image-based PDFs (scans) extract nothing — but for those the user
//   can fall back to the camera flow ("describe an image / math
//   sheet") on a per-page basis.

import Foundation
import PDFKit

enum PdfReadError: Error, LocalizedError {
    case couldNotOpen
    case empty
    case tooLarge(pages: Int, max: Int)

    var errorDescription: String? {
        switch self {
        case .couldNotOpen:
            return L10n.t("تعذّر فتح ملف PDF.", "Could not open the PDF file.")
        case .empty:
            return L10n.t("هذا الملف لا يحتوي على نص قابل للاستخراج. قد يكون صورة ممسوحة.",
                          "This PDF has no extractable text. It may be a scanned image.")
        case .tooLarge(let pages, let max):
            return L10n.t(
                "الملف يحتوي على \(pages) صفحة. الحد الأقصى لمعالجة دفعة واحدة \(max) صفحة. قسّم الملف.",
                "The file has \(pages) pages. Single-shot processing is limited to \(max). Split the file."
            )
        }
    }
}

struct PdfReader {

    /// Maximum pages to process in one Gemini call. Larger files would
    /// run out of foreground time on iOS; the user can split or use
    /// the Android version for very long documents.
    static let maxPagesPerShot = 60

    /// Returns the extracted text of every page, joined by a
    /// "[Page N]" marker. The marker lets the prompt builder ask
    /// Gemini to preserve page references in the output.
    static func extractText(from url: URL) throws -> String {
        guard let doc = PDFDocument(url: url) else {
            throw PdfReadError.couldNotOpen
        }
        let pageCount = doc.pageCount
        if pageCount > maxPagesPerShot {
            throw PdfReadError.tooLarge(pages: pageCount, max: maxPagesPerShot)
        }
        var sb = ""
        var anyText = false
        for i in 0..<pageCount {
            guard let page = doc.page(at: i) else { continue }
            sb += "\n[Page \(i + 1)]\n"
            if let text = page.string {
                let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cleaned.isEmpty {
                    anyText = true
                    sb += cleaned
                    sb += "\n"
                }
            }
        }
        guard anyText else { throw PdfReadError.empty }
        return sb
    }

    static func pageCount(of url: URL) -> Int {
        PDFDocument(url: url)?.pageCount ?? 0
    }
}
