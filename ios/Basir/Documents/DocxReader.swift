// DocxReader.swift
//
// Reads the text content of a .docx by unzipping the package and
// pulling visible runs out of word/document.xml. Equivalent to a
// subset of Android's DocxExtractor — we extract enough for the
// Gemini "convert / translate document" flow on iOS to work, but
// we do NOT round-trip styling back into a new .docx.
//
// Why we extract on-device instead of letting Gemini parse the
// .docx directly: Gemini's Files API rejects DOCX (only accepts
// PDF, TXT, MD, and a few media types). The Android port hit the
// same wall and solved it with DocxExtractor.java — this file is
// the iOS equivalent.
//
// What we read
//   - <w:t> runs from paragraphs → flat paragraphs, joined by "\n".
//   - <w:tbl> table rows → each row collapsed into a "| cell1 | cell2 | ..." line.
//   - <w:p> paragraph boundaries → preserved.
//
// What we ignore
//   - Numbering, bookmarks, fields, comments, footnotes, headers /
//     footers in word/header*.xml / word/footer*.xml. For the
//     convert-and-translate UX, the body covers >95% of intent.
//   - Images, embedded objects. The user can extract those via the
//     image-description flow.

import Foundation

enum DocxReadError: Error, LocalizedError {
    case malformed
    case missingDocumentXml

    var errorDescription: String? {
        switch self {
        case .malformed:           return L10n.t("ملف Word تالف.", "The Word file is malformed.")
        case .missingDocumentXml:  return L10n.t("لا يحتوي ملف Word على محتوى نصي قابل للقراءة.",
                                                 "The Word file has no readable body content.")
        }
    }
}

struct DocxReader {

    /// Extracts the visible body text from the file at `url`. Returns
    /// a single string preserving paragraph and table-row boundaries
    /// with newlines so a screen reader can navigate it sanely.
    static func extractText(from url: URL) throws -> String {
        let zip = try ZipReader(url: url)
        guard zip.contains("word/document.xml") else {
            throw DocxReadError.missingDocumentXml
        }
        let xmlData = try zip.read("word/document.xml")
        let parser = XMLParser(data: xmlData)
        let collector = BodyCollector()
        parser.delegate = collector
        parser.shouldProcessNamespaces = false
        guard parser.parse() else { throw DocxReadError.malformed }
        let out = collector.finish()
        return out
    }
}

// MARK: - XML body collector

/// Walks the document.xml stream and accumulates paragraph + table
/// content into a plain-text representation. Designed for the OOXML
/// shape (w:p, w:r, w:t, w:tbl, w:tr, w:tc) — no other dialect is
/// expected inside a .docx.
private final class BodyCollector: NSObject, XMLParserDelegate {

    private var output = ""

    // Per-paragraph buffer so we can drop empty paragraphs.
    private var currentParagraph = ""
    private var inTextRun = false

    // Table state. Word allows nested tables but for the convert
    // flow we collapse nesting — each row gets its own line.
    private var tableDepth = 0
    private var currentRow: [String] = []
    private var currentCell = ""
    private var inCell = false

    func finish() -> String {
        output.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func parser(_ parser: XMLParser,
                didStartElement name: String,
                namespaceURI: String?,
                qualifiedName: String?,
                attributes: [String: String] = [:]) {
        switch localName(of: name) {
        case "p":
            currentParagraph = ""
        case "t":
            inTextRun = true
        case "br", "cr":
            // Word's soft / hard line breaks inside a paragraph.
            if inCell { currentCell.append("\n") } else { currentParagraph.append("\n") }
        case "tab":
            if inCell { currentCell.append("\t") } else { currentParagraph.append("\t") }
        case "tbl":
            tableDepth += 1
        case "tr":
            currentRow.removeAll()
        case "tc":
            inCell = true
            currentCell = ""
        default:
            break
        }
    }

    func parser(_ parser: XMLParser,
                foundCharacters string: String) {
        guard inTextRun else { return }
        if inCell { currentCell.append(string) }
        else      { currentParagraph.append(string) }
    }

    func parser(_ parser: XMLParser,
                didEndElement name: String,
                namespaceURI: String?,
                qualifiedName: String?) {
        switch localName(of: name) {
        case "t":
            inTextRun = false
        case "p":
            // OOXML emits an empty <w:p/> at the end of every cell.
            // Inside a cell we don't flush — the cell aggregates
            // multiple paragraphs before emitting a row line.
            if inCell { currentCell.append(currentParagraph + "\n") }
            else {
                let trimmed = currentParagraph
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    output.append(trimmed)
                    output.append("\n")
                }
            }
            currentParagraph = ""
        case "tc":
            currentRow.append(
                currentCell.trimmingCharacters(in: .whitespacesAndNewlines))
            currentCell = ""
            inCell = false
        case "tr":
            if !currentRow.isEmpty {
                output.append("| ")
                output.append(currentRow.joined(separator: " | "))
                output.append(" |\n")
            }
            currentRow.removeAll()
        case "tbl":
            tableDepth = max(0, tableDepth - 1)
            output.append("\n")
        default:
            break
        }
    }

    /// w:p / w:t / w:tbl come prefixed with the namespace "w:" but
    /// foundElement gives us the raw name. Strip the prefix once.
    private func localName(of qualified: String) -> String {
        if let colon = qualified.firstIndex(of: ":") {
            return String(qualified[qualified.index(after: colon)...])
        }
        return qualified
    }
}
