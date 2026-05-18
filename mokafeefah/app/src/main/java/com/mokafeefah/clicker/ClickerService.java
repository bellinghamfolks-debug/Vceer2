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

    private static final long MAX_WAIT_AFTER_LIKE_MS = 2500L;
    private static final long MAX_WAIT_AFTER_YES_MS  = 2000L;
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
    private static final long SNAPSHOT_INTERVAL_MS = 15_000L;
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
        long now = System.currentTimeMillis();
        lastButtonFoundMs = now;
        // v8: rewind the target list to its true top before searching. Mawadda's
        // RecyclerView recycles rows and drops the "إهتمام" content description
        // on mid-list cards, so starting mid-list found nothing.
        state = STATE_REWIND_TO_TOP;
        stateChangedAt = now;
        diagBegin(now);
        diagEvent("startBot pkg=" + (cfg.targetPackage == null ? "*" : cfg.targetPackage)
                + " idleTimeoutMs=" + cfg.idleTimeoutMs
                + " scanIntervalMs=" + cfg.scanIntervalMs
                + " popupWaitMs=" + cfg.popupWaitMs
                + " likeText='" + cfg.likeText + "'"
                + " yesText='" + cfg.yesText + "'"
                + " dedup=" + cfg.dedupEnabled);
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
            case STATE_REWIND_TO_TOP: return "REWIND";
            case STATE_LOOK_LIKE:     return "LOOK";
            case STATE_AFTER_LIKE:    return "AFTER_LIKE";
            case STATE_AFTER_YES:     return "AFTER_YES";
            case STATE_MUST_SCROLL:   return "SCROLL";
            default:                  return "?(" + s + ")";
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
        diagLog.append(String.format(Locale.US, "%7d ms | s=%-10s | likes=%d skip=%d | %s%n",
                elapsed, stateName(state), likesCount.get(), skippedCount.get(), event));
        if (diagLog.length() > 256 * 1024) {
            // Safety cap so a very long session doesn't OOM us.
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
        diagLog.append(String.format(Locale.US,
                "%n--- Snapshot @ %d ms | state=%s ---%n%s%n%n",
                elapsed, stateName(state), summary));
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
        if (state != STATE_REWIND_TO_TOP && endOfListVisible(roots)) {
            diagEvent("AUTO-STOP: end-of-list message detected on screen");
            autoStopWithDiagnostic(STATUS_AUTO_STOPPED,
                    getString(R.string.stop_reason_end_of_list), roots);
            return config.scanIntervalMs;
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

        if (now - lastBackTime > 1500L && !config.profileKeywords.isEmpty()) {
            if (anyKeywordVisible(roots, config.profileKeywords)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                lastBackTime = now;
                setAction(getString(R.string.action_back));
                transitionTo(STATE_LOOK_LIKE);
                return POST_BACK_WAIT_MS;
            }
        }

        switch (state) {
            case STATE_REWIND_TO_TOP: return handleRewindToTop(roots, now);
            case STATE_LOOK_LIKE:     return handleLookLike(roots, now);
            case STATE_AFTER_LIKE:    return handleAfterLike(roots, now);
            case STATE_AFTER_YES:     return handleAfterYes(roots, now);
            case STATE_MUST_SCROLL:   return handleMustScroll(roots, now);
            default:
                transitionTo(STATE_LOOK_LIKE);
                return config.scanIntervalMs;
        }
    }

    // ============================================================
    //                          STATE HANDLERS
    // ============================================================

    private long handleLookLike(List<AccessibilityNodeInfo> roots, long now) {
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

            if (performClick(likeBtn)) {
                processedBounds.add(key);
                likesCount.incrementAndGet();
                if (fp != null && db != null) db.markLiked(fp);
                diagEvent("LOOK: clicked like-btn at " + key + " (likes now " + likesCount.get() + ")");
                setAction(getString(R.string.action_like));
                lastButtonFoundMs = now;
                firstNoProgressScrollMs = 0L;
                scrollPatternIndex = 0;
                transitionTo(STATE_AFTER_LIKE);
                return config.popupWaitMs;
            }
            diagEvent("LOOK: like-btn at " + key + " click FAILED");
            return config.scanIntervalMs;
        }

        diagEvent("LOOK: no like button found, transitioning to SCROLL"
                + " (processedBounds=" + processedBounds.size() + ")");
        transitionTo(STATE_MUST_SCROLL);
        return 300L;
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
        String needle = normalizeArabic(text);
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo hit =
                    findFirstClickableMatching(root, needle, 0, /* skipProcessed */ true);
            if (hit != null) return hit;
        }
        return null;
    }

    private AccessibilityNodeInfo findClickableInAll(List<AccessibilityNodeInfo> roots, String text) {
        if (text == null || text.isEmpty()) return null;
        String needle = normalizeArabic(text);
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo hit =
                    findFirstClickableMatching(root, needle, 0, /* skipProcessed */ false);
            if (hit != null) return hit;
        }
        return null;
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
        String r = s.replaceAll("[ً-ْٰٱ]", "");
        r = r.replace((char) 1571, (char) 1575)
             .replace((char) 1573, (char) 1575)
             .replace((char) 1570, (char) 1575)
             .replace((char) 1609, (char) 1610)
             .replace((char) 1577, (char) 1607);
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

        public BotConfig(String likeText, String yesText, String closeText, String targetPackage,
                         List<String> profileKeywords, long scanIntervalMs, long popupWaitMs,
                         long idleTimeoutMs, boolean dedupEnabled) {
            this.likeText = likeText == null ? "" : likeText.trim();
            this.yesText = yesText == null ? "" : yesText.trim();
            this.closeText = closeText == null ? "" : closeText.trim();
            this.targetPackage = targetPackage != null ? targetPackage.trim() : "";
            this.profileKeywords = profileKeywords == null ? new ArrayList<String>() : profileKeywords;
            this.scanIntervalMs = Math.max(150L, scanIntervalMs);
            this.popupWaitMs = Math.max(300L, popupWaitMs);
            this.idleTimeoutMs = Math.max(5000L, idleTimeoutMs);
            this.dedupEnabled = dedupEnabled;
        }
    }
}
