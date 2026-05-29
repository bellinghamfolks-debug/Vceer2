package com.basir.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;

/**
 * v2.3.1 — direct Gemini provider. Talks to {@code generativelanguage.googleapis.com}
 * using the user's own API key. No developer-side server.
 *
 * Thin wrapper for now: today most of the heavy lifting still lives as
 * package-private static methods on {@link AiClient}. A later phase will
 * fold those bodies into this class directly, but the interface is the
 * unit of stability — callers see {@link AiProvider} and never see the
 * static methods.
 */
public final class GeminiAiProvider implements AiProvider {

    private final SharedPreferences prefs;

    public GeminiAiProvider(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public String ask(String task, String input, String instruction, String language,
                      String imageBase64, String mimeType) throws Exception {
        String key = prefs.getString("gemini_api_key", "");
        String model = AiClient.pickModel(prefs, task);
        String systemText = AiClient.systemPrompt(language, instruction);
        String userMessage = AiClient.buildUserMessage(task, input, instruction,
                imageBase64 != null);
        return GeminiDirectClient.generateText(key, model, systemText, userMessage,
                imageBase64, mimeType);
    }

    @Override
    public String convertToDocx(Context ctx, Uri sourceUri, String mode, String language,
                                File outFile, AiClient.ProgressCallback progress)
            throws Exception {
        return AiClient.directConvertToDocx(ctx, prefs, sourceUri, mode, language,
                outFile, progress);
    }
}
