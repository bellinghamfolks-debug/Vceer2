// PptxReader.swift
//
// Reads the visible text out of every slide in a .pptx by unzipping
// the package and collecting <a:t> runs from each
// ppt/slides/slideN.xml. Mirrors a subset of Android's
// PptxExtractor — text per slide, no images, no animations.
//
// Output shape
// ────────────
// Each slide becomes a "[Slide N]" header followed by the slide's
// title (if any) and the rest of the text in document order. Slides
// are emitted in their natural order (slide1.xml, slide2.xml, ...)
// — but PPTX does NOT guarantee filename order matches slide
// order in the presentation. For the convert-and-translate UX
// that's good enough; a future enhancement could read
// ppt/presentation.xml's <p:sldIdLst> to honour reorders.

import Foundation

enum PptxReadError: Error, LocalizedError {
    case malformed
    case noSlides

    var errorDescription: String? {
        switch self {
        case .malformed:  return L10n.t("ملف PowerPoint تالف.",
                                         "The PowerPoint file is malformed.")
        case .noSlides:   return L10n.t("لا تحتوي الشرائح على نص قابل للقراءة.",
                                         "Slides have no readable text.")
        }
    }
}

struct PptxReader {

    /// Extracts the visible text from every slide. Empty slides are
    /// preserved with their header so the user knows the position.
    static func extractText(from url: URL) throws -> String {
        let zip = try ZipReader(url: url)
        // Collect slide entries by their numeric suffix (slide1.xml,
        // slide10.xml ...). Filenames inside the archive use natural
        // numeric ordering on the wire but lexicographic sort would
        // place slide10 before slide2 — sort numerically.
        let slideNames = zip.fileNames
            .filter { $0.hasPrefix("ppt/slides/slide") && $0.hasSuffix(".xml") }
            .sorted { slideIndex($0) < slideIndex($1) }
        guard !slideNames.isEmpty else { throw PptxReadError.noSlides }

        var out = ""
        var emittedAny = false
        for (idx, name) in slideNames.enumerated() {
            let xmlData = try zip.read(name)
            let parser = XMLParser(data: xmlData)
            let collector = SlideTextCollector()
            parser.delegate = collector
            parser.shouldProcessNamespaces = false
            guard parser.parse() else { throw PptxReadError.malformed }
            let slideText = collector.finish()
            out.append("[Slide \(idx + 1)]\n")
            if slideText.isEmpty {
                out.append("(no readable text)\n\n")
            } else {
                emittedAny = true
                out.append(slideText)
                out.append("\n\n")
            }
        }
        guard emittedAny else { throw PptxReadError.noSlides }
        return out.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Returns the numeric index in a "ppt/slides/slideN.xml" filename
    /// — used to sort slide10.xml AFTER slide2.xml. Falls back to a
    /// large sentinel for anything that doesn't match the expected
    /// shape so we don't reorder unknown entries randomly.
    private static func slideIndex(_ name: String) -> Int {
        guard let lastSlash = name.lastIndex(of: "/") else { return .max }
        let leaf = name[name.index(after: lastSlash)...]
        let digits = leaf
            .replacingOccurrences(of: "slide", with: "")
            .replacingOccurrences(of: ".xml", with: "")
        return Int(digits) ?? .max
    }
}

// MARK: - Per-slide text collector

private final class SlideTextCollector: NSObject, XMLParserDelegate {

    private var buf = ""
    private var inText = false

    func finish() -> String {
        buf.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func parser(_ parser: XMLParser,
                didStartElement name: String,
                namespaceURI: String?,
                qualifiedName: String?,
                attributes: [String: String] = [:]) {
        switch localName(of: name) {
        case "t":
            inText = true
        case "br":
            buf.append("\n")
        case "p":
            // <a:p> = each paragraph in a text body. Emit a newline
            // boundary so distinct bullets / lines come through.
            if !buf.isEmpty && !buf.hasSuffix("\n") { buf.append("\n") }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser,
                foundCharacters string: String) {
        guard inText else { return }
        buf.append(string)
    }

    func parser(_ parser: XMLParser,
                didEndElement name: String,
                namespaceURI: String?,
                qualifiedName: String?) {
        if localName(of: name) == "t" { inText = false }
    }

    private func localName(of qualified: String) -> String {
        if let colon = qualified.firstIndex(of: ":") {
            return String(qualified[qualified.index(after: colon)...])
        }
        return qualified
    }
}
