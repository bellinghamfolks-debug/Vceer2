// AiProviderFactory.swift
//
// Single entry point for "give me whichever AiProvider the user
// configured." Mirrors the AiClient.providerFor(prefs) idea on
// Android: views and controllers ask the factory, and only the
// factory knows about the direct-vs-proxy choice. Adding a third
// transport (or swapping the JSON wire format) becomes a one-file
// change here, not a sweep across 7 views.

import Foundation

enum AiProviderFactory {

    /// Build the AiProvider matching the current connection mode.
    /// Reads `BasirSettings.aiMode` ("direct" | "proxy") on the
    /// main actor and returns the corresponding provider.
    @MainActor
    static func current() -> AiProvider {
        let mode = BasirSettings.shared.aiMode
        if mode == "proxy" {
            return ProxyAiProvider()
        }
        return GeminiAiProvider()
    }

    /// True when the chosen mode has a usable key / URL. Mirrors
    /// BasirSettings.isConfigured but exposed here so callers can
    /// short-circuit before composing a Gemini request.
    @MainActor
    static var isConfigured: Bool {
        BasirSettings.shared.isConfigured
    }
}
