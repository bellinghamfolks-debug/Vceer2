package com.basir.ai.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.basir.ai.R;

import java.util.Locale;

/**
 * The actual gameplay screen. Hosts a {@link WorldView}, a transparent
 * gesture surface on top, a HUD strip, and dialog modals for NPC choices.
 *
 * Inputs (all mapped on the same surface, so the player never hunts for
 * a button):
 *   swipe up    → step forward      swipe down → stop / re-announce
 *   swipe left  → turn left         swipe right → turn right
 *   double tap  → interact          long press  → describe environment
 *   shake       → re-orient
 *
 * A small bottom row of large buttons is kept for sighted users and
 * TalkBack focus — same semantics as the gestures.
 */
public class BlindLifeGameActivity extends Activity
        implements TextToSpeech.OnInitListener,
                   GameEngine.Listener,
                   SensorEventListener {

    private TextToSpeech tts;
    private GameEngine engine;
    private GameState state;
    private WorldView world;
    private TextView log;
    private TextView statsView;
    private GestureDetector gestures;
    private SensorManager sensors;
    private Sensor accel;
    private long lastShake;
    private float[] lastG = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        state = GameState.load(this);
        tts = new TextToSpeech(this, this);

        setContentView(buildUi());

        engine = new GameEngine(this, state, tts);
        engine.setListener(this);

        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensors != null) accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        setupGestures();

        if (state.firstRun) {
            state.firstRun = false;
            state.save(this);
            new Handler(Looper.getMainLooper()).postDelayed(this::firstRunIntro, 400);
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(engine::announceScene, 350);
        }
    }

    private void firstRunIntro() {
        appendLog("استيقظت في غرفة نومك. اليوم الأول.");
        engine.announceScene();
        Toast.makeText(this,
                "اسحب للأعلى للمشي، اضغط مطوّلًا لوصف ما حولك",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            try {
                tts.setLanguage(new Locale("ar"));
                tts.setSpeechRate(0.95f);
            } catch (Throwable ignored) {}
        }
    }

    // ---------- UI ----------

    private View buildUi() {
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.BLACK);

        world = new WorldView(this);
        container.addView(world, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Top stats bar
        statsView = new TextView(this);
        statsView.setText("");
        statsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statsView.setTextColor(Color.WHITE);
        statsView.setBackgroundColor(Color.argb(180, 0, 0, 0));
        statsView.setPadding(dp(14), dp(8), dp(14), dp(8));
        statsView.setContentDescription("شريط الإحصائيات");
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.TOP;
        container.addView(statsView, sp);

        // Spoken-line log just above the controls
        log = new TextView(this);
        log.setText("جاهز.");
        log.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        log.setTextColor(Color.WHITE);
        log.setBackgroundColor(Color.argb(180, 0, 0, 0));
        log.setPadding(dp(14), dp(12), dp(14), dp(12));
        log.setMaxLines(3);
        log.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        log.setContentDescription("آخر وصف");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        lp.bottomMargin = dp(220);
        container.addView(log, lp);

        // Big controls row at the bottom (also accessible via gestures)
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackgroundColor(Color.argb(220, 0, 0, 0));
        controls.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(actionButton("⤴", "للأمام", () -> engine.stepForward()));
        row1.addView(actionButton("←", "يسار", () -> engine.turnLeft()));
        row1.addView(actionButton("→", "يمين", () -> engine.turnRight()));
        controls.addView(row1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(actionButton("◯", "تفاعل", () -> engine.interact()));
        row2.addView(actionButton("👂", "وصف", () -> engine.describeEnvironment()));
        row2.addView(actionButton("⏸", "توقّف", () -> engine.stop()));
        controls.addView(row2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(actionButton("📊", "حالتي", this::showStats));
        row3.addView(actionButton("🌙", "نهاية اليوم", this::confirmEndDay));
        row3.addView(actionButton("⏏", "خروج", this::confirmExit));
        controls.addView(row3, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.BOTTOM;
        container.addView(controls, cp);

        return container;
    }

    private View actionButton(String icon, String label, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(6), dp(10), dp(6), dp(10));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setMinimumHeight(dp(64));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.argb(255, 18, 28, 58));
        bg.setStroke(dp(1), Color.argb(255, 70, 90, 130));
        bg.setCornerRadius(dp(14));
        cell.setBackground(bg);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        ic.setGravity(Gravity.CENTER);
        ic.setTextColor(Color.WHITE);
        ic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cell.addView(ic);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.WHITE);
        cell.addView(tv);

        cell.setContentDescription(label);
        cell.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        cell.setLayoutParams(lp);
        return cell;
    }

    // ---------- Gestures ----------

    private void setupGestures() {
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapUp(MotionEvent e) { return false; }

            @Override public boolean onDoubleTap(MotionEvent e) {
                engine.interact();
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                engine.describeEnvironment();
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) < 60 && Math.abs(dy) < 60) return false;
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) engine.turnRight();
                    else        engine.turnLeft();
                } else {
                    if (dy > 0) engine.stop();
                    else        engine.stepForward();
                }
                return true;
            }
        });
        world.setOnTouchListener((v, e) -> {
            gestures.onTouchEvent(e);
            return true;
        });
        world.setContentDescription(
                "ساحة اللعب. اسحب للأعلى لتمشي، اسحب يمين أو يسار للاستدارة، "
              + "اضغط مطوّلًا لتسمع وصف ما حولك، انقر مرتين للتفاعل.");
    }

    // ---------- Listener callbacks ----------

    @Override
    public void onSpeak(String arabic) {
        appendLog(arabic);
    }

    @Override
    public void onState(GameState s, Scene scene) {
        world.setState(scene, s.playerX, s.playerY, s.facing,
                s.vision, engine.hudLine());
        statsView.setText("اليوم " + s.day
                + "  •  ⚡ " + s.energy
                + "  •  💰 " + s.money
                + "  •  🚶 " + s.mobility
                + "  •  💬 " + s.communication
                + "  •  🔧 " + s.technology
                + "  •  💪 " + s.confidence);
        statsView.setContentDescription(
                "اليوم " + s.day + ". الطاقة " + s.energy
              + ". المال " + s.money + ". مهارة الحركة " + s.mobility
              + ". مهارة التواصل " + s.communication
              + ". مهارة التقنية " + s.technology
              + ". الثقة " + s.confidence);
        state = s;
        state.save(this);
    }

    @Override
    public void onDialogue(Dialogue.Node node) {
        // Always render dialog choices as a vertical list of large
        // buttons. Works for any number of choices, is easier for
        // TalkBack to read, and gives every option a 48dp touch target.
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(12), dp(20), dp(20));

        TextView prompt = new TextView(this);
        prompt.setText(node.prompt);
        prompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        prompt.setTextColor(getColor(R.color.basir_text));
        prompt.setPadding(0, dp(4), 0, dp(16));
        prompt.setLineSpacing(dp(2), 1.3f);
        prompt.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        if (Build.VERSION.SDK_INT >= 28) prompt.setAccessibilityHeading(true);
        list.addView(prompt);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("حوار")
                .setView(list)
                .setCancelable(true)
                .create();

        for (final Dialogue.Choice c : node.choices) {
            Button btn = new Button(this);
            btn.setText(c.label);
            btn.setAllCaps(false);
            btn.setMinHeight(dp(56));
            btn.setOnClickListener(v -> {
                d.dismiss();
                engine.applyChoice(c);
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.bottomMargin = dp(8);
            list.addView(btn, bp);
        }
        d.show();
    }

    @Override
    public void onMessage(String arabic) {
        Toast.makeText(this, arabic, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onGameOver(String arabic) {
        new AlertDialog.Builder(this)
                .setTitle("نهاية اللعبة")
                .setMessage(arabic)
                .setPositiveButton("القائمة", (d, w) -> finish())
                .show();
    }

    private void appendLog(String s) {
        log.setText(s);
    }

    // ---------- Bottom button helpers ----------

    private void showStats() {
        String body = "اليوم: " + state.day
                + "\nالطاقة: " + state.energy
                + "\nالمال: " + state.money
                + "\nمهارة الحركة: " + state.mobility
                + "\nمهارة التواصل: " + state.communication
                + "\nمهارة التقنية: " + state.technology
                + "\nالثقة بالنفس: " + state.confidence
                + "\nالصداقة: " + state.friendship
                + "\nالسمعة: " + state.reputation
                + "\nأجهزة معك: " + (state.devices.isEmpty() ? "لا شيء" : String.join("، ", state.devices))
                + "\nوضع الرؤية: " + state.vision.arabicName();
        new AlertDialog.Builder(this)
                .setTitle("حالتي")
                .setMessage(body)
                .setPositiveButton("إغلاق", null)
                .show();
    }

    private void confirmEndDay() {
        new AlertDialog.Builder(this)
                .setTitle("نهاية اليوم")
                .setMessage("ستذهب للنوم. ترتفع طاقتك ويبدأ يوم جديد.")
                .setPositiveButton("نم الآن", (d, w) -> {
                    engine.endDay();
                    engine.travelTo("bedroom", "غرفة النوم");
                })
                .setNegativeButton("لا، تابع", null)
                .show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("الخروج من اللعبة")
                .setMessage("تقدّمك محفوظ تلقائيًا. هل تريد الخروج؟")
                .setPositiveButton("خروج", (d, w) -> finish())
                .setNegativeButton("لا", null)
                .show();
    }

    // ---------- Sensors (shake to reorient) ----------

    @Override protected void onResume() {
        super.onResume();
        if (sensors != null && accel != null) {
            sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override protected void onPause() {
        if (sensors != null) sensors.unregisterListener(this);
        if (state != null) state.save(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float lx = lastG[0], ly = lastG[1], lz = lastG[2];
        lastG[0] = x; lastG[1] = y; lastG[2] = z;
        float delta = Math.abs(x - lx) + Math.abs(y - ly) + Math.abs(z - lz);
        long now = System.currentTimeMillis();
        if (delta > 22f && now - lastShake > 1200) {
            lastShake = now;
            engine.reorient();
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    @Override protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
