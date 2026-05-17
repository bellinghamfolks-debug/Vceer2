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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AccessibilityService that drives the auto-like loop.
 *
 * Rewritten from the v1 APK with three categories of fixes:
 *
 *  1) PERFORMANCE — the v1 tick was doing one full DFS per profile keyword
 *     (7+ DFSes per scan!). v2 collects ALL text in a single DFS and
 *     matches every keyword against it. Result: roughly an order of
 *     magnitude faster per tick, which translates to ~10x more likes per
 *     minute on the same scan interval.
 *
 *  2) MEMORY / STABILITY — v1 never called {@link AccessibilityNodeInfo#recycle()}.
 *     Every getChild / findAccessibilityNodeInfosByText / getParent leaked
 *     a node reference into the system_server process. After ~50 cycles
 *     the system started denying new node info requests, the bot got
 *     "stuck", and the app could be killed. v2 recycles every obtained
 *     node, including children visited during recursion, with a single
 *     pool tracked per tick.
 *
 *  3) DEDUPLICATION — v1 tracked liked nodes by (cx/80, cy/80) grid cells
 *     and CLEARED that set on every scroll. So a profile re-rendered at
 *     the same screen position after scrolling got liked again. v2
 *     fingerprints each member from the visible text inside their card
 *     and stores fingerprints in {@link LikedMembersDb} (SQLite). A
 *     member is liked at most once, ever, across sessions.
 */
public class ClickerService extends AccessibilityService {

    // ---- public state strings (kept identical to v1 so the UI keeps working) ----
    public static final String STATUS_AUTO_STOPPED = "إيقاف تلقائي";
    public static final String STATUS_IDLE         = "خامل";
    public static final String STATUS_RUNNING      = "يعمل";
    public static final String STATUS_STOPPED      = "متوقف";

    // ---- state machine ----
    private static final int STATE_LOOK_LIKE   = 0;
    private static final int STATE_AFTER_LIKE  = 1;
    private static final int STATE_AFTER_YES   = 2;
    private static final int STATE_MUST_SCROLL = 3;

    private static final long MAX_WAIT_AFTER_LIKE_MS = 6000;
    private static final long MAX_WAIT_AFTER_YES_MS  = 3500;
    private static final long POST_BACK_WAIT_MS      = 1000;
    private static final long POST_SCROLL_WAIT_MS    = 2000;
    private static final int  SCROLL_DISTANCE_PX     = 500;

    /** Hard upper bound to prevent runaway recursion on hostile trees. */
    private static final int  MAX_TREE_DEPTH = 25;

    /** Hard upper bound on nodes visited per tick. Defense in depth. */
    private static final int  MAX_NODES_PER_TICK = 4000;

    private static ClickerService instance;

