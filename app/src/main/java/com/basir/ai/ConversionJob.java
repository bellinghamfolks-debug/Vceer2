package com.basir.ai;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered conversion orchestrator.
 *
 * Each chunk has an explicit state and may be restored from a previously
 * validated snapshot. Non-cancellation failures are recorded so the caller can
 * retain successful pages for a later retry. The production caller in
 * {@link AiClient} deliberately refuses to write a partial DOCX when any chunk
 * fails; this class only manages execution state and does not choose the output
 * policy.
 */
public final class ConversionJob {

    /** Processes one chunk and returns validated JSON. */
    public interface ChunkProcessor {
        JSONObject process(ConversionChunk chunk) throws Exception;
    }

    /** Called once per successfully validated chunk. */
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
     * Process every chunk in order. Non-cancellation failures are recorded and
     * processing continues so successful pages can be checkpointed. The caller
     * must inspect {@link #failedChunkCount()} before creating any output.
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
