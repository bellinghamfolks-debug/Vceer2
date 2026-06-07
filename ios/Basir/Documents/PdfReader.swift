// PdfReader.swift
// On-device PDF text extraction via Apple's PDFKit framework.
// Equivalent to a small subset of what Android's PdfRenderer + Gemini
// Files API does — minus the cloud upload, since for translation /
// summarisation we ship extracted text to Gemini as plain text.
//
// v3.3 — chunked processing
// ─────────────────────────
// The previous 60-page hard cap forced users with long documents
// to split files manually, which made iOS look weaker than apps
// like ScribeMe at the same job. We now:
//
//   • Extract every page to a separate string up-front
//     (extractPages). PDFKit can handle 500+ pages in a few
//     seconds on a modern iPhone, no memory issues.
//   • Let the calling view (DocumentConvertView) split the page
//     array into Gemini-sized batches and run them sequentially
//     in the foreground. Progress is reported by the view so the
//     user sees "page X of Y" while the loop runs.
//
// The single-shot extractText helper stays for callers that want
// "everything in one string" (the older simpler flow). It now
// respects the much higher maxPagesPerShot, and the chunked
// runners go through extractPages directly.

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
                "الملف يحتوي على \(pages) صفحة. الحد الأقصى المدعوم \(max) صفحة. قسّم الملف.",
                "The file has \(pages) pages. The supported maximum is \(max). Split the file."
            )
        }
    }
}

struct PdfReader {

    /// Safety ceiling. iOS PDFKit can technically open larger files,
    /// but past a few hundred pages the Gemini round-trips start to
    /// dominate the UX and the user is better off splitting. 500 is
    /// the same order of magnitude as ScribeMe-class tools.
    static let maxPagesPerShot = 500

    /// Per-Gemini-batch page count. Each batch is one foreground
    /// `convert` call; we keep batches small enough that the output
    /// stays under Gemini Flash's per-response token cap (~8K) with
    /// dense Arabic / English text. 8 pages per batch is the sweet
    /// spot in our tests: large enough to amortise the round-trip,
    /// small enough that an 80-page lecture pack only needs 10 calls.
    static let pagesPerBatch = 8

    /// Per-page extracted text. Empty strings mean the page had no
    /// readable content (typical for scanned PDFs and cover pages).
    /// The caller decides how to batch + label them.
    static func extractPages(from url: URL) throws -> [String] {
        guard let doc = PDFDocument(url: url) else {
            throw PdfReadError.couldNotOpen
        }
        let pageCount = doc.pageCount
        if pageCount > maxPagesPerShot {
            throw PdfReadError.tooLarge(pages: pageCount, max: maxPagesPerShot)
        }
        var pages: [String] = []
        pages.reserveCapacity(pageCount)
        var anyText = false
        for i in 0..<pageCount {
            let raw = doc.page(at: i)?.string ?? ""
            let cleaned = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleaned.isEmpty { anyText = true }
            pages.append(cleaned)
        }
        guard anyText else { throw PdfReadError.empty }
        return pages
    }

    /// Convenience for callers that want every page joined into a
    /// single "[Page N]" string. Used by the single-shot fallback
    /// path for tiny documents that don't need chunking.
    static func extractText(from url: URL) throws -> String {
        let pages = try extractPages(from: url)
        var sb = ""
        for (i, page) in pages.enumerated() {
            sb += "\n[Page \(i + 1)]\n"
            if !page.isEmpty { sb += page + "\n" }
        }
        return sb
    }

    static func pageCount(of url: URL) -> Int {
        PDFDocument(url: url)?.pageCount ?? 0
    }
}
