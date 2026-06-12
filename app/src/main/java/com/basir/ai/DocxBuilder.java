package com.basir.ai;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;

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

    public enum BlockType { TITLE, HEADING, PARAGRAPH, TABLE, PAGE_BREAK }

    public static final class Block {
        final BlockType type;
        final int level;        // for HEADING (1..6); 0 otherwise
        final String text;
        // v2.2 — table support. Non-null only when type == TABLE.
        // Each inner list is one row; first row is rendered as a header.
        final List<List<String>> cells;
        // v3.1.2 — header flags. headerRow defaults true (matches v2.2
        // behaviour). headerCol turns ON the "first column is also a
        // header" rendering for schedule-style tables.
        final boolean headerRow;
        final boolean headerCol;
        final String tableCaption;
        final String tableDescription;
        Block(BlockType type, int level, String text) {
            this(type, level, text, null, true, false, "", "");
        }
        Block(BlockType type, int level, String text, List<List<String>> cells) {
            this(type, level, text, cells, true, false, "", "");
        }
        Block(BlockType type, int level, String text, List<List<String>> cells,
              boolean headerRow, boolean headerCol) {
            this(type, level, text, cells, headerRow, headerCol, "", "");
        }
        Block(BlockType type, int level, String text, List<List<String>> cells,
              boolean headerRow, boolean headerCol,
              String tableCaption, String tableDescription) {
            this.type = type;
            this.level = level;
            this.text = text == null ? "" : text;
            this.cells = cells;
            this.headerRow = headerRow;
            this.headerCol = headerCol;
            this.tableCaption = tableCaption == null ? "" : tableCaption;
            this.tableDescription = tableDescription == null ? "" : tableDescription;
        }
    }

    private final List<Block> blocks = new ArrayList<>();
    private final boolean rtl;
    private final String langTag; // BCP 47 e.g. "ar" or "en-US"

    public DocxBuilder(String language) {
        String normalized = language == null ? "" : language.trim();
        String low = normalized.toLowerCase(java.util.Locale.ROOT);
        this.rtl = low.startsWith("ar") || low.startsWith("he")
                || low.startsWith("fa") || low.startsWith("ur");
        if (normalized.isEmpty()) this.langTag = "en-US";
        else if (low.equals("ar")) this.langTag = "ar-SA";
        else this.langTag = normalized;
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

    /** Begin the next source page on a new physical Word page. */
    public DocxBuilder pageBreak() {
        blocks.add(new Block(BlockType.PAGE_BREAK, 0, ""));
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
        return table(cells, /*headerRow*/ true, /*headerCol*/ false);
    }

    /**
     * v3.1.2 — table overload with explicit header flags.
     *
     * @param cells       2-D row-major grid. Jagged rows are padded with "".
     * @param headerRow   when true (default), the first row is bold-shaded
     *                    and repeats on every page break.
     * @param headerCol   when true, the first column is ALSO bold-shaded.
     *                    Used for schedule / timetable layouts where the
     *                    left edge holds time labels. A blind reader's
     *                    Word screen-reader treats the first column as a
     *                    row header and announces it before each cell.
     */
    public DocxBuilder table(List<List<String>> cells,
                              boolean headerRow, boolean headerCol) {
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
        blocks.add(new Block(BlockType.TABLE, 0, "", padded,
                headerRow, headerCol, "", ""));
        return this;
    }

    /** Accessible table overload with assistive-technology metadata. */
    public DocxBuilder table(List<List<String>> cells,
                             boolean headerRow, boolean headerCol,
                             String caption, String description) {
        if (cells == null || cells.isEmpty()) return this;
        int maxCols = 0;
        for (List<String> row : cells) {
            if (row != null && row.size() > maxCols) maxCols = row.size();
        }
        if (maxCols == 0) return this;
        List<List<String>> padded = new ArrayList<>(cells.size());
        for (List<String> row : cells) {
            List<String> copy = new ArrayList<>(maxCols);
            if (row != null) for (String value : row) copy.add(value == null ? "" : value);
            while (copy.size() < maxCols) copy.add("");
            padded.add(copy);
        }
        blocks.add(new Block(BlockType.TABLE, 0, "", padded,
                headerRow, headerCol, caption, description));
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
                    sb.append(tableXml(b.cells, b.headerRow, b.headerCol,
                            b.tableCaption, b.tableDescription));
                    break;
                case PAGE_BREAK:
                    sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
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
    private String tableXml(List<List<String>> rows,
                             boolean headerRow, boolean headerCol,
                             String caption, String description) {
        if (rows == null || rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<w:tbl>");
        // Table properties: full width, single-line borders on every edge.
        sb.append("<w:tblPr>");
        sb.append("<w:tblStyle w:val=\"TableGrid\"/>");
        sb.append("<w:tblW w:w=\"5000\" w:type=\"pct\"/>");
        if (caption != null && !caption.trim().isEmpty()) {
            sb.append("<w:tblCaption w:val=\"")
              .append(escapeAttribute(caption)).append("\"/>");
        }
        if (description != null && !description.trim().isEmpty()) {
            sb.append("<w:tblDescription w:val=\"")
              .append(escapeAttribute(description)).append("\"/>");
        }
        sb.append("<w:tblLook w:val=\"04A0\" w:firstRow=\"")
          .append(headerRow ? "1" : "0")
          .append("\" w:lastRow=\"0\" w:firstColumn=\"")
          .append(headerCol ? "1" : "0")
          .append("\" w:lastColumn=\"0\" w:noHBand=\"0\" w:noVBand=\"1\"/>");
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

        // tblGrid: when headerCol is set (schedule-style table) the
        // first column carries short labels (time, day) and looks cramped
        // if it gets an equal share of A4's 9000-twip usable width. Give
        // it ~14% and split the remaining width evenly across data cols.
        int cols = rows.get(0).size();
        sb.append("<w:tblGrid>");
        int firstColWidth;
        int dataColWidth;
        if (headerCol && cols > 1) {
            firstColWidth = (int)(9000 * 0.14);
            dataColWidth  = (9000 - firstColWidth) / (cols - 1);
        } else {
            firstColWidth = 9000 / Math.max(1, cols);
            dataColWidth  = firstColWidth;
        }
        for (int i = 0; i < cols; i++) {
            sb.append("<w:gridCol w:w=\"")
              .append(i == 0 ? firstColWidth : dataColWidth)
              .append("\"/>");
        }
        sb.append("</w:tblGrid>");

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            boolean inHeaderRow = headerRow && r == 0;
            sb.append("<w:tr>");
            sb.append("<w:trPr><w:cantSplit/>");
            if (inHeaderRow) {
                // Repeat header row on each page break.
                sb.append("<w:tblHeader/>");
            }
            sb.append("</w:trPr>");
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c);
                if (cell == null || cell.isEmpty()) cell = " ";
                boolean inHeaderCol = headerCol && c == 0;
                boolean isCorner    = inHeaderRow && inHeaderCol;
                boolean isBold      = inHeaderRow || inHeaderCol;
                // Three-way fill scheme so a sighted reader can scan the
                // table at a glance; the bold flag carries the same info
                // for screen readers that ignore <w:shd>.
                String fill = null;
                if (isCorner)         fill = "C9D6EC";
                else if (inHeaderRow) fill = "E8EEF7";
                else if (inHeaderCol) fill = "F0F3F9";
                sb.append("<w:tc>");
                sb.append("<w:tcPr>");
                sb.append("<w:tcW w:w=\"")
                  .append(c == 0 ? firstColWidth : dataColWidth)
                  .append("\" w:type=\"dxa\"/>");
                if (fill != null) {
                    sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"")
                      .append(fill).append("\"/>");
                }
                sb.append("</w:tcPr>");
                sb.append("<w:p>");
                sb.append("<w:pPr>");
                if (rtl) sb.append("<w:bidi/>");
                sb.append("</w:pPr>");
                appendRuns(sb, cell, 22, isBold);
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

        appendRuns(sb, text, halfPointSize, false);
        sb.append("</w:p>");
        return sb.toString();
    }

    private void appendRuns(StringBuilder sb, String text,
                            int halfPointSize, boolean bold) {
        String safe = text == null ? "" : text;
        String[] lines = safe.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            sb.append("<w:r><w:rPr>");
            if (rtl) sb.append("<w:rtl/>");
            if (bold) sb.append("<w:b/><w:bCs/>");
            sb.append("<w:sz w:val=\"").append(halfPointSize).append("\"/>");
            sb.append("<w:szCs w:val=\"").append(halfPointSize).append("\"/>");
            sb.append("<w:lang w:val=\"").append(escapeAttribute(langTag))
              .append("\" w:bidi=\"").append(escapeAttribute(langTag)).append("\"/>");
            sb.append("</w:rPr>");
            sb.append("<w:t xml:space=\"preserve\">").append(escape(lines[i])).append("</w:t>");
            if (i < lines.length - 1) sb.append("<w:br/>");
            sb.append("</w:r>");
        }
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

    private static String escapeAttribute(String s) {
        return escape(s == null ? "" : s).replace("\n", " ").replace("\r", " ");
    }

    /** Structural post-write validation for the generated DOCX package. */
    public static void validatePackage(File file, int expectedPages,
                                       boolean expectTables) throws Exception {
        if (file == null || !file.isFile() || file.length() < 500) {
            throw new Exception("Generated DOCX is missing or too small.");
        }
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry document = zip.getEntry("word/document.xml");
            ZipEntry styles = zip.getEntry("word/styles.xml");
            if (document == null || styles == null) {
                throw new Exception("Generated DOCX is missing required Word parts.");
            }
            String xml;
            try (InputStream in = zip.getInputStream(document);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
            if (!xml.contains("<w:document") || !xml.contains("</w:document>")) {
                throw new Exception("Generated Word XML is incomplete.");
            }
            boolean hasWordTable = xml.contains("<w:tbl>");
            if (expectTables && !hasWordTable) {
                throw new Exception("Expected Word tables are missing from the generated DOCX.");
            }
            // A common catastrophic conversion writes table rows as ordinary
            // paragraphs separated by | characters. Even when the caller did
            // not know beforehand that the source had tables, reject this
            // unmistakable flattened-grid signature instead of presenting it
            // as a successful Word document.
            if (!hasWordTable && countOccurrences(xml, "|") >= 4) {
                throw new Exception("The generated DOCX appears to contain a flattened table instead of real Word cells.");
            }
            if (expectedPages > 0) {
                int pageLabels = countOccurrences(xml, "الصفحة ")
                        + countOccurrences(xml, "Page ");
                if (pageLabels < expectedPages) {
                    throw new Exception("Generated DOCX contains only " + pageLabels
                            + " page headings for " + expectedPages + " source pages.");
                }
            }
        }
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(token, from);
            if (at < 0) return count;
            count++;
            from = at + token.length();
        }
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
