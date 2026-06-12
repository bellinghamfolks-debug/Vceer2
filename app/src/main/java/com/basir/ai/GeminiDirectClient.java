package com.basir.ai;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Direct client for Google's Generative Language API (Gemini).
 *
 * Endpoints used:
 *   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={API_KEY}
 *   POST https://generativelanguage.googleapis.com/upload/v1beta/files?key={API_KEY}    (Files API)
 *
 * No server is required - the user's Gemini key is stored locally inside the app.
 * Used when the user picks "direct" mode in the settings dialog.
 */
public final class GeminiDirectClient {

    private GeminiDirectClient() {}

    // Default model identifiers. Mapped to user-visible quality presets:
    //   FLASH_LITE -> "Fast"      (cheapest, fastest)
    //   FLASH      -> "Balanced"  (default)
    //   PRO        -> "Best"      (highest quality, slowest)
    //
    // v3.3 — promoted to the Gemini 3 family per the IDs currently
    // available in Google AI Studio:
    //   Fast       gemini-3.1-flash-lite
    //   Balanced   gemini-3.5-flash
    //   Best       gemini-3.1-pro-preview
    //
    // The minor numbers (3.1 vs 3.5) are NOT a typo — Google
    // shipped the Flash refresh ahead of the Pro / Flash-Lite line,
    // so the active checkpoints don't share a single minor. Pro
    // still carries the "-preview" suffix until Google flips it to
    // GA, at which point a maintainer should drop the suffix.
    //
    // The user-visible Settings → "Model picker" UI still lets the
    // user override these per-preset via the SharedPreferences
    // keys "gemini_model_fast_lite", "gemini_model_quick", and
    // "gemini_model_doc" — useful for pinning to 2.5 for cost or
    // eval baselines, or adopting a later 3.x snapshot ahead of
    // the next default bump.
    public static final String DEFAULT_FLASH_LITE = "gemini-3.1-flash-lite";
    public static final String DEFAULT_FLASH      = "gemini-3.5-flash";
    public static final String DEFAULT_PRO        = "gemini-3.1-pro-preview";

    // Legacy aliases (kept so older code paths keep compiling).
    public static final String DEFAULT_FAST = DEFAULT_FLASH;

    private static final String BASE        = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String UPLOAD_BASE = "https://generativelanguage.googleapis.com/upload/v1beta/files";
    private static final String FILES_BASE  = "https://generativelanguage.googleapis.com/v1beta/";

    // Gemini caps inline data at ~20 MB request size. We stay below that with margin
    // and switch to the Files API for anything larger.
    public static final int INLINE_MAX_BYTES = 18 * 1024 * 1024;

    private static final int RETRY_ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MS = { 2_000L, 4_000L, 8_000L };

    private static String generateEndpoint(String model, String apiKey) throws Exception {
        return BASE + model + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8");
    }

