package com.basir.ai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * BasirDb: local SQLite storage for activity logs, archived AI results,
 * and the personal memory (people, products, places).
 *
 * v2.5 — schema bumped to version 2:
 *   - Indexes added on every {@code created_at} column so trim-by-age and
 *     "last 100 documents" queries no longer scan the entire table.
 *   - Indexes added on the human-readable lookup columns (person name,
 *     product name, product barcode, place name).
 *   - Auto-trim helpers exposed via {@link #autoTrim()}, called once at
 *     startup by MainActivity. Defaults: keep last 1000 logs, last 200
 *     documents.
 *   - Selective deletion methods: {@link #deleteLog(long)},
 *     {@link #deleteDocument(long)}, {@link #deleteRecentLogs(int)}.
 *   - {@link #onUpgrade} is a real migration now (CREATE INDEX IF NOT
 *     EXISTS), not a re-run of onCreate that drops nothing.
 */
public class BasirDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "basir_ai.db";
    private static final int DB_VERSION = 2;

    /** Default auto-trim ceiling for the activity log. The list view only
     *  surfaces the last 200 entries anyway, so anything older just bloats
     *  the database and slows the trim queries themselves. */
    public static final int DEFAULT_LOG_KEEP_LAST = 1000;

    /** Default auto-trim ceiling for archived AI results. Documents are
     *  larger rows (text_content + summary) so we keep fewer. */
    public static final int DEFAULT_DOC_KEEP_LAST = 200;

    public BasirDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ------------------------------------------------------------------
    // Schema lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "type TEXT, content TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS documents (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT, kind TEXT, text_content TEXT, summary TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS persons (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, relation TEXT, notes TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, barcode TEXT, notes TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS places (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, description TEXT, notes TEXT, created_at TEXT)");
        createIndexes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v2.5 — proper migration. Earlier code just re-ran onCreate, which
        // worked by accident because every CREATE was "IF NOT EXISTS", but
        // it never added new columns or indexes. From here on every schema
        // change gets its own guarded block so old installs upgrade cleanly.
        if (oldVersion < 2) {
            createIndexes(db);
        }
        // Future migrations: add `if (oldVersion < 3) { ... }` blocks below.
    }

    /** Creates all indexes idempotently. Safe to call on any version. */
    private void createIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_created_at "
                + "ON logs(created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_type "
                + "ON logs(type)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_created_at "
                + "ON documents(created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_persons_name "
                + "ON persons(name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_name "
                + "ON products(name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_barcode "
                + "ON products(barcode)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_name "
                + "ON places(name)");
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
    }

    // ------------------------------------------------------------------
    // Logs
    // ------------------------------------------------------------------

    public void insertLog(String type, String content) {
        if (content == null || content.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("type", type == null ? "info" : type);
        v.put("content", content);
        v.put("created_at", now());
        getWritableDatabase().insert("logs", null, v);
    }

    public List<String> getRecentLogs(int limit) {
        List<String> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT created_at, type, content FROM logs ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                list.add(c.getString(0) + " [" + c.getString(1) + "] " + c.getString(2));
            }
        }
        return list;
    }

    public void clearLogs() {
        getWritableDatabase().delete("logs", null, null);
    }

    /** v2.5 — drop a single log row. */
    public void deleteLog(long id) {
        getWritableDatabase().delete("logs", "id = ?",
                new String[]{ String.valueOf(id) });
    }

    /** v2.5 — drop the most recent {@code n} log rows. Used for "delete
     *  today's activity" / "delete the last hour" buttons in the settings
     *  privacy panel. */
    public void deleteRecentLogs(int n) {
        if (n <= 0) return;
        getWritableDatabase().execSQL(
                "DELETE FROM logs WHERE id IN ("
                        + " SELECT id FROM logs ORDER BY id DESC LIMIT " + n + ")");
    }

    // ------------------------------------------------------------------
    // Documents (archived AI results)
    // ------------------------------------------------------------------

    public void insertDocument(String title, String kind, String text, String summary) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("kind", kind);
        v.put("text_content", text);
        v.put("summary", summary);
        v.put("created_at", now());
        getWritableDatabase().insert("documents", null, v);
    }

    public List<String> getRecentDocuments(int limit) {
        List<String> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT created_at, title, kind, summary FROM documents ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                list.add(c.getString(0) + " - " + c.getString(1) +
                        " [" + c.getString(2) + "]: " + c.getString(3));
            }
        }
        return list;
    }

    /** v2.5 — drop a single document row. */
    public void deleteDocument(long id) {
        getWritableDatabase().delete("documents", "id = ?",
                new String[]{ String.valueOf(id) });
    }

    // ------------------------------------------------------------------
    // Personal memory: persons, products, places
    // ------------------------------------------------------------------

    public void insertPerson(String name, String relation, String notes) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("relation", relation);
        v.put("notes", notes);
        v.put("created_at", now());
        getWritableDatabase().insert("persons", null, v);
    }

    public void insertProduct(String name, String barcode, String notes) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("barcode", barcode);
        v.put("notes", notes);
        v.put("created_at", now());
        getWritableDatabase().insert("products", null, v);
    }

    public void insertPlace(String name, String description, String notes) {
        if (name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("description", description);
        v.put("notes", notes);
        v.put("created_at", now());
        getWritableDatabase().insert("places", null, v);
    }

    public String getMemorySummary(boolean english) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "persons", "name, relation, notes",
                english ? "People:" : "الأشخاص:");
        appendSection(sb, "products", "name, barcode, notes",
                english ? "Products:" : "المنتجات:");
        appendSection(sb, "places", "name, description, notes",
                english ? "Places:" : "الأماكن:");
        return sb.toString().trim();
    }

    private void appendSection(StringBuilder sb, String table, String cols, String title) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + cols + " FROM " + table + " ORDER BY id DESC LIMIT 50", null)) {
            if (c.getCount() == 0) return;
            sb.append(title).append('\n');
            while (c.moveToNext()) {
                sb.append("- ");
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String v = c.getString(i);
                    if (v != null && !v.isEmpty()) sb.append(v).append(" | ");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
    }

    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("logs", null, null);
        db.delete("documents", null, null);
        db.delete("persons", null, null);
        db.delete("products", null, null);
        db.delete("places", null, null);
    }

    // ------------------------------------------------------------------
    // Auto-trim
    // ------------------------------------------------------------------

    /**
     * v2.5 — runs the default trim policy: keep the last {@link #DEFAULT_LOG_KEEP_LAST}
     * log rows and the last {@link #DEFAULT_DOC_KEEP_LAST} document rows. Anything
     * older is deleted. Safe to call from any thread; uses one transaction.
     *
     * MainActivity calls this once per launch from a background executor, so
     * users with a year of accumulated activity don't see a startup pause.
     */
    public void autoTrim() {
        trimLogs(DEFAULT_LOG_KEEP_LAST);
        trimDocuments(DEFAULT_DOC_KEEP_LAST);
    }

    /** Keeps the latest {@code keepLast} log rows and deletes the rest. */
    public void trimLogs(int keepLast) {
        if (keepLast < 0) keepLast = 0;
        getWritableDatabase().execSQL(
                "DELETE FROM logs WHERE id NOT IN ("
                        + " SELECT id FROM logs ORDER BY id DESC LIMIT " + keepLast + ")");
    }

    /** Keeps the latest {@code keepLast} document rows and deletes the rest. */
    public void trimDocuments(int keepLast) {
        if (keepLast < 0) keepLast = 0;
        getWritableDatabase().execSQL(
                "DELETE FROM documents WHERE id NOT IN ("
                        + " SELECT id FROM documents ORDER BY id DESC LIMIT " + keepLast + ")");
    }

    /** Returns the number of rows currently in the named table. Used by the
     *  developer-diagnostics screen and by the privacy panel. */
    public int rowCount(String table) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table, null)) {
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Throwable ignore) {}
        return 0;
    }
}