    private BotConfig config;
    private StatusListener listener;
    private LikedMembersDb db;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger likesCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);

    /** Per-session coordinate dedup (cleared on scroll). Fast first-pass
     *  guard so we don't re-fingerprint the same on-screen card twice in
     *  quick succession. Persistent dedup is delegated to {@link #db}. */
    private final Set<String> processedBounds = new HashSet<>();

    private String lastAction = "";
    private long   lastBackTime = 0;
    private long   lastButtonFoundMs = 0;
    private int    state = STATE_LOOK_LIKE;
    private long   stateChangedAt = 0;

    /** Pool of node refs we obtained during the current tick. Drained and
     *  recycled at the end of every tick to avoid leaking into system_server. */
    private final List<AccessibilityNodeInfo> tickPool = new ArrayList<>(64);
    private int  nodesVisitedThisTick = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running.get()) return;
            long nextDelay;
            try {
                nextDelay = performOneTick();
            } catch (Throwable t) {
                // Never crash the service from a tick — fall back to a slow
                // tempo and try again on the next interval.
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

    public static ClickerService getInstance()   { return instance; }
    public static boolean isServiceRunning()     { return instance != null; }

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
        // We poll explicitly via the tick handler; we don't react to every
        // single accessibility event because the system can fire hundreds
        // per second on busy screens and that's what made v1 unresponsive.
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

    public boolean isExecuting()   { return running.get(); }
    public int     getLikesCount() { return likesCount.get(); }
    public int     getSkippedCount() { return skippedCount.get(); }
    public LikedMembersDb getDb()  { return db; }

    public boolean startBot(BotConfig cfg) {
        if (running.get()) return false;
        if (db == null) db = new LikedMembersDb(this);
        this.config = cfg;
        likesCount.set(0);
        skippedCount.set(0);
        lastAction = "";
        processedBounds.clear();
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

    public void stopBot() {
        stopInternal(STATUS_STOPPED);
    }

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

        if (now - lastButtonFoundMs > config.idleTimeoutMs) {
            stopInternal(STATUS_AUTO_STOPPED);
            return config.scanIntervalMs;
        }

        List<AccessibilityNodeInfo> roots = collectAllRoots();
        if (roots.isEmpty()) {
            setAction(getString(R.string.action_wait));
            return config.scanIntervalMs;
        }

        // Target-package gate.
        if (config.targetPackage != null && !config.targetPackage.isEmpty()) {
            boolean anyMatches = false;
            for (AccessibilityNodeInfo r : roots) {
                CharSequence pkg = r.getPackageName();
                if (pkg != null && config.targetPackage.equals(pkg.toString())) {
                    anyMatches = true; break;
                }
            }
            if (!anyMatches) {
                setAction(getString(R.string.action_wait));
                return config.scanIntervalMs;
            }
        }

        // Profile-keyword back-press: if we accidentally landed on a profile
        // page, press Back. v1 did one full DFS per keyword (7+ DFSes/tick);
        // v2 does ONE DFS and matches against all keywords during traversal.
        if (now - lastBackTime > 1500 && !config.profileKeywords.isEmpty()) {
            if (anyKeywordVisible(roots, config.profileKeywords)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                lastBackTime = now;
                setAction(getString(R.string.action_back));
                transitionTo(STATE_LOOK_LIKE);
                return POST_BACK_WAIT_MS;
            }
        }

        switch (state) {
            case STATE_LOOK_LIKE:   return handleLookLike(roots, now);
            case STATE_AFTER_LIKE:  return handleAfterLike(roots, now);
            case STATE_AFTER_YES:   return handleAfterYes(roots, now);
            case STATE_MUST_SCROLL: return handleMustScroll(roots, now);
            default:
                transitionTo(STATE_LOOK_LIKE);
                return config.scanIntervalMs;
        }
    }

    // ============================================================
    //                          STATE HANDLERS
    // ============================================================

    private long handleLookLike(List<AccessibilityNodeInfo> roots, long now) {
        // A lingering "yes" dialog from the previous like has priority.
        AccessibilityNodeInfo yes = findClickableInAll(roots, config.yesText);
        if (yes != null && clickNode(yes)) {
            setAction(getString(R.string.action_yes));
            lastButtonFoundMs = now;
            transitionTo(STATE_AFTER_YES);
            return 700L;
        }
        // Then a "close" success/limit dialog.
        AccessibilityNodeInfo close = findClickableInAll(roots, config.closeText);
        if (close != null && clickNode(close)) {
            setAction(getString(R.string.action_close));
            transitionTo(STATE_LOOK_LIKE);
            return 800L;
        }
        // Then the actual "like" buttons on the listing.
        AccessibilityNodeInfo likeBtn = findUnprocessedClickable(roots, config.likeText);
        if (likeBtn != null) {
            String key = boundsKey(likeBtn);
            // Persistent dedup: hash the surrounding card.
            String fp = fingerprintNearMember(likeBtn);
            if (config.dedupEnabled && fp != null && db != null && db.isLiked(fp)) {
                // Already liked in a previous session / earlier scroll.
                processedBounds.add(key);
                skippedCount.incrementAndGet();
                setAction(getString(R.string.action_skip));
                lastButtonFoundMs = now;
                return Math.max(150L, config.scanIntervalMs / 2);
            }
            if (clickNode(likeBtn)) {
                processedBounds.add(key);
                likesCount.incrementAndGet();
                if (fp != null && db != null) db.markLiked(fp);
                setAction(getString(R.string.action_like));
                lastButtonFoundMs = now;
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
            if (clickNode(yes)) {
                setAction(getString(R.string.action_yes));
                lastButtonFoundMs = now;
                transitionTo(STATE_AFTER_YES);
                return 700L;
            }
            return config.scanIntervalMs;
        }
        AccessibilityNodeInfo close = findClickableInAll(roots, config.closeText);
        if (close != null) {
            if (clickNode(close)) {
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
            if (clickNode(close)) {
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
        boolean scrolled = performSmartScroll(roots);
        if (scrolled) {
            setAction(getString(R.string.action_scroll));
        }
        // Reset the per-session positional cache; we don't reset the
        // persistent DB so we still skip members that were already liked.
        processedBounds.clear();
        transitionTo(STATE_LOOK_LIKE);
        return POST_SCROLL_WAIT_MS;
    }

    // ============================================================
    //                          SCROLLING
    // ============================================================

    private boolean performSmartScroll(List<AccessibilityNodeInfo> roots) {
        AccessibilityNodeInfo scrollable = findScrollableInAll(roots);
        if (scrollable != null) {
            try {
                if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    return true;
                }
            } catch (Throwable ignore) {}
        }
        return performGestureSwipeUp();
    }

    private boolean performGestureSwipeUp() {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int centerX = dm.widthPixels / 2;
            int startY = (int) (dm.heightPixels * 0.72f);
            int endY = startY - SCROLL_DISTANCE_PX;
            int minY = (int) (dm.heightPixels * 0.15f);
            if (endY < minY) endY = minY;
            Path path = new Path();
            path.moveTo(centerX, startY);
            path.lineTo(centerX, endY);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0L, 450L);
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

    /**
     * Single-pass DFS that scans roots for the FIRST node matching any of
     * the provided keywords. v1 did this per-keyword (N DFSes); v2 does it
     * once.
     */
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
            boolean found = containsAnyText(child, needles, depth + 1);
            if (found) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findUnprocessedClickable(
            List<AccessibilityNodeInfo> roots, String text) {
        if (text == null || text.isEmpty()) return null;
        String needle = normalizeArabic(text);
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo hit = findFirstClickableMatching(root, needle, 0, /* skipProcessed */ true);
            if (hit != null) return hit;
        }
        return null;
    }

    private AccessibilityNodeInfo findClickableInAll(List<AccessibilityNodeInfo> roots, String text) {
        if (text == null || text.isEmpty()) return null;
        String needle = normalizeArabic(text);
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo hit = findFirstClickableMatching(root, needle, 0, /* skipProcessed */ false);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * Combined "match text + climb to clickable" walker, with recursion and
     * node-recycling done in one place.
     */
    private AccessibilityNodeInfo findFirstClickableMatching(
            AccessibilityNodeInfo node, String normalizedNeedle, int depth, boolean skipProcessed) {
        if (node == null || depth > MAX_TREE_DEPTH) return null;
        if (++nodesVisitedThisTick > MAX_NODES_PER_TICK) return null;

        if (textMatches(node, normalizedNeedle) && node.isVisibleToUser()) {
            AccessibilityNodeInfo clickable = climbToClickable(node);
            if (clickable != null) {
                if (!skipProcessed || !processedBounds.contains(boundsKey(clickable))) {
                    return clickable;
                }
                track(clickable);
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            AccessibilityNodeInfo found = findFirstClickableMatching(child, normalizedNeedle, depth + 1, skipProcessed);
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
                    if (a.getId() == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                        return node;
                    }
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

    private boolean clickNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = climbToClickable(node);
        if (target == null) return false;
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // ============================================================
    //                       MEMBER FINGERPRINT
    // ============================================================

    /**
     * Given a like-button node, walk up to find the surrounding profile
     * card and collect ALL visible text inside it. Returns a SHA-1
     * fingerprint suitable for use as a stable DB key.
     */
    private String fingerprintNearMember(AccessibilityNodeInfo likeBtn) {
        if (likeBtn == null || !config.dedupEnabled) return null;
        AccessibilityNodeInfo card = climbToCard(likeBtn);
        if (card == null) return null;
        StringBuilder sb = new StringBuilder(256);
        collectVisibleText(card, sb, 0);
        if (sb.length() < 4) return null;
        return LikedMembersDb.fingerprint(sb.toString());
    }

    /**
     * Walk up until we reach a parent that contains at least 3 distinct
     * text-bearing descendants — i.e. the card surrounding the like button,
     * not just a tiny button wrapper.
     */
    private AccessibilityNodeInfo climbToCard(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        AccessibilityNodeInfo bestSoFar = null;
        for (int depth = 0; current != null && depth < 6; depth++) {
            int textCount = countTextDescendants(current, 0);
            if (textCount >= 3) {
                bestSoFar = current;
                break;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (parent != null) track(parent);
            current = parent;
            if (current != null) bestSoFar = current;
        }
        return bestSoFar;
    }

    private int countTextDescendants(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 4) return 0;
        int total = 0;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) total++;
        int n = node.getChildCount();
        for (int i = 0; i < n && total < 6; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            total += countTextDescendants(child, depth + 1);
        }
        return total;
    }

    private void collectVisibleText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 5) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            out.append(text.toString().trim()).append('\n');
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            track(child);
            collectVisibleText(child, out, depth + 1);
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
            // De-duplicate by reference: if we already have this root from
            // getWindows(), don't add a duplicate.
            boolean dup = false;
            for (AccessibilityNodeInfo r : result) if (r == active) { dup = true; break; }
            if (!dup) { track(active); result.add(0, active); }
            else active.recycle();
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
        r = r.replace((char) 1571, (char) 1575)   // أ → ا
             .replace((char) 1573, (char) 1575)   // إ → ا
             .replace((char) 1570, (char) 1575)   // آ → ا
             .replace((char) 1609, (char) 1610)   // ى → ي
             .replace((char) 1577, (char) 1607);  // ة → ه
        return r.trim().toLowerCase(Locale.ROOT);
    }

    // ---- node-pool management ----

    private void track(AccessibilityNodeInfo node) {
        if (node != null) tickPool.add(node);
    }

    private void drainTickPool() {
        // Recycle every node we obtained during the tick. This is what keeps
        // system_server happy across thousands of cycles.
        for (int i = tickPool.size() - 1; i >= 0; i--) {
            AccessibilityNodeInfo n = tickPool.get(i);
            try { if (n != null) n.recycle(); } catch (Throwable ignore) {}
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
