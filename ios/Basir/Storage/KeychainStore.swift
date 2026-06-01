// KeychainStore.swift
// Secure storage for the Gemini API key.
//
// On Android we use Android Keystore + AES/GCM ourselves (see
// SecurePrefs.java). On iOS the equivalent is the system Keychain
// Services API, which the OS automatically encrypts at rest using a
// per-device hardware key.
//
// kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly means:
//   - Available after the user unlocks the device once after boot.
//   - Stays available even when the device is later re-locked.
//   - NOT migrated when the user restores a backup to a different
//     device (matches Android's android:allowBackup="false" intent).
//
// Bridge methods at the bottom give a one-time migration from a legacy
// UserDefaults key for users upgrading from a dev / TestFlight build
// that stored the key in plain UserDefaults.

import Foundation
import Security

enum KeychainStore {
    private static let service = "com.basir.ai.gemini"
    private static let account = "gemini_api_key"
    private static let legacyKey = "gemini_api_key_plaintext"

    /// Returns the stored API key, or "" if none.
    static func geminiKey() -> String {
        var query: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String:  true,
            kSecMatchLimit as String:  kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess,
              let data = item as? Data,
              let key = String(data: data, encoding: .utf8) else {
            return ""
        }
        return key
    }

    /// Persists the key, replacing any previous value. Pass "" to clear.
    @discardableResult
    static func setGeminiKey(_ key: String) -> Bool {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        let baseQuery: [String: Any] = [
            kSecClass as String:       kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        if trimmed.isEmpty {
            SecItemDelete(baseQuery as CFDictionary)
            return true
        }
        let data = trimmed.data(using: .utf8) ?? Data()
        let attrs: [String: Any] = [
            kSecValueData as String:       data,
            kSecAttrAccessible as String:  kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        // Try update first; if no row exists, add a new one.
        var updateStatus = SecItemUpdate(baseQuery as CFDictionary,
                                          attrs as CFDictionary)
        if updateStatus == errSecItemNotFound {
            var addQuery = baseQuery
            addQuery.merge(attrs) { _, new in new }
            updateStatus = SecItemAdd(addQuery as CFDictionary, nil)
        }
        return updateStatus == errSecSuccess
    }

    /// One-shot migration on app startup. If a legacy plaintext key
    /// exists in UserDefaults from an older build, move it into Keychain
    /// and wipe the UserDefaults copy. Idempotent across launches.
    static func migrateLegacyKeyIfNeeded() {
        let defaults = UserDefaults.standard
        guard let legacy = defaults.string(forKey: legacyKey),
              !legacy.isEmpty else { return }
        if geminiKey().isEmpty {
            setGeminiKey(legacy)
        }
        defaults.removeObject(forKey: legacyKey)
    }
}
