package com.basir.ai;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * v2.5 — transparent at-rest encryption for the Gemini API key.
 *
 * Why this exists
 * ───────────────
 *   v1 through v2.4 stored the user's Gemini API key in SharedPreferences
 *   as plaintext. Even with allowBackup=false (added in v2.2.5) the key
 *   was readable by any process with root or an adb pull of /data/data
 *   on a debuggable build. AndroidX's EncryptedSharedPreferences would
 *   solve this in one line — but this project deliberately runs on
 *   android.useAndroidX=false (see gradle.properties), so we use the
 *   raw Android Keystore + Cipher API ourselves. The platform Keystore
 *   keeps the AES key inside the TEE / Strongbox when available, which
 *   means the encrypted blob in SharedPreferences cannot be decrypted
 *   without running code on this exact device.
 *
 * Storage layout
 * ──────────────
 *   - {@code gemini_api_key}     legacy plaintext slot (cleared after migration)
 *   - {@code gemini_api_key_v2_enc}  base64(iv_len || iv || ciphertext)
 *
 * The first read after upgrading will migrate any legacy plaintext key
 * into the encrypted slot, then wipe the legacy slot. The user does not
 * notice anything — getGeminiKey() always returns the plaintext.
 *
 * The minimum supported API is 23, which is also the minSdk of the app,
 * so we can rely unconditionally on KeyGenParameterSpec + AES/GCM.
 *
 * Failure mode
 * ────────────
 *   If the Keystore is unavailable (rare — emulators without Google
 *   Play, or a wiped Keystore from a backup restore), getGeminiKey()
 *   transparently falls back to the legacy plaintext slot, and
 *   setGeminiKey() writes there too. A working app, just with the same
 *   security posture as before this class existed. This is preferred
 *   over locking the user out of their own settings.
 */
public final class SecurePrefs {

    static final String LEGACY_KEY     = "gemini_api_key";
    static final String CIPHERTEXT_KEY = "gemini_api_key_v2_enc";
    private static final String KEY_ALIAS = "BasirGeminiKeyV1";

    private SecurePrefs() {}

    /** Returns the plaintext Gemini API key, or "" if none is stored. */
    public static String getGeminiKey(SharedPreferences prefs) {
        String encrypted = prefs.getString(CIPHERTEXT_KEY, "");
        if (!encrypted.isEmpty()) {
            try {
                return decrypt(encrypted);
            } catch (Throwable t) {
                // Decryption failed — likely the Keystore was wiped (factory
                // reset restore, app moved between users on the device, or
                // an OEM bug). The ciphertext is now useless: wipe it and
                // fall through to the legacy slot so the user is at least
                // not in a permanently broken state.
                prefs.edit().remove(CIPHERTEXT_KEY).apply();
            }
        }
        return prefs.getString(LEGACY_KEY, "");
    }

    /** Persists {@code key}, encrypted via the platform Keystore when
     *  possible. Pass "" to clear. */
    public static void setGeminiKey(SharedPreferences prefs, String key) {
        if (key == null) key = "";
        SharedPreferences.Editor e = prefs.edit();
        if (key.isEmpty()) {
            e.remove(CIPHERTEXT_KEY);
            e.remove(LEGACY_KEY);
            e.apply();
            return;
        }
        try {
            e.putString(CIPHERTEXT_KEY, encrypt(key));
            e.remove(LEGACY_KEY); // drop the old plaintext
        } catch (Throwable t) {
            // Keystore unavailable — fall back to the legacy plaintext
            // slot so the user can still talk to Gemini. We do not log
            // the exception text because it can contain the key in some
            // weird future code paths.
            e.putString(LEGACY_KEY, key);
            e.remove(CIPHERTEXT_KEY);
        }
        e.apply();
    }

    /**
     * Called once at app startup. If a legacy plaintext key exists and the
     * encrypted slot is empty, this re-saves it through the Keystore and
     * wipes the legacy slot. Idempotent; safe to call on every launch.
     */
    public static void migrateLegacyKeyOnStartup(SharedPreferences prefs) {
        String legacy = prefs.getString(LEGACY_KEY, "");
        if (legacy.trim().isEmpty()) return;
        String existing = prefs.getString(CIPHERTEXT_KEY, "");
        if (!existing.isEmpty()) {
            // Already migrated AND a legacy slot was left behind by an
            // older version. Wipe the stale plaintext.
            prefs.edit().remove(LEGACY_KEY).apply();
            return;
        }
        setGeminiKey(prefs, legacy);
    }

    // ------------------------------------------------------------------
    // Crypto helpers
    // ------------------------------------------------------------------

    private static String encrypt(String plaintext) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buf = ByteBuffer.allocate(1 + iv.length + ct.length);
        buf.put((byte) iv.length);
        buf.put(iv);
        buf.put(ct);
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP);
    }

    private static String decrypt(String encoded) throws Exception {
        byte[] raw = Base64.decode(encoded, Base64.NO_WRAP);
        ByteBuffer buf = ByteBuffer.wrap(raw);
        int ivLen = buf.get() & 0xff;
        byte[] iv = new byte[ivLen];
        buf.get(iv);
        byte[] ct = new byte[buf.remaining()];
        buf.get(ct);
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        Key existing = ks.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }
}
