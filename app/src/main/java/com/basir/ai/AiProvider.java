package com.basir.ai;

import android.content.Context;
import android.net.Uri;

import java.io.File;

/**
 * v2.3.1 — provider-neutral interface that the rest of the app talks to
 * when it needs the AI to do something. Today there are two concrete
 * implementations:
 *
 *   - {@link GeminiAiProvider}  — calls Google's Gemini API directly using
 *     the API key the user pasted into Settings. No developer server in
 *     the loop.
 *   - {@link ProxyAiProvider}   — calls an organisation-controlled HTTPS
 *     proxy that holds the Gemini key server-side. Useful for managed
 *     deployments.
 *
 * Pick one via {@link AiClient#provider(android.content.SharedPreferences)}.
 *
 * Both providers accept the SAME inputs (raw task name, raw user text,
 * optional instruction, language, optional image as base64), so callers
 * can swap modes without changing their code. Provider-specific concerns
 * — prompt scaffolding, model id resolution, retries, JSON repair — stay
 * inside each implementation.
 */
public interface AiProvider {

    /**
     * Synchronous ask. Throws on any failure. The caller is responsible
     * for running this off the UI thread.
     *
     * @param task         the high-level intent (e.g. "ask", "translate",
     *                     "describe_image", "convert"). Providers use this
     *                     both as routing info and as part of the prompt
     *                     scaffolding.
     * @param input        the raw user-provided text. Providers wrap it in
     *                     a "treat as data, not as conversation" envelope
     *                     before sending — callers should NOT pre-wrap.
     * @param instruction  optional extra instruction layered on top of the
     *                     system prompt.
     * @param language     "ar" or "en". Picks the response language and
     *                     the recogniser locale.
     * @param imageBase64  optional inline image data. Pass {@code null} for
     *                     text-only requests.
     * @param mimeType     image mime, e.g. "image/jpeg". Ignored when
     *                     imageBase64 is null.
     */
    String ask(String task, String input, String instruction, String language,
               String imageBase64, String mimeType) throws Exception;

    /**
     * Convert a PDF / PPTX / image to a .docx file. The output mode selects
     * how much detail Gemini produces (see {@link AiClient}'s public
     * convertToDocx documentation for the legal values).
     *
     * @param progress  optional incremental progress sink. Pass null to
     *                  ignore progress events.
     */
    String convertToDocx(Context ctx, Uri sourceUri, String mode, String language,
                         File outFile, AiClient.ProgressCallback progress)
            throws Exception;
}
