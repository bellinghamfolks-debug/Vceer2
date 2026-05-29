package com.basir.ai;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * v2.8.3 — lightweight on-device .docx parser.
 *
 * Why this exists
 * ───────────────
 *   The Gemini Files API does not accept Word documents — only PDF,
 *   plain text, images, audio, video. Earlier Basir versions fell into
 *   the "generic binary" path for DOCX and Gemini rejected the upload
 *   with HTTP 400 "Unsupported MIME type". Translating a DOCX therefore
 *   never worked end-to-end. This extractor pulls the text and basic
 *   structure (paragraphs, headings, tables) out of a DOCX locally so
 *   the rest of the pipeline can hand Gemini plain text it accepts.
 *
 * What it extracts
 * ────────────────
 *   - paragraphs with their text runs concatenated
 *   - heading level (1-6) from <w:pStyle w:val="Heading1..6"/>
 *   - table cells in row order
 *
 * What it deliberately skips
 * ──────────────────────────
 *   - formatting (bold, italic, fonts, colors) — the translation flow
 *     does not preserve those anyway
 *   - embedded images — translation operates on text only
 *   - headers / footers — typically boilerplate not worth translating
 *
 * A .docx file is a ZIP with this layout:
 *   word/document.xml       main body
 *   word/header*.xml        page headers
 *   word/footer*.xml        page footers
 *   word/numbering.xml      list numbering definitions
 *   word/media/*            embedded images
 *
 * We only read word/document.xml. The text content is inside
 * <w:t>...</w:t> elements grouped by <w:p>...</w:p> paragraphs, with
 * tables wrapped in <w:tbl>...</w:tbl>.
 */
public final class DocxExtractor {

    public enum BlockType { PARAGRAPH, HEADING, TABLE }

    public static final class Block {
        public final BlockType type;
        /** Paragraph or heading text. Empty for TABLE blocks. */
        public final String text;
        /** 1-6 for HEADING, 0 otherwise. */
        public final int headingLevel;
        /** Cell grid for TABLE blocks. First row is the header row by
         *  convention (matches DocxBuilder's contract). Null for other
         *  block types. */
        public final List<List<String>> tableCells;

        Block(BlockType type, String text, int headingLevel,
              List<List<String>> tableCells) {
            this.type = type;
            this.text = text == null ? "" : text;
            this.headingLevel = headingLevel;
            this.tableCells = tableCells;
        }
    }

    public static final class Doc {
        public final List<Block> blocks;
        Doc(List<Block> blocks) { this.blocks = Collections.unmodifiableList(blocks); }

        /** Convenience: concatenate every paragraph/heading text plus a
         *  flat dump of each table. Used by the prompt builder so the
         *  prompt itself stays simple. */
        public String plainText() {
            StringBuilder sb = new StringBuilder();
            for (Block b : blocks) {
                if (b.type == BlockType.TABLE) {
                    if (b.tableCells != null) {
                        for (List<String> row : b.tableCells) {
                            for (int i = 0; i < row.size(); i++) {
                                if (i > 0) sb.append(" | ");
                                sb.append(row.get(i));
                            }
                            sb.append('\n');
                        }
                    }
                } else {
                    if (b.headingLevel > 0) {
                        for (int i = 0; i < b.headingLevel; i++) sb.append('#');
                        sb.append(' ');
                    }
                    sb.append(b.text).append('\n');
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        }
    }

    private static final Pattern T_PATTERN =
            Pattern.compile("<w:t[^>]*>(.*?)</w:t>", Pattern.DOTALL);
    private static final Pattern PARA_PATTERN =
            Pattern.compile("<w:p[ >].*?</w:p>", Pattern.DOTALL);
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("<w:pStyle\\s+w:val=\"Heading(\\d+)\"", Pattern.DOTALL);
    private static final Pattern TABLE_PATTERN =
            Pattern.compile("<w:tbl[ >].*?</w:tbl>", Pattern.DOTALL);
    private static final Pattern TR_PATTERN =
            Pattern.compile("<w:tr[ >].*?</w:tr>", Pattern.DOTALL);
    private static final Pattern TC_PATTERN =
            Pattern.compile("<w:tc[ >].*?</w:tc>", Pattern.DOTALL);

    private DocxExtractor() {}

    public static Doc parse(Context ctx, Uri uri) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        String documentXml = null;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new Exception("Could not open the Word file");
            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry e;
                byte[] buf = new byte[8192];
                while ((e = zip.getNextEntry()) != null) {
                    if ("word/document.xml".equals(e.getName())) {
                        documentXml = readAll(zip, buf);
                        break;
                    }
                }
            }
        }
        if (documentXml == null) {
            throw new Exception("The Word file does not contain word/document.xml");
        }

