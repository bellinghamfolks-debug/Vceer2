package com.basir.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;

/**
 * v2.3.1 — proxy provider. Forwards every request to an HTTPS proxy
 * deployed by the user / their organisation. The proxy holds the Gemini
 * API key, so end users never paste a key into their phone.
 *
 * Thin wrapper for now over {@link AiClient}'s package-private static
 * methods. A later phase will inline the HTTP body construction here.
 */
public final class ProxyAiProvider implements AiProvider {

    private final SharedPreferences prefs;

    public ProxyAiProvider(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public String ask(String task, String input, String instruction, String language,
                      String imageBase64, String mimeType) throws Exception {
        // The proxy receives the RAW input, plus task / instruction / language
        // metadata, and is responsible for building the actual Gemini prompt
        // server-side. That is why this method does NOT call buildUserMessage()
        // — the proxy will do its own scaffolding.
        return AiClient.proxyAsk(prefs, task, input, instruction, language,
                imageBase64, mimeType);
    }

    @Override
    public String convertToDocx(Context ctx, Uri sourceUri, String mode, String language,
                                File outFile, AiClient.ProgressCallback progress)
            throws Exception {
        return AiClient.proxyConvertToDocx(ctx, prefs, sourceUri, mode, language,
                outFile, progress);
    }
}
