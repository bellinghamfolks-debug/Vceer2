package com.basir.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict, page-scoped document extraction and deterministic validation.
 *
 * A page is never considered successful merely because Gemini returned valid
 * JSON. The response must identify the exact page, account for every visible
 * table, contain rectangular cell grids, avoid flattened pipe-delimited tables,
 * and pass academic-grade arithmetic checks when such a table is detected.
 */
final class DocumentPageExtractor {

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_OUTPUT_TOKENS = 32768;
    private static final int MAX_SECTIONS_PER_PAGE = 500;

    private DocumentPageExtractor() {}

    private interface Requester {
        JSONObject request(String model, String prompt, JSONObject schema) throws Exception;
    }

    static JSONObject extractFromImage(String apiKey, String model,
                                       int pageNumber, int totalPages,
                                       String mode, String outputLanguageName,
                                       PdfPageRasterizer.PageImage image) throws Exception {
        if (image == null || image.jpegBytes == null || image.jpegBytes.length == 0) {
            throw new Exception("Rendered page image is empty.");
        }
        Requester requester = (requestModel, prompt, schema) ->
                GeminiDirectClient.generateStrictJsonWithImageBytes(
                        apiKey, requestModel,
                        strictSystemInstruction(), prompt,
                        image.jpegBytes, "image/jpeg", schema,
                        MAX_OUTPUT_TOKENS);
        String sourceNote = "The supplied image is a high-resolution rendering of this page. "
                + "Automatic orientation correction applied " + image.rotationDegrees
                + " degrees. If the text still appears upside down, mentally rotate it 180 degrees before reading.";
        return extractAndVerify(requester, model, pageNumber, totalPages, mode,
                outputLanguageName, sourceNote, image.visuallyBlank);
    }

    static JSONObject extractFromPdfFilePart(String apiKey, String model,
                                             int pageNumber, int totalPages,
                                             String mode, String outputLanguageName,
                                             JSONObject filePart) throws Exception {
        Requester requester = (requestModel, prompt, schema) ->
                GeminiDirectClient.generateStrictJsonWithFilePart(
                        apiKey, requestModel,
                        strictSystemInstruction(), prompt,
                        filePart, schema, MAX_OUTPUT_TOKENS);
        String sourceNote = "The supplied source is the original PDF. Inspect ONLY page "
                + pageNumber + ". The scan may be sideways or upside down; rotate it mentally before reading.";
        return extractAndVerify(requester, model, pageNumber, totalPages, mode,
                outputLanguageName, sourceNote, null);
    }

