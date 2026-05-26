package com.mokafeefah.clicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v3 rewrite — root-cause fixes for "one like then infinite scroll, then crash".
 *
 *  CRASH ROOT CAUSE (v2): tickPool was a List, and track() blindly appended.
 *  Any helper that returned a child node which had been added during recursion
 *  AND then re-tracked by the caller would be recycle()'d twice at end of tick.
 *  Double-recycle corrupts system_server's state for our service and after a
 *  few dozen ticks the OS denies every accessibility op → "frozen / crashed".
 *  v3 uses an IdentityHashMap pool — each unique reference is recycled exactly
 *  once, ever.
 *
 *  "ONE LIKE THEN SCROLL FOREVER" ROOT CAUSE (v2): climbToCard climbed up to
 *  the first parent with "≥3 text descendants". In a list of member cards, the
 *  first such ancestor is usually the LIST itself, not the individual card.
 *  Then collectVisibleText collected every text in the list (including
 *  off-screen items) → identical fingerprint for every member. After the very
 *  first successful like, every member matched the cached fingerprint → all
 *  skipped → scroll → still same fingerprint → loop forever.
 *  v3 detects the card by SCREEN BOUNDS heuristics (smallest ancestor that's
 *  card-shaped relative to the like button) and collects text only inside that
 *  bounding rect, filtered by isVisibleToUser().
 */
public class ClickerService extends AccessibilityService {

    public static final String STATUS_AUTO_STOPPED = "إيقاف تلقائي";
    public static final String STATUS_IDLE         = "خامل";
    public static final String STATUS_RUNNING      = "يعمل";
    public static final String STATUS_STOPPED      = "متوقف";

    // v8: states. STATE_REWIND_TO_TOP runs once at startBot before LOOK_LIKE
    // and forces the target list back to its true top, because Mawadda's
    // recycler doesn't always reattach the "إهتمام" content description on
    // recycled rows — starting mid-list left the bot finding nothing.
    private static final int STATE_REWIND_TO_TOP = 4;
    private static final int STATE_LOOK_LIKE     = 0;
    private static final int STATE_AFTER_LIKE    = 1;
    private static final int STATE_AFTER_YES     = 2;
    private static final int STATE_MUST_SCROLL   = 3;
    // v10.0: every N likes the bot taps Mawada's own bottom-bar "members"
    // tab to force a soft refresh of the search-results page. Mawada
    // doesn't garbage-collect its WebView DOM as the user scrolls — by
    // like #300 the accessibility tree has grown from ~1.3 K to ~6.5 K
    // nodes and every popup takes 7-13 s to settle. Tapping the members
    // tab makes Mawada re-render the page, the DOM drops back to its
    // initial size, and per-cycle latency snaps back to early-session
    // values. Filters are kept (Mawada's own state).
    private static final int STATE_SOFT_REFRESH  = 5;
    // v1.11: second nav step inside soft-refresh — after tapping "الأعضاء"
    // we tap "المتواجدون الآن" to land on the active-members list
    // explicitly. The user's request is to always follow that path.
    private static final int STATE_NAV_ONLINE_NOW = 6;
    // v1.11: filter-mode profile inspection. After opening a member's
    // profile from the list, this state checks whether the page is the
    // "already added" view (closes it) or a new profile (reads marital
    // status and likes only if مطلقة / أرملة).
    private static final int STATE_INSPECT_PROFILE = 7;

    // v1.12: three-dots refresh path. After every LIKES_PER_SOFT_REFRESH
    // likes, if findMembersMode = "three_dots" the bot taps the saved
    // 'ثلاث نقاط' coordinate (gesture-dispatched, since the menu button
    // has empty contentDescription and can't be reached by text lookup),
    // then text-matches the 'بحث' button twice in sequence:
    //   STATE_REFRESH_FIND_SEARCH_1  — tap the menu's بحث entry
    //   STATE_REFRESH_FIND_SEARCH_2  — submit by tapping بحث again
    // After search completes, the same finishNavOnlineNow() logic runs
    // (rewind vs. continue) so refreshContinueMode keeps working.
    private static final int STATE_REFRESH_FIND_SEARCH_1 = 8;
    private static final int STATE_REFRESH_FIND_SEARCH_2 = 9;

    // v10.0: latency budgets — relaxed because Mawada's late-session popup
    // can take 6-7 s to even appear. The previous 2.5 s timeout was firing
    // a stale AFTER_LIKE → LOOK transition before "نعم" had rendered, then
    // LOOK had to redo the work. Pushed to 8 s — still bounded, but no
    // longer racing Mawada's worst-case render.
    private static final long MAX_WAIT_AFTER_LIKE_MS = 8000L;
    private static final long MAX_WAIT_AFTER_YES_MS  = 8000L;
    private static final long POST_BACK_WAIT_MS      = 900L;
    private static final long POST_SCROLL_WAIT_MS    = 1800L;

    private static final int  MAX_TREE_DEPTH        = 30;
    private static final int  MAX_NODES_PER_TICK    = 6000;
    private static final int  CARD_MAX_CLIMB        = 9;
    private static final int  CARD_TEXT_DEPTH       = 6;
    private static final int  MIN_TEXT_CHARS_FOR_FP = 6;

    // v8: rewind state — repeatedly fires SCROLL_BACKWARD on the main
    // scrollable until it refuses or we hit the safety cap.
    private static final int  MAX_REWIND_STEPS      = 12;
    private static final long REWIND_STEP_WAIT_MS   = 400L;

    // v8: stuck detection is now time-based, not count-based. v7's count of
    // 30 was ≈60s of trying, which expires before a slow paginated list can
    // actually respond. 3 minutes of unproductive scrolling is the new bar.
    private static final long NO_PROGRESS_BUDGET_MS = 180_000L;

    // v10.0: soft-refresh tuning. Tap Mawada's "members" tab every N
    // confirmed likes. 80 is a sweet spot from the dump data — by then the
    // tree has roughly doubled, latency is starting to climb, but a refresh
    // is still well below Mawada's apparent rate-limit thresholds.
    private static final int  LIKES_PER_SOFT_REFRESH = 80;
    // Visible text / desc of Mawada's bottom-bar entry that returns the
    // user to the members search page. Confirmed in the v9.4 dump:
    //   [C] [V] [412,2387][806,2712] android.view.View desc="الأعضاء"
    private static final String[] MEMBERS_TAB_KEYWORDS = { "الأعضاء" };
    // v1.11: text used to navigate to the active-members sub-tab once we
    // are on the members page. The user's spec: every list traversal must
    // follow "الأعضاء ← المتواجدون الآن".
    private static final String[] ONLINE_NOW_KEYWORDS = { "المتواجدون الآن", "المتواجدون الان" };
    // v1.12.9: 'بحث' label inside the menu opened by tapping three-dots.
    // Mawada writes the submit button as 'بـحـث' (with U+0640 tatweel
    // characters between letters) — the native substring search ignores
    // string normalization, so we have to feed it both literal forms.
    private static final String[] SEARCH_KEYWORDS = { "بحث", "بـحـث" };
    // v1.12: the saved-coordinate row this refresh path looks up.
    private static final String COORD_THREE_DOTS = "ثلاث نقاط (قائمة علوية)";
    // How long to wait after tapping the members tab before resuming.
    // Mawada's first paint of the fresh list is ~1.5-2 s on average.
    private static final long SOFT_REFRESH_WAIT_MS = 2500L;
    private static final long NAV_ONLINE_WAIT_MS    = 2000L;

    // v1.11: keywords used by the filter-mode profile inspector.
    // "الملف الشخصي" appears on the popup we get when we tap a member
    // who is ALREADY in our liked list — that popup only has an
    // "إغلاق" button and means we must back out and skip.
    private static final String[] PROFILE_ALREADY_ADDED_HINTS = { "الملف الشخصي" };
    // The marital-status label that confirms we're on a fresh profile.
    private static final String[] MARITAL_LABEL_HINTS = { "الحالة الاجتماعية", "الحاله الاجتماعيه" };
    // Eligibility values. Normalized matching tolerates ة/ه and ا variants.
    private static final String[] ELIGIBLE_MARITAL_VALUES = { "مطلقة", "ارملة", "أرملة", "مطلقه", "ارمله", "أرمله" };
    // Latency budgets for the inspect-profile state.
    private static final long MAX_WAIT_FOR_PROFILE_LOAD_MS = 7000L;
    private static final long MAX_WAIT_IN_INSPECT_MS       = 10000L;

    // v8: keywords that say "load more / show more" — we tap them before
    // swiping if visible, because some apps don't auto-paginate on scroll.
    private static final String[] LOAD_MORE_KEYWORDS = {
            // v9.3: "مشاهدة المزيد" added — it's the literal text Mawada uses
            // at the bottom of its search-results list (confirmed in the
            // accessibility dump).
            "مشاهدة المزيد",
            "تحميل المزيد", "عرض المزيد", "المزيد", "إظهار المزيد",
            "Load more", "Show more", "More"
    };

    // v8: phrases the target app might show when the list is exhausted.
    // Catching these stops the bot immediately with a clear reason instead
    // of letting it spin out the no-progress budget silently.
    private static final String[] END_OF_LIST_HINTS = {
            "لا يوجد المزيد", "وصلت إلى النهاية", "انتهت",
            "لا توجد نتائج", "لا يوجد أعضاء"
    };

    private static ClickerService instance;

