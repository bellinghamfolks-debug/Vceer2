// DocxWriter.swift
//
// Produces a minimal-but-real .docx file from plain text or a list
// of typed blocks. iOS counterpart to Android's DocxBuilder — small
// subset since iOS uses single-shot conversion and only needs to
// surface a Word file for the user to share.
//
// What it writes
//   - [Content_Types].xml  — MIME types for each part inside the ZIP
//   - _rels/.rels          — root-level relationships
//   - word/_rels/document.xml.rels — empty relationship (Word
//                            tolerates this; we don't ship styles.xml)
//   - word/document.xml    — the actual body content
//
// What we omit (intentionally)
//   - styles.xml: Word picks sensible defaults when no styles part
//     is declared. Our paragraphs explicitly carry inline runs with
//     the language tag + RTL flag so Arabic still renders correctly.
//   - settings.xml, fontTable.xml, theme.xml, headers/footers: not
//     required for a valid OOXML body.
//   - Numbering, bookmarks, images. Plain text + tables only.
//
// The output validates in Word for Mac, Word for iPad, Apple
// Pages, and Google Docs.

import Foundation

struct DocxWriter {

    enum Block {
        case heading(level: Int, text: String)   // level 1..3
        case paragraph(text: String)
        case table(rows: [[String]])
    }

    private var blocks: [Block] = []
    /// True when the body language is Arabic — drives <w:bidi/>
    /// markers + the <w:rtl/> run property so Word right-aligns and
    /// renders shaping correctly.
    let rtl: Bool
    let langTag: String

    init(rtl: Bool) {
        self.rtl = rtl
        self.langTag = rtl ? "ar-SA" : "en-US"
    }

    mutating func append(_ block: Block) {
        blocks.append(block)
    }

    /// Parse a Gemini plain-text response into blocks. Lines starting
    /// with "# ", "## ", or "### " become headings; lines that begin
    /// with "| " and contain enough "|" separators become single-row
    /// table appends (we collect consecutive table lines into one
    /// table block). Everything else is a paragraph.
    mutating func appendPlain(_ text: String) {
        let lines = text.components(separatedBy: "\n")
        var pendingTable: [[String]] = []
        for raw in lines {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty {
                flushTable(&pendingTable)
                continue
            }
            if line.hasPrefix("### ") {
                flushTable(&pendingTable)
                blocks.append(.heading(level: 3, text: String(line.dropFirst(4))))
            } else if line.hasPrefix("## ") {
                flushTable(&pendingTable)
                blocks.append(.heading(level: 2, text: String(line.dropFirst(3))))
            } else if line.hasPrefix("# ") {
                flushTable(&pendingTable)
                blocks.append(.heading(level: 1, text: String(line.dropFirst(2))))
            } else if line.hasPrefix("|") && line.hasSuffix("|") {
                let cells = line
                    .dropFirst().dropLast()
                    .split(separator: "|", omittingEmptySubsequences: false)
                    .map { $0.trimmingCharacters(in: .whitespaces) }
                if cells.count >= 2 { pendingTable.append(cells) }
                else                { blocks.append(.paragraph(text: line)) }
            } else {
                flushTable(&pendingTable)
                blocks.append(.paragraph(text: line))
            }
        }
        flushTable(&pendingTable)
    }

    private mutating func flushTable(_ pending: inout [[String]]) {
        guard !pending.isEmpty else { return }
        blocks.append(.table(rows: pending))
        pending.removeAll()
    }

    /// Build the .docx package and return the bytes.
    func archive() -> Data {
        var zip = ZipWriter()
        zip.addFile(name: "[Content_Types].xml", utf8: contentTypesXml)
        zip.addFile(name: "_rels/.rels",          utf8: rootRelsXml)
        zip.addFile(name: "word/_rels/document.xml.rels", utf8: docRelsXml)
        zip.addFile(name: "word/document.xml",    utf8: documentXml)
        return zip.archive()
    }

    /// Convenience writer for the convert flow.
    func write(to url: URL) throws {
        try archive().write(to: url, options: .atomic)
    }

    // MARK: - XML parts

