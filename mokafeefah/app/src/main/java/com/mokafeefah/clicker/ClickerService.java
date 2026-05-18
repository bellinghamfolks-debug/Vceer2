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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
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

    private static final int STATE_LOOK_LIKE    = 0;
    private static final int STATE_AFTER_LIKE   = 1;
    private static final int STATE_AFTER_YES    = 2;
    private static final int STATE_MUST_SCROLL  = 3;
    private static final int STATE_AFTER_SCROLL = 4;  // v4 — be patient with pagination

    private static final long MAX_WAIT_AFTER_LIKE_MS    = 2500L;
    private static final long MAX_WAIT_AFTER_YES_MS     = 2000L;
    private static final long POST_BACK_WAIT_MS         = 900L;

    // Post-scroll loading patience. Many target apps paginate over the
    // network; we must NOT count "waiting for the next page to load" as a
    // stuck condition or we give up after only a few seconds. v5 is even
    // more generous than v4 because real users may be on slow networks.
    private static final long POST_SCROLL_INITIAL_WAIT_MS = 1500L;
    private static final long POST_SCROLL_LOOP_WAIT_MS    = 800L;
    private static final long POST_SCROLL_MAX_WAIT_MS     = 12000L; // 12s per re-scroll attempt
    private static final int  POST_SCROLL_RETRY_ATTEMPTS  = 5;      // re-swipe up to N times with varied patterns

    private static final int  MAX_TREE_DEPTH         = 30;
    private static final int  MAX_NODES_PER_TICK     = 6000;
    private static final int  CARD_MAX_CLIMB         = 9;
    private static final int  CARD_TEXT_DEPTH        = 6;
    private static final int  MIN_TEXT_CHARS_FOR_FP  = 6;
    private static final int  SNAPSHOT_TEXT_LIMIT    = 700;  // chars hashed as "screen content"

    // Stop conditions after the bot has clearly run out of work.
    // With v5 the retries-per-cycle are 5 × 12s = 60s, so 3 cycles ≈ 3 min
    // of attempts before declaring true end-of-list.
    private static final int  MAX_SCROLLS_NO_CONTENT_CHANGE = 3;
    private static final int  MAX_SCROLLS_WITHOUT_NEW_LIKE  = 30;

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
    private long   lastBackTime = 0L;
    private long   lastButtonFoundMs = 0L;
    private int    state = STATE_LOOK_LIKE;
    private long   stateChangedAt = 0L;
    private int    nodesVisitedThisTick = 0;

    // Stuck detection — v4 splits the v3 single counter into two distinct
    // signals so we can correctly tell "the app is paginating, wait" apart
    // from "we've truly run out of likeable members".
    private int    scrollsWithoutContentChange = 0; // re-swipes with no new like buttons appearing
    private int    scrollsWithoutNewLike       = 0; // successful scrolls that produced 0 new likes
    private int    likesAtLastScroll           = 0;
    private int    scrollRetryCount            = 0; // re-swipes within one MUST_SCROLL session
    private String scrollSnapshot              = ""; // signature of like-buttons before a scroll

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
        scrollsWithoutContentChange = 0;
        scrollsWithoutNewLike = 0;
        likesAtLastScroll = 0;
        scrollRetryCount = 0;
        scrollSnapshot = "";
        long now = System.currentTimeMillis();
        lastButtonFoundMs = now;
        state = STATE_LOOK_LIKE;
        stateChangedAt = now;
        running.set(true);
        notifyUpdate(STATUS_RUNNING);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(tick, 500L);
        return true;
    }

    public void stopBot() { stopInternal(STATUS_STOPPED); }

    private void stopInternal(String reason) {
        if (!running.get()) return;
        running.set(false);
        handler.removeCallbacksAndMessages(null);
        vibrateAlert();
        notifyUpdate(reason);
    }

    private void transitionTo(int newState) {
        this.state = newState;
        this.stateChangedAt = System.currentTimeMillis();
    }

    // ============================================================
    //                         TICK / STATE MACHINE
    // ============================================================

    private long performOneTick() {
        long now = System.currentTimeMillis();
        nodesVisitedThisTick = 0;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        if (now - lastButtonFoundMs > config.idleTimeoutMs) {
            stopInternal(STATUS_AUTO_STOPPED);
            return config.scanIntervalMs;
        }

        List<AccessibilityNodeInfo> roots = collectAllRoots();
        if (roots.isEmpty()) {
            setAction(getString(R.string.action_wait));
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
            case STATE_LOOK_LIKE:    return handleLookLike(roots, now);
            case STATE_AFTER_LIKE:   return handleAfterLike(roots, now);
            case STATE_AFTER_YES:    return handleAfterYes(roots, now);
            case STATE_MUST_SCROLL:  return handleMustScroll(roots, now);
            case STATE_AFTER_SCROLL: return handleAfterScroll(roots, now);
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

            // Smart card detection + fingerprinting.
            String fp = fingerprintMemberFromButton(likeBtn);
            if (config.dedupEnabled && fp != null && db != null && db.isLiked(fp)) {
                processedBounds.add(key);
                skippedCount.incrementAndGet();
                setAction(getString(R.string.action_skip));
                lastButtonFoundMs = now;
                return Math.max(150L, config.scanIntervalMs / 2);
            }

            if (performClick(likeBtn)) {
                processedBounds.add(key);
                likesCount.incrementAndGet();
                if (fp != null && db != null) db.markLiked(fp);
                setAction(getString(R.string.action_like));
                lastButtonFoundMs = now;
                // Real progress: clear all stuck signals so the next scroll
                // budget is fresh.
                scrollsWithoutContentChange = 0;
                scrollsWithoutNewLike = 0;
                transitionTo(STATE_AFTER_LIKE);
                return config.popupWaitMs;
            }
            return config.scanIntervalMs;
        }

        transitionTo(STATE_MUST_SCROLL);
        return 300L;
    }

    private long handleAfterLike(List<AccessibilityNodeInfo> roots, long now) {
        AccessibilityNodeInfo yes = findClickableInAll(roots, config.yesText);
        if (yes != null) {
            if (performClick(yes)) {
                setAction(getString(R.string.action_yes));
                lastButtonFoundMs = now;
                transitionTo(STATE_AFTER_YES);
                return 700L;
            }
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

    private long handleMustScroll(List<AccessibilityNodeInfo> roots, long now) {
        // Have we made any like progress since the previous scroll? If not,
        // this is a "successful scroll but no useful members on it" — bump
        // the all-dups counter. Hits its ceiling only after MANY unhelpful
        // scrolls, so paginated apps with stretches of already-liked members
        // still get processed.
        int curLikes = likesCount.get();
        if (curLikes == likesAtLastScroll) {
            scrollsWithoutNewLike++;
            if (scrollsWithoutNewLike >= MAX_SCROLLS_WITHOUT_NEW_LIKE) {
                stopInternal(STATUS_AUTO_STOPPED);
                return config.scanIntervalMs;
            }
        } else {
            scrollsWithoutNewLike = 0;
        }
        likesAtLastScroll = curLikes;

        // Snapshot of currently-visible like buttons. handleAfterScroll
        // compares against this to know whether the scroll actually moved
        // content (vs. swiping at the bottom of a finite list, or while a
        // network request is in flight).
        scrollSnapshot = collectLikeButtonsSnapshot(roots);

        boolean scrolled = performSmartScroll(roots);
        if (scrolled) setAction(getString(R.string.action_scroll));

        processedBounds.clear();
        scrollRetryCount = 0;
        transitionTo(STATE_AFTER_SCROLL);
        return POST_SCROLL_INITIAL_WAIT_MS;
    }

    /**
     * Patient post-scroll handler. The whole point of this state is to NOT
     * call something "stuck" just because a paginated app needs a few seconds
     * to fetch the next batch from the network.
     *
     *   - Wait up to {@link #POST_SCROLL_MAX_WAIT_MS} for new like-buttons
     *     to appear. Each tick re-samples the screen.
     *   - If new content appears → reset stuck counters and resume.
     *   - If still no new content after the wait → re-swipe and wait again,
     *     up to {@link #POST_SCROLL_RETRY_ATTEMPTS} times.
     *   - Only after all retries fail do we count this as one "no content
     *     change" event. We tolerate {@link #MAX_SCROLLS_NO_CONTENT_CHANGE}
     *     of them before giving up — at which point the user really is at
     *     the bottom of an empty list.
     */
    private long handleAfterScroll(List<AccessibilityNodeInfo> roots, long now) {
        String current = collectLikeButtonsSnapshot(roots);

        if (!current.equals(scrollSnapshot)) {
            // Scroll succeeded — new like-button layout visible.
            scrollsWithoutContentChange = 0;
            transitionTo(STATE_LOOK_LIKE);
            return 350L;
        }

        long waited = now - stateChangedAt;
        if (waited < POST_SCROLL_MAX_WAIT_MS) {
            setAction(getString(R.string.action_wait_loading));
            return POST_SCROLL_LOOP_WAIT_MS;
        }

        scrollRetryCount++;
        if (scrollRetryCount >= POST_SCROLL_RETRY_ATTEMPTS) {
            scrollsWithoutContentChange++;
            if (scrollsWithoutContentChange >= MAX_SCROLLS_NO_CONTENT_CHANGE) {
                stopInternal(STATUS_AUTO_STOPPED);
                return config.scanIntervalMs;
            }
            scrollRetryCount = 0;
            transitionTo(STATE_MUST_SCROLL);
            return 400L;
        }

        // Re-swipe within the same MUST_SCROLL session — some paginated lists
        // need a second nudge after the first swipe triggers the request.
        performSmartScroll(roots);
        setAction(getString(R.string.action_scroll));
        scrollSnapshot = current;
        stateChangedAt = now;          // reset wait timer for this attempt
        return POST_SCROLL_INITIAL_WAIT_MS;
    }

    // ============================================================
    //                          SCROLLING
    // ============================================================

    private boolean performSmartScroll(List<AccessibilityNodeInfo> roots) {
        return performSmartScroll(roots, scrollRetryCount);
    }

    /**
     * Pattern-aware scroll. On the first attempt of a MUST_SCROLL session we
     * try the standard scrollable action; on retries we vary the gesture so
     * a stubborn paginated list eventually accepts the swipe and triggers a
     * "load more" request.
     */
    private boolean performSmartScroll(List<AccessibilityNodeInfo> roots, int patternIndex) {
        // ACTION_SCROLL_FORWARD on the scrollable container is the friendliest
        // option — it lets the app know "we want more content". Try it first.
        if (patternIndex <= 1) {
            AccessibilityNodeInfo scrollable = findScrollableInAll(roots);
            if (scrollable != null) {
                try {
                    if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                        return true;
                    }
                } catch (Throwable ignore) {}
            }
        }
        return performGestureSwipeUp(patternIndex);
    }

    private boolean performGestureSwipeUp(int patternIndex) {
        try {
            int centerX = screenW / 2;
            float startFrac, endFrac;
            long duration;
            switch (patternIndex % 5) {
                case 0: startFrac = 0.72f; endFrac = 0.28f; duration = 450L; break; // standard
                case 1: startFrac = 0.85f; endFrac = 0.15f; duration = 380L; break; // long fast
                case 2: startFrac = 0.62f; endFrac = 0.38f; duration = 280L; break; // short flick
                case 3: startFrac = 0.80f; endFrac = 0.18f; duration = 700L; break; // long slow
                default: startFrac = 0.75f; endFrac = 0.20f; duration = 250L; break;// hard flick
            }
            int jitter = (int) ((System.currentTimeMillis() % 7) - 3) * 12;
            int startY = (int) (screenH * startFrac);
            int endY   = (int) (screenH * endFrac) + jitter;
            if (endY < (int) (screenH * 0.10f)) endY = (int) (screenH * 0.10f);
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
        if (++nodesVisitedThisTick > MAX_NODES_PER_TICK) return false;

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

    /**
     * Returns a content-based fingerprint of what's CURRENTLY visible on
     * screen. Used by AFTER_SCROLL to decide whether a swipe actually
     * loaded new content.
     *
     * IMPORTANT (v5 fix): the v4 implementation hashed the POSITIONS of the
     * visible like-buttons. That's broken for any scrollable list because
     * the viewport stays at the same screen Y coordinates while the content
     * flows through it — so the bounds-keys after a successful scroll were
     * identical to the bounds-keys before, and v4 always concluded "scroll
     * didn't move anything" and stopped after a few tries.
     *
     * v5 hashes the actual VISIBLE TEXT (names, ages, cities) on screen,
     * which DOES change as new members slide into view.
     */
    private String collectLikeButtonsSnapshot(List<AccessibilityNodeInfo> roots) {
        StringBuilder sb = new StringBuilder(SNAPSHOT_TEXT_LIMIT);
        for (AccessibilityNodeInfo root : roots) {
            if (sb.length() >= SNAPSHOT_TEXT_LIMIT) break;
            collectVisibleScreenText(root, sb, 0);
        }
        // Bound the length so very chatty screens don't blow up the hash.
        if (sb.length() > SNAPSHOT_TEXT_LIMIT) sb.setLength(SNAPSHOT_TEXT_LIMIT);
        return sb.toString();
    }

    private void collectVisibleScreenText(AccessibilityNodeInfo node,
                                          StringBuilder out, int depth) {
        if (node == null || depth > 7) return;
        if (++nodesVisitedThisTick > MAX_NODES_PER_TICK) return;
        if (out.length() >= SNAPSHOT_TEXT_LIMIT) return;

        if (node.isVisibleToUser()) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                out.append(text).append('|');
                if (out.length() >= SNAPSHOT_TEXT_LIMIT) return;
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            if (out.length() >= SNAPSHOT_TEXT_LIMIT) return;
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            collectVisibleScreenText(child, out, depth + 1);
        }
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
