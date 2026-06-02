package com.basir.ai;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v2.4 — orchestrator for a chunked PDF → DOCX conversion.
 *
 * Previous architecture (pre-v2.4)
 * ─────────────────────────────────
 *   The PDF batched loop lived inline in AiClient.directConvertToDocx.
 *   If ANY batch threw (network glitch, JSON parse error, safety block,
 *   timeout) the whole loop bailed and the partial output was discarded.
 *   A user converting a 200-page document who saw batch #15 of 50 fail
 *   got nothing — not the 14 batches that already succeeded, not even an
 *   indication of where the failure was.
 *
 * v2.4 model
 * ──────────
 *   The job holds a list of {@link ConversionChunk}s, one per batch.
 *   {@link #runAll} processes them in order. A chunk failure marks that
 *   chunk FAILED but the loop continues — every successful chunk still
 *   contributes its sections to the DocxBuilder. After the loop the
 *   caller checks {@link #failedChunks()}; if non-empty the resulting
 *   DOCX gets a footer listing the failed page ranges, so the user
 *   knows exactly which pages to retry instead of the whole document.
 *
 *   Cancellation is propagated by re-throwing from {@link ChunkProcessor}
 *   — the job recognises any exception whose message contains "cancel"
 *   and aborts the whole loop. Real cancellation should also surface via
 *   the {@link CancelCheck} hook between chunks.
 *
 * What this class does NOT do (yet)
 * ─────────────────────────────────
 *   - On-disk checkpointing across process death. The job state lives
 *     in-process for now. A later phase will serialise this to a JSON
 *     file in cache so a service-killed conversion can resume from the
 *     last completed chunk, using the same Gemini Files API upload
 *     (file persists 48 h on Gemini's side).
 *   - Retry of failed chunks. The job marks failure and moves on; the
 *     "retry just the failed pages" UI is a future deliverable too.
 */
public final class ConversionJob {

    /** Processes one chunk, returning the parsed JSON. May throw on
     *  network or parsing failures — the job treats throws as chunk
     *  failures, not job failures. */
    public interface ChunkProcessor {
        JSONObject process(ConversionChunk chunk) throws Exception;
    }

    /** Called once per successfully-parsed chunk so the caller can fold
     *  its sections into a DocxBuilder. */
    public interface ChunkRenderer {
        void render(ConversionChunk chunk);
    }

    /** Progress sink. Receives (currentPage, totalPages, stage) — same
     *  shape as {@link AiClient.ProgressCallback}. */
    public interface ProgressSink {
        void onProgress(int currentPage, int totalPages, String stage);
    }

    /** Polled between chunks. Throw to abort the job (e.g. "Cancelled"). */
    public interface CancelCheck {
        void throwIfCancelled() throws Exception;
    }

    private final List<ConversionChunk> chunks;
    private final int totalPages;
    private final int pagesPerBatch;

    public ConversionJob(int totalPages, int pagesPerBatch) {
        if (totalPages < 1) totalPages = 1;
        if (pagesPerBatch < 1) pagesPerBatch = 1;
        this.totalPages = totalPages;
        this.pagesPerBatch = pagesPerBatch;

        List<ConversionChunk> cs = new ArrayList<>();
        int p = 1;
        while (p <= totalPages) {
            int end = Math.min(p + pagesPerBatch - 1, totalPages);
            cs.add(new ConversionChunk(p, end));
            // Advance using the originally-requested end. Effective ends
            // returned by Gemini are honoured per-chunk inside runAll but
            // we still partition by the requested grid here — otherwise
            // chunks would overlap or miss pages on a misbehaving response.
            p = end + 1;
        }
        this.chunks = cs;
    }

    public int totalPages()    { return totalPages; }
    public int pagesPerBatch() { return pagesPerBatch; }
    public int totalChunks()   { return chunks.size(); }

    public List<ConversionChunk> chunks() {
        return Collections.unmodifiableList(chunks);
    }

    /**
     * Process every chunk in order. A chunk failure does NOT abort the
     * job — it just marks that chunk FAILED and the loop continues to
     * the next one. The exception is re-raised only if its message
     * indicates cancellation (so an interrupt from
     * {@link CancelCheck#throwIfCancelled} reliably ends the job).
     *
     * Returns the highest effectiveEnd reached across successful chunks,
     * mostly useful for the progress bar's final tick.
     */
    public int runAll(ChunkProcessor processor,
                      ChunkRenderer renderer,
                      ProgressSink progress,
                      CancelCheck cancel) throws Exception {
        int finalEffectiveEnd = 0;
        for (ConversionChunk chunk : chunks) {
            cancel.throwIfCancelled();
            // v2.9.2 — on a retry pass the job is pre-loaded with chunks
            // whose status is already SUCCEEDED (from the previous run).
            // Skip the Gemini round-trip for those — but still call the
            // renderer so the DocxBuilder accumulates their sections into
            // the freshly rebuilt output.
            if (chunk.isSucceeded()) {
                renderer.render(chunk);
                if (chunk.effectiveEnd() > finalEffectiveEnd) {
                    finalEffectiveEnd = chunk.effectiveEnd();
                }
                progress.onProgress(chunk.effectiveEnd(), totalPages, "processing");
                continue;
            }
            chunk.markRunning();
            progress.onProgress(chunk.startPage() - 1, totalPages, "processing");

            try {
                JSONObject parsed = processor.process(chunk);
                int eff = parsed.optInt("end_page", chunk.endPage());
                if (eff < chunk.startPage()) eff = chunk.endPage();
                if (eff > totalPages)        eff = totalPages;
                chunk.markSucceeded(parsed, eff);
                renderer.render(chunk);
                if (eff > finalEffectiveEnd) finalEffectiveEnd = eff;
            } catch (Exception chunkErr) {
                String msg = chunkErr.getMessage() == null ? "" : chunkErr.getMessage();
                // Cancellation propagates upward; any other failure is
                // recorded on the chunk and the loop moves on.
                if (msg.toLowerCase().contains("cancel")
                        || chunkErr instanceof InterruptedException
                        || Thread.currentThread().isInterrupted()) {
                    throw chunkErr;
                }
                chunk.markFailed(msg);
            }
            progress.onProgress(chunk.effectiveEnd(), totalPages, "processing");
        }
        return finalEffectiveEnd;
    }

    public int succeededChunkCount() {
        int n = 0;
        for (ConversionChunk c : chunks) if (c.isSucceeded()) n++;
        return n;
    }

    public int failedChunkCount() {
        int n = 0;
        for (ConversionChunk c : chunks) if (c.isFailed()) n++;
        return n;
    }

    public List<ConversionChunk> failedChunks() {
        List<ConversionChunk> out = new ArrayList<>();
        for (ConversionChunk c : chunks) if (c.isFailed()) out.add(c);
        return out;
    }
}
