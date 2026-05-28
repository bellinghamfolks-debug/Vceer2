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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persistent dedup store. Across app restarts the bot remembers which
 * members have been liked so refresh cycles never re-tap them.
 *
 * v1.13.0 — writes are batched and async:
 *   • markLiked/markInspected update the in-memory cache synchronously
 *     so isLiked() reflects the new entry immediately, then add the
 *     fingerprint to a pending batch.
 *   • Every FLUSH_BATCH_SIZE inserts, the batch is handed off to a
 *     single-thread executor that does a single transactional insert.
 *   • flushNow() drains the pending batch synchronously; called when
 *     the service stops, unbinds, or is destroyed so we never lose a
 *     batch worth of likes on process kill.
 *
 * Two tables — `liked` and `inspected`. Both contain BOTH the strict
 * Arabic-only fingerprints AND the v1.13.0 "loose" (T:hash) IDs;
 * because looseId values are prefixed with "T:" they never collide
 * with strict SHA-1 hex strings.
 */
public final class LikedMembersDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "mokafeefah_liked.db";
    private static final int    DB_VERSION = 2;

    private static final String TABLE = "liked";
    private static final String COL_FP = "fp";
    private static final String COL_AT = "liked_at";

    private static final String INSPECTED_TABLE = "inspected";

    private static final int FLUSH_BATCH_SIZE = 25;

    private Set<String> cache;
    private Set<String> inspectedCache;
    private final Object cacheLock = new Object();

    private final Set<String> pendingLiked = new HashSet<>();
    private final Set<String> pendingInspected = new HashSet<>();
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

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
     * v1.13.0 — preload both caches on the executor so the first
     * call to isLiked() doesn't pay the seed-from-disk latency on
     * the hot path. Called from ClickerService.startBot().
     */
    public void warmUp() {
        dbExecutor.execute(new Runnable() {
            @Override public void run() {
                synchronized (cacheLock) {
                    ensureCacheLocked();
                    ensureInspectedCacheLocked();
                }
            }
        });
    }

    /** O(1) memory lookup. */
    public boolean isLiked(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return false;
        synchronized (cacheLock) { return ensureCacheLocked().contains(fingerprint); }
    }

    public void markLiked(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return;
        boolean shouldFlush;
        synchronized (cacheLock) {
            ensureCacheLocked().add(fingerprint);
            pendingLiked.add(fingerprint);
            shouldFlush = pendingLiked.size() + pendingInspected.size() >= FLUSH_BATCH_SIZE;
            if (shouldFlush) scheduleFlushLocked();
        }
    }

    public boolean isInspected(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return false;
        synchronized (cacheLock) { return ensureInspectedCacheLocked().contains(fingerprint); }
    }

    public void markInspected(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) return;
        boolean shouldFlush;
        synchronized (cacheLock) {
            ensureInspectedCacheLocked().add(fingerprint);
            pendingInspected.add(fingerprint);
            shouldFlush = pendingLiked.size() + pendingInspected.size() >= FLUSH_BATCH_SIZE;
            if (shouldFlush) scheduleFlushLocked();
        }
    }

    /**
     * Force-flush any pending writes. Safe to call from any thread —
     * the work is still dispatched to dbExecutor so we don't block the
     * accessibility-service main loop on disk I/O.
     */
    public void flushNow() {
        synchronized (cacheLock) {
            if (pendingLiked.isEmpty() && pendingInspected.isEmpty()) return;
            scheduleFlushLocked();
        }
    }

    /** Caller must hold cacheLock. Snapshots the pending sets and clears them. */
    private void scheduleFlushLocked() {
        final HashSet<String> likedBatch = new HashSet<>(pendingLiked);
        final HashSet<String> inspectedBatch = new HashSet<>(pendingInspected);
        pendingLiked.clear();
        pendingInspected.clear();
        dbExecutor.execute(new Runnable() {
            @Override public void run() { writeBatch(likedBatch, inspectedBatch); }
        });
    }

    private void writeBatch(HashSet<String> likedBatch, HashSet<String> inspectedBatch) {
        if (likedBatch.isEmpty() && inspectedBatch.isEmpty()) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long now = System.currentTimeMillis();
                ContentValues v = new ContentValues();
                for (String fp : likedBatch) {
                    v.clear();
                    v.put(COL_FP, fp);
                    v.put(COL_AT, now);
                    db.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE);
                }
                for (String fp : inspectedBatch) {
                    v.clear();
                    v.put(COL_FP, fp);
                    v.put(COL_AT, now);
                    db.insertWithOnConflict(INSPECTED_TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Throwable ignore) {
            // DB full / read-only / corrupted — in-memory cache still
            // dedups within the current session.
        }
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public void clearAll() {
        synchronized (cacheLock) {
            pendingLiked.clear();
            pendingInspected.clear();
            cache = new HashSet<>();
            inspectedCache = new HashSet<>();
        }
        dbExecutor.execute(new Runnable() {
            @Override public void run() {
                try {
                    SQLiteDatabase db = getWritableDatabase();
                    db.delete(TABLE, null, null);
                    db.delete(INSPECTED_TABLE, null, null);
                } catch (Throwable ignore) {}
            }
        });
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
            return "h" + Integer.toHexString(normalized.hashCode());
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        // v1.12.9 — include U+0640 (Arabic Tatweel) in the strip class so
        // 'بـحـث' normalises to 'بحث'.
        String r = s.replaceAll("[ً-ْٰٱـ]", "");
        r = r.replace((char) 1571, (char) 1575)   // أ → ا
             .replace((char) 1573, (char) 1575)   // إ → ا
             .replace((char) 1570, (char) 1575)   // آ → ا
             .replace((char) 1609, (char) 1610)   // ى → ي
             .replace((char) 1577, (char) 1607);  // ة → ه
        r = r.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return r;
    }
}