    private static JSONObject extractAndVerify(Requester requester, String model,
                                               int pageNumber, int totalPages,
                                               String mode, String outputLanguageName,
                                               String sourceNote,
                                               Boolean visuallyBlankHint) throws Exception {
        JSONObject schema = pageResponseSchema(pageNumber);
        String basePrompt = buildPagePrompt(pageNumber, totalPages, mode,
                outputLanguageName, sourceNote);
        JSONObject first = requestValidated(requester, model, basePrompt, schema,
                pageNumber, mode, visuallyBlankHint);

        // A second blind pass is performed for every non-blank page in a mode
        // that preserves tables. This matters even when the first pass reports
        // zero tables: otherwise a model could silently miss every grid and the
        // local validator would have no independent inventory to contradict it.
        if (requiresTableExtraction(mode) && !first.optBoolean("is_blank", false)) {
            String verifyPrompt = buildVerificationPrompt(pageNumber, totalPages,
                    mode, outputLanguageName, sourceNote, first);
            JSONObject second = requestValidated(requester, model, verifyPrompt, schema,
                    pageNumber, mode, visuallyBlankHint);
            String firstDigest = tableDigest(first);
            String secondDigest = tableDigest(second);
            boolean anyTables = tableCount(first) > 0 || tableCount(second) > 0
                    || first.optInt("visible_table_count", 0) > 0
                    || second.optInt("visible_table_count", 0) > 0;

            // Academic records are high-stakes and row swaps can remain
            // arithmetically self-consistent. Always perform a third blind read,
            // then accept only a two-out-of-three table-grid consensus.
            if (anyTables && (looksAcademicPage(first) || looksAcademicPage(second))) {
                String thirdPrompt = verifyPrompt
                        + "\n\nTHIRD INDEPENDENT AUDIT: reread from the source again without trusting any earlier result.";
                JSONObject third = requestValidated(requester, model, thirdPrompt, schema,
                        pageNumber, mode, visuallyBlankHint);
                String thirdDigest = tableDigest(third);
                if (firstDigest.equals(secondDigest)) return second;
                if (firstDigest.equals(thirdDigest)) return third;
                if (secondDigest.equals(thirdDigest)) return third;

                String adjudicatePrompt = buildAdjudicationPrompt(pageNumber, totalPages,
                        mode, outputLanguageName, sourceNote, first, second, third);
                return requestValidated(requester, model, adjudicatePrompt, schema,
                        pageNumber, mode, visuallyBlankHint);
            }

            if (!firstDigest.equals(secondDigest)) {
                String adjudicatePrompt = buildAdjudicationPrompt(pageNumber, totalPages,
                        mode, outputLanguageName, sourceNote, first, second, null);
                return requestValidated(requester, model, adjudicatePrompt, schema,
                        pageNumber, mode, visuallyBlankHint);
            }
            // When both passes independently find no table, keep the first full
            // transcription. If tables exist and agree, use the second audit.
            return anyTables ? second : first;
        }
        return first;
    }