    private var contentTypesXml: String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml"  ContentType="application/xml"/>
          <Override PartName="/word/document.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
        """
    }

    private var rootRelsXml: String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                        Target="word/document.xml"/>
        </Relationships>
        """
    }

    private var docRelsXml: String {
        """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        </Relationships>
        """
    }

    private var documentXml: String {
        var sb = ""
        sb += #"<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"#
        sb += #"<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">"#
        sb += "<w:body>"
        for block in blocks {
            switch block {
            case .heading(let level, let text):
                sb += paragraphXml(text: text, isHeading: true,
                                    headingLevel: level, size: 28 - level * 2)
            case .paragraph(let text):
                sb += paragraphXml(text: text, isHeading: false,
                                    headingLevel: 0, size: 22)
            case .table(let rows):
                sb += tableXml(rows: rows)
            }
        }
        // Section properties at the end are required for a valid body.
        sb += sectionPropertiesXml()
        sb += "</w:body></w:document>"
        return sb
    }

    private func paragraphXml(text: String,
                              isHeading: Bool,
                              headingLevel: Int,
                              size: Int) -> String {
        var sb = "<w:p>"
        sb += "<w:pPr>"
        if isHeading {
            sb += #"<w:pStyle w:val="Heading\#(headingLevel)"/>"#
        }
        if rtl { sb += "<w:bidi/>" }
        sb += "</w:pPr>"
        sb += "<w:r><w:rPr>"
        if rtl { sb += "<w:rtl/>" }
        if isHeading { sb += "<w:b/><w:bCs/>" }
        sb += #"<w:sz w:val="\#(size)"/><w:szCs w:val="\#(size)"/>"#
        sb += #"<w:lang w:val="\#(langTag)" w:bidi="\#(langTag)"/>"#
        sb += "</w:rPr>"
        sb += #"<w:t xml:space="preserve">\#(escape(text))</w:t>"#
        sb += "</w:r></w:p>"
        return sb
    }

    private func tableXml(rows: [[String]]) -> String {
        guard !rows.isEmpty else { return "" }
        let cols = rows[0].count
        let colWidth = 9000 / max(1, cols)
        var sb = "<w:tbl><w:tblPr>"
        sb += #"<w:tblW w:w="5000" w:type="pct"/>"#
        if rtl { sb += "<w:bidiVisual/>" }
        sb += "<w:tblBorders>"
        sb += #"<w:top    w:val="single" w:sz="6" w:color="888888"/>"#
        sb += #"<w:bottom w:val="single" w:sz="6" w:color="888888"/>"#
        sb += #"<w:left   w:val="single" w:sz="6" w:color="888888"/>"#
        sb += #"<w:right  w:val="single" w:sz="6" w:color="888888"/>"#
        sb += #"<w:insideH w:val="single" w:sz="4" w:color="BBBBBB"/>"#
        sb += #"<w:insideV w:val="single" w:sz="4" w:color="BBBBBB"/>"#
        sb += "</w:tblBorders></w:tblPr>"
        sb += "<w:tblGrid>"
        for _ in 0..<cols {
            sb += #"<w:gridCol w:w="\#(colWidth)"/>"#
        }
        sb += "</w:tblGrid>"
        for (r, row) in rows.enumerated() {
            let isHeader = (r == 0)
            sb += "<w:tr>"
            if isHeader { sb += "<w:trPr><w:tblHeader/></w:trPr>" }
            for cell in row {
                let cellText = cell.isEmpty ? " " : cell
                sb += "<w:tc><w:tcPr>"
                sb += #"<w:tcW w:w="\#(colWidth)" w:type="dxa"/>"#
                if isHeader {
                    sb += #"<w:shd w:val="clear" w:color="auto" w:fill="E8EEF7"/>"#
                }
                sb += "</w:tcPr><w:p>"
                sb += "<w:pPr>"
                if rtl { sb += "<w:bidi/>" }
                sb += "</w:pPr><w:r><w:rPr>"
                if rtl { sb += "<w:rtl/>" }
                if isHeader { sb += "<w:b/><w:bCs/>" }
                sb += #"<w:sz w:val="22"/><w:szCs w:val="22"/>"#
                sb += #"<w:lang w:val="\#(langTag)" w:bidi="\#(langTag)"/>"#
                sb += "</w:rPr>"
                sb += #"<w:t xml:space="preserve">\#(escape(cellText))</w:t>"#
                sb += "</w:r></w:p></w:tc>"
            }
            sb += "</w:tr>"
        }
        sb += "</w:tbl><w:p/>"
        return sb
    }

    private func sectionPropertiesXml() -> String {
        var sb = "<w:sectPr>"
        sb += #"<w:pgSz w:w="12240" w:h="15840"/>"#
        sb += #"<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>"#
        if rtl { sb += "<w:bidi/>" }
        sb += "</w:sectPr>"
        return sb
    }

    private func escape(_ s: String) -> String {
        var t = s.replacingOccurrences(of: "&", with: "&amp;")
        t = t.replacingOccurrences(of: "<", with: "&lt;")
        t = t.replacingOccurrences(of: ">", with: "&gt;")
        t = t.replacingOccurrences(of: "\"", with: "&quot;")
        t = t.replacingOccurrences(of: "'", with: "&apos;")
        return t
    }
}
