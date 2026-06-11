// BasirApp.swift
// Basir — iOS port (starter scaffold)
//
// @main entry point. SwiftUI app lifecycle.
// Mirrors the responsibilities of Android's MainActivity.onCreate:
//   - load settings
//   - bootstrap speech / accessibility services
//   - present the root tab UI

import SwiftUI

@main
struct BasirApp: App {
    @StateObject private var settings = BasirSettings.shared

    // Mirrors the Android resetScreen "title is heading, focus on mount"
    // behaviour: every NavigationStack root sets its title as accessibility
    // heading by default in iOS 17+ SwiftUI.

    init() {
        // One-shot migration: if a legacy plaintext API key exists in
        // UserDefaults from an older build, move it into Keychain.
        // Idempotent across launches.
        KeychainStore.migrateLegacyKeyIfNeeded()
        // v3.3 — one-shot migration of doc_quality. Existing installs
        // that saved doc_quality="best" while we were defaulting to it
        // would keep routing through gemini-3.1-pro-preview, which is
        // unreliable under the preview quota tier. Flip those installs
        // to "balanced" once. Users who had explicitly picked another
        // value are not touched.
        let defaults = UserDefaults.standard
        if !defaults.bool(forKey: "doc_quality_v33_migrated") {
            if defaults.string(forKey: "doc_quality") == "best" {
                defaults.set("balanced", forKey: "doc_quality")
            }
            defaults.set(true, forKey: "doc_quality_v33_migrated")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settings)
                // Apply RTL when Arabic is selected. iOS picks layout
                // direction from the locale by default, but our app
                // keeps its own language preference independent of the
                // system locale so users can override it.
                .environment(\.layoutDirection,
                             settings.language == .arabic ? .rightToLeft : .leftToRight)
        }
    }
}