    private BotConfig config;
    private StatusListener listener;
    private LikedMembersDb db;
    // v1.12: lookup for saved (name → x,y). Used by the three-dots
    // refresh path to find the menu coordinate the user pre-saved.
    private SavedCoordinatesDb coordsDb;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger likesCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);

    private final Set<String> processedBounds = new HashSet<>();

    /**
     * Identity-keyed pool. Two distinct AccessibilityNodeInfo refs to the same
     * logical node ARE recycled separately (correct). The same ref is recycled
     * AT MOST ONCE (also correct). This is what makes v3 stable.
     */
    private final IdentityHashMap<AccessibilityNodeInfo, Boolean> tickPool =
            new IdentityHashMap<>(256);

    private String lastAction = "";
    private String lastDumpPath = ""; // v9: latest auto-stop diagnostic file
    private long   lastBackTime = 0L;
    private long   lastButtonFoundMs = 0L;

    // v9.2: comprehensive diagnostic — every state change, every click, every
    // scroll, every rewind step is appended here from startBot to stopInternal,
    // and the whole timeline goes into the dump file with a periodic snapshot
    // every 15s so a slow loop is recorded as a series of screen states.
    private final StringBuilder diagLog = new StringBuilder(16 * 1024);
    private long   diagLogStartMs = 0L;
    private long   lastSnapshotMs = 0L;
    // v9.4: snapshots used to fire every 15s, which on Mawada's ~4 K-node
    // WebView meant a full keyword scan and screen summary 4x/minute. Push
    // out to 30s — losing one snapshot per minute is invisible in the
    // diagnostics but recovers measurable CPU/GC budget at scale.
    private static final long SNAPSHOT_INTERVAL_MS = 30_000L;

    // v9.4: end-of-list keyword scan used to run on EVERY tick. With ticks
    // at ~250-500 ms and a 4 K-node tree, that was 4-8 full tree walks per
    // second purely for an "are we done?" check that's only meaningful
    // while we're stuck scrolling. Now we run it at most every 20s, and
    // also once whenever we're in MUST_SCROLL (which is where Mawada
    // typically surfaces the end-of-list banner anyway).
    private long lastEndOfListCheckMs = 0L;
    private static final long END_OF_LIST_INTERVAL_MS = 20_000L;
    private int    state = STATE_LOOK_LIKE;
    private long   stateChangedAt = 0L;
    private int    nodesVisitedThisTick = 0;
    // v9.3: separate budget for keyword scans (containsAnyText) so they don't
    // starve findUnprocessedClickable of its budget on huge WebView trees.
    private int    keywordScanThisTick  = 0;

    // v8: stuck detection — instead of counting scrolls, we record when
    // the no-progress streak started. Reset to 0 on every confirmed like.
    private long   firstNoProgressScrollMs = 0L;
    private int    likesAtLastScroll       = 0;
    private int    rewindStepsDone         = 0;
    private int    scrollPatternIndex      = 0; // rotates 0,1,2 within a streak
    // v10.0: confirmed likes since the most recent soft refresh. When this
    // reaches LIKES_PER_SOFT_REFRESH we switch into STATE_SOFT_REFRESH to
    // tap Mawada's own "members" tab and reset its WebView DOM.
    private int    likesSinceRefresh        = 0;

    // v1.11: tracks the member fingerprint we entered the profile for in
    // filter mode, so that on exit (whether we liked or not) we can record
    // the outcome on the right member and avoid re-opening it next round.
    private String pendingProfileFp = null;
    // Bounds key of the card whose profile we are inspecting, used to
    // mark the row as processed in the per-viewport cache once the
    // inspection finishes (so we don't immediately re-pick the same row
    // when we return to the list).
    private String pendingProfileBoundsKey = null;
    // Sub-stages for the filter-mode inspect state:
    //   0 = no like yet (still deciding eligibility)
    //   1 = clicked "إهتمام" on profile, waiting for "نعم"
    //   2 = clicked "نعم",            waiting for "إغلاق"
    //   3 = clicked "إغلاق", sending BACK then transitioning to LOOK
    private int inspectStage = 0;

    // Screen metrics — cached per tick
    private int    screenW = 0;
    private int    screenH = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running.get()) return;
            long nextDelay;
            try {
                nextDelay = performOneTick();
            } catch (Throwable t) {
                nextDelay = Math.max(1000L, config != null ? config.scanIntervalMs : 1000L);
            } finally {
                drainTickPool();
            }
            if (running.get()) {
                handler.postDelayed(this, Math.max(150L, nextDelay));
            }
        }
    };

    public interface StatusListener {
        void onUpdate(String status, int likes, int skipped, String lastAction);
    }

    public static ClickerService getInstance() { return instance; }
    public static boolean isServiceRunning()   { return instance != null; }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        if (db == null) db = new LikedMembersDb(this);
        if (coordsDb == null) coordsDb = new SavedCoordinatesDb(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        running.set(false);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        running.set(false);
        handler.removeCallbacksAndMessages(null);
        drainTickPool();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally a no-op. We poll via the tick handler; reacting to
        // every event makes the service unresponsive on busy screens.
    }

    @Override
    public void onInterrupt() {
        running.set(false);
    }

    public void setStatusListener(StatusListener l) {
        this.listener = l;
        if (l != null) {
            l.onUpdate(running.get() ? STATUS_RUNNING : STATUS_IDLE,
                    likesCount.get(), skippedCount.get(),
                    lastAction == null ? "" : lastAction);
        }
    }

    public boolean isExecuting()     { return running.get(); }
    public String  getLastDumpPath() { return lastDumpPath == null ? "" : lastDumpPath; }
    public int     getLikesCount()   { return likesCount.get(); }
    public int     getSkippedCount() { return skippedCount.get(); }
    public LikedMembersDb getDb()    { return db; }

    public boolean startBot(BotConfig cfg) {
        if (running.get()) return false;
        if (db == null) db = new LikedMembersDb(this);
        this.config = cfg;
        likesCount.set(0);
        skippedCount.set(0);
        lastAction = "";
        processedBounds.clear();
        firstNoProgressScrollMs = 0L;
        likesAtLastScroll = 0;
        rewindStepsDone = 0;
        scrollPatternIndex = 0;
        likesSinceRefresh = 0;
        long now = System.currentTimeMillis();
        lastButtonFoundMs = now;
        // v8: rewind the target list to its true top before searching. Mawadda's
        // RecyclerView recycles rows and drops the "إهتمام" content description
        // on mid-list cards, so starting mid-list found nothing.
        state = STATE_REWIND_TO_TOP;
        stateChangedAt = now;
        diagBegin(now);
        pendingProfileFp = null;
        pendingProfileBoundsKey = null;
        inspectStage = 0;
        diagEvent("startBot pkg=" + (cfg.targetPackage == null ? "*" : cfg.targetPackage)
                + " idleTimeoutMs=" + cfg.idleTimeoutMs
                + " scanIntervalMs=" + cfg.scanIntervalMs
                + " popupWaitMs=" + cfg.popupWaitMs
                + " likeText='" + cfg.likeText + "'"
                + " yesText='" + cfg.yesText + "'"
                + " dedup=" + cfg.dedupEnabled
                + " filterMode=" + (cfg.filterDivorcedWidowedOnly ? "DIV_WID_ONLY" : "ALL")
                + " refreshMode=" + (cfg.refreshContinueMode ? "CONTINUE" : "RESTART"));
        running.set(true);
        notifyUpdate(STATUS_RUNNING);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(tick, 500L);
        return true;
    }

    public void stopBot() { stopInternal(STATUS_STOPPED, getString(R.string.stop_reason_manual)); }

    /**
     * @param status  the public status string shown beside "حالة التنفيذ"
     * @param reason  a longer human-readable explanation shown beside "آخر إجراء",
     *                so the user never sees a silent stop again.
     */
    private void stopInternal(String status, String reason) {
        if (!running.get()) return;
        running.set(false);
        handler.removeCallbacksAndMessages(null);
        if (reason != null && !reason.isEmpty()) lastAction = reason;
        vibrateAlert();
        notifyUpdate(status);
    }

    private void transitionTo(int newState) {
        int oldState = this.state;
        this.state = newState;
        this.stateChangedAt = System.currentTimeMillis();
        if (oldState != newState) diagEvent("transition " + stateName(oldState) + " -> " + stateName(newState));
    }

    private static String stateName(int s) {
        switch (s) {
            case STATE_REWIND_TO_TOP:   return "REWIND";
            case STATE_SOFT_REFRESH:    return "REFRESH";
            case STATE_NAV_ONLINE_NOW:  return "NAV_ONLINE";
            case STATE_LOOK_LIKE:       return "LOOK";
            case STATE_AFTER_LIKE:      return "AFTER_LIKE";
            case STATE_AFTER_YES:       return "AFTER_YES";
            case STATE_MUST_SCROLL:     return "SCROLL";
            case STATE_INSPECT_PROFILE: return "INSPECT";
            case STATE_REFRESH_FIND_SEARCH_1: return "REFRESH_SEARCH_1";
            case STATE_REFRESH_FIND_SEARCH_2: return "REFRESH_SEARCH_2";
            default:                    return "?(" + s + ")";
        }
    }

    private void diagBegin(long now) {
        diagLog.setLength(0);
        diagLogStartMs = now;
        lastSnapshotMs = now;
        diagLog.append("=== Mokafeefah Diagnostic Log ===\n")
               .append("session start: ").append(new Date(now).toString()).append("\n")
               .append("\n=== Event Timeline ===\n");
    }

    private void diagEvent(String event) {
        if (diagLogStartMs == 0L) return;
        long elapsed = System.currentTimeMillis() - diagLogStartMs;
        // v9.4: avoid String.format() per event. Profile data from a 240-like
        // session showed diagEvent was called ~10x per like (state changes,
        // clicks, scrolls); String.format allocates several short-lived
        // String + char[] objects each time, adding measurable GC pressure
        // that compounded after 90+ likes. Direct StringBuilder append
        // is ~5x cheaper and produces the same output.
        diagLog.append(elapsed).append(" ms | s=").append(stateName(state))
               .append(" | likes=").append(likesCount.get())
               .append(" skip=").append(skippedCount.get())
               .append(" | ").append(event).append('\n');
        // Aggressively trim. The previous 256 KB cap meant a long session
        // could keep ~250 KB of log text live in the heap, fragmenting the
        // young generation and triggering more frequent GCs.
        if (diagLog.length() > 64 * 1024) {
            diagLog.delete(0, diagLog.length() / 2);
        }
    }

    private void diagMaybeSnapshot(long now, List<AccessibilityNodeInfo> roots) {
        if (diagLogStartMs == 0L) return;
        if (now - lastSnapshotMs < SNAPSHOT_INTERVAL_MS) return;
        lastSnapshotMs = now;
        String summary;
        try { summary = buildScreenSummary(roots); }
        catch (Throwable t) { summary = "(snapshot failed)"; }
        long elapsed = now - diagLogStartMs;
        // v9.4: same allocation reason as diagEvent — append parts directly.
        diagLog.append('\n').append("--- Snapshot @ ").append(elapsed)
               .append(" ms | state=").append(stateName(state))
               .append(" ---\n").append(summary).append('\n').append('\n');
    }

    // ============================================================
    //                         TICK / STATE MACHINE
    // ============================================================

    private long performOneTick() {
        long now = System.currentTimeMillis();
        nodesVisitedThisTick = 0;
        keywordScanThisTick = 0;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        List<AccessibilityNodeInfo> roots = collectAllRoots();
        if (roots.isEmpty()) {
            setAction(getString(R.string.action_wait));
            return config.scanIntervalMs;
        }

        diagMaybeSnapshot(now, roots);

        // v9: idle-timeout check is now after roots are collected so the
        // diagnostic dump has data to record. If we're idle, the dump tells
        // us exactly what's on screen at the moment of failure.
        if (now - lastButtonFoundMs > config.idleTimeoutMs) {
            diagEvent("AUTO-STOP: idle timeout fired (no buttons for "
                    + (now - lastButtonFoundMs) + "ms, limit=" + config.idleTimeoutMs + ")");
            autoStopWithDiagnostic(STATUS_AUTO_STOPPED,
                    getString(R.string.stop_reason_idle), roots);
            return config.scanIntervalMs;
        }

        // v8: detect Mawadda's "no more members" message before doing anything
        // else this tick, so the user sees a clear stop reason instead of a
        // silent timeout.
        // v9.4: throttle this. Running a full tree scan every tick added
        // ~50-100 ms per tick on Mawadda's ~4 K-node WebView, which is the
        // dominant source of the "throughput drops from 5/min to 3/min
        // after 90 likes" complaint. The check is only meaningful when
        // pagination is stuck — so check it on every MUST_SCROLL tick (free
        // signal that pagination just failed) plus a 20 s periodic poll.
        boolean shouldCheckEol =
                state == STATE_MUST_SCROLL
             || (now - lastEndOfListCheckMs > END_OF_LIST_INTERVAL_MS);
        if (state != STATE_REWIND_TO_TOP && shouldCheckEol) {
            lastEndOfListCheckMs = now;
            if (endOfListVisible(roots)) {
                diagEvent("AUTO-STOP: end-of-list message detected on screen");
                autoStopWithDiagnostic(STATUS_AUTO_STOPPED,
                        getString(R.string.stop_reason_end_of_list), roots);
                return config.scanIntervalMs;
            }
        }

        if (config.targetPackage != null && !config.targetPackage.isEmpty()) {
            boolean matches = false;
            for (AccessibilityNodeInfo r : roots) {
                CharSequence pkg = r.getPackageName();
                if (pkg != null && config.targetPackage.equals(pkg.toString())) {
                    matches = true; break;
                }
            }
            if (!matches) {
                setAction(getString(R.string.action_wait));
                return config.scanIntervalMs;
            }
        }

        // v1.11: in filter mode we INTENTIONALLY navigate into profile
        // pages, so the auto-back guard must NOT fire while we're in the
        // inspect state — that's the bot's own work, not a wrong tap.
        if (state != STATE_INSPECT_PROFILE
                && now - lastBackTime > 1500L
                && !config.profileKeywords.isEmpty()) {
            if (anyKeywordVisible(roots, config.profileKeywords)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                lastBackTime = now;
                setAction(getString(R.string.action_back));
                transitionTo(STATE_LOOK_LIKE);
                return POST_BACK_WAIT_MS;
            }
        }

        switch (state) {
            case STATE_REWIND_TO_TOP:   return handleRewindToTop(roots, now);
            case STATE_LOOK_LIKE:       return handleLookLike(roots, now);
            case STATE_AFTER_LIKE:      return handleAfterLike(roots, now);
            case STATE_AFTER_YES:       return handleAfterYes(roots, now);
            case STATE_MUST_SCROLL:     return handleMustScroll(roots, now);
            case STATE_SOFT_REFRESH:    return handleSoftRefresh(roots, now);
            case STATE_NAV_ONLINE_NOW:  return handleNavOnlineNow(roots, now);
            case STATE_INSPECT_PROFILE: return handleInspectProfile(roots, now);
            case STATE_REFRESH_FIND_SEARCH_1: return handleRefreshFindSearch(roots, now, 1);
            case STATE_REFRESH_FIND_SEARCH_2: return handleRefreshFindSearch(roots, now, 2);
            default:
                transitionTo(STATE_LOOK_LIKE);
                return config.scanIntervalMs;
        }
    }

    // ============================================================
    //                          STATE HANDLERS
    // ============================================================

    private long handleLookLike(List<AccessibilityNodeInfo> roots, long now) {
        // v10.0: periodic soft refresh of Mawada's search page. Triggered
        // here (top of LOOK_LIKE, when we're about to scan for a new like
        // button) so it never fires mid-popup. After ~80 likes Mawada's
        // WebView DOM has bloated to 2-3x its initial size and per-cycle
        // latency starts climbing — tapping the members tab forces a
        // re-render, the tree drops back to ~1.3 K nodes, and the next
        // 80 likes run at early-session speed.
        if (likesSinceRefresh >= LIKES_PER_SOFT_REFRESH) {
            diagEvent("LOOK: triggering soft refresh after "
                    + likesSinceRefresh + " likes since last refresh");
            transitionTo(STATE_SOFT_REFRESH);
            return 100L;
        }
        AccessibilityNodeInfo yes = findClickableInAll(roots, config.yesText);
        if (yes != null && performClick(yes)) {
            setAction(getString(R.string.action_yes));
            lastButtonFoundMs = now;
            transitionTo(STATE_AFTER_YES);
            return 700L;
        }
        AccessibilityNodeInfo close = findClickableInAll(roots, config.closeText);
        if (close != null && performClick(close)) {
            setAction(getString(R.string.action_close));
            transitionTo(STATE_LOOK_LIKE);
            return 800L;
        }

        AccessibilityNodeInfo likeBtn = findUnprocessedClickable(roots, config.likeText);
        if (likeBtn != null) {
            String key = boundsKey(likeBtn);

            String fp = fingerprintMemberFromButton(likeBtn);
            if (config.dedupEnabled && fp != null && db != null && db.isLiked(fp)) {
                processedBounds.add(key);
                skippedCount.incrementAndGet();
                diagEvent("LOOK: like-btn at " + key + " skipped (already liked)");
                setAction(getString(R.string.action_skip));
                lastButtonFoundMs = now;
                return Math.max(150L, config.scanIntervalMs / 2);
            }

            // v1.11: filter mode — don't tap the like button directly.
            // Open the member's profile, verify marital status, then
            // either like or skip and back out.
            if (config.filterDivorcedWidowedOnly) {
                if (fp != null && db != null && db.isInspected(fp)) {
                    processedBounds.add(key);
                    skippedCount.incrementAndGet();
                    diagEvent("LOOK[FILTER]: member at " + key + " skipped (inspected before, not eligible)");
                    setAction(getString(R.string.action_skip_inspected));
                    lastButtonFoundMs = now;
                    return Math.max(150L, config.scanIntervalMs / 2);
                }
                AccessibilityNodeInfo openTarget = findProfileOpenTarget(likeBtn);
                if (openTarget == null) {
                    // No safe tap target found on the card — skip this row,
                    // mark its bounds processed so we don't loop on it.
                    processedBounds.add(key);
                    diagEvent("LOOK[FILTER]: no profile-open target on card at " + key + ", skipping");
                    skippedCount.incrementAndGet();
                    setAction(getString(R.string.action_skip));
                    return Math.max(150L, config.scanIntervalMs / 2);
                }
                if (performClick(openTarget)) {
                    processedBounds.add(key);
                    pendingProfileFp = fp;
                    pendingProfileBoundsKey = key;
                    inspectStage = 0;
                    diagEvent("LOOK[FILTER]: opened profile from card at " + key);
                    setAction(getString(R.string.action_inspect_open));
                    lastButtonFoundMs = now;
                    transitionTo(STATE_INSPECT_PROFILE);
                    return 700L;
                }
                diagEvent("LOOK[FILTER]: open-profile click FAILED at " + key);
                return config.scanIntervalMs;
            }

            if (performClick(likeBtn)) {
                processedBounds.add(key);
                likesCount.incrementAndGet();
                likesSinceRefresh++;
                if (fp != null && db != null) db.markLiked(fp);
                diagEvent("LOOK: clicked like-btn at " + key + " (likes now "
                        + likesCount.get() + ") fp="
                        + (fp == null ? "NULL" : fp.substring(0, Math.min(12, fp.length()))));
                setAction(getString(R.string.action_like));
                lastButtonFoundMs = now;
                firstNoProgressScrollMs = 0L;
                scrollPatternIndex = 0;
                transitionTo(STATE_AFTER_LIKE);
                // v10.0: shorter post-click delay. The previous popupWaitMs
                // (1500 ms) was a blind sleep before we'd even start looking
                // for "نعم"; the popup typically appears in 200-400 ms so
                // we were wasting ~1 s on every successful like. Now we
                // wait just long enough for the tap to register, then start
                // polling at the (now-tighter) scanIntervalMs.
                return 300L;
            }
            diagEvent("LOOK: like-btn at " + key + " click FAILED");
            return config.scanIntervalMs;
        }

        diagEvent("LOOK: no like button found, transitioning to SCROLL"
                + " (processedBounds=" + processedBounds.size() + ")");
        transitionTo(STATE_MUST_SCROLL);
        return 300L;
    }

    /**
     * v1.11 — filter-mode inspector. Three possible paths through this
     * state:
     *
     *   A) "الملف الشخصي" popup is on screen
     *      → member is ALREADY in our liked list (Mawada showed the
     *        short "already added" card). Tap "إغلاق" → LOOK_LIKE.
     *        We also markInspected so we don't re-enter on next refresh.
     *
     *   B) Marital-status label is visible and the value is مطلقة / أرملة
     *      → eligible. Find the "إهتمام" button on this profile, tap it,
     *        let the popup flow run (AFTER_LIKE / AFTER_YES handle the
     *        rest), then markLiked.
     *
     *   C) Marital-status label is visible and value is anything else
     *      → ineligible. Press BACK to return to the list, markInspected.
     *
     *   D) Neither condition met yet → wait, up to a budget.
     */
    private long handleInspectProfile(List<AccessibilityNodeInfo> roots, long now) {
        switch (inspectStage) {
            case 1: return inspectStageWaitForYes(roots, now);
            case 2: return inspectStageWaitForCloseAfterYes(roots, now);
            case 3: return inspectStageExitToList(now);
            default: return inspectStageDecide(roots, now);
        }
    }

    /**
     * Stage 0 — we are inside a freshly-opened profile and must decide
     * what to do.
     *   • "الملف الشخصي" indicator visible → "already added" popup → close.
     *   • Marital-status label visible AND value is مطلقة / أرملة → like.
     *   • Marital-status label visible AND value is NOT eligible → BACK.
     *   • None of the above visible yet → wait, up to a budget.
     */
    private long inspectStageDecide(List<AccessibilityNodeInfo> roots, long now) {
        if (containsAnyKeyword(roots, PROFILE_ALREADY_ADDED_HINTS)) {
            AccessibilityNodeInfo close = findClickableInAll(roots, config.closeText);
            if (close != null && performClick(close)) {
                diagEvent("INSPECT: 'already added' popup, clicked close");
                setAction(getString(R.string.action_inspect_already));
                if (pendingProfileFp != null && db != null) db.markInspected(pendingProfileFp);
                skippedCount.incrementAndGet();
                clearInspectState();
                transitionTo(STATE_LOOK_LIKE);
                return 700L;
            }
            if (now - stateChangedAt > MAX_WAIT_IN_INSPECT_MS) {
                diagEvent("INSPECT: 'already added' detected but no close button; BACK");
                performGlobalAction(GLOBAL_ACTION_BACK);
                lastBackTime = now;
                if (pendingProfileFp != null && db != null) db.markInspected(pendingProfileFp);
                skippedCount.incrementAndGet();
                clearInspectState();
                transitionTo(STATE_LOOK_LIKE);
                return POST_BACK_WAIT_MS;
            }
            setAction(getString(R.string.action_inspect_loading));
            return config.scanIntervalMs;
        }

        boolean profileReady = containsAnyKeyword(roots, MARITAL_LABEL_HINTS);
        if (!profileReady) {
            if (now - stateChangedAt > MAX_WAIT_FOR_PROFILE_LOAD_MS) {
                diagEvent("INSPECT: profile didn't load within budget, BACK + markInspected");
                performGlobalAction(GLOBAL_ACTION_BACK);
                lastBackTime = now;
                // v1.12.6: mark the member as inspected even though we
                // couldn't read their marital status. Without this, the
                // very next LOOK_LIKE picks the same member, opens their
                // profile, hits the same load-timeout, sends BACK, and
                // the bot loops forever between list and one slow
                // profile — exactly the "enter / exit / enter / exit"
                // symptom the user reported.
                if (pendingProfileFp != null && db != null) {
                    db.markInspected(pendingProfileFp);
                }
                skippedCount.incrementAndGet();
                clearInspectState();
                transitionTo(STATE_LOOK_LIKE);
                return POST_BACK_WAIT_MS;
            }
            setAction(getString(R.string.action_inspect_loading));
            return config.scanIntervalMs;
        }

        boolean eligible = containsAnyKeyword(roots, ELIGIBLE_MARITAL_VALUES);
        if (!eligible) {
            diagEvent("INSPECT: not eligible (not مطلقة/أرملة), BACK");
            performGlobalAction(GLOBAL_ACTION_BACK);
            lastBackTime = now;
            setAction(getString(R.string.action_inspect_not_eligible));
            if (pendingProfileFp != null && db != null) db.markInspected(pendingProfileFp);
            skippedCount.incrementAndGet();
            clearInspectState();
            transitionTo(STATE_LOOK_LIKE);
            return POST_BACK_WAIT_MS;
        }

        AccessibilityNodeInfo likeBtn = findClickableInAll(roots, config.likeText);
        if (likeBtn != null && performClick(likeBtn)) {
            diagEvent("INSPECT: eligible (مطلقة/أرملة), clicked like on profile");
            setAction(getString(R.string.action_inspect_eligible));
            inspectStage = 1;
            stateChangedAt = now;
            lastButtonFoundMs = now;
            return 700L;
        }
        if (now - stateChangedAt > MAX_WAIT_IN_INSPECT_MS) {
            diagEvent("INSPECT: eligible but like button not found, BACK");
            performGlobalAction(GLOBAL_ACTION_BACK);
            lastBackTime = now;
            if (pendingProfileFp != null && db != null) db.markInspected(pendingProfileFp);
            skippedCount.incrementAndGet();
            clearInspectState();
            transitionTo(STATE_LOOK_LIKE);
            return POST_BACK_WAIT_MS;
        }
        return config.scanIntervalMs;
    }

    /** Stage 1 — after clicking like on the profile, wait for "نعم" popup. */
    private long inspectStageWaitForYes(List<AccessibilityNodeInfo> roots, long now) {
        AccessibilityNodeInfo yes = findClickableInAll(roots, config.yesText);
        if (yes != null && performClick(yes)) {
            diagEvent("INSPECT: clicked '" + config.yesText + "' on profile");
            setAction(getString(R.string.action_yes));
            // The like is committed at this point — record it.
            likesCount.incrementAndGet();
            likesSinceRefresh++;
            if (pendingProfileFp != null && db != null) db.markLiked(pendingProfileFp);
            inspectStage = 2;
            stateChangedAt = now;
            lastButtonFoundMs = now;
            return 700L;
        }
        if (now - stateChangedAt > MAX_WAIT_IN_INSPECT_MS) {
            diagEvent("INSPECT: timeout waiting for yes, BACK + markInspected");
            performGlobalAction(GLOBAL_ACTION_BACK);
            lastBackTime = now;
            // v1.12.6: same defence as stage 0. If we clicked like on
            // the profile but "نعم" never appeared, the like didn't
            // commit and we'd re-pick this member next loop. Mark
            // inspected so we move on to a different candidate.
            if (pendingProfileFp != null && db != null) {
                db.markInspected(pendingProfileFp);
            }
            clearInspectState();
            transitionTo(STATE_LOOK_LIKE);
            return POST_BACK_WAIT_MS;
        }
        return config.scanIntervalMs;
    }

    /** Stage 2 — after "نعم", look for "إغلاق" on success popup. */
    private long inspectStageWaitForCloseAfterYes(List<AccessibilityNodeInfo> roots, long now) {
        AccessibilityNodeInfo close = findClickableInAll(roots, config.closeText);
        if (close != null && performClick(close)) {
            diagEvent("INSPECT: clicked close after like");
            setAction(getString(R.string.action_inspect_liked));
            inspectStage = 3;
            stateChangedAt = now;
            lastButtonFoundMs = now;
            return 700L;
        }
        if (now - stateChangedAt > MAX_WAIT_IN_INSPECT_MS) {
            diagEvent("INSPECT: timeout waiting for close, sending BACK");
            inspectStage = 3;
            stateChangedAt = now;
            return 300L;
        }
        return config.scanIntervalMs;
    }

    /** Stage 3 — send a BACK to exit the profile, return to the list. */
    private long inspectStageExitToList(long now) {
        performGlobalAction(GLOBAL_ACTION_BACK);
        lastBackTime = now;
        setAction(getString(R.string.action_back));
        clearInspectState();
        transitionTo(STATE_LOOK_LIKE);
        return POST_BACK_WAIT_MS;
    }

    private void clearInspectState() {
        pendingProfileFp = null;
        pendingProfileBoundsKey = null;
        inspectStage = 0;
    }

    /**
     * v1.11 — used by filter mode to find the right node to TAP on a
     * member card to open their profile. Strategy:
     *   1. Climb to the member card from the like button.
     *   2. Walk the card looking for a clickable that is NOT the like
     *      button itself (and not its parent). The card's "name row" is
     *      usually clickable and opens the profile.
     *   3. Falls back to the card itself if no other clickable exists.
     */
    private AccessibilityNodeInfo findProfileOpenTarget(AccessibilityNodeInfo likeBtn) {
        if (likeBtn == null) return null;
        AccessibilityNodeInfo card = climbToMemberCard(likeBtn);
        if (card == null) return null;
        Rect btnR = new Rect();
        likeBtn.getBoundsInScreen(btnR);
        AccessibilityNodeInfo found = findClickableInsideExcept(card, btnR, 0);
        if (found != null) return found;
        // Fallback: the card itself if it's clickable.
        if (card.isClickable() && card.isEnabled() && card.isVisibleToUser()) return card;
        return null;
    }

    private AccessibilityNodeInfo findClickableInsideExcept(
            AccessibilityNodeInfo node, Rect excludeBtnBounds, int depth) {
        if (node == null || depth > CARD_TEXT_DEPTH) return null;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return null;
        // Skip nodes whose bounds are essentially the like-button bounds.
        boolean isLikeBtnRect =
                Math.abs(r.left - excludeBtnBounds.left) < 8 &&
                Math.abs(r.top - excludeBtnBounds.top) < 8 &&
                Math.abs(r.right - excludeBtnBounds.right) < 8 &&
                Math.abs(r.bottom - excludeBtnBounds.bottom) < 8;
        if (!isLikeBtnRect && node.isClickable() && node.isEnabled() && node.isVisibleToUser()) {
            return node;
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            AccessibilityNodeInfo found = findClickableInsideExcept(child, excludeBtnBounds, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private boolean containsAnyKeyword(List<AccessibilityNodeInfo> roots, String[] keywords) {
        List<String> needles = new ArrayList<>(keywords.length);
        for (String k : keywords) {
            if (k == null) continue;
            String t = normalizeArabic(k.trim());
            if (!t.isEmpty()) needles.add(t);
        }
        if (needles.isEmpty()) return false;
        for (AccessibilityNodeInfo root : roots) {
            if (containsAnyText(root, needles, 0)) return true;
        }
        return false;
    }

    private long handleAfterLike(List<AccessibilityNodeInfo> roots, long now) {
        AccessibilityNodeInfo yes = findClickableInAll(roots, config.yesText);
        if (yes != null) {
            if (performClick(yes)) {
                diagEvent("AFTER_LIKE: clicked '" + config.yesText + "' (confirm yes)");
                setAction(getString(R.string.action_yes));
                lastButtonFoundMs = now;
                transitionTo(STATE_AFTER_YES);
                return 700L;
            }
            diagEvent("AFTER_LIKE: '" + config.yesText + "' click FAILED");
            return config.scanIntervalMs;
        }
        AccessibilityNodeInfo close = findClickableInAll(roots, config.closeText);
        if (close != null) {
            if (performClick(close)) {
                setAction(getString(R.string.action_close));
                transitionTo(STATE_LOOK_LIKE);
                return 800L;
            }
            return config.scanIntervalMs;
        }
        if (now - stateChangedAt > MAX_WAIT_AFTER_LIKE_MS) {
            transitionTo(STATE_LOOK_LIKE);
            return 300L;
        }
        setAction(getString(R.string.action_wait));
        return config.scanIntervalMs;
    }

    private long handleAfterYes(List<AccessibilityNodeInfo> roots, long now) {
        AccessibilityNodeInfo close = findClickableInAll(roots, config.closeText);
        if (close != null) {
            if (performClick(close)) {
                setAction(getString(R.string.action_close));
                transitionTo(STATE_LOOK_LIKE);
                return 800L;
            }
            return config.scanIntervalMs;
        }
        if (now - stateChangedAt > MAX_WAIT_AFTER_YES_MS) {
            transitionTo(STATE_LOOK_LIKE);
            return 300L;
        }
        setAction(getString(R.string.action_wait));
        return config.scanIntervalMs;
    }

    /**
     * v10.0 — periodic soft refresh of Mawada's search-results page.
     *
     * Once every {@link #LIKES_PER_SOFT_REFRESH} confirmed likes we tap
     * Mawada's own bottom-bar "الأعضاء" tab. That makes Mawada re-render
     * the search page; the WebView accessibility DOM drops from ~6.5 K
     * back to ~1.3 K nodes and per-cycle latency snaps back to early-
     * session values. The user's filters are preserved because we're
     * navigating WITHIN Mawada, not restarting it.
     *
     * Two-phase implementation:
     *   1. Find a clickable matching MEMBERS_TAB_KEYWORDS via the native
     *      text-search API (fast even on a bloated tree). Tap it.
     *      Wait SOFT_REFRESH_WAIT_MS for the new page to paint.
     *   2. Walk straight into STATE_REWIND_TO_TOP so the new page is
     *      definitely positioned at row #1, then resume LOOK_LIKE.
     *
     * If the members tab isn't visible (e.g. Mawada is showing a popup
     * we left behind), fall back to GLOBAL_ACTION_BACK once, then retry.
     * If even that doesn't surface the tab, give up and resume LOOK_LIKE
     * without a refresh — better to keep liking slowly than to halt.
     */
    private long handleSoftRefresh(List<AccessibilityNodeInfo> roots, long now) {
        // v1.12: branch on the user's find-members mode. The default
        // ('online') keeps the existing الأعضاء ← المتواجدون الآن path;
        // 'three_dots' taps the saved menu coordinate and then matches
        // 'بحث' by text twice.
        if ("three_dots".equals(config.findMembersMode)) {
            return startThreeDotsRefresh(now);
        }
        AccessibilityNodeInfo tab = null;
        for (String kw : MEMBERS_TAB_KEYWORDS) {
            tab = findClickableInAll(roots, kw);
            if (tab != null) break;
        }
        if (tab != null && performClick(tab)) {
            diagEvent("REFRESH: tapped الأعضاء tab, navigating to المتواجدون الآن");
            setAction(getString(R.string.action_soft_refresh));
            likesSinceRefresh = 0;
            // Clear our per-viewport processed-bounds cache since after the
            // refresh Mawada will re-render different rows at the same
            // screen coordinates as the freshly-loaded list.
            processedBounds.clear();
            // v1.11: the user's spec requires the path الأعضاء ← المتواجدون
            // الآن. Move to a sub-state that waits then taps the second tab.
            transitionTo(STATE_NAV_ONLINE_NOW);
            return SOFT_REFRESH_WAIT_MS;
        }
        // Tab not visible — probably a popup is in the way. One BACK press
        // usually clears stray dialogs without harming the search list.
        long sinceState = now - stateChangedAt;
        if (sinceState < 4000L) {
            diagEvent("REFRESH: members tab not visible, sending BACK");
            performGlobalAction(GLOBAL_ACTION_BACK);
            lastBackTime = now;
            setAction(getString(R.string.action_back));
            return POST_BACK_WAIT_MS;
        }
        // Tab still not findable after a back press — skip the refresh
        // this round so we don't get stuck. Try again in another 80 likes.
        diagEvent("REFRESH: tab still not found, skipping refresh this round");
        likesSinceRefresh = 0;
        transitionTo(STATE_LOOK_LIKE);
        return 300L;
    }

    /**
     * v1.11 — after tapping الأعضاء, try to also tap المتواجدون الآن so
     * the bot lands on the "currently-online" sub-list as the user
     * requested. After this completes, behaviour branches on the user's
     * "after refresh" setting:
     *   • refreshContinueMode = false (default)  → rewind to top.
     *   • refreshContinueMode = true             → resume in place (dedup
     *     will skip already-liked members and the bot keeps going).
     */
    private long handleNavOnlineNow(List<AccessibilityNodeInfo> roots, long now) {
        AccessibilityNodeInfo onlineTab = null;
        for (String kw : ONLINE_NOW_KEYWORDS) {
            onlineTab = findClickableInAll(roots, kw);
            if (onlineTab != null) break;
        }
        if (onlineTab != null && performClick(onlineTab)) {
            diagEvent("NAV_ONLINE: tapped المتواجدون الآن");
            setAction(getString(R.string.action_nav_online));
            // Land on the active-members list. Now branch on continue mode.
            return finishNavOnlineNow(now);
        }
        // Couldn't find it — wait a short while in case the page is still
        // rendering, then give up and resume.
        if (now - stateChangedAt < NAV_ONLINE_WAIT_MS * 2) {
            setAction(getString(R.string.action_inspect_loading));
            return NAV_ONLINE_WAIT_MS;
        }
        diagEvent("NAV_ONLINE: المتواجدون الآن not visible, continuing without sub-tab tap");
        return finishNavOnlineNow(now);
    }

    private long finishNavOnlineNow(long now) {
        processedBounds.clear();
        if (config.refreshContinueMode) {
            // Resume in place. Dedup (if on) handles skipping already-liked
            // members as the bot scans down again.
            diagEvent("NAV_ONLINE: refreshContinueMode=ON, resuming LOOK_LIKE");
            transitionTo(STATE_LOOK_LIKE);
            return 600L;
        }
        // Restart from top: rewind back to row #1 of the fresh list.
        diagEvent("NAV_ONLINE: refreshContinueMode=OFF, rewinding to top");
        rewindStepsDone = 0;
        transitionTo(STATE_REWIND_TO_TOP);
        return 600L;
    }

    /**
     * v1.12 — three-dots refresh entry. Looks up the saved 'ثلاث نقاط'
     * coordinate and dispatches a gesture tap. The menu button has empty
     * contentDescription in Mawada, so a text-based lookup never finds
     * it; that's exactly why we record the coordinate up front.
     *
     * If the coord isn't saved (e.g. user wiped it without re-seeding)
     * we fall back to the existing online path so the bot keeps making
     * progress instead of stalling.
     */
    private long startThreeDotsRefresh(long now) {
        SavedCoordinatesDb.Coord coord =
                coordsDb == null ? null : coordsDb.findByName(COORD_THREE_DOTS);
        if (coord == null) {
            diagEvent("REFRESH(3-dots): saved coord missing, falling back to online path");
            // Force the rest of this tick down the online branch by
            // temporarily mutating the mode? No — just delegate.
            return fallbackOnlinePath(now);
        }
        boolean ok = tapAt(coord.x, coord.y);
        if (!ok) {
            diagEvent("REFRESH(3-dots): tapAt failed at (" + coord.x + "," + coord.y + ")");
            likesSinceRefresh = 0;
            transitionTo(STATE_LOOK_LIKE);
            return 300L;
        }
        diagEvent("REFRESH(3-dots): tapped ثلاث نقاط at (" + coord.x + "," + coord.y + ")");
        setAction(getString(R.string.action_three_dots_tap));
        likesSinceRefresh = 0;
        processedBounds.clear();
        transitionTo(STATE_REFRESH_FIND_SEARCH_1);
        return SOFT_REFRESH_WAIT_MS;
    }

    /**
     * Used when three-dots mode is selected but no coord is saved.
     * Re-runs the online branch of handleSoftRefresh manually so we
     * still consume this tick productively.
     */
    private long fallbackOnlinePath(long now) {
        // We're already inside handleSoftRefresh's scope; emit one BACK
        // so a popup can't block us, then come back next tick in the
        // online branch by clearing the find-members override locally.
        likesSinceRefresh = 0;
        transitionTo(STATE_SOFT_REFRESH);
        // Force one tick under the online branch by toggling: we can't
        // mutate config, but the caller will re-enter handleSoftRefresh
        // and we'll branch on findMembersMode again — same outcome
        // (still three_dots, still missing). To avoid an infinite loop,
        // resume LOOK_LIKE directly.
        transitionTo(STATE_LOOK_LIKE);
        return 600L;
    }

    /**
     * v1.12 — find the 'بحث' button by text and tap it. Used twice in
     * the three-dots refresh:
     *   attempt 1 → tap بحث in the menu opened by the three-dots tap
     *   attempt 2 → tap بحث again on the search form (submit)
     * After attempt 2 the bot is back on a fresh search-results list
     * and finishNavOnlineNow() applies the same continue/restart logic
     * the online path uses, so refreshContinueMode keeps working.
     */
    private long handleRefreshFindSearch(List<AccessibilityNodeInfo> roots, long now, int attempt) {
        // v1.12.10 — replace the native-search-driven picker with a
        // DFS-based one that walks the tree ourselves, normalises every
        // text / contentDescription, and ranks candidates. The native
        // findAccessibilityNodeInfosByText API was returning hits that
        // didn't include Mawada's tatweel-laden 'بـحـث' submit button
        // even though it was clearly visible in the dump — most likely
        // a WebView accessibility-cache quirk. DFS avoids the issue
        // entirely.
        AccessibilityNodeInfo searchNode = findSearchSubmitByDfs(roots, "بحث");
        if (searchNode == null) {
            // Last resort: fall back to the older native lookups so we
            // never regress on screens where DFS missed something.
            for (String kw : SEARCH_KEYWORDS) {
                searchNode = findExactClickableInAll(roots, kw);
                if (searchNode != null) break;
            }
            if (searchNode == null) {
                for (String kw : SEARCH_KEYWORDS) {
                    searchNode = findShortestClickableContaining(roots, kw);
                    if (searchNode != null) break;
                }
            }
            if (searchNode == null) {
                for (String kw : SEARCH_KEYWORDS) {
                    searchNode = findClickableInAll(roots, kw);
                    if (searchNode != null) break;
                }
            }
        }
        if (searchNode != null) {
            CharSequence picked = searchNode.getText();
            if (picked == null) picked = searchNode.getContentDescription();
            diagEvent("REFRESH(3-dots) attempt " + attempt
                    + ": chose search candidate label='" + picked + "'");
        }
        if (searchNode != null && performClick(searchNode)) {
            diagEvent("REFRESH(3-dots): tapped بحث (attempt " + attempt + ")");
            setAction(getString(R.string.action_search_tap));
            if (attempt == 1) {
                transitionTo(STATE_REFRESH_FIND_SEARCH_2);
                return SOFT_REFRESH_WAIT_MS;
            }
            // attempt 2 done — reuse the existing finish logic so
            // refreshContinueMode (continue vs. restart) still applies.
            return finishNavOnlineNow(now);
        }
        // Not visible yet — wait briefly in case the screen is still
        // rendering. Give up after ~3× the normal wait so we don't
        // stall forever on a popup the user dismissed already.
        long sinceState = now - stateChangedAt;
        if (sinceState < SOFT_REFRESH_WAIT_MS * 3) {
            setAction(getString(R.string.action_inspect_loading));
            return SOFT_REFRESH_WAIT_MS;
        }
        diagEvent("REFRESH(3-dots): بحث not found at attempt " + attempt + ", resuming");
        transitionTo(STATE_LOOK_LIKE);
        return 300L;
    }

    /**
     * v8 — rewinds the target list to its true top before the first search.
     * Mawadda's RecyclerView recycles row views and the "إهتمام" content
     * description on recycled rows isn't always reattached; starting mid-list
     * left the bot finding nothing. After this state finishes the bot enters
     * STATE_LOOK_LIKE in a known-good state, the same state where the user
     * has empirically proved liking works.
     */
    private long handleRewindToTop(List<AccessibilityNodeInfo> roots, long now) {
        if (rewindStepsDone >= MAX_REWIND_STEPS) {
            diagEvent("REWIND: max steps reached, transitioning to LOOK");
            setAction(getString(R.string.action_rewind_done));
            transitionTo(STATE_LOOK_LIKE);
            return 350L;
        }
        boolean acted = false;
        AccessibilityNodeInfo scrollable = findScrollableInAll(roots);
        boolean haveScrollable = scrollable != null;
        if (scrollable != null) {
            try {
                if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                    acted = true;
                }
            } catch (Throwable ignore) {}
        }
        boolean gestured = performGestureSwipeDown();
        diagEvent("REWIND step " + (rewindStepsDone + 1) + "/12: scrollable="
                + haveScrollable + " action=" + acted + " gesture=" + gestured);

        if (!acted && !gestured) {
            setAction(getString(R.string.action_rewind_done));
            transitionTo(STATE_LOOK_LIKE);
            return 350L;
        }

        rewindStepsDone++;
        setAction(getString(R.string.action_rewind_to_top));
        return REWIND_STEP_WAIT_MS;
    }

    /**
     * v8 — scroll forward, with three rotating patterns and a time-based
     * no-progress budget. Also taps a "تحميل المزيد" button if one is visible,
     * before swiping.
     */
    private long handleMustScroll(List<AccessibilityNodeInfo> roots, long now) {
        int curLikes = likesCount.get();
        if (curLikes != likesAtLastScroll) {
            // Real progress since the last scroll — reset the stuck clock.
            firstNoProgressScrollMs = 0L;
            scrollPatternIndex = 0;
        } else {
            // No new likes since we last entered this state. Start (or keep)
            // the no-progress timer; if it exceeds the budget, stop with a
            // clear reason.
            if (firstNoProgressScrollMs == 0L) firstNoProgressScrollMs = now;
            if (now - firstNoProgressScrollMs > NO_PROGRESS_BUDGET_MS) {
                diagEvent("AUTO-STOP: 3-min no-progress budget exhausted");
                autoStopWithDiagnostic(STATUS_AUTO_STOPPED,
                        getString(R.string.stop_reason_no_progress), roots);
                return config.scanIntervalMs;
            }
        }
        likesAtLastScroll = curLikes;

        // v8: prefer a literal "load more" button over a swipe when one is
        // visible — some apps page only via that explicit tap.
        AccessibilityNodeInfo loadMore = findLoadMoreButton(roots);
        if (loadMore != null && performClick(loadMore)) {
            diagEvent("SCROLL: tapped 'load more' button instead of swiping");
            setAction(getString(R.string.action_load_more));
            processedBounds.clear();
            transitionTo(STATE_LOOK_LIKE);
            return POST_SCROLL_WAIT_MS;
        }

        boolean scrolled = performSmartScroll(roots, scrollPatternIndex);
        diagEvent("SCROLL: pattern=" + scrollPatternIndex + " result=" + scrolled
                + " (likesAtLastScroll=" + likesAtLastScroll + ")");
        if (scrolled) setAction(getString(R.string.action_scroll));
        scrollPatternIndex = (scrollPatternIndex + 1) % 3;

        processedBounds.clear();
        transitionTo(STATE_LOOK_LIKE);
        return POST_SCROLL_WAIT_MS;
    }

    // ============================================================
    //                          SCROLLING
    // ============================================================

    /**
     * Always dispatches a real finger gesture (most paginated lists only
     * trigger "load more" on a gesture, not on the accessibility action),
     * and additionally invokes ACTION_SCROLL_FORWARD if a scrollable
     * container is reachable. The pattern index rotates the gesture shape
     * across consecutive scrolls inside a single MUST_SCROLL streak, so a
     * stubborn paginated list eventually gets a velocity / distance it
     * recognises as "user wants more".
     */
    private boolean performSmartScroll(List<AccessibilityNodeInfo> roots, int patternIndex) {
        boolean acted = false;
        AccessibilityNodeInfo scrollable = findScrollableInAll(roots);
        if (scrollable != null) {
            try {
                if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    acted = true;
                }
            } catch (Throwable ignore) {}
        }
        boolean gestured = performGestureSwipeUp(patternIndex);
        return acted || gestured;
    }

    private boolean performGestureSwipeUp(int patternIndex) {
        try {
            int centerX = screenW / 2;
            float startFrac, endFrac;
            long duration;
            switch (patternIndex % 3) {
                case 0: startFrac = 0.82f; endFrac = 0.18f; duration = 450L; break; // standard
                case 1: startFrac = 0.88f; endFrac = 0.12f; duration = 750L; break; // long slow drag
                default: startFrac = 0.92f; endFrac = 0.08f; duration = 320L; break;// fast fling
            }
            int jitter = (int) ((System.currentTimeMillis() % 7) - 3) * 10;
            int startY = (int) (screenH * startFrac);
            int endY   = (int) (screenH * endFrac) + jitter;
            if (endY < (int) (screenH * 0.06f)) endY = (int) (screenH * 0.06f);
            Path path = new Path();
            path.moveTo(centerX, startY);
            path.lineTo(centerX, endY);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, duration);
            GestureDescription gesture =
                    new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Public helper — dispatch a synthetic tap at absolute screen coordinates.
     * Used by MainActivity to test recorded coordinates and by the bot when
     * a target button is unlabeled (empty contentDescription) and therefore
     * unreachable via accessibility-text lookup.
     *
     * The path must contain at least one segment, so we move and then draw
     * a 1-px line — a no-op visually but required by GestureDescription.
     *
     * v1.12.5 — duration extended from 80 ms to 150 ms. 80 ms taps were
     * landing on the right pixel but Mawada's WebView never opened the
     * menu, because some WebView touch handlers treat presses shorter
     * than ~120 ms as accidental brushes and ignore them. 150 ms matches
     * a natural finger tap and is still well below the long-press
     * threshold (~500 ms on Android).
     */
    public boolean tapAt(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            path.lineTo(x + 1, y + 1);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, 150L);
            GestureDescription gesture =
                    new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** v8: gesture used by STATE_REWIND_TO_TOP. Pulls content downward. */
    private boolean performGestureSwipeDown() {
        try {
            int centerX = screenW / 2;
            int startY = (int) (screenH * 0.22f);
            int endY   = (int) (screenH * 0.86f);
            Path path = new Path();
            path.moveTo(centerX, startY);
            path.lineTo(centerX, endY);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, 500L);
            GestureDescription gesture =
                    new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * v8: search the screen for a clickable node whose text/contentDesc
     * matches any of LOAD_MORE_KEYWORDS. Returns null if none found.
     */
    private AccessibilityNodeInfo findLoadMoreButton(List<AccessibilityNodeInfo> roots) {
        for (String kw : LOAD_MORE_KEYWORDS) {
            AccessibilityNodeInfo hit = findClickableInAll(roots, kw);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * v8: returns true if any END_OF_LIST_HINTS phrase is visible anywhere
     * in the current accessibility tree. Used to stop the bot cleanly when
     * the target app explicitly says "you've reached the end".
     */
    private boolean endOfListVisible(List<AccessibilityNodeInfo> roots) {
        List<String> needles = new ArrayList<>(END_OF_LIST_HINTS.length);
        for (String s : END_OF_LIST_HINTS) needles.add(normalizeArabic(s));
        for (AccessibilityNodeInfo root : roots) {
            if (containsAnyText(root, needles, 0)) return true;
        }
        return false;
    }

    // ============================================================
    //                        NODE LOOKUPS
    // ============================================================

    private boolean anyKeywordVisible(List<AccessibilityNodeInfo> roots, List<String> keywords) {
        List<String> needles = new ArrayList<>(keywords.size());
        for (String k : keywords) {
            if (k == null) continue;
            String t = normalizeArabic(k.trim());
            if (!t.isEmpty()) needles.add(t);
        }
        if (needles.isEmpty()) return false;

        for (AccessibilityNodeInfo root : roots) {
            if (containsAnyText(root, needles, 0)) return true;
        }
        return false;
    }

    private boolean containsAnyText(AccessibilityNodeInfo node, List<String> needles, int depth) {
        if (node == null || depth > MAX_TREE_DEPTH) return false;
        // v9.3: use the separate keyword-scan budget. Sharing nodesVisitedThisTick
        // here was a critical bug — on Mawada's WebView (5000+ nodes) it ate the
        // entire budget before findUnprocessedClickable could even start.
        if (++keywordScanThisTick > MAX_NODES_PER_TICK) return false;

        CharSequence text = node.getText();
        if (text != null) {
            String hay = normalizeArabic(text.toString());
            for (String n : needles) if (hay.contains(n)) return true;
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String hay = normalizeArabic(desc.toString());
            for (String n : needles) if (hay.contains(n)) return true;
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            if (containsAnyText(child, needles, depth + 1)) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findUnprocessedClickable(
            List<AccessibilityNodeInfo> roots, String text) {
        if (text == null || text.isEmpty()) return null;
        // v9.5: hot path — use the native findAccessibilityNodeInfosByText
        // API instead of a user-space DFS. Native is implemented in C++ in
        // the Android framework and avoids the per-node JNI cost of
        // node.getChild(i) / node.getBoundsInScreen() that our DFS pays.
        // Profiling a 17-min/111-like session showed Mawada's WebView grows
        // from ~1.3 K to ~4.5 K accessibility nodes as the user scrolls
        // (loaded-but-off-screen rows are kept in the DOM), and the DFS
        // walk grew linearly with the tree — hence the user's 6 → 12 sec
        // per-like slowdown. Native search returns matches in roughly
        // constant time regardless of tree size.
        AccessibilityNodeInfo hit = findByTextNative(roots, text, /* skipProcessed */ true);
        if (hit != null) return hit;
        // Fall back to DFS only when the native call returns nothing. Some
        // AccessibilityNodeInfo implementations on older Android versions
        // don't implement the text-search method for virtual WebView nodes.
        String needle = normalizeArabic(text);
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo dfs =
                    findFirstClickableMatching(root, needle, 0, /* skipProcessed */ true);
            if (dfs != null) return dfs;
        }
        return null;
    }

    private AccessibilityNodeInfo findClickableInAll(List<AccessibilityNodeInfo> roots, String text) {
        if (text == null || text.isEmpty()) return null;
        // Same v9.5 native-first approach for one-shot lookups (close, yes,
        // load-more keywords, etc.).
        AccessibilityNodeInfo hit = findByTextNative(roots, text, /* skipProcessed */ false);
        if (hit != null) return hit;
        String needle = normalizeArabic(text);
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo dfs =
                    findFirstClickableMatching(root, needle, 0, /* skipProcessed */ false);
            if (dfs != null) return dfs;
        }
        return null;
    }

    /**
     * v9.5 — fast path. AccessibilityNodeInfo.findAccessibilityNodeInfosByText
     * is a native Android API that returns a flat list of every node whose
     * text or contentDescription contains the given substring. It's
     * implemented inside the framework's accessibility cache so it scales
     * essentially independently of tree size, whereas our user-space DFS
     * grew linearly with the number of off-screen rows Mawada had loaded.
     *
     * Filters applied here, in the order that lets us bail out quickest:
     *   1. isVisibleToUser()  → skip the hundreds of off-screen rows
     *   2. climbToClickable() → walk up at most 8 levels to a clickable
     *   3. processedBounds    → skip rows we already clicked this scroll
     *
     * Returns the first match that passes all three.
     */
    private AccessibilityNodeInfo findByTextNative(
            List<AccessibilityNodeInfo> roots, String text, boolean skipProcessed) {
        for (AccessibilityNodeInfo root : roots) {
            if (root == null) continue;
            List<AccessibilityNodeInfo> hits;
            try {
                hits = root.findAccessibilityNodeInfosByText(text);
            } catch (Throwable t) {
                continue;
            }
            if (hits == null) continue;
            for (AccessibilityNodeInfo h : hits) {
                if (h == null) continue;
                track(h);
                if (!h.isVisibleToUser()) continue;
                AccessibilityNodeInfo clickable = climbToClickable(h);
                if (clickable == null) continue;
                if (skipProcessed && processedBounds.contains(boundsKey(clickable))) continue;
                return clickable;
            }
        }
        return null;
    }

    /**
     * v1.12.7 — exact-text variant for cases where the substring search
     * isn't selective enough. Mawada's "بحث" screen contains three
     * different buttons whose text starts with بحث ("بحث",
     * "بحث متقدم", "بحث باسم المستخدم"); the substring search returns
     * whichever the framework iterates to first, which is rarely the
     * simple submit button we want. This variant filters the native-API
     * hits down to the ones whose visible text OR contentDescription
     * equals the needle exactly (after normalization), and returns the
     * first clickable ancestor of any such match.
     */
    private AccessibilityNodeInfo findExactClickableInAll(
            List<AccessibilityNodeInfo> roots, String text) {
        if (text == null || text.isEmpty()) return null;
        String exactNeedle = normalizeArabic(text.trim());
        for (AccessibilityNodeInfo root : roots) {
            if (root == null) continue;
            List<AccessibilityNodeInfo> hits;
            try {
                hits = root.findAccessibilityNodeInfosByText(text);
            } catch (Throwable t) {
                continue;
            }
            if (hits == null) continue;
            for (AccessibilityNodeInfo h : hits) {
                if (h == null) continue;
                track(h);
                if (!h.isVisibleToUser()) continue;
                boolean exact = false;
                CharSequence ht = h.getText();
                if (ht != null
                        && exactNeedle.equals(normalizeArabic(ht.toString().trim()))) {
                    exact = true;
                }
                if (!exact) {
                    CharSequence hd = h.getContentDescription();
                    if (hd != null
                            && exactNeedle.equals(normalizeArabic(hd.toString().trim()))) {
                        exact = true;
                    }
                }
                if (!exact) continue;
                AccessibilityNodeInfo clickable = climbToClickable(h);
                if (clickable != null) return clickable;
            }
        }
        return null;
    }

    /**
     * v1.12.8 — last-resort heuristic for the search-button picker.
     * Returns the visible clickable node whose label (text or contentDesc)
     * contains the needle AND is the shortest among all such nodes. The
     * idea: when there are several variants like 'بحث', 'بحث متقدم',
     * 'بحث باسم المستخدم', the plain submit button has the shortest
     * label, so picking by length reliably skips the longer ones even
     * if the exact-equality check missed (because of stray punctuation,
     * an extra space, an icon character glued to the text, etc.).
     * Falls back to substring search only after this returns null.
     */
    private AccessibilityNodeInfo findShortestClickableContaining(
            List<AccessibilityNodeInfo> roots, String text) {
        if (text == null || text.isEmpty()) return null;
        String needle = normalizeArabic(text);
        AccessibilityNodeInfo bestClickable = null;
        int bestLen = Integer.MAX_VALUE;
        for (AccessibilityNodeInfo root : roots) {
            if (root == null) continue;
            List<AccessibilityNodeInfo> hits;
            try { hits = root.findAccessibilityNodeInfosByText(text); }
            catch (Throwable t) { continue; }
            if (hits == null) continue;
            for (AccessibilityNodeInfo h : hits) {
                if (h == null) continue;
                track(h);
                if (!h.isVisibleToUser()) continue;
                CharSequence cls = h.getClassName();
                if (cls != null && cls.toString().contains("EditText")) continue;
                String label = "";
                CharSequence ht = h.getText();
                if (ht != null) label = normalizeArabic(ht.toString());
                if (label.isEmpty()) {
                    CharSequence hd = h.getContentDescription();
                    if (hd != null) label = normalizeArabic(hd.toString());
                }
                if (label.isEmpty() || !label.contains(needle)) continue;
                if (label.length() >= bestLen) continue;
                AccessibilityNodeInfo clickable = climbToClickable(h);
                if (clickable == null) continue;
                bestClickable = clickable;
                bestLen = label.length();
            }
        }
        return bestClickable;
    }

    /**
     * v1.12.10 — pure-DFS search for Mawada's 'بـحـث' submit button.
     *
     * Why DFS instead of findAccessibilityNodeInfosByText: on the v1.12.9
     * production diagnostic, native search returned only the three
     * substring siblings ('بحث بإسم المستخدم', 'البحث السريع', 'البحث
     * المتقدم') and never the tatweel form 'بـحـث', even though it was
     * clearly in the tree at the time. WebView accessibility caches
     * appear to index by raw substring without normalisation, so a
     * tatweel-decorated label is invisible to that API. DFS reads every
     * node ourselves and compares NORMALISED forms, so the tatweel
     * collapse in normalizeArabic() lets us see all 'بحث'-containing
     * labels uniformly.
     *
     * Ranking, in order:
     *   1) exact normalised-label match  (one node: the 'بـحـث' submit)
     *   2) shortest normalised label that still contains the needle
     *
     * Every visited candidate is logged via diagEvent so the dump shows
     * the full ranking next time something goes wrong.
     */
    private AccessibilityNodeInfo findSearchSubmitByDfs(
            List<AccessibilityNodeInfo> roots, String needleRaw) {
        String needle = normalizeArabic(needleRaw);
        AccessibilityNodeInfo exact = null;
        AccessibilityNodeInfo shortest = null;
        int shortestLen = Integer.MAX_VALUE;
        StringBuilder logBuf = new StringBuilder();
        int logCount = 0;
        for (AccessibilityNodeInfo root : roots) {
            if (root == null) continue;
            AccessibilityNodeInfo[] ex = { exact };
            AccessibilityNodeInfo[] sh = { shortest };
            int[] shLen = { shortestLen };
            int[] logC = { logCount };
            dfsCollectSearchCandidates(root, needle, 0, ex, sh, shLen, logBuf, logC);
            exact = ex[0];
            shortest = sh[0];
            shortestLen = shLen[0];
            logCount = logC[0];
            if (exact != null) break; // exact wins immediately
        }
        if (logBuf.length() > 0) {
            diagEvent("REFRESH(3-dots) DFS candidates: " + logBuf.toString());
        }
        return exact != null ? exact : shortest;
    }

    private void dfsCollectSearchCandidates(
            AccessibilityNodeInfo node, String needle, int depth,
            AccessibilityNodeInfo[] exact, AccessibilityNodeInfo[] shortest,
            int[] shortestLen, StringBuilder logBuf, int[] logCount) {
        if (node == null || depth > MAX_TREE_DEPTH) return;
        if (exact[0] != null) return; // already found best possible
        // Read both text and contentDescription, normalise, check
        // whether either contains the needle. Don't restrict to leaves —
        // many of Mawada's buttons are <view.View> wrappers that own the
        // contentDescription on themselves and a TextView child with the
        // same string.
        String[] labels = new String[2];
        CharSequence t = node.getText();
        if (t != null) labels[0] = normalizeArabic(t.toString());
        CharSequence d = node.getContentDescription();
        if (d != null) labels[1] = normalizeArabic(d.toString());
        for (String label : labels) {
            if (label == null || label.isEmpty()) continue;
            if (!label.contains(needle)) continue;
            if (!node.isVisibleToUser()) break;
            CharSequence cls = node.getClassName();
            if (cls != null && cls.toString().contains("EditText")) break;
            AccessibilityNodeInfo clickable = climbToClickable(node);
            if (clickable == null) break;
            if (logCount[0] < 8) {
                if (logBuf.length() > 0) logBuf.append(" | ");
                logBuf.append(label).append("[").append(label.length()).append("]");
                logCount[0]++;
            }
            if (label.equals(needle)) {
                exact[0] = clickable;
                return;
            }
            if (label.length() < shortestLen[0]) {
                shortest[0] = clickable;
                shortestLen[0] = label.length();
            }
            break;
        }
        int n = node.getChildCount();
        for (int i = 0; i < n && exact[0] == null; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            dfsCollectSearchCandidates(child, needle, depth + 1, exact, shortest, shortestLen, logBuf, logCount);
        }
    }

    /**
     * NOTE: returned node is already in the tick pool (because every obtain
     * tracked it). Callers MUST NOT re-track or pre-recycle it.
     */
    private AccessibilityNodeInfo findFirstClickableMatching(
            AccessibilityNodeInfo node, String normalizedNeedle, int depth, boolean skipProcessed) {
        if (node == null || depth > MAX_TREE_DEPTH) return null;
        if (++nodesVisitedThisTick > MAX_NODES_PER_TICK) return null;

        // v9.3: prune zero-area subtrees. Mawada's WebView exposes thousands of
        // off-screen list items as accessibility nodes with bounds like
        // [x,y][x',y] (zero height). Walking into them wastes the budget on
        // unclickable virtualized rows before the DFS ever reaches the visible
        // "إهتمام" buttons that live at the end of the tree. The actual visible
        // buttons have non-zero bounds and are unaffected by this prune.
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return null;

        if (textMatches(node, normalizedNeedle) && node.isVisibleToUser()) {
            AccessibilityNodeInfo clickable = climbToClickable(node);
            if (clickable != null
                    && (!skipProcessed || !processedBounds.contains(boundsKey(clickable)))) {
                return clickable;
            }
            // If skipped, clickable is already in the pool from its obtain;
            // nothing to track here.
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            AccessibilityNodeInfo found =
                    findFirstClickableMatching(child, normalizedNeedle, depth + 1, skipProcessed);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollableInAll(List<AccessibilityNodeInfo> roots) {
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo s = findScrollable(root, 0);
            if (s != null) return s;
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_TREE_DEPTH) return null;
        if (++nodesVisitedThisTick > MAX_NODES_PER_TICK) return null;
        if (node.isScrollable() && node.isVisibleToUser()) {
            List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
            if (actions != null) {
                for (AccessibilityNodeInfo.AccessibilityAction a : actions) {
                    if (a.getId() == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) return node;
                }
            } else {
                return node;
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            AccessibilityNodeInfo found = findScrollable(child, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo climbToClickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current.isClickable() && current.isEnabled() && current.isVisibleToUser()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (parent != null) track(parent);
            current = parent;
        }
        return null;
    }

    private boolean performClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = climbToClickable(node);
        if (target == null) return false;
        try {
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================
    //               MEMBER CARD DETECTION & FINGERPRINT
    // ============================================================

    /**
     * Walk up from the like button to find the surrounding member CARD,
     * detected by SCREEN BOUNDS — not by text-descendant count, which in
     * a RecyclerView gives you the whole list.
     *
     * A card is:
     *   - taller than the like button by a factor (so it contains more than
     *     just the button)
     *   - shorter than ~55% of screen height (so it's NOT the list/page)
     *   - at least half the screen wide (so it's a real card, not a tag)
     * We pick the SMALLEST ancestor satisfying these constraints, because
     * the smallest one is the tightest fit = the member's card.
     */
    private AccessibilityNodeInfo climbToMemberCard(AccessibilityNodeInfo likeBtn) {
        if (likeBtn == null) return null;
        Rect btnR = new Rect();
        likeBtn.getBoundsInScreen(btnR);
        int btnH = Math.max(1, btnR.height());

        AccessibilityNodeInfo current = likeBtn;
        AccessibilityNodeInfo best = null;
        long bestArea = Long.MAX_VALUE;

        for (int depth = 0; current != null && depth < CARD_MAX_CLIMB; depth++) {
            Rect r = new Rect();
            current.getBoundsInScreen(r);
            long area = (long) Math.max(0, r.width()) * (long) Math.max(0, r.height());

            boolean cardShaped =
                    r.height() >= btnH * 2 &&
                    r.height() <= (int) (screenH * 0.55f) &&
                    r.width()  >= (int) (screenW * 0.50f) &&
                    area > 0;

            if (cardShaped && area < bestArea) {
                best = current;
                bestArea = area;
            }

            AccessibilityNodeInfo parent = current.getParent();
            if (parent != null) track(parent);
            current = parent;
        }
        return best;
    }

    /**
     * Compute a per-member fingerprint by collecting visible text inside the
     * card's bounding rect. Filters by isVisibleToUser AND bounds intersection
     * so off-screen list items don't contaminate the hash.
     */
    private String fingerprintMemberFromButton(AccessibilityNodeInfo likeBtn) {
        if (likeBtn == null || !config.dedupEnabled) return null;
        AccessibilityNodeInfo card = climbToMemberCard(likeBtn);
        if (card == null) return null;
        Rect cardR = new Rect();
        card.getBoundsInScreen(cardR);
        if (cardR.isEmpty()) return null;

        StringBuilder sb = new StringBuilder(256);
        collectCardText(card, cardR, sb, 0);
        if (sb.length() < MIN_TEXT_CHARS_FOR_FP) return null;

        // Validation: a real member card usually has SOME Arabic letters in
        // it. If we see none, treat the fingerprint as untrusted and return
        // null so the like fires but is NOT marked as deduped (avoids
        // poisoning the DB with junk).
        boolean hasArabic = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) { hasArabic = true; break; }
        }
        if (!hasArabic) return null;

        return LikedMembersDb.fingerprint(sb.toString());
    }

    private void collectCardText(AccessibilityNodeInfo node, Rect cardBounds,
                                 StringBuilder out, int depth) {
        if (node == null || depth > CARD_TEXT_DEPTH) return;
        if (++nodesVisitedThisTick > MAX_NODES_PER_TICK) return;

        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (!Rect.intersects(r, cardBounds)) return;

        if (node.isVisibleToUser()) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                String t = text.toString().trim();
                if (!t.isEmpty()) out.append(t).append('\n');
            }
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.length() > 0) {
                String d = desc.toString().trim();
                if (!d.isEmpty()) out.append(d).append('\n');
            }
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            collectCardText(child, cardBounds, out, depth + 1);
        }
    }

    // ============================================================
    //                            HELPERS
    // ============================================================

    private List<AccessibilityNodeInfo> collectAllRoots() {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = null;
        try { windows = getWindows(); } catch (Throwable ignore) {}

        if (windows != null && !windows.isEmpty()) {
            List<AccessibilityWindowInfo> sorted = new ArrayList<>(windows);
            Collections.sort(sorted, new Comparator<AccessibilityWindowInfo>() {
                @Override
                public int compare(AccessibilityWindowInfo a, AccessibilityWindowInfo b) {
                    return Integer.compare(b.getLayer(), a.getLayer());
                }
            });
            for (AccessibilityWindowInfo w : sorted) {
                if (w == null) continue;
                AccessibilityNodeInfo r = null;
                try { r = w.getRoot(); } catch (Throwable ignore) {}
                if (r != null) { track(r); result.add(r); }
            }
        }
        AccessibilityNodeInfo active = null;
        try { active = getRootInActiveWindow(); } catch (Throwable ignore) {}
        if (active != null) {
            // Track BEFORE checking duplicates so the pool will recycle it
            // either way (we never call recycle() directly anywhere — that
            // was a v2 footgun).
            track(active);
            boolean dup = false;
            for (AccessibilityNodeInfo r : result) if (r == active) { dup = true; break; }
            if (!dup) result.add(0, active);
        }
        return result;
    }

    private String boundsKey(AccessibilityNodeInfo node) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        int cx = (r.left + r.right) / 2;
        int cy = (r.top + r.bottom) / 2;
        return (cx / 80) + "," + (cy / 80);
    }

    private boolean textMatches(AccessibilityNodeInfo node, String normalizedNeedle) {
        if (node == null) return false;
        CharSequence text = node.getText();
        if (text != null && normalizeArabic(text.toString()).contains(normalizedNeedle)) return true;
        CharSequence desc = node.getContentDescription();
        if (desc != null && normalizeArabic(desc.toString()).contains(normalizedNeedle)) return true;
        return false;
    }

    private static String normalizeArabic(String s) {
        if (s == null) return "";
        // v1.12.9 — also strip Arabic Tatweel (U+0640 ـ) so 'بـحـث'
        // and 'بحث' compare equal. Mawada uses the tatweel form for its
        // search-submit button (confirmed in the v1.12.8 diagnostic dump),
        // which made the substring search return only the three OTHER
        // buttons whose label contains plain 'بحث', and the bot kept
        // tapping 'بحث بإسم المستخدم'.
        String r = s.replaceAll("[ً-ْٰٱـ]", "");
        r = r.replace((char) 1571, (char) 1575)
             .replace((char) 1573, (char) 1575)
             .replace((char) 1570, (char) 1575)
             .replace((char) 1609, (char) 1610)
             .replace((char) 1577, (char) 1607);
        // v1.12.8 — strip bidi / format / zero-width characters that
        // Mawada sometimes embeds in button labels. String.trim() does
        // not remove these (they are not whitespace by Java's rules),
        // so a node whose text is literally "بحث‎" would fail an
        // equals("بحث") check even though it is visually identical.
        StringBuilder b = null;
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            boolean drop =
                    c == '​' || c == '‌' || c == '‍' ||
                    c == '‎' || c == '‏' ||
                    c == '‪' || c == '‫' || c == '‬' ||
                    c == '‭' || c == '‮' ||
                    c == '⁦' || c == '⁧' || c == '⁨' ||
                    c == '⁩' || c == '﻿';
            if (drop) {
                if (b == null) {
                    b = new StringBuilder(r.length());
                    b.append(r, 0, i);
                }
            } else if (b != null) {
                b.append(c);
            }
        }
        if (b != null) r = b.toString();
        return r.trim().toLowerCase(Locale.ROOT);
    }

    // ---- node-pool management ----

    private void track(AccessibilityNodeInfo node) {
        if (node == null) return;
        tickPool.put(node, Boolean.TRUE);
    }

    private void drainTickPool() {
        if (tickPool.isEmpty()) return;
        for (AccessibilityNodeInfo n : tickPool.keySet()) {
            if (n == null) continue;
            try { n.recycle(); } catch (Throwable ignore) {}
        }
        tickPool.clear();
    }

    // ============================================================
    //                       DIAGNOSTIC DUMP
    // ============================================================

    /**
     * v9 — called at every auto-stop. Builds a one-line summary of what's
     * on screen (counts of clickables, top unique text/desc strings) and
     * writes the full accessibility tree to a text file the user can share.
     *
     * The returned status reason includes:
     *   - the human reason ("توقفت — ...")
     *   - a one-line on-screen summary so the user can diagnose without
     *     opening the file
     *   - the full file path
     *
     * The full file goes to getExternalFilesDir(null), which is
     * /sdcard/Android/data/com.mokafeefah.clicker/files/ — accessible
     * via Android's Files app on all versions, no permission needed.
     */
    private void autoStopWithDiagnostic(String status, String reasonBase,
                                        List<AccessibilityNodeInfo> roots) {
        String summary;
        String path;
        try {
            summary = buildScreenSummary(roots);
            path    = writeAccessibilityDump(roots, reasonBase, summary);
        } catch (Throwable t) {
            summary = "(فشل التشخيص)";
            path    = "—";
        }
        lastDumpPath = path;
        String full = reasonBase + " | " + summary + " | تشخيص: " + path;
        stopInternal(status, full);
    }

    /**
     * Walks the visible accessibility tree and returns a one-line summary:
     * total clickable nodes + top 6 unique text/contentDescription strings
     * by frequency. This is the most informative diagnostic we can fit
     * into "آخر إجراء" — it tells me at a glance whether Mawadda is showing
     * the same 10 cards (scroll didn't work), a "no more members" message,
     * an upgrade screen, or something else entirely.
     */
    private String buildScreenSummary(List<AccessibilityNodeInfo> roots) {
        Map<String, Integer> textFreq = new HashMap<>();
        int[] counters = new int[]{ 0, 0, 0 }; // clickable, visible, total
        for (AccessibilityNodeInfo r : roots) {
            collectSummary(r, textFreq, counters, 0);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(textFreq.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("clk=").append(counters[0])
          .append(", vis=").append(counters[1])
          .append(", n=").append(counters[2])
          .append(", نصوص: ");
        int shown = 0;
        for (Map.Entry<String, Integer> e : entries) {
            if (shown >= 6) break;
            String t = e.getKey();
            if (t.length() > 28) t = t.substring(0, 28) + "…";
            sb.append("'").append(t).append("'×").append(e.getValue()).append(" ");
            shown++;
        }
        if (shown == 0) sb.append("(لا نصوص)");
        return sb.toString();
    }

    private void collectSummary(AccessibilityNodeInfo node, Map<String, Integer> freq,
                                int[] counters, int depth) {
        if (node == null || depth > MAX_TREE_DEPTH) return;
        track(node);
        if (counters[2]++ > MAX_NODES_PER_TICK) return;
        if (node.isClickable()) counters[0]++;
        if (node.isVisibleToUser()) counters[1]++;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        if (t != null && t.length() > 0) {
            String s = t.toString().trim();
            if (!s.isEmpty()) freq.put(s, (freq.containsKey(s) ? freq.get(s) : 0) + 1);
        }
        if (d != null && d.length() > 0) {
            String s = d.toString().trim();
            if (!s.isEmpty()) freq.put(s, (freq.containsKey(s) ? freq.get(s) : 0) + 1);
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo c = null;
            try { c = node.getChild(i); } catch (Throwable ignore) {}
            if (c != null) collectSummary(c, freq, counters, depth + 1);
        }
    }

    /**
     * Writes the full accessibility tree to a text file. Returns the absolute
     * path so the user can find and share it via the Files app.
     */
    private String writeAccessibilityDump(List<AccessibilityNodeInfo> roots,
                                          String reason, String summary) {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        File file = new File(dir, "dump_" + stamp + ".txt");
        StringBuilder sb = new StringBuilder(16 * 1024);
        sb.append("=== Mokafeefah Diagnostic Dump ===\n")
          .append("time:     ").append(new Date().toString()).append("\n")
          .append("reason:   ").append(reason).append("\n")
          .append("summary:  ").append(summary).append("\n")
          .append("screen:   ").append(screenW).append("x").append(screenH).append("\n")
          .append("likes:    ").append(likesCount.get()).append("\n")
          .append("skipped:  ").append(skippedCount.get()).append("\n")
          .append("state:    ").append(stateName(state)).append("\n");
        if (diagLog.length() > 0) {
            sb.append('\n').append(diagLog);
        }
        sb.append("\n=== Final Accessibility Tree ===\n");
        for (int i = 0; i < roots.size(); i++) {
            AccessibilityNodeInfo r = roots.get(i);
            sb.append("\n--- Root ").append(i)
              .append(" pkg=").append(r.getPackageName()).append(" ---\n");
            dumpNode(r, sb, 0);
        }
        try {
            FileWriter fw = new FileWriter(file);
            try { fw.write(sb.toString()); }
            finally { try { fw.close(); } catch (Throwable ignore) {} }
        } catch (Throwable t) {
            return "(فشل الكتابة: " + t.getClass().getSimpleName() + ")";
        }
        return file.getAbsolutePath();
    }

    private void dumpNode(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null || depth > MAX_TREE_DEPTH) return;
        track(node);
        Rect b = new Rect();
        node.getBoundsInScreen(b);
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(node.isClickable() ? "[C] " : "[ ] ")
          .append(node.isVisibleToUser() ? "[V] " : "[ ] ")
          .append(b.toShortString()).append(' ');
        CharSequence cls = node.getClassName();
        if (cls != null) sb.append(cls);
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) {
            sb.append(" text=\"").append(t.toString().replace('\n', ' ')).append("\"");
        }
        CharSequence d = node.getContentDescription();
        if (d != null && d.length() > 0) {
            sb.append(" desc=\"").append(d.toString().replace('\n', ' ')).append("\"");
        }
        sb.append('\n');
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo c = null;
            try { c = node.getChild(i); } catch (Throwable ignore) {}
            if (c != null) dumpNode(c, sb, depth + 1);
        }
    }

    private void setAction(String action) {
        lastAction = action;
        notifyUpdate(STATUS_RUNNING);
    }

    private void notifyUpdate(final String status) {
        final StatusListener l = listener;
        if (l == null) return;
        final int likes = likesCount.get();
        final int skipped = skippedCount.get();
        final String act = lastAction == null ? "" : lastAction;
        handler.post(new Runnable() {
            @Override public void run() { l.onUpdate(status, likes, skipped, act); }
        });
    }

    private void vibrateAlert() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = { 0, 250, 150, 250, 150, 250 };
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        } catch (Throwable ignore) {}
    }

    // ============================================================
    //                          CONFIG OBJECT
    // ============================================================

    public static class BotConfig {
        public final String likeText;
        public final String yesText;
        public final String closeText;
        public final String targetPackage;
        public final List<String> profileKeywords;
        public final long scanIntervalMs;
        public final long popupWaitMs;
        public final long idleTimeoutMs;
        public final boolean dedupEnabled;
        // v1.11 — like only members whose marital status is مطلقة / أرملة.
        // When false, the bot likes everyone in the list (original behaviour).
        public final boolean filterDivorcedWidowedOnly;
        // v1.11 — what to do after a soft refresh:
        //   false (default)  → rewind to top of fresh list and start over.
        //   true             → resume in place, letting dedup skip already-
        //                      liked members until a new one is reached.
        public final boolean refreshContinueMode;
        // v1.12 — how the bot finds the next batch of members on refresh:
        //   "online"      → الأعضاء ← المتواجدون الآن (default, text only)
        //   "three_dots"  → tap saved 'ثلاث نقاط' coord ← بحث ← بحث
        public final String findMembersMode;

        public BotConfig(String likeText, String yesText, String closeText, String targetPackage,
                         List<String> profileKeywords, long scanIntervalMs, long popupWaitMs,
                         long idleTimeoutMs, boolean dedupEnabled,
                         boolean filterDivorcedWidowedOnly, boolean refreshContinueMode,
                         String findMembersMode) {
            this.likeText = likeText == null ? "" : likeText.trim();
            this.yesText = yesText == null ? "" : yesText.trim();
            this.closeText = closeText == null ? "" : closeText.trim();
            this.targetPackage = targetPackage != null ? targetPackage.trim() : "";
            this.profileKeywords = profileKeywords == null ? new ArrayList<String>() : profileKeywords;
            this.scanIntervalMs = Math.max(150L, scanIntervalMs);
            this.popupWaitMs = Math.max(300L, popupWaitMs);
            this.idleTimeoutMs = Math.max(5000L, idleTimeoutMs);
            this.dedupEnabled = dedupEnabled;
            this.filterDivorcedWidowedOnly = filterDivorcedWidowedOnly;
            this.refreshContinueMode = refreshContinueMode;
            this.findMembersMode = (findMembersMode != null && !findMembersMode.isEmpty())
                    ? findMembersMode : "online";
        }
    }
}