        // Find <w:body> ... </w:body> so we don't accidentally walk
        // styles, settings, etc. The body holds the linear content stream.
        String body = documentXml;
        int bs = body.indexOf("<w:body");
        int be = body.lastIndexOf("</w:body>");
        if (bs >= 0 && be > bs) {
            int afterOpen = body.indexOf('>', bs);
            if (afterOpen > 0) body = body.substring(afterOpen + 1, be);
        }

        List<Block> blocks = new ArrayList<>();
        // Walk top-level <w:p> and <w:tbl> in document order. The
        // simplest correct approach: find each opening tag and dispatch.
        int i = 0;
        while (i < body.length()) {
            int nextP = body.indexOf("<w:p", i);
            int nextT = body.indexOf("<w:tbl", i);
            if (nextP < 0 && nextT < 0) break;

            int pick;
            boolean isTable;
            if (nextP < 0)              { pick = nextT; isTable = true; }
            else if (nextT < 0)         { pick = nextP; isTable = false; }
            else if (nextP < nextT)     { pick = nextP; isTable = false; }
            else                        { pick = nextT; isTable = true; }

            // Defensive: <w:p ...> opens a paragraph; <w:pPr> opens the
            // paragraph-properties block (children of <w:p>). Disambiguate
            // by checking the character right after the prefix.
            if (!isTable) {
                char after = body.charAt(pick + "<w:p".length());
                if (after != ' ' && after != '>' && after != '/') {
                    // Not a paragraph (e.g. <w:pPr>, <w:proofErr>). Skip
                    // past this character and re-scan.
                    i = pick + 1;
                    continue;
                }
            }

            if (isTable) {
                int end = findClose(body, pick, "<w:tbl", "</w:tbl>");
                if (end < 0) break;
                String tblXml = body.substring(pick, end);
                Block b = parseTable(tblXml);
                if (b != null) blocks.add(b);
                i = end;
            } else {
                int end = findClose(body, pick, "<w:p",   "</w:p>");
                if (end < 0) break;
                String pXml = body.substring(pick, end);
                Block b = parseParagraph(pXml);
                if (b != null) blocks.add(b);
                i = end;
            }
        }
        return new Doc(blocks);
    }

    /** Find the offset of the END of the matching closing tag, given the
     *  start of an opening tag. Handles nesting. Returns -1 if no match. */
    private static int findClose(String src, int openStart,
                                  String openPrefix, String closeTag) {
        int depth = 0;
        int i = openStart;
        while (i < src.length()) {
            int nextOpen  = src.indexOf(openPrefix, i);
            int nextClose = src.indexOf(closeTag,   i);
            if (nextClose < 0) return -1;
            // Confirm the openPrefix is really a tag of the same kind
            // by checking it's followed by ' ', '>', or '/' (self-closing).
            if (nextOpen >= 0 && nextOpen < nextClose) {
                char after = src.charAt(nextOpen + openPrefix.length());
                if (after == ' ' || after == '>' || after == '/') {
                    depth++;
                    i = nextOpen + openPrefix.length();
                    continue;
                }
                // Not a real opener (e.g. <w:pPr>). Skip past it.
                i = nextOpen + 1;
                continue;
            }
            depth--;
            if (depth == 0) return nextClose + closeTag.length();
            i = nextClose + closeTag.length();
        }
        return -1;
    }

    private static Block parseParagraph(String pXml) {
        int level = 0;
        Matcher hm = HEADING_PATTERN.matcher(pXml);
        if (hm.find()) {
            try {
                int n = Integer.parseInt(hm.group(1));
                if (n >= 1 && n <= 6) level = n;
            } catch (NumberFormatException ignore) {}
        }
        StringBuilder text = new StringBuilder();
        Matcher tm = T_PATTERN.matcher(pXml);
        while (tm.find()) {
            String chunk = unescapeXml(tm.group(1));
            text.append(chunk);
        }
        String body = text.toString().trim();
        if (body.isEmpty() && level == 0) return null;
        return new Block(level > 0 ? BlockType.HEADING : BlockType.PARAGRAPH,
                body, level, null);
    }

    private static Block parseTable(String tblXml) {
        List<List<String>> rows = new ArrayList<>();
        Matcher tr = TR_PATTERN.matcher(tblXml);
        while (tr.find()) {
            String row = tr.group();
            List<String> cells = new ArrayList<>();
            Matcher tc = TC_PATTERN.matcher(row);
            while (tc.find()) {
                String cell = tc.group();
                StringBuilder t = new StringBuilder();
                Matcher tm = T_PATTERN.matcher(cell);
                while (tm.find()) t.append(unescapeXml(tm.group(1)));
                cells.add(t.toString().trim());
            }
            if (!cells.isEmpty()) rows.add(cells);
        }
        if (rows.isEmpty()) return null;
        return new Block(BlockType.TABLE, "", 0, rows);
    }

    private static String unescapeXml(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;",  "<")
                .replace("&gt;",  ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private static String readAll(InputStream in, byte[] buf) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
