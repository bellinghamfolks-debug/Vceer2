package com.mokafeefah.clicker;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.13.0 — minimal control surface.
 *
 * Earlier versions exposed 30+ fields, coordinate recording, advanced
 * settings, and a diagnostic share button. None of that is needed for
 * day-to-day use by a blind user; it just added TalkBack noise and
 * surface area for bugs. This activity now shows status + counters and
 * exposes three buttons: start, stop, open-accessibility, plus
 * defaults-reset and a clear-history button.
 *
 * Bot configuration is hard-wired to safe values inside onStartClicked.
 */
public class MainActivity extends AppCompatActivity {

    // Bot defaults — non-configurable in v1.13.0.
    private static final String DEF_LIKE   = "إهتمام";
    private static final String DEF_YES    = "نعم";
    private static final String DEF_CLOSE  = "إغلاق";
    private static final String DEF_PKG    = "mawada.net.app";
    private static final String DEF_KW     = "المؤهل التعليمي,الوزن,الطول,تاريخ الميلاد,تاريخ التسجيل,مواصفات زوجي,إبلاغ";
    private static final long   DEF_SCAN_INTERVAL = 350L;
    private static final long   DEF_POPUP_WAIT    = 900L;
    private static final long   DEF_IDLE_TIMEOUT_MS = 60_000L;
    private static final String DEF_FIND_MODE = "three_dots";

    private TextView serviceStatusText;
    private TextView execStatusText;
    private TextView counterText;
    private TextView skippedText;
    private TextView lastActionText;

    // v1.13.0 — TalkBack noise reduction. We only announce the
    // like-count milestone every 10 likes; the views themselves are
    // no longer live regions.
    private int lastAnnouncedLikes = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        wireButtons();
    }

    private void bindViews() {
        serviceStatusText = findViewById(R.id.serviceStatusText);
        execStatusText    = findViewById(R.id.execStatusText);
        counterText       = findViewById(R.id.counterText);
        skippedText       = findViewById(R.id.skippedText);
        lastActionText    = findViewById(R.id.lastActionText);
    }

    private void wireButtons() {
        Button btnStart        = findViewById(R.id.btnStart);
        Button btnStop         = findViewById(R.id.btnStop);
        Button btnOpenAcc      = findViewById(R.id.btnOpenAccessibility);
        Button btnReset        = findViewById(R.id.btnReset);
        Button btnClearHistory = findViewById(R.id.btnClearHistory);

        btnStart.setOnClickListener(v -> onStartClicked());
        btnStop.setOnClickListener(v -> onStopClicked());
        btnOpenAcc.setOnClickListener(v -> openAccessibilitySettings());
        btnReset.setOnClickListener(v -> toast(getString(R.string.msg_reset_done)));
        btnClearHistory.setOnClickListener(v -> confirmClearHistory());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServiceStatus();
        ClickerService svc = ClickerService.getInstance();
        if (svc != null) {
            svc.setStatusListener((status, likes, skipped, lastAction) ->
                    runOnUiThread(() -> updateLiveStatus(status, likes, skipped, lastAction)));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ClickerService svc = ClickerService.getInstance();
        if (svc != null) svc.setStatusListener(null);
    }

    private void refreshServiceStatus() {
        boolean enabled = ClickerService.isServiceRunning();
        String text = getString(enabled
                ? R.string.service_status_enabled
                : R.string.service_status_disabled);
        serviceStatusText.setText(text);
        serviceStatusText.setContentDescription(text);
    }

    private void updateLiveStatus(String status, int likes, int skipped, String lastAction) {
        String prev = execStatusText.getText() == null ? "" : execStatusText.getText().toString();
        execStatusText.setText(status);
        execStatusText.setContentDescription(status);

        counterText.setText(String.valueOf(likes));
        counterText.setContentDescription(getString(R.string.counter_label) + ": " + likes);

        skippedText.setText(String.valueOf(skipped));
        skippedText.setContentDescription(getString(R.string.skipped_label) + ": " + skipped);

        if (lastAction != null && !lastAction.isEmpty()) {
            lastActionText.setText(lastAction);
            lastActionText.setContentDescription(lastAction);
        }

        // v1.13.0 — announce a milestone every 10 likes instead of letting
        // every counter update fire a live-region event.
        if (likes > 0 && likes % 10 == 0 && likes != lastAnnouncedLikes) {
            View root = findViewById(android.R.id.content);
            if (root != null) {
                root.announceForAccessibility("تم الوصول إلى " + likes + " إعجاب");
            }
            lastAnnouncedLikes = likes;
        }

        if (ClickerService.STATUS_AUTO_STOPPED.equals(status)
                && !ClickerService.STATUS_AUTO_STOPPED.equals(prev)) {
            toast(getString(R.string.msg_auto_stopped));
        }
    }

    private void onStartClicked() {
        ClickerService svc = ClickerService.getInstance();
        if (svc == null) { toast(getString(R.string.msg_service_off)); return; }
        if (svc.isExecuting()) { toast(getString(R.string.msg_already_running)); return; }

        List<String> keywords = new ArrayList<>();
        for (String part : DEF_KW.split("[،,]")) {
            String t = part.trim();
            if (!t.isEmpty()) keywords.add(t);
        }

        ClickerService.BotConfig cfg = new ClickerService.BotConfig(
                DEF_LIKE, DEF_YES, DEF_CLOSE,
                DEF_PKG, keywords,
                DEF_SCAN_INTERVAL, DEF_POPUP_WAIT, DEF_IDLE_TIMEOUT_MS,
                /*dedupEnabled=*/ true,
                /*filterDivorcedWidowedOnly=*/ false,
                DEF_FIND_MODE);

        boolean ok = svc.startBot(cfg);
        if (ok) lastAnnouncedLikes = 0;
        toast(getString(ok ? R.string.msg_started : R.string.msg_already_running));
    }

    private void onStopClicked() {
        ClickerService svc = ClickerService.getInstance();
        if (svc == null) {
            toast(getString(R.string.msg_service_off));
            return;
        }
        svc.stopBot();
        toast(getString(R.string.msg_stopped));
    }

    private void openAccessibilitySettings() {
        Intent i = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_clear_history)
                .setMessage(R.string.label_dedup_desc)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    LikedMembersDb db = new LikedMembersDb(getApplicationContext());
                    ClickerService svc = ClickerService.getInstance();
                    if (svc != null && svc.getDb() != null) svc.getDb().clearAll();
                    db.clearAll();
                    toast(getString(R.string.msg_history_cleared));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        View root = findViewById(android.R.id.content);
        if (root != null) root.announceForAccessibility(text);
    }
}
