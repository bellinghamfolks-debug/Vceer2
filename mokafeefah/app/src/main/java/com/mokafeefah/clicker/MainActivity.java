package com.mokafeefah.clicker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    // v1.11 — new selectable modes.
    private static final String K_LIKE_MODE_FILTER = "like_mode_filter";   // false = normal, true = divorced/widowed
    private static final String K_REFRESH_CONTINUE = "refresh_continue";   // false = restart, true = continue

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
    // v1.11
    private RadioGroup  groupLikeMode;
    private RadioButton radioLikeModeNormal;
    private RadioButton radioLikeModeFilter;
    private RadioGroup  groupRefreshMode;
    private RadioButton radioRefreshModeContinue;
    private RadioButton radioRefreshModeRestart;

    private Button       btnToggleAdvanced;
    private LinearLayout advancedSettings;

    private TextView serviceStatusText;
    private TextView execStatusText;
    private TextView counterText;
    private TextView skippedText;
    private TextView lastActionText;

    // v1.12 — saved coordinates UI
    private LinearLayout coordsList;
    private Button       btnClearCoords;
    private SavedCoordinatesDb coordsDb;

    private static final int REQ_RECORD_COORD = 4711;
    private int    pendingCoordX = 0;
    private int    pendingCoordY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        coordsDb = new SavedCoordinatesDb(getApplicationContext());
        coordsDb.seedIfEmpty();
        bindViews();
        loadSavedValues();
        wireButtons();
        refreshCoordsList();
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
        groupLikeMode            = findViewById(R.id.groupLikeMode);
        radioLikeModeNormal      = findViewById(R.id.radioLikeModeNormal);
        radioLikeModeFilter      = findViewById(R.id.radioLikeModeFilter);
        groupRefreshMode         = findViewById(R.id.groupRefreshMode);
        radioRefreshModeContinue = findViewById(R.id.radioRefreshModeContinue);
        radioRefreshModeRestart  = findViewById(R.id.radioRefreshModeRestart);
        btnToggleAdvanced    = findViewById(R.id.btnToggleAdvanced);
        advancedSettings     = findViewById(R.id.advancedSettings);
        serviceStatusText    = findViewById(R.id.serviceStatusText);
        execStatusText       = findViewById(R.id.execStatusText);
        counterText          = findViewById(R.id.counterText);
        skippedText          = findViewById(R.id.skippedText);
        lastActionText       = findViewById(R.id.lastActionText);
        coordsList           = findViewById(R.id.coordsList);
        btnClearCoords       = findViewById(R.id.btnClearCoords);
    }

    private void wireButtons() {
        Button btnStart        = findViewById(R.id.btnStart);
        Button btnStop         = findViewById(R.id.btnStop);
        Button btnOpenAcc      = findViewById(R.id.btnOpenAccessibility);
        Button btnReset        = findViewById(R.id.btnReset);
        Button btnClearHistory = findViewById(R.id.btnClearHistory);
        Button btnShareDiag    = findViewById(R.id.btnShareDiagnostic);

        Button btnRecordCoord  = findViewById(R.id.btnRecordCoord);

        btnStart.setOnClickListener(v -> onStartClicked());
        btnStop.setOnClickListener(v -> onStopClicked());
        btnOpenAcc.setOnClickListener(v -> openAccessibilitySettings());
        btnReset.setOnClickListener(v -> resetDefaults());
        btnClearHistory.setOnClickListener(v -> confirmClearHistory());
        btnShareDiag.setOnClickListener(v -> shareLatestDiagnostic());
        btnToggleAdvanced.setOnClickListener(v -> toggleAdvancedSettings());
        Button btnSeedDefaults = findViewById(R.id.btnSeedDefaults);
        btnRecordCoord.setOnClickListener(v -> startCoordRecording());
        btnSeedDefaults.setOnClickListener(v -> {
            int added = coordsDb.seedMissing();
            if (added > 0) {
                toast(getString(R.string.msg_seed_added, added));
                refreshCoordsList();
            } else {
                toast(getString(R.string.msg_seed_already));
            }
        });
        btnClearCoords.setOnClickListener(v -> confirmClearAllCoords());
        checkDedup.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean(K_DEDUP_ENABLED, checked).apply());
        groupLikeMode.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putBoolean(K_LIKE_MODE_FILTER,
                        id == R.id.radioLikeModeFilter).apply());
        groupRefreshMode.setOnCheckedChangeListener((g, id) ->
                prefs.edit().putBoolean(K_REFRESH_CONTINUE,
                        id == R.id.radioRefreshModeContinue).apply());
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
        boolean filterMode = radioLikeModeFilter != null && radioLikeModeFilter.isChecked();
        boolean refreshContinue = radioRefreshModeContinue != null && radioRefreshModeContinue.isChecked();
        ClickerService.BotConfig cfg = new ClickerService.BotConfig(
                like, yes, close, pkg, keywords,
                interval, popup, idleS * 1000L, dedup,
                filterMode, refreshContinue);

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
        if (radioLikeModeNormal != null) radioLikeModeNormal.setChecked(true);
        if (radioRefreshModeContinue != null) radioRefreshModeContinue.setChecked(true);
        saveCurrentValues();
        toast(getString(R.string.msg_reset_done));
    }

    private void shareLatestDiagnostic() {
        ClickerService svc = ClickerService.getInstance();
        String path = svc == null ? null : svc.getLastDumpPath();
        if (path == null || path.isEmpty() || !path.startsWith("/")) {
            // No dump yet — fall back to the newest file in the external dir.
            java.io.File dir = getExternalFilesDir(null);
            java.io.File newest = null;
            if (dir != null && dir.isDirectory()) {
                java.io.File[] files = dir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        if (!f.getName().startsWith("dump_")) continue;
                        if (newest == null || f.lastModified() > newest.lastModified()) {
                            newest = f;
                        }
                    }
                }
            }
            if (newest == null) {
                toast(getString(R.string.msg_no_diagnostic));
                return;
            }
            path = newest.getAbsolutePath();
        }
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            toast(getString(R.string.msg_no_diagnostic));
            return;
        }
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,
                    getString(R.string.share_diagnostic_title)));
        } catch (Throwable t) {
            toast(getString(R.string.msg_share_failed) + ": " + t.getMessage());
        }
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
        boolean filterMode = prefs.getBoolean(K_LIKE_MODE_FILTER, false);
        if (filterMode) radioLikeModeFilter.setChecked(true);
        else            radioLikeModeNormal.setChecked(true);
        boolean refreshContinue = prefs.getBoolean(K_REFRESH_CONTINUE, true);
        if (refreshContinue) radioRefreshModeContinue.setChecked(true);
        else                 radioRefreshModeRestart.setChecked(true);
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
        if (radioLikeModeFilter != null) {
            e.putBoolean(K_LIKE_MODE_FILTER, radioLikeModeFilter.isChecked());
        }
        if (radioRefreshModeContinue != null) {
            e.putBoolean(K_REFRESH_CONTINUE, radioRefreshModeContinue.isChecked());
        }
        e.apply();
    }

    // ----- Saved coordinates (v1.12) -----

    private void startCoordRecording() {
        Intent i = new Intent(this, RecordCoordinateActivity.class);
        startActivityForResult(i, REQ_RECORD_COORD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_RECORD_COORD) return;
        if (resultCode != RESULT_OK || data == null) {
            toast(getString(R.string.msg_coord_canceled));
            return;
        }
        pendingCoordX = data.getIntExtra(RecordCoordinateActivity.EXTRA_X, 0);
        pendingCoordY = data.getIntExtra(RecordCoordinateActivity.EXTRA_Y, 0);
        promptNameAndSave();
    }

    private void promptNameAndSave() {
        EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.dialog_name_coord_hint);
        nameInput.setMinHeight(dp(56));
        int pad = dp(8);
        nameInput.setPadding(pad, pad, pad, pad);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int sidePad = dp(20);
        container.setPadding(sidePad, dp(8), sidePad, 0);
        TextView pos = new TextView(this);
        pos.setText(getString(R.string.dialog_name_coord_position, pendingCoordX, pendingCoordY));
        pos.setTextSize(15);
        pos.setPadding(0, 0, 0, dp(12));
        container.addView(pos);
        container.addView(nameInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_name_coord_title)
                .setView(container)
                .setPositiveButton(R.string.save_coord, (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        toast(getString(R.string.msg_coord_name_required));
                        return;
                    }
                    coordsDb.upsert(name, pendingCoordX, pendingCoordY);
                    toast(getString(R.string.msg_coord_saved, name));
                    refreshCoordsList();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refreshCoordsList() {
        if (coordsList == null) return;
        coordsList.removeAllViews();
        List<SavedCoordinatesDb.Coord> all = coordsDb.listAll();
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.msg_no_coords);
            empty.setTextSize(14);
            empty.setPadding(dp(12), dp(8), dp(12), dp(8));
            empty.setContentDescription(getString(R.string.msg_no_coords));
            coordsList.addView(empty);
            if (btnClearCoords != null) btnClearCoords.setVisibility(View.GONE);
            return;
        }
        for (SavedCoordinatesDb.Coord c : all) coordsList.addView(buildCoordRow(c));
        if (btnClearCoords != null) btnClearCoords.setVisibility(View.VISIBLE);
    }

    private View buildCoordRow(SavedCoordinatesDb.Coord c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        row.setPadding(pad, pad, pad, pad);
        row.setBackgroundResource(R.color.surface_elevated);
        ViewGroup.MarginLayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView name = new TextView(this);
        name.setText(c.name);
        name.setTextSize(16);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);

        TextView xy = new TextView(this);
        xy.setText(getString(R.string.coord_xy_format, c.x, c.y));
        xy.setTextSize(14);
        xy.setPadding(0, dp(2), 0, dp(8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.START);

        Button testBtn = new Button(this);
        testBtn.setText(R.string.btn_test_coord);
        testBtn.setContentDescription(getString(R.string.btn_test_coord) + " " + c.name);
        testBtn.setMinHeight(dp(48));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMarginEnd(dp(6));
        testBtn.setLayoutParams(tlp);
        testBtn.setOnClickListener(v -> performTestTap(c));

        Button delBtn = new Button(this);
        delBtn.setText(R.string.btn_delete_coord);
        delBtn.setContentDescription(getString(R.string.btn_delete_coord) + " " + c.name);
        delBtn.setMinHeight(dp(48));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        delBtn.setLayoutParams(dlp);
        delBtn.setOnClickListener(v -> confirmDeleteCoord(c));

        actions.addView(testBtn);
        actions.addView(delBtn);

        row.addView(name);
        row.addView(xy);
        row.addView(actions);
        return row;
    }

    private void performTestTap(SavedCoordinatesDb.Coord c) {
        ClickerService svc = ClickerService.getInstance();
        if (svc == null) {
            toast(getString(R.string.msg_tap_failed));
            return;
        }
        boolean ok = svc.tapAt(c.x, c.y);
        toast(ok ? getString(R.string.msg_tap_done, c.name)
                 : getString(R.string.msg_tap_failed));
    }

    private void confirmDeleteCoord(SavedCoordinatesDb.Coord c) {
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setMessage(R.string.confirm_delete_coord)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    coordsDb.deleteById(c.id);
                    refreshCoordsList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClearAllCoords() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_clear_coords_title)
                .setMessage(R.string.confirm_clear_coords_msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    coordsDb.clearAll();
                    toast(getString(R.string.msg_coords_cleared));
                    refreshCoordsList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int dp(int v) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (v * density + 0.5f);
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
