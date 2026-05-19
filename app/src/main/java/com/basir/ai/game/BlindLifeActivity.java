package com.basir.ai.game;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.basir.ai.R;

/**
 * Main menu for "حياة كفيف". This is the screen the user lands on when
 * they pick the game from Basir's More tab.
 *
 * Designed accessibility-first: every card is a single focusable unit with
 * a meaningful contentDescription, the heading is announced as a heading,
 * touch targets are 96dp tall, and the entire screen is RTL.
 */
public class BlindLifeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(buildRoot());
    }

    private View buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.basir_bg));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("حياة كفيف");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(getColor(R.color.basir_text));
        title.setContentDescription("حياة كفيف، لعبة محاكاة حياة شخص كفيف");
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("لعبة قصّة وحياة. اختر تبويبًا للبدء أو لقراءة دليل المستخدم.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sub.setTextColor(getColor(R.color.basir_text_secondary));
        sub.setPadding(0, dp(6), 0, dp(20));
        sub.setLineSpacing(dp(2), 1.25f);
        root.addView(sub);

        addCard(root, "▶", "ابدأ لعبة جديدة",
                "ابدأ من غرفة النوم في اليوم الأول. اختر وضع الرؤية الذي تريد تجربته.",
                v -> startNewGame());

        if (GameState.hasSave(this)) {
            addCard(root, "↻", "متابعة اللعب",
                    "تابع من آخر مكان وصلت إليه. يتم الحفظ تلقائيًا.",
                    v -> {
                        Intent i = new Intent(this, BlindLifeGameActivity.class);
                        i.putExtra("mode", "continue");
                        startActivity(i);
                    });
        }

        addCard(root, "📖", "دليل المستخدم التفصيلي",
                "كل ما تحتاج معرفته: الإيماءات، أوضاع الرؤية، الاهتزازات، التقدّم في القصة.",
                v -> startActivity(new Intent(this, BlindLifeGuideActivity.class)));

        addCard(root, "👁", "اختيار وضع الرؤية",
                "كفيف كلي، ضعف بصر شديد، رؤية ضبابية، رؤية مركزية، رؤية طرفية، أو وضع مبصر.",
                v -> showVisionPicker());

        addCard(root, "🧹", "حذف الحفظ",
                "حذف تقدمك وبدء من جديد. هذا الإجراء لا يمكن التراجع عنه.",
                v -> confirmWipe());

        addCard(root, "←", "رجوع",
                "العودة إلى الشاشة الرئيسية لتطبيق بصير.",
                v -> finish());

        return scroll;
    }

    private void startNewGame() {
        GameState.clear(this);
        showVisionPicker();
    }

    private void showVisionPicker() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.basir_bg));
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root);

        TextView heading = new TextView(this);
        heading.setText("اختر وضع الرؤية");
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(getColor(R.color.basir_text));
        heading.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        root.addView(heading);

        TextView sub = new TextView(this);
        sub.setText("سيؤثر وضع الرؤية على ما تراه فقط. الصوت والاهتزاز والوصف بصوت عالٍ متاحة في كل الأوضاع.");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sub.setTextColor(getColor(R.color.basir_text_secondary));
        sub.setPadding(0, dp(4), 0, dp(16));
        sub.setLineSpacing(dp(2), 1.25f);
        root.addView(sub);

        for (VisionMode m : VisionMode.values()) {
            addCard(root, modeIcon(m), m.arabicName(), m.arabicDescription(),
                    v -> {
                        GameState g = new GameState();
                        g.vision = m;
                        g.save(this);
                        Intent i = new Intent(this, BlindLifeGameActivity.class);
                        i.putExtra("mode", "new");
                        startActivity(i);
                    });
        }

        addCard(root, "←", "رجوع", "العودة إلى قائمة اللعبة.",
                v -> setContentView(buildRoot()));

        setContentView(scroll);
    }

    private String modeIcon(VisionMode m) {
        switch (m) {
            case BLIND_TOTAL:       return "⚫";
            case LOW_VISION_SEVERE: return "◐";
            case BLURRY:            return "◌";
            case CENTRAL_ONLY:      return "◉";
            case PERIPHERAL_ONLY:   return "◎";
            case SIGHTED:           return "○";
        }
        return "?";
    }

    private void confirmWipe() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("حذف الحفظ")
                .setMessage("هل أنت متأكد من حذف تقدمك؟ لا يمكن التراجع.")
                .setPositiveButton("نعم، احذف", (d, w) -> {
                    GameState.clear(this);
                    Toast.makeText(this, "تم حذف الحفظ", Toast.LENGTH_SHORT).show();
                    setContentView(buildRoot());
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void addCard(LinearLayout root, String icon, String title,
                         String description, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setClickable(true);
        card.setFocusable(true);
        card.setMinimumHeight(dp(96));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(getColor(R.color.basir_surface));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), getColor(R.color.basir_card_stroke));
        card.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));

        if (icon != null) {
            TextView ic = new TextView(this);
            ic.setText(icon);
            ic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            ic.setGravity(Gravity.CENTER);
            ic.setMinWidth(dp(48));
            ic.setMinHeight(dp(48));
            GradientDrawable ibg = new GradientDrawable();
            ibg.setShape(GradientDrawable.OVAL);
            ibg.setColor(getColor(R.color.basir_primary_soft));
            ic.setBackground(ibg);
            ic.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    dp(48), dp(48));
            ip.rightMargin = dp(14);
            ip.leftMargin = dp(0);
            card.addView(ic, ip);
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(getColor(R.color.basir_text));
        texts.addView(tv);

        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setTextColor(getColor(R.color.basir_text_secondary));
        desc.setPadding(0, dp(4), 0, 0);
        desc.setLineSpacing(dp(2), 1.25f);
        texts.addView(desc);

        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(texts, tp);

        card.setContentDescription(title + ". " + description);
        card.setOnClickListener(l);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        root.addView(card, lp);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
