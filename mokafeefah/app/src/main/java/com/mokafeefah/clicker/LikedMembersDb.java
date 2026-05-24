package com.mokafeefah.clicker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Tiny SQLite store that remembers which members have already been "liked"
 * across app restarts. The original APK only deduplicated by on-screen
 * coordinates (cleared on every scroll), which guaranteed that the same
 * profile got liked again the moment it appeared at the same position.
 *
 * Approach:
 *   1) When we detect a "like" button, we walk up to find the surrounding
 *      profile card and collect ALL visible text inside it (name, age,
 *      weight, etc.).
 *   2) We normalize the text and compute a SHA-1 fingerprint.
 *   3) We INSERT-OR-IGNORE the fingerprint into the {@code liked} table.
 *      If the row already existed, the caller skips the click.
 *
 * To keep query latency negligible we also load a Bloom-friendly Set<String>
 * cache into memory and check that first.
 */
public final class LikedMembersDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "mokafeefah_liked.db";
    // v1.11: bumped to 2 to add the inspected_members table used by the
    // "divorced / widowed only" mode — it records every member whose
    // profile we opened and decided NOT to like, so we don't keep
    // re-opening the same ineligible profiles on every refresh.
    private static final int    DB_VERSION = 2;

    private static final String TABLE = "liked";
    private static final String COL_FP = "fp";
    private static final String COL_AT = "liked_at";

    private static final String INSPECTED_TABLE = "inspected";

    /** In-memory mirror of the fingerprint column. Keeps the hot-path fast. */
    private Set<String> cache;
    private Set<String> inspectedCache;
    private final Object cacheLock = new Object();

    public LikedMembersDb(Context ctx) {
        super(ctx.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_FP + " TEXT PRIMARY KEY NOT NULL, "
                + COL_AT + " INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX idx_liked_at ON " + TABLE + "(" + COL_AT + ")");
        createInspectedTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createInspectedTable(db);
        }
    }

    private void createInspectedTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + INSPECTED_TABLE + " ("
                + COL_FP + " TEXT PRIMARY KEY NOT NULL, "
                + COL_AT + " INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inspected_at ON "
                + INSPECTED_TABLE + "(" + COL_AT + ")");
    }

    /**
     * Lookup is O(1) thanks to the in-memory cache. The DB is only touched
     * lazily, once, to seed the cache.
     */
    public boolean isLiked(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return false;
        return ensureCache().contains(fingerprint);
    }

    /**
     * Record that we just liked this member. Inserts both into the in-memory
     * cache and the SQLite store. Safe to call from any thread.
     */
    public void markLiked(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return;
        synchronized (cacheLock) {
            ensureCacheLocked().add(fingerprint);
        }
        try {
            ContentValues v = new ContentValues();
            v.put(COL_FP, fingerprint);
            v.put(COL_AT, System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict(
                    TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Throwable ignore) {
            // If the DB is full or the disk is read-only, fall back to
            // in-memory dedup only — better than nothing.
        }
    }

    /** Returns the number of unique members ever liked. */
    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Wipes the entire history. Used by the "Clear history" button. */
    public void clearAll() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(TABLE, null, null);
            db.delete(INSPECTED_TABLE, null, null);
        } catch (Throwable ignore) {}
        synchronized (cacheLock) {
            cache = new HashSet<>();
            inspectedCache = new HashSet<>();
        }
    }

    // ---------- inspected (filter-mode, profile checked, NOT liked) ----------

    /**
     * v1.11 — true if we have previously opened this member's profile in
     * filter mode and decided they didn't qualify (not divorced / widowed).
     * Used so that on every soft-refresh we don't re-open the same
     * ineligible profiles over and over.
     */
    public boolean isInspected(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return false;
        synchronized (cacheLock) { return ensureInspectedCacheLocked().contains(fingerprint); }
    }

    public void markInspected(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return;
        synchronized (cacheLock) {
            ensureInspectedCacheLocked().add(fingerprint);
        }
        try {
            ContentValues v = new ContentValues();
            v.put(COL_FP, fingerprint);
            v.put(COL_AT, System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict(
                    INSPECTED_TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Throwable ignore) {}
    }

    private Set<String> ensureInspectedCacheLocked() {
        if (inspectedCache != null) return inspectedCache;
        Set<String> seeded = new HashSet<>();
        try (Cursor c = getReadableDatabase().query(
                INSPECTED_TABLE, new String[]{ COL_FP }, null, null, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) seeded.add(c.getString(0));
            }
        } catch (Throwable ignore) {}
        inspectedCache = seeded;
        return inspectedCache;
    }

    // ---------- private helpers ----------

    private Set<String> ensureCache() {
        synchronized (cacheLock) { return ensureCacheLocked(); }
    }

    private Set<String> ensureCacheLocked() {
        if (cache != null) return cache;
        Set<String> seeded = new HashSet<>();
        try (Cursor c = getReadableDatabase().query(
                TABLE, new String[]{ COL_FP }, null, null, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) seeded.add(c.getString(0));
            }
        } catch (Throwable ignore) {}
        cache = seeded;
        return cache;
    }

    // ---------- static fingerprint helper ----------

    /**
     * Compute a stable SHA-1 fingerprint of a profile's visible text.
     * The text is normalized (Arabic letter forms collapsed, whitespace
     * squashed) so the same profile produces the same hash regardless of
     * incidental differences.
     */
    public static String fingerprint(String text) {
        if (text == null) return null;
        String normalized = normalize(text);
        if (normalized.isEmpty()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(normalized.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            // Fallback: use a stable Java hash of the normalized text. Lower
            // resolution than SHA-1 but still useful for dedup.
            return "h" + Integer.toHexString(normalized.hashCode());
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String r = s.replaceAll("[ً-ْٰٱ]", "");                    // tashkeel
        r = r.replace((char) 1571, (char) 1575)   // أ → ا
             .replace((char) 1573, (char) 1575)   // إ → ا
             .replace((char) 1570, (char) 1575)   // آ → ا
             .replace((char) 1609, (char) 1610)   // ى → ي
             .replace((char) 1577, (char) 1607);  // ة → ه
        r = r.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return r;
    }
}