    /**
     * Send a text-only or text+image prompt and return the model's plain text answer.
     */
    public static String generateText(String apiKey, String model,
                                      String systemText, String userText,
                                      String imageBase64, String mimeType) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Gemini API key is empty");
        }
        JSONObject body = baseBody(systemText);
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userText == null ? "" : userText));
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            JSONObject inline = new JSONObject();
            inline.put("mimeType", (mimeType == null || mimeType.isEmpty()) ? "image/jpeg" : mimeType);
            inline.put("data", imageBase64);
            parts.put(new JSONObject().put("inlineData", inline));
        }
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        JSONObject resp = postJsonWithRetry(generateEndpoint(model, apiKey), body);
        return extractText(resp);
    }

    /**
     * v3.0 — vision call that forces JSON output via the Gemini
     * responseMimeType + responseSchema mechanism. Used by the math
     * extraction flow: schema constrains the model to emit only LaTeX,
     * and the on-device {@link LatexToSpeech} parser handles the
     * spoken-form rendering deterministically.
     *
     * Why a separate method rather than overloading generateText:
     *   - signature carries the schema, max-output, and forces JSON
     *     mime in one place
     *   - the returned JSONObject is already parsed (no extractText
     *     round-trip through a text response)
     */
    public static JSONObject generateJsonWithImage(String apiKey, String model,
                                                    String systemText, String userText,
                                                    String imageBase64, String mimeType,
                                                    JSONObject responseSchema,
                                                    int maxOutputTokens) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Gemini API key is empty");
        }
        JSONObject body = baseBody(systemText);
        JSONObject gen = body.getJSONObject("generationConfig");
        gen.put("responseMimeType", "application/json");
        if (responseSchema != null) gen.put("responseSchema", responseSchema);
        gen.put("maxOutputTokens", maxOutputTokens);
        // Math extraction wants deterministic output; lower the
        // temperature so the model picks the most likely LaTeX
        // transcription rather than exploring alternatives.
        gen.put("temperature", 0.1);

        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userText == null ? "" : userText));
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            JSONObject inline = new JSONObject();
            inline.put("mimeType", (mimeType == null || mimeType.isEmpty()) ? "image/jpeg" : mimeType);
            inline.put("data", imageBase64);
            parts.put(new JSONObject().put("inlineData", inline));
        }
        body.put("contents", new JSONArray().put(
                new JSONObject().put("role", "user").put("parts", parts)));

        JSONObject resp = postJsonWithRetry(generateEndpoint(model, apiKey), body);
        String raw = extractText(resp);
        // The model returned JSON as text; parse it. parseJsonLenient is
        // tolerant of trailing whitespace / fence markers some prompts
        // can elicit even in JSON mode.
        return parseJsonLenient(raw);
    }

    /**
     * Send a binary file (PDF, image, audio, etc.) along with a prompt.
     * Returns plain text.
     */
    public static String generateWithFile(String apiKey, String model,
                                          String systemText, String userPrompt,
                                          byte[] fileBytes, String mimeType) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Gemini API key is empty");
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new Exception("Empty file");
        }
        JSONObject body = baseBody(systemText);
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userPrompt == null ? "" : userPrompt));
        parts.put(fileDataOrInlinePart(apiKey, fileBytes, mimeType));
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        JSONObject resp = postJsonWithRetry(generateEndpoint(model, apiKey), body);
        return extractText(resp);
    }

    /**
     * Like generateWithFile but instructs Gemini to return a JSON object.
     * Used for the PDF/PPTX → Word pipeline. Automatically picks between
     * inlineData (small files) and the Files API (large files).
     */
    public static JSONObject generateJsonWithFile(String apiKey, String model,
                                                  String systemText, String userPrompt,
                                                  byte[] fileBytes, String mimeType) throws Exception {
        JSONObject filePart = fileDataOrInlinePart(apiKey, fileBytes, mimeType);
        return generateJsonWithFilePart(apiKey, model, systemText, userPrompt, filePart);
    }

    /**
     * Variant that accepts a pre-built file part (typically a previously-uploaded
     * fileData reference). Lets callers upload a PDF once and reuse it across
     * multiple batched generateContent calls.
     */
    public static JSONObject generateJsonWithFilePart(String apiKey, String model,
                                                      String systemText, String userPrompt,
                                                      JSONObject filePart) throws Exception {
        JSONObject body = baseBody(systemText);
        JSONObject gen = body.getJSONObject("generationConfig");
        gen.put("responseMimeType", "application/json");
        // v3.3 — document transcription must be faithful, not
        // creative. Lock temperature at 0.1 + topP=0.9 so the model
        // picks the most likely token at every step. Without this
        // the Gemini 3 family invented column headers, paraphrased
        // paragraphs, and added "summary" tail rows to extracted
        // tables that didn't exist in the PDF.
        gen.put("temperature", 0.1);
        gen.put("topP", 0.9);

        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userPrompt == null ? "" : userPrompt));
        parts.put(filePart);

        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));

        JSONObject resp = postJsonWithRetry(generateEndpoint(model, apiKey), body);
        String text = extractText(resp);
        if (text == null || text.trim().isEmpty()) {
            throw new Exception("Empty Gemini response");
        }
        return parseJsonLenient(text);
    }

    /**
     * Multi-part request with a list of inline parts. Used for PPTX processing,
     * where we send all slide images at once together with text instructions.
     */
    public static JSONObject generateJsonWithParts(String apiKey, String model,
                                                   String systemText, JSONArray userParts) throws Exception {
        JSONObject body = baseBody(systemText);
        JSONObject gen = body.getJSONObject("generationConfig");
        gen.put("responseMimeType", "application/json");
        // v3.3 — same fidelity lock as generateJsonWithFilePart.
        // PPTX slide processing reads visible text + image
        // descriptions; we don't want the model inventing slide
        // titles or paraphrasing bullets.
        gen.put("temperature", 0.1);
        gen.put("topP", 0.9);
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", userParts)));

        JSONObject resp = postJsonWithRetry(generateEndpoint(model, apiKey), body);
        String text = extractText(resp);
        if (text == null || text.trim().isEmpty()) throw new Exception("Empty Gemini response");
        return parseJsonLenient(text);
    }

    // ---------- Files API ----------

    /**
     * Build a `parts` element pointing at the supplied bytes. Small files are
     * embedded as inlineData; larger files are first uploaded via the Files API
     * and referenced by fileUri.
     */
    public static JSONObject fileDataOrInlinePart(String apiKey, byte[] bytes, String mimeType) throws Exception {
        String mt = (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType;
        if (bytes.length <= INLINE_MAX_BYTES) {
            JSONObject inline = new JSONObject();
            inline.put("mimeType", mt);
            inline.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
            return new JSONObject().put("inlineData", inline);
        }
        // Large file: upload then reference.
        UploadedFile up = uploadFile(apiKey, bytes, mt, "basir-doc");
        JSONObject fd = new JSONObject();
        fd.put("fileUri", up.uri);
        fd.put("mimeType", up.mimeType);
        return new JSONObject().put("fileData", fd);
    }

    /** Reference to a file previously uploaded via the Gemini Files API. */
    public static final class UploadedFile {
        public final String name;     // e.g. "files/abc123"
        public final String uri;      // full URI used in fileData.fileUri
        public final String mimeType;
        UploadedFile(String name, String uri, String mimeType) {
            this.name = name; this.uri = uri; this.mimeType = mimeType;
        }
    }

    // ============================================================
    //   v2.0 — Files API helpers used by document Q&A + batching
    // ============================================================

    /** Returns the file's current state (PROCESSING / ACTIVE / FAILED). */
    public static String getFileState(String apiKey, String fileName) throws Exception {
        if (fileName == null || fileName.isEmpty()) throw new Exception("Empty file name");
        String url = BASE.replace("/models/", "/") + fileName
                + "?key=" + URLEncoder.encode(apiKey, "UTF-8");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("User-Agent", "Basir-Android/2.0");
            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(in);
            if (code < 200 || code >= 300) {
                throw new Exception("File-info HTTP " + code + ": " + truncate(extractError(text), 300));
            }
            return new JSONObject(text).optString("state", "");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Poll until the file becomes ACTIVE or the budget is exhausted. */
    public static void waitForFileActive(String apiKey, String fileName, long maxWaitMs) throws Exception {
        long start = System.currentTimeMillis();
        long delay = 1_000L;
        while (System.currentTimeMillis() - start < maxWaitMs) {
            checkInterrupted();
            String state = getFileState(apiKey, fileName);
            if ("ACTIVE".equals(state)) return;
            if ("FAILED".equals(state)) throw new Exception("Gemini failed to process the uploaded file");
            try { Thread.sleep(delay); }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new Exception("Cancelled");
            }
            delay = Math.min(delay * 2, 4_000L);
        }
        // Most PDFs are ACTIVE within a few seconds; if we timed out, the
        // next generateContent call will surface a clearer error than this.
    }

    /** Best-effort cleanup. Files auto-expire after 48 h either way. */
    public static void deleteFileQuietly(String apiKey, String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        if (apiKey == null || apiKey.trim().isEmpty()) return;
        HttpURLConnection conn = null;
        try {
            String url = BASE.replace("/models/", "/") + fileName
                    + "?key=" + URLEncoder.encode(apiKey, "UTF-8");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "Basir-Android/2.0");
            conn.getResponseCode();
        } catch (Throwable ignore) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * v2.0 — Document Q&A. Asks a follow-up question about a previously-
     * uploaded file. The reply is plain text (no JSON envelope), suitable
     * for direct TTS playback or display in the conversation UI.
     */
    public static String askAboutFile(String apiKey, String model,
                                       String systemText, String userQuestion,
                                       String fileUri, String mimeType) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new Exception("Gemini API key is empty");
        if (fileUri == null || fileUri.isEmpty()) throw new Exception("No file URI");
        JSONObject body = baseBody(systemText);
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userQuestion == null ? "" : userQuestion));
        JSONObject fd = new JSONObject();
        fd.put("fileUri", fileUri);
        fd.put("mimeType", (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType);
        parts.put(new JSONObject().put("fileData", fd));
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));
        JSONObject resp = postJsonWithRetry(generateEndpoint(model, apiKey), body);
        return extractText(resp);
    }

    /**
     * Upload bytes to the Files API. Returns the file reference. The file is
     * available for ~48h and is reused across subsequent generateContent calls.
     */
    public static UploadedFile uploadFile(String apiKey, byte[] bytes, String mimeType,
                                          String displayName) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Gemini API key is empty");
        }
        String url = UPLOAD_BASE + "?key=" + URLEncoder.encode(apiKey, "UTF-8");
        Exception lastErr = null;
        for (int attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
            checkInterrupted();
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(30_000);
                conn.setReadTimeout(600_000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                conn.setRequestProperty("X-Goog-Upload-Protocol", "raw");
                conn.setRequestProperty("X-Goog-Upload-Header-Content-Type", mimeType);
                conn.setRequestProperty("Content-Type", mimeType);
                if (displayName != null && !displayName.isEmpty()) {
                    conn.setRequestProperty("X-Goog-File-Display-Name", displayName);
                }
                conn.setRequestProperty("User-Agent", "Basir-Android/1.0.7");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }
                int code = conn.getResponseCode();
                String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
                if (code >= 200 && code < 300) {
                    JSONObject obj = new JSONObject(body);
                    JSONObject file = obj.optJSONObject("file");
                    if (file == null) throw new Exception("Upload response missing 'file'");
                    String name = file.optString("name", "");
                    String uri  = file.optString("uri", "");
                    String mt   = file.optString("mimeType", mimeType);
                    if (uri.isEmpty()) throw new Exception("Upload response missing 'uri'");
                    return new UploadedFile(name, uri, mt);
                }
                if (isRetryable(code)) {
                    lastErr = new Exception("HTTP " + code + " uploading file: " + truncate(extractError(body), 200));
                    sleepBackoff(attempt);
                    continue;
                }
                throw new Exception("HTTP " + code + " uploading file: " + truncate(extractError(body), 400));
            } catch (java.io.IOException e) {
                lastErr = new Exception("Upload failed: " + e.getMessage());
                sleepBackoff(attempt);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        if (lastErr == null) lastErr = new Exception("Upload failed after retries");
        throw lastErr;
    }

    // ---------- internals ----------

    private static JSONObject baseBody(String systemText) throws Exception {
        JSONObject body = new JSONObject();
        if (systemText != null && !systemText.isEmpty()) {
            JSONObject sys = new JSONObject();
            sys.put("parts", new JSONArray().put(new JSONObject().put("text", systemText)));
            body.put("systemInstruction", sys);
        }
        JSONObject gen = new JSONObject();
        // v3.3 — temperature lowered from 0.7 → 0.2. The 0.7 default
        // was set when the Q&A / chat surface dominated; with the
        // Gemini 3 family it became aggressive on fidelity tasks
        // (document transcription, image OCR, table extraction),
        // inventing words, dates, and table cells that don't exist
        // in the source. 0.2 keeps the model conservative enough
        // for transcription without breaking the chat flow where
        // some variation is fine. Callers that need creative writing
        // (rephrasing, tone adaptation) can still override after
        // baseBody() returns.
        gen.put("temperature", 0.2);
        // v2.1.1: explicitly request the headroom we need for batched PDF
        // conversion. The user reported "Unterminated array at character X"
        // crashes around page 40 — that's Gemini cutting the response off
        // mid-JSON because we hit its default per-response output budget.
        // 16384 fits a 4-page batch with detailed image+table descriptions
        // and stays well under what every modern Gemini model supports.
        gen.put("maxOutputTokens", 16384);
        body.put("generationConfig", gen);
        return body;
    }

    private static JSONObject postJsonWithRetry(String url, JSONObject payload) throws Exception {
        Exception lastErr = null;
        for (int attempt = 0; attempt < RETRY_ATTEMPTS; attempt++) {
            checkInterrupted();
            try {
                return postJsonOnce(url, payload);
            } catch (RetryableException re) {
                lastErr = re.cause;
                sleepBackoff(attempt);
            }
        }
        if (lastErr == null) lastErr = new Exception("Request failed after retries");
        throw lastErr;
    }

    private static JSONObject postJsonOnce(String url, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(600_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Basir-Android/1.0.7");

            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(in);
            if (code < 200 || code >= 300) {
                String message = extractError(text);
                if (isRetryable(code)) {
                    throw new RetryableException(new Exception("HTTP " + code + ": " + truncate(message, 300)));
                }
                throw new Exception("HTTP " + code + ": " + truncate(message, 500));
            }
            return new JSONObject(text);
        } catch (java.io.IOException ioe) {
            // Network glitch — retry.
            throw new RetryableException(new Exception("Network error: " + ioe.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean isRetryable(int httpCode) {
        return httpCode == 408 || httpCode == 429
            || httpCode == 500 || httpCode == 502
            || httpCode == 503 || httpCode == 504;
    }

    private static void sleepBackoff(int attempt) throws Exception {
        if (attempt + 1 >= RETRY_ATTEMPTS) return;
        long ms = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)];
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new Exception("Cancelled");
        }
    }

    private static void checkInterrupted() throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new Exception("Cancelled");
    }

    /** Internal marker so postJsonWithRetry can tell a retryable error apart. */
    private static final class RetryableException extends Exception {
        final Exception cause;
        RetryableException(Exception cause) { this.cause = cause; }
    }

    private static String extractError(String body) {
        try {
            JSONObject err = new JSONObject(body);
            if (err.has("error")) {
                Object e = err.get("error");
                if (e instanceof JSONObject) {
                    JSONObject eo = (JSONObject) e;
                    if (eo.has("message")) return eo.getString("message");
                }
            }
        } catch (Exception ignore) {}
        return body;
    }

    private static JSONObject parseJsonLenient(String text) throws Exception {
        String cleaned = text.trim();
        // Strip markdown fences first — Gemini sometimes wraps JSON in ```json fences.
        if (cleaned.startsWith("```")) {
            int firstNl = cleaned.indexOf('\n');
            if (firstNl > 0) cleaned = cleaned.substring(firstNl + 1);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();
        }
        // Try strict parse.
        try { return new JSONObject(cleaned); } catch (Exception ignore) {}
        // Strip non-JSON prefix/suffix.
        int firstBrace = cleaned.indexOf('{');
        int lastBrace  = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String trimmed = cleaned.substring(firstBrace, lastBrace + 1);
            try { return new JSONObject(trimmed); } catch (Exception ignore) {}
        }
        // v2.1.1 — last-resort repair for TRUNCATED JSON. The user reported
        // "Unterminated array at character" failures around page 40 of
        // long PDFs: Gemini's response is cut off mid-section because the
        // output token cap was reached. Rather than discard everything,
        // walk the string and rebuild a valid JSON by dropping any
        // half-written trailing element and closing all open brackets.
        // We lose ~1 section worth of content for that batch, but we keep
        // everything Gemini DID emit, and the document conversion proceeds
        // to the next batch instead of failing outright.
        if (firstBrace >= 0) {
            try {
                return new JSONObject(repairTruncatedJson(
                        cleaned.substring(firstBrace)));
            } catch (Exception ignore) {}
        }
        // Out of ideas — surface the original parse error to the caller.
        return new JSONObject(cleaned);
    }

    /**
     * Walks the (likely-truncated) JSON string keeping a bracket stack.
     * Returns a valid JSON document by rolling back to the last point where
     * everything was balanced at the current depth (right before a comma,
     * or right after a closing bracket), then appending the matching
     * closing brackets for whatever was still open. Designed for the
     * specific case of {@code {"sections":[{...},{...},{...incomplete}}.
     */
    private static String repairTruncatedJson(String input) {
        char[] cs = input.toCharArray();
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        boolean inString = false;
        boolean escape = false;
        int safeEnd = 0;
        java.util.Deque<Character> safeStack = new java.util.ArrayDeque<>();

        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (escape) { escape = false; continue; }
            if (inString && c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}' || c == ']') {
                if (!stack.isEmpty()) stack.pop();
                // Just closed a balanced container — safe truncation point.
                safeEnd = i + 1;
                safeStack = new java.util.ArrayDeque<>(stack);
            } else if (c == ',') {
                // Just finished a complete element at this depth — safe
                // truncation point is BEFORE the comma. The next element
                // (which may be incomplete) is dropped.
                safeEnd = i;
                safeStack = new java.util.ArrayDeque<>(stack);
            }
        }

        // Balanced and complete — nothing to repair.
        if (stack.isEmpty()) return input;

        // Truncate to last known-safe state, then close the stack.
        StringBuilder out = new StringBuilder();
        out.append(cs, 0, safeEnd);
        while (!safeStack.isEmpty()) {
            char open = safeStack.pop();
            out.append(open == '{' ? '}' : ']');
        }
        return out.toString();
    }

    private static String extractText(JSONObject resp) throws Exception {
        JSONArray candidates = resp.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            if (resp.has("promptFeedback")) {
                return "(Blocked) " + resp.getJSONObject("promptFeedback").toString();
            }
            return "";
        }
        JSONObject cand = candidates.getJSONObject(0);
        JSONObject content = cand.optJSONObject("content");
        if (content == null) return "";
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.getJSONObject(i);
            if (p.has("text")) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(p.getString("text"));
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
