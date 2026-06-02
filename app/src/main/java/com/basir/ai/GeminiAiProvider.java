package com.basir.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * v2.3.1 — direct Gemini provider. Talks to {@code generativelanguage.googleapis.com}
 * using the user's own API key. No developer-side server.
 *
 * v3.0 — math extraction takes a structured JSON path internally:
 *   - prompt asks for LaTeX-only output (small, deterministic shape)
 *   - Gemini API responseMimeType=application/json + responseSchema
 *     guarantees parseable output
 *   - the LaTeX strings are then converted to spoken Arabic / English
 *     on-device via {@link LatexToSpeech} — no network round-trip
 *     for the spoken form, so it never truncates or drifts.
 */
public final class GeminiAiProvider implements AiProvider {

    private final SharedPreferences prefs;

    public GeminiAiProvider(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public String ask(String task, String input, String instruction, String language,
                      String imageBase64, String mimeType) throws Exception {
        String key = SecurePrefs.getGeminiKey(prefs);
        String model = AiClient.pickModel(prefs, task);

        // v3.0 — math extraction takes a dedicated JSON-mode path with
        // an on-device spoken-form renderer. See class javadoc.
        if ("math_extract".equals(task) && imageBase64 != null) {
            return mathExtract(key, model, language, imageBase64, mimeType);
        }

        String systemText = AiClient.systemPrompt(language, instruction);
        String userMessage = AiClient.buildUserMessage(task, input, instruction,
                imageBase64 != null);
        return GeminiDirectClient.generateText(key, model, systemText, userMessage,
                imageBase64, mimeType);
    }

    /**
     * v3.0 — JSON-mode math extraction. Returns the rendered
     * "spoken-form [LaTeX: ...]" output that the result screen
     * displays. Fall-back: if JSON parsing fails (model ignored the
     * schema), fall through to the v2.9 free-text path so the user
     * never gets an empty result.
     */
    private String mathExtract(String key, String model, String language,
                                String imageBase64, String mimeType) throws Exception {
        boolean arabic = language == null || language.toLowerCase().startsWith("ar");
        boolean english = !arabic;
        String systemText = "You are Basir, an assistant for blind and low-vision users.";
        String userMessage = AiClient.mathExtractionJsonPrompt(english);
        JSONObject schema = AiClient.mathExtractionResponseSchema();

        try {
            JSONObject json = GeminiDirectClient.generateJsonWithImage(
                    key, model, systemText, userMessage,
                    imageBase64, mimeType, schema, 32_768);
            return renderMathItems(json, arabic);
        } catch (Throwable jsonErr) {
            // Fall back to the v2.9 free-text path. The model's free-text
            // response is sent to the same result screen and may still be
            // useful; the user gets SOMETHING instead of a hard error.
            String fallbackInstruction = AiClient.mathExtractionInstruction(english);
            String sysFallback = AiClient.systemPrompt(language, fallbackInstruction);
            String userFallback = AiClient.buildUserMessage("math_extract", "",
                    fallbackInstruction, true);
            return GeminiDirectClient.generateText(key, model, sysFallback,
                    userFallback, imageBase64, mimeType);
        }
    }

    /** v3.0 — turn the structured JSON response into the user-facing
     *  "spoken [LaTeX: ...]" listing. */
    private String renderMathItems(JSONObject json, boolean arabic) {
        StringBuilder out = new StringBuilder();
        String prose = json.optString("non_math_text", "");
        if (!prose.trim().isEmpty()) {
            out.append(prose.trim()).append("\n\n");
        }
        JSONArray items = json.optJSONArray("items");
        if (items == null || items.length() == 0) {
            if (out.length() == 0) {
                return arabic
                        ? "لم يتم العثور على أي تعبير رياضي في الصورة."
                        : "No mathematical expression was found in the image.";
            }
            return out.toString().trim();
        }
        int idx = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String latex = item.optString("latex", "").trim();
            if (latex.isEmpty()) continue;
            idx++;
            String context = item.optString("context", "").trim();
            String spoken = LatexToSpeech.convert(latex, arabic);

            out.append(idx).append(". ");
            if (!context.isEmpty()) {
                out.append("[").append(context).append("] ");
            }
            out.append(spoken)
               .append(arabic ? "\n   [اللاتيكس: " : "\n   [LaTeX: ")
               .append(latex)
               .append("]\n\n");
        }
        return out.toString().trim();
    }

    @Override
    public String convertToDocx(Context ctx, Uri sourceUri, String mode, String language,
                                File outFile, AiClient.ProgressCallback progress)
            throws Exception {
        return AiClient.directConvertToDocx(ctx, prefs, sourceUri, mode, language,
                outFile, progress);
    }
}
