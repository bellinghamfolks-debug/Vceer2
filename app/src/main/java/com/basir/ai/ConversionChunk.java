package com.basir.ai;

import org.json.JSONObject;

/**
 * v2.4 — one batch of pages inside a PDF conversion job.
 *
 * The chunked-PDF pipeline (see {@link ConversionJob}) splits the source
 * file into ranges of N pages, sends each range to Gemini as its own
 * generateContent call, and accumulates the parsed JSON section-by-section
 * into a DocxBuilder. Each range gets a chunk record carrying:
 *
 *   - its requested page range            (startPage..endPage)
 *   - its status                          (PENDING → RUNNING → SUCCEEDED|FAILED)
 *   - on success: the parsed JSON object so the renderer can replay it
 *   - on failure: a user-friendly message explaining what broke
 *
 * The chunk is a passive value-with-state, not a runner — the job
 * orchestrates the transitions.
 */
public final class ConversionChunk {

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    private final int startPage;
    private final int endPage;
    private Status status = Status.PENDING;
    private JSONObject parsed;
    private String errorMessage = "";
    private int effectiveEnd;

    ConversionChunk(int startPage, int endPage) {
        if (startPage < 1) startPage = 1;
        if (endPage < startPage) endPage = startPage;
        this.startPage = startPage;
        this.endPage = endPage;
        this.effectiveEnd = endPage;
    }

    public int startPage()    { return startPage; }
    public int endPage()      { return endPage; }
    /** What Gemini said it actually processed. May differ from {@link #endPage()}
     *  if the model decided to stop early (e.g. blank trailing pages). */
    public int effectiveEnd() { return effectiveEnd; }

    public Status status() { return status; }
    public boolean isPending()   { return status == Status.PENDING; }
    public boolean isRunning()   { return status == Status.RUNNING; }
    public boolean isSucceeded() { return status == Status.SUCCEEDED; }
    public boolean isFailed()    { return status == Status.FAILED; }

    public JSONObject parsed()        { return parsed; }
    public String     errorMessage()  { return errorMessage; }

    void markRunning() {
        this.status = Status.RUNNING;
    }

    void markSucceeded(JSONObject parsed, int effectiveEnd) {
        this.status = Status.SUCCEEDED;
        this.parsed = parsed;
        this.effectiveEnd = effectiveEnd;
    }

    void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
