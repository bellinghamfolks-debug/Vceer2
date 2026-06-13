package com.basir.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v3.3.1 — deep conversion diagnostic.
 *
 * Persistence contract (per the user)
 * ────────────────────────────────────
 *   "خزن كل شيء بدقة حتى لو حاولت اليوم أربع محاولات بكرة خمس،
 *    ما يطرح شي ولا يحذف."
 *
 * Translation: store every single event, even across many
 * sessions and many days, never drop anything, never overwrite.
 *
 * Implementation
 * ──────────────
 * Each event is appended to a single growing file the moment it
 * happens:
 *
 *   /Android/data/com.basir.ai/files/basir-conversion-diagnostic.txt
 *
 * A second copy is appended to the app's cache directory so the
 * in-app share sheet works even when external storage is
 * unavailable (the cache copy may be reclaimed by the OS under
 * pressure — the external-files copy is the source of truth).
 *
 * Sessions are delimited by a ═══ NEW SESSION ═══ banner. Past
 * sessions stay verbatim — never truncated, never deleted, never
 * overwritten. Only the user can clear the file manually from
 * Settings → Apps → Basir → Storage, or via the explicit
 * {@link #clearForever(Context)} button (off by default).
 *
 * The in-memory buffer
 * ────────────────────
 * For fast in-app share-as-text and previewing the current
 * session, the last {@link #IN_MEMORY_CAP} events are kept in a
 * list. This is a memory bound, NOT a persistence bound — the
 * file on disk keeps every single event regardless of cap.
 *
 * Privacy
 * ───────
 * The report includes Gemini response snippets (first ~800 chars
 * per call). If the source document contains personal data those
 * snippets will too. Gemini URLs are scrubbed: every
 * "key=..." query parameter is replaced with "key=***REDACTED***"
 * before the URL hits the file.
 */
public final class ConversionDiagnostic {

    private static final String TAG = "BasirConvDiag";
    private static final int    IN_MEMORY_CAP = 8000;
    private static final int    HTTP_SNIPPET  = 4000;
    private static final String FILE_NAME     = "basir-conversion-diagnostic.txt";
    /**
     * v3.3.2 — visible diagnostic identity. Bump this every time the
     * diagnostic format / fields change. Lets the user see at a glance
     * whether they have the latest APK installed: an old APK will
     * print an older DIAG line, a new APK will print the new one.
     */
    public  static final String DIAG_VERSION  = "v3.3.5-quality-auditor";

    private static final ConversionDiagnostic INSTANCE = new ConversionDiagnostic();
    public static ConversionDiagnostic get() { return INSTANCE; }

    private final List<String> events = new ArrayList<>();
    private final SimpleDateFormat tsFmt =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private long   sessionStartMs = 0;
    private String sessionId = "";
    private Context appCtx = null;

    // v3.3.2 — session-level counters surfaced in the summary block.
    private int httpCallCount  = 0;
    private int httpFailCount  = 0;
    private int pagesRendered  = 0;
    private int pagesValidated = 0;
    private int pagesRejected  = 0;
    private long bytesUploaded = 0;
    private long bytesReceived = 0;

    private ConversionDiagnostic() {}

    // ─────────────────── lifecycle ───────────────────

    /**
     * Open a new diagnostic session. Past content on disk is
     * preserved — only a header divider is appended. Past
     * in-memory events are flushed but stay in memory subject to
     * the in-memory cap.
     */
    public synchronized void start(Context context,
                                    String fileName, String mime,
                                    long sizeBytes, int totalPages,
                                    String mode, String language) {
        if (context != null) this.appCtx = context.getApplicationContext();
        sessionStartMs = SystemClock.elapsedRealtime();
        sessionId = String.format(Locale.US, "S%d", System.currentTimeMillis());
        // Reset session counters for a clean per-attempt summary at
        // the end. The on-disk file still has previous sessions'
        // counters in their own SUMMARY blocks.
        httpCallCount = 0;
        httpFailCount = 0;
        pagesRendered = 0;
        pagesValidated = 0;
        pagesRejected = 0;
        bytesUploaded = 0;
        bytesReceived = 0;
        // Note: events list is NOT cleared — multiple sessions can
        // coexist in the in-memory window if they fit. The file on
        // disk accumulates forever regardless.
        addRaw("");
        addRaw("═══════════════════════ NEW SESSION ═══════════════════════");
        addRaw("Diag format   : " + DIAG_VERSION);
        addRaw("Session ID    : " + sessionId);
        addRaw("Started at    : " + dateFmt.format(new Date()));
        addRaw("Build         : " + buildLabel());
        addRaw("Device        : " + Build.MANUFACTURER + " " + Build.MODEL
                + " — Android " + Build.VERSION.RELEASE
                + " (SDK " + Build.VERSION.SDK_INT + ")");
        addRaw("Source file   : " + nullSafe(fileName));
        addRaw("Source MIME   : " + nullSafe(mime));
        addRaw("Source size   : " + sizeBytes + " bytes");
        addRaw("Source pages  : " + totalPages);
        addRaw("Mode / lang   : " + nullSafe(mode) + " / " + nullSafe(language));
        addRaw("════════════════════════════════════════════════════════════");
    }

    public synchronized void step(String stage, String detail) {
        add("STEP", stage + (detail == null ? "" : "  | " + detail));
    }

    public synchronized void pageStart(int pageNumber, int widthPx, int heightPx,
                                        int rotationDeg) {
        add("PAGE", "▶ page " + pageNumber + " — " + widthPx + "×" + heightPx
                + " rot=" + rotationDeg);
    }

    public synchronized void pageEnd(int pageNumber, boolean ok, String detail) {
        add("PAGE", (ok ? "✓" : "✗") + " page " + pageNumber
                + (detail == null ? "" : "  | " + detail));
    }

    public synchronized void validatorReject(int pageNumber,
                                              String rule, String why) {
        pagesRejected++;
        add("REJECT", "page " + pageNumber + "  rule=" + nullSafe(rule)
                + (why == null ? "" : "  why=" + why));
    }

    public synchronized void httpExchange(String method, String url,
                                           int statusCode, long elapsedMs,
                                           String responseSnippet) {
        String trimmed = (url == null) ? "" : url;
        trimmed = trimmed.replaceAll("(?i)([?&]key=)[^&]+", "$1***REDACTED***");
        httpCallCount++;
        if (statusCode < 200 || statusCode >= 300) httpFailCount++;
        if (responseSnippet != null) bytesReceived += responseSnippet.length();
        add("HTTP", method + " " + statusCode + "  " + elapsedMs + "ms"
                + "\n           url=" + trimmed
                + (responseSnippet == null || responseSnippet.isEmpty()
                    ? ""
                    : "\n           body=" + safeSnippet(responseSnippet, HTTP_SNIPPET)));
    }

    /** Record an outbound upload size so the summary can show total
     *  bytes Gemini received from the device (image bytes + prompt). */
    public synchronized void uploadObserved(int pageNumber, long bytes) {
        bytesUploaded += bytes;
        add("UPLOAD", "page " + pageNumber + "  bytes=" + bytes);
    }

    /** Snapshot of Java heap usage at the current moment. */
    public synchronized void memorySnapshot(String label) {
        Runtime r = Runtime.getRuntime();
        long total = r.totalMemory();
        long free  = r.freeMemory();
        long used  = total - free;
        long max   = r.maxMemory();
        add("MEM", label
                + "  used=" + (used / (1024 * 1024)) + "MB"
                + "  total=" + (total / (1024 * 1024)) + "MB"
                + "  max=" + (max / (1024 * 1024)) + "MB"
                + "  free=" + (free / (1024 * 1024)) + "MB");
    }

    /** Page-rendering specific hook with the rich info the user
     *  flagged as missing in v3.3.1: dims, rotation, ink ratio,
     *  blank-detection verdict, jpeg byte count. */
    public synchronized void pageRendered(int pageNumber, int width, int height,
                                            int rotationDegrees,
                                            boolean visuallyBlank, double inkRatio,
                                            int jpegBytes) {
        pagesRendered++;
        add("RENDER", "page " + pageNumber
                + "  dims=" + width + "x" + height
                + "  rot=" + rotationDegrees
                + "  ink=" + String.format(Locale.US, "%.4f", inkRatio)
                + "  blank=" + visuallyBlank
                + "  jpeg=" + jpegBytes + "B");
    }

    /** Validator passed for a page. Pair with pageEnd(true, ...). */
    public synchronized void validatorPass(int pageNumber, int sections,
                                            int tablesDetected) {
        pagesValidated++;
        add("PASS", "page " + pageNumber
                + "  sections=" + sections
                + "  tables=" + tablesDetected);
    }

    /**
     * v3.3.5 — quality auditor. Runs structural heuristics over the
     * model's extracted JSON and flags patterns that indicate a
     * SUCCESSFUL but WRONG conversion. The flag is informational —
     * it does NOT reject the page — but a maintainer reading the
     * log can immediately see "page 1 emitted a fabricated summary"
     * or "table on page 3 has only 1 row but PDF clearly has 12".
     *
     * What we flag
     *   • model emitted a "summary" / "ملخص" paragraph not present
     *     in any table or heading on the page → likely hallucination
     *   • page returned 0 tables but the source page was visually
     *     dense (high ink ratio) AND the document mode is "full"
     *     → likely a table that the model collapsed into paragraphs
     *   • bilingual source heuristic: pages with ink-ratio that
     *     suggests visible text, but only Arabic OR only English
     *     extracted → likely dropped half
     *   • any section text contains pipe-separated cells "| a | b |"
     *     → table was flattened to text
     *   • single-character sections → OCR noise
     *
     * Each flag becomes one [QUALITY] line in the log. The user
     * can grep for QUALITY to spot every suspicious page.
     */
    public synchronized void quality(int pageNumber, String flag, String detail) {
        add("QUALITY", "⚠ page " + pageNumber + "  " + flag
                + (detail == null ? "" : "  | " + detail));
    }

    /**
     * Snapshot a small text fingerprint per page so the report
     * carries verbatim samples (not just structural counts). The
     * user can scan the fingerprint and tell at a glance whether
     * the transcription looks right.
     */
    public synchronized void textFingerprint(int pageNumber,
                                              String label,
                                              String text,
                                              int maxChars) {
        if (text == null) return;
        String snip = text.length() > maxChars
                ? text.substring(0, maxChars) + "…"
                : text;
        snip = snip.replace('\n', ' ').replace('\r', ' ').trim();
        add("FINGERPRINT", "page " + pageNumber + "  " + label
                + "=\"" + snip + "\"");
    }

    public synchronized void jsonObserved(String stage, JSONObject json,
                                            int maxChars) {
        if (json == null) {
            add("JSON", stage + "  (null)");
        } else {
            String s = json.toString();
            add("JSON", stage + "  " + safeSnippet(s, maxChars > 0 ? maxChars : 400));
        }
    }

    public synchronized void failure(String stage, Throwable t) {
        if (t == null) {
            add("FAIL", stage + "  | <null throwable>");
            return;
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        add("FAIL", stage
                + "\n           " + t.getClass().getName()
                + ": " + nullSafe(t.getMessage())
                + "\n" + sw.toString());
    }

    public synchronized void success(String outputPath, long outputBytes) {
        add("DONE", "✅ success  path=" + nullSafe(outputPath)
                + "  size=" + outputBytes + " bytes"
                + "  elapsed=" + elapsedMs() + "ms");
        writeSummary();
    }

    public synchronized void aborted(String summary) {
        add("DONE", "❌ aborted  " + nullSafe(summary)
                + "  elapsed=" + elapsedMs() + "ms");
        writeSummary();
    }

    /**
     * Final summary block. Closes every session — success or
     * failure — with a counters snapshot so a maintainer can read
     * the SUMMARY line and immediately know how far the pipeline
     * got before stopping (5 HTTP calls / 2 pages rendered / 0
     * validated → all three pages rejected at the validator).
     */
    private void writeSummary() {
        Runtime r = Runtime.getRuntime();
        long used = (r.totalMemory() - r.freeMemory()) / (1024 * 1024);
        addRaw("──────── SESSION SUMMARY ────────");
        addRaw("  elapsed         : " + elapsedMs() + " ms");
        addRaw("  http calls      : " + httpCallCount
                + "  (failures: " + httpFailCount + ")");
        addRaw("  bytes uploaded  : " + bytesUploaded);
        addRaw("  bytes received  : " + bytesReceived);
        addRaw("  pages rendered  : " + pagesRendered);
        addRaw("  pages validated : " + pagesValidated);
        addRaw("  pages rejected  : " + pagesRejected);
        addRaw("  heap used at end: " + used + " MB");
        addRaw("─────────────────────────────────");
    }

    // ─────────────────── sharing ───────────────────

    /**
     * Share the full disk file (not just the in-memory window) as
     * plain text via the system share sheet. We embed the file
     * content inline via {@code EXTRA_TEXT} so this works on a
     * pre-AndroidX project (no FileProvider) and on every
     * messenger / mail client.
     *
     * If the file is huge (many sessions), most messengers truncate
     * silently at their own limit. The user can still attach the
     * full file manually from {@link #externalReportPath()}.
     */
    public void shareViaActivity(Activity activity, String chooserTitle) {
        if (activity == null) return;
        if (appCtx == null) appCtx = activity.getApplicationContext();
        String body = readFullReport();
        if (body.isEmpty()) body = "(no diagnostic events recorded yet)";
        Intent text = new Intent(Intent.ACTION_SEND);
        text.setType("text/plain");
        text.putExtra(Intent.EXTRA_SUBJECT,
                "Basir conversion diagnostic — " + sessionId);
        text.putExtra(Intent.EXTRA_TEXT, body);
        try {
            activity.startActivity(Intent.createChooser(text,
                    chooserTitle == null ? "Share diagnostic" : chooserTitle));
        } catch (Throwable ignore) {}
    }

    /**
     * The full file content from disk. Reads the external-files
     * copy first; falls back to cache if external isn't available.
     */
    public synchronized String readFullReport() {
        File f = bestReportFile();
        if (f == null || !f.exists()) {
            // Fall back to the in-memory window.
            StringBuilder sb = new StringBuilder(events.size() * 64);
            for (String e : events) sb.append(e).append('\n');
            return sb.toString();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder((int) Math.min(f.length() + 16, Integer.MAX_VALUE));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Throwable t) {
            return "(failed to read report file: " + t.getMessage() + ")";
        }
    }

    /**
     * Absolute path of the persistent report — the user can attach
     * it manually from any file manager. Returns the external-files
     * path when available, otherwise the cache path.
     */
    public synchronized String externalReportPath() {
        File f = bestReportFile();
        return f == null ? "" : f.getAbsolutePath();
    }

    /**
     * Total bytes the report has grown to on disk. Useful as a
     * preview ("the log is 4.2 MB across 23 sessions").
     */
    public synchronized long reportFileSize() {
        File f = bestReportFile();
        return f == null ? 0L : f.length();
    }

    /**
     * Explicit, user-initiated wipe. Off by default and not invoked
     * anywhere automatically. The whole point of this class is that
     * nothing disappears without a deliberate request from the user.
     */
    public synchronized void clearForever(Context context) {
        if (context != null && appCtx == null) {
            appCtx = context.getApplicationContext();
        }
        if (appCtx == null) return;
        events.clear();
        deleteIfExists(new File(appCtx.getCacheDir(), FILE_NAME));
        try {
            File ext = appCtx.getExternalFilesDir(null);
            if (ext != null) deleteIfExists(new File(ext, FILE_NAME));
        } catch (Throwable ignore) {}
    }

    // ─────────────────── internals ───────────────────

    private void add(String tag, String body) {
        String ts = tsFmt.format(new Date());
        String elapsed = String.format(Locale.US, "%6dms", elapsedMs());
        String line = ts + "  " + elapsed + "  [" + tag + "]  " + body;
        appendOne(line);
    }

    private void addRaw(String line) { appendOne(line); }

    private void appendOne(String line) {
        events.add(line);
        if (events.size() > IN_MEMORY_CAP) {
            // Drop the oldest IN-MEMORY entry only. The on-disk
            // file still has it — this cap is a memory bound, not
            // a persistence one. Keeping ~25% slack so the cap
            // doesn't trip on every single new event.
            int drop = IN_MEMORY_CAP / 4;
            for (int i = 0; i < drop && !events.isEmpty(); i++) {
                events.remove(0);
            }
        }
        Log.d(TAG, line);
        appendToDisk(line);
    }

    /**
     * The single most important method in this class: appends ONE
     * line to BOTH the external-files copy and the cache copy of
     * the report. Never truncates, never overwrites, never
     * deletes. If both writes fail we accept the loss silently —
     * diagnostics must never crash the converter.
     */
    private void appendToDisk(String line) {
        if (appCtx == null) return;
        // External-files copy: visible in Files apps, survives
        // process death, survives app force-stop.
        try {
            File ext = appCtx.getExternalFilesDir(null);
            if (ext != null) {
                File f = new File(ext, FILE_NAME);
                try (PrintWriter pw = new PrintWriter(new FileWriter(f, true))) {
                    pw.println(line);
                }
            }
        } catch (Throwable ignore) {}
        // Cache copy: fallback for share-as-text when external is
        // unmounted or sandboxed away.
        try {
            File cache = new File(appCtx.getCacheDir(), FILE_NAME);
            try (PrintWriter pw = new PrintWriter(new FileWriter(cache, true))) {
                pw.println(line);
            }
        } catch (Throwable ignore) {}
    }

    private File bestReportFile() {
        if (appCtx == null) return null;
        try {
            File ext = appCtx.getExternalFilesDir(null);
            if (ext != null) {
                File f = new File(ext, FILE_NAME);
                if (f.exists()) return f;
            }
        } catch (Throwable ignore) {}
        File cache = new File(appCtx.getCacheDir(), FILE_NAME);
        return cache.exists() ? cache : null;
    }

    private static void deleteIfExists(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Throwable ignore) {}
    }

    private long elapsedMs() {
        return SystemClock.elapsedRealtime() - sessionStartMs;
    }

    private static String safeSnippet(String s, int max) {
        if (s == null) return "";
        String trimmed = s;
        if (trimmed.length() > max) trimmed = trimmed.substring(0, max) + "...";
        return trimmed.replace('\n', ' ').replace('\r', ' ');
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String buildLabel() {
        return "basir-" + BuildBridge.versionName()
                + " (code " + BuildBridge.versionCode() + ")";
    }

    /**
     * Reflective bridge to the AGP-generated BuildConfig so the
     * diagnostic compiles in source-only inspection tools that
     * don't have BuildConfig available.
     */
    private static final class BuildBridge {
        static String versionName() {
            try {
                Class<?> c = Class.forName("com.basir.ai.BuildConfig");
                Object v = c.getField("VERSION_NAME").get(null);
                return v == null ? "?" : v.toString();
            } catch (Throwable t) { return "?"; }
        }
        static int versionCode() {
            try {
                Class<?> c = Class.forName("com.basir.ai.BuildConfig");
                Object v = c.getField("VERSION_CODE").get(null);
                return v == null ? 0 : (Integer) v;
            } catch (Throwable t) { return 0; }
        }
    }
}