    private static JSONObject requestValidated(Requester requester, String model,
                                               String initialPrompt, JSONObject schema,
                                               int pageNumber, String mode,
                                               Boolean visuallyBlankHint) throws Exception {
        Exception last = null;
        String correction = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new Exception("Cancelled");
            String prompt = initialPrompt;
            if (!correction.isEmpty()) {
                prompt += "\n\nTHE PREVIOUS OUTPUT WAS REJECTED BY THE LOCAL VALIDATOR:\n"
                        + correction
                        + "\nRe-read the page from the image/PDF. Return a complete corrected object; do not explain the error.";
            }
            // v3.3.6 — diagnostic: log every attempt explicitly so
            // the report shows triple-POST 200s with the EXACT
            // validator rule that fired between each attempt.
            ConversionDiagnostic.get().step("validate-attempt",
                    "page " + pageNumber + " attempt " + attempt + "/" + MAX_ATTEMPTS
                    + (correction.isEmpty() ? "" : "  correction-hint=" + safeShort(correction, 200)));
            try {
                JSONObject parsed = requester.request(model, prompt, schema);
                // Log a compact view of WHAT Gemini returned so the
                // user can scan it next to the validator verdict.
                if (parsed != null) {
                    int sections = parsed.optJSONArray("sections") == null
                            ? -1 : parsed.optJSONArray("sections").length();
                    ConversionDiagnostic.get().step("validate-input",
                            "page " + pageNumber + " attempt " + attempt
                            + "  page_number=" + parsed.optInt("page_number", -999)
                            + "  end_page=" + parsed.optInt("end_page", -999)
                            + "  is_blank=" + parsed.optBoolean("is_blank", false)
                            + "  visible_table_count=" + parsed.optInt("visible_table_count", -999)
                            + "  sections.length=" + sections
                            + "  has_summary=" + parsed.has("summary")
                            + "  readability=" + parsed.optString("readability", ""));
                }
                validateAndNormalize(parsed, pageNumber, mode, visuallyBlankHint);
                ConversionDiagnostic.get().step("validate-verdict",
                        "page " + pageNumber + " attempt " + attempt + "  accepted=true");
                return parsed;
            } catch (Exception e) {
                last = e;
                correction = sanitizeForPrompt(e.getMessage());
                ConversionDiagnostic.get().validatorReject(pageNumber,
                        "attempt-" + attempt, e.getMessage());
                ConversionDiagnostic.get().step("validate-verdict",
                        "page " + pageNumber + " attempt " + attempt
                        + "  accepted=false  reason=" + e.getMessage());
            }
        }
        String reason = last == null ? "unknown validation failure" : last.getMessage();
        throw new Exception("Page " + pageNumber
                + " could not pass strict extraction validation after "
                + MAX_ATTEMPTS + " attempts: " + reason, last);
    }

    private static String safeShort(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    static void validateAndNormalize(JSONObject root, int pageNumber,
                                     String mode, Boolean visuallyBlankHint) throws Exception {
        if (root == null) throw new Exception("The page response is null.");
        if (root.has("summary")) {
            throw new Exception("A summary was returned even though literal transcription was required.");
        }
        int reportedPage = root.optInt("page_number", -1);
        int reportedEnd = root.optInt("end_page", -1);
        if (reportedPage != pageNumber || reportedEnd != pageNumber) {
            throw new Exception("The response does not identify exactly page " + pageNumber + ".");
        }

        boolean blank = root.optBoolean("is_blank", false);
        if (Boolean.FALSE.equals(visuallyBlankHint) && blank) {
            throw new Exception("The model marked a visibly non-blank page as blank.");
        }

        int visibleTableCount = root.optInt("visible_table_count", -1);
        if (visibleTableCount < 0 || visibleTableCount > 100) {
            throw new Exception("visible_table_count is missing or unreasonable.");
        }

        JSONArray sections = root.optJSONArray("sections");
        if (sections == null) throw new Exception("The sections array is missing.");
        if (sections.length() > MAX_SECTIONS_PER_PAGE) {
            throw new Exception("The page returned an unreasonable number of sections.");
        }
        if (blank) {
            if (sections.length() != 0 || visibleTableCount != 0) {
                throw new Exception("A blank page must not contain extracted sections or tables.");
            }
            return;
        }
        if (sections.length() == 0) {
            throw new Exception("A non-blank page returned no content.");
        }

        int extractedTables = 0;
        int meaningfulSections = 0;
        Set<String> duplicateGuard = new HashSet<>();

        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) {
                throw new Exception("Section " + (i + 1) + " is not an object.");
            }
            String type = section.optString("type", "").trim();
            int sectionPage = section.optInt("page", -1);
            if (sectionPage != pageNumber) {
                throw new Exception("Section " + (i + 1) + " belongs to the wrong page.");
            }

            switch (type) {
                case "heading": {
                    String text = requiredText(section, "text", "heading");
                    int level = section.optInt("level", 0);
                    if (level < 1 || level > 6) {
                        throw new Exception("Heading level must be between 1 and 6.");
                    }
                    meaningfulSections++;
                    rejectDuplicate(duplicateGuard, "h:" + collapseWhitespace(text));
                    break;
                }
                case "paragraph": {
                    String text = requiredText(section, "text", "paragraph");
                    if (looksLikeFlattenedTable(text)) {
                        throw new Exception("A table was flattened into a paragraph instead of cells.");
                    }
                    meaningfulSections++;
                    rejectDuplicate(duplicateGuard, "p:" + collapseWhitespace(text));
                    break;
                }
                case "table": {
                    extractedTables++;
                    JSONArray rows = section.optJSONArray("cells");
                    validateTable(rows, i + 1);
                    int declaredRows = section.optInt("visible_row_count", -1);
                    int declaredColumns = section.optInt("visible_column_count", -1);
                    int actualColumns = rows.optJSONArray(0) == null
                            ? 0 : rows.optJSONArray(0).length();
                    if (declaredRows != rows.length() || declaredColumns != actualColumns) {
                        throw new Exception("Table " + (i + 1)
                                + " geometry mismatch: the model counted "
                                + declaredRows + "×" + declaredColumns
                                + " but returned " + rows.length() + "×" + actualColumns + ".");
                    }
                    validateAcademicArithmetic(rows, i + 1);
                    meaningfulSections++;
                    rejectDuplicate(duplicateGuard, "t:" + canonicalRows(rows));
                    break;
                }
                case "image_description": {
                    requiredText(section, "description", "image description");
                    meaningfulSections++;
                    break;
                }
                case "list": {
                    JSONArray items = section.optJSONArray("items");
                    if (items == null || items.length() == 0) {
                        throw new Exception("A list section contains no items.");
                    }
                    for (int j = 0; j < items.length(); j++) {
                        Object value = items.opt(j);
                        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
                            throw new Exception("List item " + (j + 1) + " is empty or not text.");
                        }
                    }
                    meaningfulSections++;
                    break;
                }
                case "table_description":
                    throw new Exception("table_description is prohibited; actual table cells are required.");
                case "page_marker":
                    throw new Exception("Page markers are generated locally and must not be returned by the model.");
                default:
                    throw new Exception("Unsupported section type: " + type);
            }
        }

        if (meaningfulSections == 0) {
            throw new Exception("The page contains no meaningful extracted content.");
        }
        if (requiresTableExtraction(mode) && extractedTables != visibleTableCount) {
            throw new Exception("The model reported " + visibleTableCount
                    + " visible tables but returned " + extractedTables + " table grids.");
        }
    }

    private static void validateTable(JSONArray rows, int tableNumber) throws Exception {
        if (rows == null || rows.length() == 0) {
            throw new Exception("Table " + tableNumber + " contains no cells.");
        }
        int columns = -1;
        boolean hasNonEmpty = false;
        for (int r = 0; r < rows.length(); r++) {
            JSONArray row = rows.optJSONArray(r);
            if (row == null) {
                throw new Exception("Table " + tableNumber + ", row " + (r + 1)
                        + " is not an array.");
            }
            if (columns < 0) columns = row.length();
            if (columns < 2) {
                throw new Exception("Table " + tableNumber + " must have at least two columns.");
            }
            if (row.length() != columns) {
                throw new Exception("Table " + tableNumber + " has shifted/jagged rows: row "
                        + (r + 1) + " has " + row.length() + " cells instead of " + columns + ".");
            }
            for (int c = 0; c < row.length(); c++) {
                Object value = row.opt(c);
                if (!(value instanceof String)) {
                    throw new Exception("Table " + tableNumber + ", row " + (r + 1)
                            + ", column " + (c + 1) + " is not text.");
                }
                if (!((String) value).trim().isEmpty()) hasNonEmpty = true;
            }
        }
        if (!hasNonEmpty) throw new Exception("Table " + tableNumber + " is entirely empty.");
    }

    /**
     * Strong but conservative academic-transcript check. It runs only when
     * headers clearly identify grade, credit-hours and points columns. Unknown
     * grading symbols are skipped. A contradiction forces the model to reread.
     */
    private static void validateAcademicArithmetic(JSONArray rows, int tableNumber) throws Exception {
        if (rows == null || rows.length() < 2) return;
        JSONArray header = rows.optJSONArray(0);
        if (header == null) return;

        int gradeCol = -1, hoursCol = -1, pointsCol = -1;
        for (int c = 0; c < header.length(); c++) {
            String h = normalizeArabic(header.optString(c, ""));
            if (containsAny(h, "التقدير", "grade")) gradeCol = c;
            if (h.equals("س") || containsAny(h, "الساعات", "ساعات", "credit", "hours")) hoursCol = c;
            if (containsAny(h, "النقاط", "نقاط", "points")) pointsCol = c;
        }
        if (gradeCol < 0 || hoursCol < 0 || pointsCol < 0) return;

        for (int r = 1; r < rows.length(); r++) {
            JSONArray row = rows.optJSONArray(r);
            if (row == null) continue;
            String all = normalizeArabic(row.toString());
            if (containsAny(all, "فصلي", "تراكمي", "معدل", "registered", "cumulative", "term")) {
                continue;
            }
            Double gradeValue = gradeValue(row.optString(gradeCol, ""));
            Double hours = parseNumber(row.optString(hoursCol, ""));
            Double points = parseNumber(row.optString(pointsCol, ""));
            if (gradeValue == null || hours == null || points == null || hours <= 0d) continue;
            double expected = gradeValue * hours;
            if (Math.abs(expected - points) > 0.26d) {
                throw new Exception("Academic arithmetic mismatch in table " + tableNumber
                        + ", row " + (r + 1) + ": grade/hours imply "
                        + formatNumber(expected) + " points but the extracted cell says "
                        + formatNumber(points) + ". Re-read the row from the page.");
            }
        }
    }

    private static Double gradeValue(String raw) {
        String g = normalizeArabic(raw).replace(" ", "").toUpperCase(Locale.ROOT);
        if (g.equals("ا+") || g.equals("A+")) return 5.0d;
        if (g.equals("ا")  || g.equals("A"))  return 4.75d;
        if (g.equals("ب+") || g.equals("B+")) return 4.5d;
        if (g.equals("ب")  || g.equals("B"))  return 4.0d;
        if (g.equals("ج+") || g.equals("C+")) return 3.5d;
        if (g.equals("ج")  || g.equals("C"))  return 3.0d;
        if (g.equals("د+") || g.equals("D+")) return 2.5d;
        if (g.equals("د")  || g.equals("D"))  return 2.0d;
        return null;
    }

    private static Double parseNumber(String raw) {
        if (raw == null) return null;
        String s = toWesternDigits(raw)
                .replace('٫', '.')
                .replace("٬", "")
                .replace(",", "")
                .trim();
        Matcher m = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?").matcher(s);
        if (!m.find()) return null;
        try { return Double.parseDouble(m.group()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static boolean looksAcademicPage(JSONObject page) {
        if (page == null) return false;
        String text = normalizeArabic(page.toString());
        return containsAny(text,
                "سجل اكاديمي", "رمز المقرر", "اسم المقرر",
                "التقدير", "النقاط", "official transcript",
                "course code", "credit hours", "cumulative");
    }

    static int tableCount(JSONObject page) {
        JSONArray sections = page == null ? null : page.optJSONArray("sections");
        if (sections == null) return 0;
        int count = 0;
        for (int i = 0; i < sections.length(); i++) {
            JSONObject s = sections.optJSONObject(i);
            if (s != null && "table".equals(s.optString("type"))) count++;
        }
        return count;
    }

    private static String tableDigest(JSONObject page) {
        JSONArray sections = page.optJSONArray("sections");
        StringBuilder out = new StringBuilder();
        if (sections == null) return "";
        for (int i = 0; i < sections.length(); i++) {
            JSONObject s = sections.optJSONObject(i);
            if (s == null || !"table".equals(s.optString("type"))) continue;
            out.append(collapseWhitespace(s.optString("caption", ""))).append('|');
            out.append(canonicalRows(s.optJSONArray("cells"))).append('\n');
        }
        return out.toString();
    }

    private static String canonicalRows(JSONArray rows) {
        if (rows == null) return "";
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows.length(); r++) {
            JSONArray row = rows.optJSONArray(r);
            if (row == null) continue;
            if (r > 0) out.append("⏎");
            for (int c = 0; c < row.length(); c++) {
                if (c > 0) out.append("¦");
                out.append(collapseWhitespace(row.optString(c, "")));
            }
        }
        return out.toString();
    }

    private static boolean looksLikeFlattenedTable(String text) {
        if (text == null) return false;
        int pipes = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '|') pipes++;
        if (pipes >= 2 || text.indexOf('\t') >= 0) return true;

        String[] lines = text.split("\\r?\\n");
        int tabularLines = 0;
        Pattern columns = Pattern.compile("\\S(?:.*?\\s{2,}){2,}\\S");
        for (String line : lines) {
            if (columns.matcher(line).matches()) tabularLines++;
        }
        return tabularLines >= 2;
    }

    private static String requiredText(JSONObject object, String key, String label) throws Exception {
        Object value = object.opt(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new Exception("The " + label + " field '" + key + "' is empty or not text.");
        }
        return (String) value;
    }

    private static void rejectDuplicate(Set<String> seen, String value) throws Exception {
        if (!seen.add(value)) throw new Exception("The page repeats the same section more than once.");
    }

    private static boolean requiresTableExtraction(String mode) {
        String primary = AiClient.stripMathFlag(mode);
        return primary == null || !"descriptions_only".equalsIgnoreCase(primary);
    }

    private static JSONObject pageResponseSchema(int pageNumber) throws Exception {
        // v3.3.4 — Gemini's response_schema accepts a SUBSET of
        // OpenAPI 3.0 schema. "additionalProperties" is part of
        // OpenAPI but NOT part of Gemini's subset; v3.3.3's log
        // captured the rejection verbatim:
        //   HTTP 400 INVALID_ARGUMENT
        //   Unknown name "additionalProperties" at
        //     'generation_config.response_schema': Cannot find field.
        // Strict validation lives in validateRoot(...) below, so
        // dropping the schema-level guard does not relax our
        // acceptance criteria: any extra keys the model emits still
        // get filtered by the downstream validator.
        JSONObject section = new JSONObject().put("type", "object");
        JSONObject sectionProperties = new JSONObject();
        sectionProperties.put("type", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray()
                        .put("heading").put("paragraph").put("table")
                        .put("image_description").put("list"))
                .put("description", "Exact section type. Never use table_description or page_marker."));
        sectionProperties.put("page", boundedInteger(pageNumber));
        sectionProperties.put("level", new JSONObject().put("type", "integer")
                .put("minimum", 1).put("maximum", 6));
        sectionProperties.put("text", new JSONObject().put("type", "string"));
        sectionProperties.put("context", new JSONObject().put("type", "string"));
        sectionProperties.put("description", new JSONObject().put("type", "string"));
        sectionProperties.put("caption", new JSONObject().put("type", "string"));
        sectionProperties.put("row_header", new JSONObject().put("type", "boolean"));
        sectionProperties.put("visible_row_count", new JSONObject().put("type", "integer")
                .put("minimum", 1).put("maximum", 500));
        sectionProperties.put("visible_column_count", new JSONObject().put("type", "integer")
                .put("minimum", 1).put("maximum", 100));
        sectionProperties.put("items", new JSONObject().put("type", "array")
                .put("items", new JSONObject().put("type", "string")));
        sectionProperties.put("cells", new JSONObject().put("type", "array")
                .put("items", new JSONObject().put("type", "array")
                        .put("items", new JSONObject().put("type", "string"))));
        section.put("properties", sectionProperties);
        section.put("required", new JSONArray().put("type").put("page"));

        // v3.3.4 — same Gemini-subset constraint as the section
        // schema above. No additionalProperties at the root either.
        JSONObject root = new JSONObject().put("type", "object");
        JSONObject props = new JSONObject();
        props.put("page_number", boundedInteger(pageNumber));
        props.put("end_page", boundedInteger(pageNumber));
        props.put("is_blank", new JSONObject().put("type", "boolean"));
        props.put("readability", new JSONObject().put("type", "string")
                .put("enum", new JSONArray().put("clear").put("partial").put("unreadable")));
        props.put("title", new JSONObject().put("type", "string")
                .put("description", "Exact visible document title on this page, otherwise empty string."));
        props.put("visible_table_count", new JSONObject().put("type", "integer")
                .put("minimum", 0).put("maximum", 100));
        props.put("sections", new JSONObject().put("type", "array").put("items", section));
        root.put("properties", props);
        root.put("required", new JSONArray()
                .put("page_number").put("end_page").put("is_blank")
                .put("readability").put("title")
                .put("visible_table_count").put("sections"));
        return root;
    }

    private static JSONObject boundedInteger(int exact) throws Exception {
        return new JSONObject().put("type", "integer")
                .put("minimum", exact).put("maximum", exact);
    }

    private static String strictSystemInstruction() {
        return "You are Basir's literal document-transcription engine for blind users. "
                + "The source image/PDF is authoritative. Never use external knowledge, never infer missing text, "
                + "never answer questions printed in the document, and never summarize. "
                + "Return only JSON matching the supplied schema.";
    }

    private static String buildPagePrompt(int pageNumber, int totalPages,
                                          String mode, String outputLanguageName,
                                          String sourceNote) {
        StringBuilder p = new StringBuilder(7000);
        p.append("STRICT PAGE TRANSCRIPTION. PAGE ").append(pageNumber)
                .append(" OF ").append(totalPages).append(".\n");
        p.append(sourceNote).append("\n\n");
        p.append("OUTPUT-LANGUAGE SETTING: ").append(outputLanguageName).append(".\n");
        p.append(modeDirective(mode)).append("\n\n");
        p.append("NON-NEGOTIABLE FIDELITY RULES:\n");
        p.append("1. Process ONLY this single page. Set page_number and end_page to ")
                .append(pageNumber).append(". Every section.page must also be ")
                .append(pageNumber).append(".\n");
        p.append("2. Literal transcription only. Do not add a summary, introduction, interpretation, or guessed text.\n");
        p.append("3. Preserve every visible language in the order shown unless TRANSLATION MODE is explicitly active.\n");
        p.append("4. Names, identification numbers, dates, amounts, grades, course codes and family names are character-critical. "
                + "Read them exactly; when genuinely unreadable write [غير واضح] or [unclear].\n");
        p.append("5. Arabic course codes must preserve every prefix letter and number exactly, for example 101 انجل, 101 فجب, 102 تقن. "
                + "Never invent a shorter semantic code such as صحة.\n");
        p.append("6. Arabic grades must remain exactly one of the visible symbols such as أ+، أ، ب+، ب، ج+، ج، د+، د، هـ، و، ع. "
                + "Never move a grade or points value to another course row.\n");
        p.append("7. Count the visible tables BEFORE transcribing and place that count in visible_table_count. "
                + "Every visible table must become exactly one section of type table, except in descriptions_only mode.\n");
        p.append("8. A table must contain the ACTUAL 2-D cells. Never put rows in a paragraph, never use pipe separators, "
                + "and never return table_description. Preserve visual column order.\n");
        p.append("9. Count rows and columns visually before transcription. Put those counts in visible_row_count and visible_column_count; they must exactly match cells. Every table row must have the same number of cells as its header. Use an empty string for a blank cell. "
                + "For merged cells, duplicate the visible value into every covered cell so no column shifts.\n");
        p.append("10. For academic tables, silently verify each readable row: points must agree with credit hours and the printed grade. "
                + "Also compare printed term totals against the extracted rows. If something conflicts, reread the image instead of guessing.\n");
        p.append("11. Set is_blank=true only when the page has no meaningful visible content; then sections must be empty and visible_table_count=0.\n");
        p.append("12. title must be exact visible title text, not a generated label. Use an empty string when no title is visible.\n\n");
        p.append("SECTION RULES:\n");
        p.append("- heading: include text and level.\n");
        p.append("- paragraph: continuous non-tabular text only.\n");
        p.append("- table: include caption (possibly empty), row_header, and cells.\n");
        p.append("- image_description: describe non-text visual material accurately for a blind reader; include visible text too.\n");
        p.append("- list: include items in their visible order.\n");
        p.append("Return JSON only. Perform all checking silently.");
        return p.toString();
    }

    private static String buildVerificationPrompt(int pageNumber, int totalPages,
                                                  String mode, String outputLanguageName,
                                                  String sourceNote, JSONObject ignoredCandidate) {
        return buildPagePrompt(pageNumber, totalPages, mode, outputLanguageName, sourceNote)
                + "\n\nSECOND INDEPENDENT VISUAL PASS:\n"
                + "Start from the source page only. Do not assume any earlier extraction exists. "
                + "Recount every visible table, row and column, then reread each cell character-by-character. "
                + "Pay special attention to Arabic course-code prefixes, names, grades, credit hours, points, "
                + "term totals and blank cells. Return the complete page object.";
    }

    private static String buildAdjudicationPrompt(int pageNumber, int totalPages,
                                                  String mode, String outputLanguageName,
                                                  String sourceNote,
                                                  JSONObject first, JSONObject second,
                                                  JSONObject third) {
        StringBuilder prompt = new StringBuilder(buildPagePrompt(
                pageNumber, totalPages, mode, outputLanguageName, sourceNote));
        prompt.append("\n\nFINAL VISUAL ADJUDICATION:\n");
        prompt.append("Independent table transcriptions disagree. Inspect the source page itself; do not average values. ");
        prompt.append("For every differing code, name, grade, hour, point, total, row and column, choose only what is visibly printed. ");
        prompt.append("Return one complete final page object.\n");
        prompt.append("VERSION_A_BEGIN\n").append(first.toString()).append("\nVERSION_A_END\n");
        prompt.append("VERSION_B_BEGIN\n").append(second.toString()).append("\nVERSION_B_END\n");
        if (third != null) {
            prompt.append("VERSION_C_BEGIN\n").append(third.toString()).append("\nVERSION_C_END\n");
        }
        return prompt.toString();
    }

    private static String modeDirective(String mode) {
        boolean math = AiClient.hasMathFlag(mode);
        String primary = AiClient.stripMathFlag(mode);
        String base;
        if (primary != null && primary.toLowerCase(Locale.ROOT).startsWith("translate:")) {
            base = "TRANSLATION MODE: translate every textual leaf into the declared output language while preserving table geometry, "
                    + "numbers, dates, identifiers, course codes and proper nouns. Return translation only.";
        } else if ("descriptions_only".equalsIgnoreCase(primary)) {
            base = "DESCRIPTIONS-ONLY MODE: return only image_description sections. Do not transcribe ordinary text or tables.";
        } else if ("text_only".equalsIgnoreCase(primary)) {
            base = "TEXT-ONLY MODE: transcribe all visible text and all table cells. Skip descriptions of decorative graphics.";
        } else if ("simple".equalsIgnoreCase(primary)) {
            base = "SIMPLE ACCESSIBLE MODE: keep all factual text and real table grids, but omit decorative details.";
        } else {
            base = "FULL MODE: transcribe all text, all real table cells, and useful descriptions of non-text visuals.";
        }
        if (math) {
            base += " Mathematical expressions must include a spoken form followed by [LaTeX: ...].";
        }
        return base;
    }

    private static String sanitizeForPrompt(String message) {
        if (message == null || message.trim().isEmpty()) return "Unspecified invalid response.";
        String s = message.replace('\r', ' ').replace('\n', ' ').trim();
        return s.length() <= 800 ? s : s.substring(0, 800);
    }

    private static String collapseWhitespace(String s) {
        if (s == null) return "";
        return s.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String normalizeArabic(String s) {
        if (s == null) return "";
        return toWesternDigits(s)
                .replace("ـ", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String toWesternDigits(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '٠' && c <= '٩') out.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') out.append((char) ('0' + (c - '۰')));
            else out.append(c);
        }
        return out.toString();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
