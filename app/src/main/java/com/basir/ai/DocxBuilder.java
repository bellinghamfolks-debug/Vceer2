package com.basir.ai;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal pure-Java .docx writer (no Apache POI / docx4j dependency).
 *
 * A .docx file is just a ZIP archive containing a small set of XML
 * descriptors. We assemble the bare-minimum that Microsoft Word, Google Docs,
 * LibreOffice and Pages all accept and that screen readers handle correctly.
 *
 * Supported blocks:
 *   - TITLE
 *   - HEADING (level 1..6)
 *   - PARAGRAPH (plain text)
 *
 * Right-to-left languages (Arabic) are handled via the bidi/rtl flags so
 * the document opens cleanly in any Word app.
 */
public final class DocxBuilder {

    public enum BlockType { TITLE, HEADING, PARAGRAPH, TABLE }

    public static final class Block {
        final BlockType type;
        final int level;        // for HEADING (1..6); 0 otherwise
        final String text;
        // v2.2 — table support. Non-null only when type == TABLE.
        // Each inner list is one row; first row is rendered as a header.
        final List<List<String>> cells;
        Block(BlockType type, int level, String text) {
            this(type, level, text, null);
        }
        Block(BlockType type, int level, String text, List<List<String>> cells) {
            this.type = type;
            this.level = level;
            this.text = text == null ? "" : text;
            this.cells = cells;
        }
    }

    private final List<Block> blocks = new ArrayList<>();
    private final boolean rtl;
    private final String langTag; // BCP 47 e.g. "ar" or "en-US"

    public DocxBuilder(String language) {
        boolean isArabic = language != null && language.toLowerCase().startsWith("ar");
        this.rtl = isArabic;
        this.langTag = isArabic ? "ar-SA" : "en-US";
    }

    public DocxBuilder title(String text) {
        blocks.add(new Block(BlockType.TITLE, 0, text));
        return this;
    }

    public DocxBuilder heading(int level, String text) {
        int lvl = Math.max(1, Math.min(6, level));
        blocks.add(new Block(BlockType.HEADING, lvl, text));
        return this;
    }

    public DocxBuilder paragraph(String text) {
        blocks.add(new Block(BlockType.PARAGRAPH, 0, text));
        return this;
    }

    /**
     * v2.2 — render a real Word table (not just a summary paragraph).
     * The first row is treated as a header. Empty cells render as a single
     * non-breaking space so the cell still shows borders. Null/empty
     * arguments are silently ignored.
     *
     * @param cells outer list = rows, inner list = cells in that row.
     *              Rows of unequal length are padded to the max width.
     */
    public DocxBuilder table(List<List<String>> cells) {
        if (cells == null || cells.isEmpty()) return this;
        int maxCols = 0;
        for (List<String> row : cells) {
            if (row != null && row.size() > maxCols) maxCols = row.size();
        }
        if (maxCols == 0) return this;
        // Defensive deep-copy + padding so the writer doesn't crash on
        // jagged input from the model.
        List<List<String>> padded = new ArrayList<>(cells.size());
        for (List<String> row : cells) {
            List<String> r = new ArrayList<>(maxCols);
            if (row != null) for (String s : row) r.add(s == null ? "" : s);
            while (r.size() < maxCols) r.add("");
            padded.add(r);
        }
        blocks.add(new Block(BlockType.TABLE, 0, "", padded));
        return this;
    }

