package com.mokafeefah.clicker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "mokafeefah_prefs_v2";

    // Defaults
    private static final String DEF_LIKE   = "إهتمام";
    private static final String DEF_YES    = "نعم";
    private static final String DEF_CLOSE  = "إغلاق";
    private static final String DEF_TARGET = "";
    private static final String DEF_KW     = "المؤهل التعليمي,الوزن,الطول,تاريخ الميلاد,تاريخ التسجيل,مواصفات زوجي,إبلاغ";
    private static final long   DEF_SCAN_INTERVAL = 250;
    private static final long   DEF_POPUP_WAIT    = 1500;
    private static final long   DEF_IDLE_TIMEOUT  = 30;

    // SharedPreferences keys
    private static final String K_LIKE_TEXT       = "like_text";
    private static final String K_YES_TEXT        = "yes_text";
    private static final String K_CLOSE_TEXT      = "close_text";
    private static final String K_TARGET_PKG      = "target_pkg";
    private static final String K_PROFILE_KW      = "profile_keywords";
    private static final String K_SCAN_INTERVAL   = "scan_interval";
    private static final String K_POPUP_WAIT      = "popup_wait";
    private static final String K_IDLE_TIMEOUT    = "idle_timeout";
    private static final String K_DEDUP_ENABLED   = "dedup_enabled";
    private static final String K_ADVANCED_OPEN   = "advanced_open";

    private SharedPreferences prefs;

    private EditText inputLikeText;
    private EditText inputYesText;
    private EditText inputCloseText;
    private EditText inputTargetPackage;
    private EditText inputProfileKeywords;
    private EditText inputScanInterval;
    private EditText inputPopupWait;
    private EditText inputIdleTimeout;
    private CheckBox checkDedup;

    private Button       btnToggleAdvanced;
    private LinearLayout advancedSettings;

    private TextView serviceStatusText;
    private TextView execStatusText;
    private TextView counterText;
    private TextView skippedText;
    private TextView lastActionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bindViews();
        loadSavedValues();
        wireButtons();
    }

    private void bindViews() {
        inputLikeText        = findViewById(R.id.inputLikeText);
        inputYesText         = findViewById(R.id.inputYesText);
        inputCloseText       = findViewById(R.id.inputCloseText);
        inputTargetPackage   = findViewById(R.id.inputTargetPackage);
        inputProfileKeywords = findViewById(R.id.inputProfileKeywords);
        inputScanInterval    = findViewById(R.id.inputScanInterval);
        inputPopupWait       = findViewById(R.id.inputPopupWait);
        inputIdleTimeout     = findViewById(R.id.inputIdleTimeout);
        checkDedup           = findViewById(R.id.checkDedup);
        btnToggleAdvanced    = findViewById(R.id.btnToggleAdvanced);
        advancedSettings     = findViewById(R.id.advancedSettings);
        serviceStatusText    = findViewById(R.id.serviceStatusText);
        execStatusText       = findViewById(R.id.execStatusText);
        counterText          = findViewById(R.id.counterText);
        skippedText          = findViewById(R.id.skippedText);
        lastActionText       = findViewById(R.id.lastActionText);
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
        btnReset.setOnClickListener(v -> resetDefaults());
        btnClearHistory.setOnClickListener(v -> confirmClearHistory());
        btnToggleAdvanced.setOnClickListener(v -> toggleAdvancedSettings());
        checkDedup.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean(K_DEDUP_ENABLED, checked).apply());
    }

    private void toggleAdvancedSettings() {
        boolean shown = advancedSettings.getVisibility() == View.VISIBLE;
        boolean nowShown = !shown;
        advancedSettings.setVisibility(nowShown ? View.VISIBLE : View.GONE);
        btnToggleAdvanced.setText(nowShown
                ? R.string.btn_hide_advanced
                : R.string.btn_show_advanced);
        btnToggleAdvanced.setContentDescription(getString(nowShown
                ? R.string.btn_hide_advanced
                : R.string.btn_show_advanced));
        prefs.edit().putBoolean(K_ADVANCED_OPEN, nowShown).apply();
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
        saveCurrentValues();
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
        if (ClickerService.STATUS_AUTO_STOPPED.equals(status)
                && !ClickerService.STATUS_AUTO_STOPPED.equals(prev)) {
            toast(getString(R.string.msg_auto_stopped));
        }
    }

    // ----- Button handlers -----

    private void onStartClicked() {
        ClickerService svc = ClickerService.getInstance();
        if (svc == null) { toast(getString(R.string.msg_service_off)); return; }
        if (svc.isExecuting()) { toast(getString(R.string.msg_already_running)); return; }

        String like  = textOf(inputLikeText);
        String yes   = textOf(inputYesText);
        String close = textOf(inputCloseText);
        String pkg   = textOf(inputTargetPackage);
        String kwRaw = textOf(inputProfileKeywords);
        Long interval = parseLong(inputScanInterval);
        Long popup    = parseLong(inputPopupWait);
        Long idleS    = parseLong(inputIdleTimeout);

        if (like.isEmpty() || yes.isEmpty() || close.isEmpty()
                || interval == null || interval < 150
                || popup == null || popup < 300
                || idleS == null || idleS < 5) {
            toast(getString(R.string.msg_invalid_input));
            return;
        }

        List<String> keywords = new ArrayList<>();
        if (!kwRaw.isEmpty()) {
            for (String part : kwRaw.split("[،,]")) {
                String t = part.trim();
                if (!t.isEmpty()) keywords.add(t);
            }
        }
        saveCurrentValues();

        boolean dedup = checkDedup != null && checkDedup.isChecked();
        ClickerService.BotConfig cfg = new ClickerService.BotConfig(
                like, yes, close, pkg, keywords,
                interval, popup, idleS * 1000L, dedup);

        boolean ok = svc.startBot(cfg);
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

    private void resetDefaults() {
        inputLikeText.setText(DEF_LIKE);
        inputYesText.setText(DEF_YES);
        inputCloseText.setText(DEF_CLOSE);
        inputTargetPackage.setText(DEF_TARGET);
        inputProfileKeywords.setText(DEF_KW);
        inputScanInterval.setText(String.valueOf(DEF_SCAN_INTERVAL));
        inputPopupWait.setText(String.valueOf(DEF_POPUP_WAIT));
        inputIdleTimeout.setText(String.valueOf(DEF_IDLE_TIMEOUT));
        if (checkDedup != null) checkDedup.setChecked(true);
        saveCurrentValues();
        toast(getString(R.string.msg_reset_done));
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

    // ----- Persistence -----

    private void loadSavedValues() {
        inputLikeText.setText(prefs.getString(K_LIKE_TEXT, DEF_LIKE));
        inputYesText.setText(prefs.getString(K_YES_TEXT, DEF_YES));
        inputCloseText.setText(prefs.getString(K_CLOSE_TEXT, DEF_CLOSE));
        inputTargetPackage.setText(prefs.getString(K_TARGET_PKG, DEF_TARGET));
        inputProfileKeywords.setText(prefs.getString(K_PROFILE_KW, DEF_KW));
        inputScanInterval.setText(String.valueOf(prefs.getLong(K_SCAN_INTERVAL, DEF_SCAN_INTERVAL)));
        inputPopupWait.setText(String.valueOf(prefs.getLong(K_POPUP_WAIT, DEF_POPUP_WAIT)));
        inputIdleTimeout.setText(String.valueOf(prefs.getLong(K_IDLE_TIMEOUT, DEF_IDLE_TIMEOUT)));
        checkDedup.setChecked(prefs.getBoolean(K_DEDUP_ENABLED, true));
        boolean advOpen = prefs.getBoolean(K_ADVANCED_OPEN, false);
        advancedSettings.setVisibility(advOpen ? View.VISIBLE : View.GONE);
        btnToggleAdvanced.setText(advOpen
                ? R.string.btn_hide_advanced
                : R.string.btn_show_advanced);
        btnToggleAdvanced.setContentDescription(getString(advOpen
                ? R.string.btn_hide_advanced
                : R.string.btn_show_advanced));
    }

    private void saveCurrentValues() {
        SharedPreferences.Editor e = prefs.edit();
        e.putString(K_LIKE_TEXT,  textOf(inputLikeText));
        e.putString(K_YES_TEXT,   textOf(inputYesText));
        e.putString(K_CLOSE_TEXT, textOf(inputCloseText));
        e.putString(K_TARGET_PKG, textOf(inputTargetPackage));
        e.putString(K_PROFILE_KW, textOf(inputProfileKeywords));
        Long iv = parseLong(inputScanInterval); if (iv != null) e.putLong(K_SCAN_INTERVAL, iv);
        Long pw = parseLong(inputPopupWait);    if (pw != null) e.putLong(K_POPUP_WAIT,    pw);
        Long it = parseLong(inputIdleTimeout);  if (it != null) e.putLong(K_IDLE_TIMEOUT,  it);
        if (checkDedup != null) e.putBoolean(K_DEDUP_ENABLED, checkDedup.isChecked());
        e.apply();
    }

    // ----- Tiny utilities -----

    private String textOf(EditText et) {
        return et == null ? "" : et.getText().toString().trim();
    }

    private Long parseLong(EditText et) {
        String s = textOf(et);
        if (TextUtils.isEmpty(s)) return null;
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { return null; }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        View root = findViewById(android.R.id.content);
        if (root != null) root.announceForAccessibility(text);
    }
}
