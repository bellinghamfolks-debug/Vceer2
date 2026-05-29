package com.mokafeefah.clicker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent store for named (x,y) screen coordinates that the bot or the
 * user can tap via GestureDescription. Used as a fallback for buttons the
 * AccessibilityService cannot resolve by text — e.g. the unlabeled
 * three-dot menu in Mawada whose contentDescription is empty.
 *
 * Names are unique. Re-saving the same name overwrites the old coordinate.
 */
public class SavedCoordinatesDb extends SQLiteOpenHelper {

    private static final String DB_NAME    = "saved_coords.db";
    private static final int    DB_VERSION = 1;
    private static final String TABLE      = "coords";

    public SavedCoordinatesDb(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // First version — nothing to migrate yet. Future schema changes
        // should branch on oldVersion here. Keep an idempotent CREATE so
        // a fresh install also lands correctly.
        createTable(db);
    }

    private void createTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE NOT NULL, " +
                "x INTEGER NOT NULL, " +
                "y INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL)");
    }

    public static class Coord {
        public final long   id;
        public final String name;
        public final int    x;
        public final int    y;
        public final long   createdAt;

        Coord(long id, String name, int x, int y, long createdAt) {
            this.id = id; this.name = name; this.x = x; this.y = y;
            this.createdAt = createdAt;
        }
    }

    /** Insert a new coordinate, or replace the existing row with the same name. */
    public synchronized void upsert(String name, int x, int y) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("x", x);
        v.put("y", y);
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Look up a coordinate by exact name. Returns null if missing. */
    public synchronized Coord findByName(String name) {
        Cursor c = getReadableDatabase().query(
                TABLE, null, "name = ?", new String[]{ name }, null, null, null);
        try {
            if (c.moveToFirst()) return cursorToCoord(c);
            return null;
        } finally {
            c.close();
        }
    }

    /** Every saved coordinate, newest first. */
    public synchronized List<Coord> listAll() {
        List<Coord> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(
                TABLE, null, null, null, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(cursorToCoord(c));
            return out;
        } finally {
            c.close();
        }
    }

    public synchronized void deleteById(long id) {
        getWritableDatabase().delete(TABLE, "id = ?", new String[]{ String.valueOf(id) });
    }

    public synchronized void clearAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    /**
     * Mawada home-screen coordinates pre-scaled to the user's 1220×2712 panel
     * (extracted from the home-screen screenshot). The 'ثلاث نقاط' button is
     * the unlabeled menu the user reported as 'the first unreadable button'.
     *
     * v1.12.3 — three-dots coordinate refined to (1055, 160) per the user's
     * latest on-device measurement. Earlier values (1134, 244) and (1012, 163)
     * are now both treated as stale and overwritten on first launch.
     *
     * Two seeding modes:
     *   - seedIfEmpty(): only inserts when the table is empty (called on
     *     first launch so the user sees defaults without doing anything).
     *   - seedMissing(): inserts entries that don't exist by name, leaves
     *     existing rows alone (called by the 'restore defaults' button so
     *     it never overwrites coordinates the user fine-tuned).
     */
    private static final String[][] MAWADA_DEFAULTS = {
            // name,                                 x,    y
            { "ثلاث نقاط (قائمة علوية)", "1055", "160"  },
            { "بحث (أعلى يسار)",        "61",   "244"  },
            { "جرس الإشعارات",          "976",  "244"  },
            { "الأعضاء (تنقّل سفلي)",   "610",  "2576" },
            { "بريدي الداخلي",          "208",  "2576" },
            { "باقة التميز",            "1012", "2576" },
            { "تقييم التطبيق",          "610",  "881"  },
            { "أيقونة الوصول (يسار)",   "81",   "429"  }
    };

    public synchronized int seedIfEmpty() {
        if (!listAll().isEmpty()) return 0;
        return seedMissing();
    }

    /**
     * One-shot migration that brings stale three-dots defaults forward to
     * the current best estimate. Any row whose value matches a previously
     * shipped default is overwritten; user-calibrated values (anything not
     * in the known-stale set) are preserved.
     *
     * v1.12.3 target: (1055, 160). Known stale: (1134, 244) from v1.12.0–1
     * and (1012, 163) from v1.12.2.
     */
    public synchronized boolean migrateThreeDotsCoord() {
        Coord cur = findByName("ثلاث نقاط (قائمة علوية)");
        if (cur == null) return false;
        boolean stale =
                (cur.x == 1134 && cur.y == 244) ||
                (cur.x == 1012 && cur.y == 163);
        if (stale) {
            upsert("ثلاث نقاط (قائمة علوية)", 1055, 160);
            return true;
        }
        return false;
    }

    public synchronized int seedMissing() {
        int added = 0;
        for (String[] row : MAWADA_DEFAULTS) {
            if (findByName(row[0]) != null) continue;
            upsert(row[0], Integer.parseInt(row[1]), Integer.parseInt(row[2]));
            added++;
        }
        return added;
    }

    private static Coord cursorToCoord(Cursor c) {
        return new Coord(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getInt(c.getColumnIndexOrThrow("x")),
                c.getInt(c.getColumnIndexOrThrow("y")),
                c.getLong(c.getColumnIndexOrThrow("created_at")));
    }
}