    /** Write the assembled document to disk. Returns the same File. */
    public File writeTo(File output) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(output);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            putEntry(zos, "[Content_Types].xml", CONTENT_TYPES_XML);
            putEntry(zos, "_rels/.rels", DOT_RELS_XML);
            putEntry(zos, "word/_rels/document.xml.rels", DOC_RELS_XML);
            putEntry(zos, "word/styles.xml", buildStyles());
            putEntry(zos, "word/document.xml", buildDocument());
        }
        return output;
    }

    /** Write the document into memory (useful for tests / streaming). */
    public byte[] toBytes() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            putEntry(zos, "[Content_Types].xml", CONTENT_TYPES_XML);
            putEntry(zos, "_rels/.rels", DOT_RELS_XML);
            putEntry(zos, "word/_rels/document.xml.rels", DOC_RELS_XML);
            putEntry(zos, "word/styles.xml", buildStyles());
            putEntry(zos, "word/document.xml", buildDocument());
        }
        return baos.toByteArray();
    }

    // ---------------- XML assembly ----------------

    private String buildDocument() {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">");
        sb.append("<w:body>");
        for (Block b : blocks) {
            switch (b.type) {
                case TITLE:
                    sb.append(paragraphXml(b.text, "Title", 36));
                    break;
                case HEADING:
                    int hSize;
                    switch (b.level) {
                        case 1: hSize = 32; break;
                        case 2: hSize = 28; break;
                        case 3: hSize = 24; break;
                        case 4: hSize = 22; break;
                        default: hSize = 20;
                    }
                    sb.append(paragraphXml(b.text, "Heading" + b.level, hSize));
                    break;
                case TABLE:
                    sb.append(tableXml(b.cells));
                    break;
                default:
                    sb.append(paragraphXml(b.text, "Normal", 22));
            }
        }
        // section properties: a default A4 page in portrait mode, RTL if needed.
        sb.append("<w:sectPr>");
        if (rtl) sb.append("<w:bidi/>");
        sb.append("<w:pgSz w:w=\"11906\" w:h=\"16838\"/>");
        sb.append("<w:pgMar w:top=\"1417\" w:right=\"1417\" w:bottom=\"1417\" w:left=\"1417\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>");
        sb.append("</w:sectPr>");
        sb.append("</w:body></w:document>");
        return sb.toString();
    }

    /**
     * v2.2 — render a list-of-lists as a real Word table with borders.
     * Word, Google Docs, and screen readers (TalkBack on Word for Android,
     * NVDA, JAWS) all read this as a proper table structure — the user
     * can navigate row-by-row with screen-reader shortcuts.
     */
    private String tableXml(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<w:tbl>");
        // Table properties: full width, single-line borders on every edge.
        sb.append("<w:tblPr>");
        sb.append("<w:tblW w:w=\"5000\" w:type=\"pct\"/>");
        if (rtl) sb.append("<w:bidiVisual/>");
        sb.append("<w:tblBorders>")
          .append("<w:top    w:val=\"single\" w:sz=\"6\" w:color=\"888888\"/>")
          .append("<w:bottom w:val=\"single\" w:sz=\"6\" w:color=\"888888\"/>")
          .append("<w:left   w:val=\"single\" w:sz=\"6\" w:color=\"888888\"/>")
          .append("<w:right  w:val=\"single\" w:sz=\"6\" w:color=\"888888\"/>")
          .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"BBBBBB\"/>")
          .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"BBBBBB\"/>")
          .append("</w:tblBorders>");
        sb.append("</w:tblPr>");

        // tblGrid: evenly distribute columns.
        int cols = rows.get(0).size();
        sb.append("<w:tblGrid>");
        int colWidth = 9000 / Math.max(1, cols);   // twips, ~A4 usable width
        for (int i = 0; i < cols; i++) {
            sb.append("<w:gridCol w:w=\"").append(colWidth).append("\"/>");
        }
        sb.append("</w:tblGrid>");

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            boolean isHeader = (r == 0);
            sb.append("<w:tr>");
            if (isHeader) {
                // Repeat header row on each page break.
                sb.append("<w:trPr><w:tblHeader/></w:trPr>");
            }
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c);
                if (cell == null || cell.isEmpty()) cell = " ";
                sb.append("<w:tc>");
                sb.append("<w:tcPr>");
                sb.append("<w:tcW w:w=\"").append(colWidth).append("\" w:type=\"dxa\"/>");
                if (isHeader) {
                    // Light gray fill on the header row for visual scan.
                    sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E8EEF7\"/>");
                }
                sb.append("</w:tcPr>");
                sb.append("<w:p>");
                sb.append("<w:pPr>");
                if (rtl) sb.append("<w:bidi/>");
                sb.append("</w:pPr>");
                sb.append("<w:r><w:rPr>");
                if (rtl) sb.append("<w:rtl/>");
                if (isHeader) sb.append("<w:b/><w:bCs/>");
                sb.append("<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>");
                sb.append("<w:lang w:val=\"").append(langTag)
                  .append("\" w:bidi=\"").append(langTag).append("\"/>");
                sb.append("</w:rPr>");
                sb.append("<w:t xml:space=\"preserve\">").append(escape(cell)).append("</w:t>");
                sb.append("</w:r>");
                sb.append("</w:p>");
                sb.append("</w:tc>");
            }
            sb.append("</w:tr>");
        }
        sb.append("</w:tbl>");
        // Trailing empty paragraph — Word requires content after the last
        // table or the file opens with a "format error" warning on some
        // older Word versions.
        sb.append("<w:p/>");
        return sb.toString();
    }

    private String paragraphXml(String text, String style, int halfPointSize) {
        StringBuilder sb = new StringBuilder(text.length() + 200);
        sb.append("<w:p>");
        sb.append("<w:pPr>");
        sb.append("<w:pStyle w:val=\"").append(style).append("\"/>");
        if (rtl) sb.append("<w:bidi/>");
        sb.append("</w:pPr>");

        // Split on newline so explicit line breaks survive in Word.
        String[] lines = text.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            sb.append("<w:r>");
            sb.append("<w:rPr>");
            if (rtl) sb.append("<w:rtl/>");
            sb.append("<w:sz w:val=\"").append(halfPointSize).append("\"/>");
            sb.append("<w:szCs w:val=\"").append(halfPointSize).append("\"/>");
            sb.append("<w:lang w:val=\"").append(langTag).append("\" w:bidi=\"").append(langTag).append("\"/>");
            sb.append("</w:rPr>");
            sb.append("<w:t xml:space=\"preserve\">").append(escape(lines[i])).append("</w:t>");
            if (i < lines.length - 1) sb.append("<w:br/>");
            sb.append("</w:r>");
        }
        sb.append("</w:p>");
        return sb.toString();
    }

    private String buildStyles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
             + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
             + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">"
             + "<w:name w:val=\"Normal\"/></w:style>"
             + style("Title",    "Title",    "0F172A", true)
             + style("Heading1", "heading 1", "0D47A1", true)
             + style("Heading2", "heading 2", "1565C0", true)
             + style("Heading3", "heading 3", "1976D2", true)
             + style("Heading4", "heading 4", "1976D2", true)
             + style("Heading5", "heading 5", "1976D2", true)
             + style("Heading6", "heading 6", "1976D2", true)
             + "</w:styles>";
    }

    private static String style(String id, String name, String colorHex, boolean bold) {
        StringBuilder sb = new StringBuilder();
        sb.append("<w:style w:type=\"paragraph\" w:styleId=\"").append(id).append("\">");
        sb.append("<w:name w:val=\"").append(name).append("\"/>");
        sb.append("<w:basedOn w:val=\"Normal\"/>");
        sb.append("<w:pPr><w:spacing w:before=\"240\" w:after=\"120\"/></w:pPr>");
        sb.append("<w:rPr>");
        if (bold) sb.append("<w:b/><w:bCs/>");
        sb.append("<w:color w:val=\"").append(colorHex).append("\"/>");
        sb.append("</w:rPr>");
        sb.append("</w:style>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '&':  sb.append("&amp;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    // Strip control characters not legal in XML 1.0
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static void putEntry(ZipOutputStream zos, String name, String content) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    // ---------------- static XML descriptors ----------------

    private static final String CONTENT_TYPES_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
          + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
          + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
          + "<Override PartName=\"/word/document.xml\" "
          + " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
          + "<Override PartName=\"/word/styles.xml\" "
          + " ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
          + "</Types>";

    private static final String DOT_RELS_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
          + "<Relationship Id=\"rId1\" "
          + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
          + " Target=\"word/document.xml\"/>"
          + "</Relationships>";

    private static final String DOC_RELS_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
          + "<Relationship Id=\"rId1\" "
          + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" "
          + " Target=\"styles.xml\"/>"
          + "</Relationships>";
}
