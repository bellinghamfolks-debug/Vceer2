package com.basir.ai.game;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.basir.ai.R;

import java.util.Locale;

/**
 * The detailed in-game user guide for "حياة كفيف". The user asked
 * explicitly: "ضع داخل اللعبة دليل المستخدم التفصيلي جدًا مهم" — so
 * every section here is screen-reader friendly, RTL, has a heading marked
 * as accessibility heading, and a "اقرأ بصوت عالٍ" button that pipes the
 * section text through TTS for users who prefer listening.
 */
public class BlindLifeGuideActivity extends Activity
        implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tts = new TextToSpeech(this, this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.basir_bg));
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(40));
        scroll.addView(root);

        addTitle("دليل المستخدم — حياة كفيف");
        addParagraph(
                "أهلًا بك في 'حياة كفيف'. هذه ليست لعبة تعليمية، بل تجربة حياة. "
              + "ستعيش يومًا في بيت طالب لديه إعاقة بصرية. ستستيقظ، تخرج من البيت، "
              + "تستخدم وسيلة نقل، تذهب للجامعة، تتعامل مع زملاء وأساتذة، "
              + "وتختار كيف تردّ على المواقف. كل اختيار يغيّر شخصيتك، علاقاتك، "
              + "وقصتك. الهدف ليس 'الفوز' بل عيش الحياة. ");

        section("1. كيف تبدأ اللعب",
                "افتح اللعبة من قائمة 'حياة كفيف'، اضغط 'ابدأ لعبة جديدة'، "
              + "ثم اختر وضع الرؤية الذي تريد تجربته. "
              + "ستبدأ في غرفة نومك في اليوم الأول. سيتكلّم التطبيق ويصف لك ما حولك. "
              + "تابع باستخدام الإيماءات الموضحة في القسم التالي. "
              + "اللعبة تحفظ تقدّمك تلقائيًا، ويمكنك العودة لها لاحقًا من زر 'متابعة اللعب'.");

        section("2. الإيماءات الأساسية (التحكم الكامل)",
                "تتحكم في كل اللعبة بسحبات بسيطة على الشاشة:\n"
              + "• اسحب لأعلى (للأمام): خطوة واحدة في الاتجاه الذي تواجهه.\n"
              + "• اسحب لأسفل: توقّف وأعد قراءة وصف المكان.\n"
              + "• اسحب يمينًا: استدارة 90° لجهة اليمين.\n"
              + "• اسحب يسارًا: استدارة 90° لجهة اليسار.\n"
              + "• انقر مرتين: تفاعل مع ما أمامك (شخص، باب، جهاز، طعام).\n"
              + "• اضغط مطوّلًا (ثانية أو أكثر): وصف كامل للبيئة من حولك (ما هو قريب، وأين، وعلى أيّ بُعد).\n"
              + "• هزّ الجهاز: إعادة تحديد الاتجاه. مفيد إذا ضِعت أو فقدت إحساس الجهة.\n"
              + "كل الإيماءات تعمل في أي مكان على الشاشة، فلا تحتاج إلى الوصول لزر معيّن.");

        section("3. أوضاع الرؤية الستة",
                "في بداية اللعب تختار وضع رؤية. كل وضع يغيّر ما تراه فعليًا:\n"
              + "• كفيف كلي: الشاشة سوداء تقريبًا. اعتمادك الكامل على الصوت والاهتزاز.\n"
              + "• ضعيف بصر شديد: ضبابية قوية، تباين منخفض، لا ترى تفاصيل دقيقة.\n"
              + "• رؤية ضبابية: ترى أشكالًا عامة، لكن الوجوه والكلمات غير واضحة.\n"
              + "• رؤية مركزية فقط: ترى دائرة صغيرة في المنتصف فقط. الأطراف مظلمة (شائع في أمراض الشبكية).\n"
              + "• رؤية طرفية فقط: المنتصف مظلم، ترى الحركة من زوايا عينيك (شائع في الجلوكوما المتقدمة).\n"
              + "• وضع المبصر: رؤية كاملة. مخصّص للمبصرين الذين يريدون تجربة العالم بدون فلتر، "
              + "أو لمن يريد رؤية الخريطة بوضوح. يمكنك تغيير الوضع لاحقًا من قائمة اللعبة.");

        section("4. لغة الاهتزاز",
                "الاهتزازات ليست تنبيهات اعتباطية، بل لغة:\n"
              + "• اهتزاز قصير واحد: شيء قريب على بُعد خطوة.\n"
              + "• اهتزازان متتاليان: عائق يمكن المرور حوله (شخص، باب مغلق).\n"
              + "• اهتزاز طويل قوي: خطر فعلي. غالبًا طريق سيارات أو حافة درج هابطة.\n"
              + "• نمط متصاعد: درج. كل نقرة هي درجة.\n"
              + "• هزّتان قصيرتان متباعدتان: مكافأة أو نجاح في حوار.\n"
              + "تعلّم النمط في الدقائق الأولى يجعل بقية اللعبة طبيعية.");

        section("5. الصوت المكاني 360°",
                "كل صوت في اللعبة يأتي من جهته الحقيقية. إذا كانت سيارة على يمينك، "
              + "ستسمعها من سمّاعة اليمين. كلما اقترب الصوت، ارتفع حجمه. ينصح بشدّة "
              + "باستخدام سماعات استيريو أو سماعات أذن لتحصل على التجربة الكاملة. "
              + "بدون سماعات، يظل الصوت موجودًا، لكن قدرتك على تحديد الجهة ستضعف. "
              + "اللعبة تستخدم نغمات قصيرة مصاغة في الوقت الفعلي، فلا تحتاج إلى إنترنت.");

        section("6. وصف البيئة الذكي",
                "اضغط مطوّلًا في أي وقت ليصف لك التطبيق ما حولك:\n"
              + "• اسم المكان الذي أنت فيه.\n"
              + "• الأرضية تحت قدميك (سجاد، إسفلت، عشب…).\n"
              + "• الجهة التي تواجهها الآن (شمالًا، جنوبًا، شرقًا، غربًا).\n"
              + "• قائمة بالأشياء القريبة في حدود أربع خطوات، مع اتجاه كل شيء بالنسبة لك "
              + "(أمامك، خلفك، يمينك، يسارك)، والبُعد بالخطوات.\n"
              + "الوصف قصير ومتكرر. اضغط مطوّلًا في أيّ وقت تشعر فيه بالتشتت.");

        section("7. الإحصائيات والتقدّم",
                "اللعبة تتابع لك عدة أرقام:\n"
              + "• الطاقة (0 إلى 100): تنخفض بالحركة والمواقف الصعبة، وترتفع بالأكل والراحة.\n"
              + "• المال: تبدأ بـ 100. تشتري به أجهزة مساعدة.\n"
              + "• مهارة الحركة: تتحسّن باستخدام العصا والمشي في الأماكن العامة.\n"
              + "• مهارة التواصل: تتحسّن بالمحادثات الناجحة.\n"
              + "• مهارة التقنية: تتحسّن باستخدام الهاتف، التسجيل، النظارات الذكية.\n"
              + "• الثقة: ترتفع بالاختيارات الجريئة.\n"
              + "• الصداقة والسمعة: تتأثران بكيف تتعامل مع الناس.\n"
              + "ترتفع هذه المهارات إلى 100 كحدّ أقصى. كلما ارتفعت، فُتحت لك خيارات حوار جديدة.");

        section("8. الشخصيات والعلاقات",
                "ستلتقي بشخصيات متعدّدة:\n"
              + "• أمّك: تذكّرك بالأشياء وتدعمك معنويًا.\n"
              + "• صديقك سامي: صديق مدرسة قديم، يساعدك في الجامعة.\n"
              + "• زميل فضولي: قد يطرح أسئلة محرجة، الطريقة التي تردّ بها تحدّد علاقتكما.\n"
              + "• الأستاذ: يقدّر المشاركة والاجتهاد، ويتفهّم احتياجاتك.\n"
              + "• سائق الحافلة، الكاشير، أمين المكتبة، صاحب متجر الأجهزة: كل واحد يفتح فرصة مختلفة.\n"
              + "كل حوار له عدة خيارات، وكل خيار يغير شيئًا. لا توجد إجابة 'صحيحة' دائمًا.");

        section("9. الأجهزة المساعدة داخل اللعبة",
                "في متجر الأجهزة (يقع في الشارع) تستطيع شراء:\n"
              + "• عصا التنقّل العادية: في غرفتك مجانًا. خذها معك دائمًا.\n"
              + "• عصا ذكية (200): تستشعر العوائق على مستوى الرأس والصدر.\n"
              + "• نظارات إلكترونية (500): تصف لك الوجوه والمشاهد.\n"
              + "• اشتراك مساعد ذكي (150): يفسّر لك ما حولك بشكل مفصّل ودائم.\n"
              + "كل جهاز يغيّر أسلوب اللعب. النظارات تجعل وصف البيئة أكثر تفصيلًا، "
              + "والعصا الذكية تنبّهك بالعوائق العلوية. لست مضطرًا لشرائها كلها.");

        section("10. الأماكن الرئيسية",
                "ستزور خلال اللعبة:\n"
              + "• غرفة نومك ومنزلك.\n"
              + "• الشارع، الموقف، ومتجر الأجهزة.\n"
              + "• مدخل الجامعة وقاعة المحاضرة.\n"
              + "• الكافتيريا والمكتبة.\n"
              + "تنتقل بين الأماكن بالوصول لباب أو ممر، ثم نقرة مزدوجة. "
              + "في كل مكان، الأشياء موضوعة في نقاط محددة على شبكة، وتحتاج للسير حتى تصل إليها.");

        section("11. نصائح للتجربة الأمثل",
                "• إذا كنت كفيفًا أو ضعيف بصر، شغّل قارئ الشاشة (TalkBack) قبل اللعبة. اللعبة لا تتعارض معه.\n"
              + "• استعمل سماعات استيريو لتمييز اتجاه الأصوات بدقة.\n"
              + "• إذا تشتّت اتجاهك، استخدم هزّة الجهاز لإعادة التحديد.\n"
              + "• في كل وقت يمكنك الضغط المطوّل للحصول على وصف كامل لمكانك.\n"
              + "• خذ خطوات صغيرة في البداية حتى تتعوّد على لغة الاهتزاز.\n"
              + "• لا تتسرّع. اللعبة لا تنتهي بمؤقّت. الهدف هو عيش اللحظة، ليس السباق.");

        section("12. للمبصرين الذين يجرّبون اللعبة",
                "إذا أنت مبصر، نشجّعك أن تبدأ بوضع 'كفيف كلي'. اللعبة ستكون أصعب، "
              + "لكنها ستمنحك نظرة حقيقية عن كيف يتنقّل من فقد بصره: بالأصوات، "
              + "بالاهتزاز، وبثقة في الذاكرة. لاحقًا يمكنك الانتقال لوضع 'مبصر' "
              + "لتفهم الخريطة، أو للاحدى أوضاع الرؤية الجزئية. الهدف ليس "
              + "التعاطف فقط، بل الفهم.");

        section("13. الأمان وحدود اللعبة",
                "هذه لعبة. لا تستخدم آلية اللعبة كبديل عن أدوات التنقّل الحقيقية. "
              + "العصا الحقيقية، التدريب على الحركة، والأدوات المساعدة لا غنى عنها "
              + "في الحياة الفعلية. اللعبة تحاول أن تنقل التجربة بإحساس، لا أن تكون "
              + "دليلًا للتنقّل في الواقع.");

        section("14. الحفظ والاستمرارية",
                "يتم حفظ تقدّمك تلقائيًا في كل مكان جديد تدخله، وفي نهاية كل حوار. "
              + "إذا أغلقت اللعبة بالخطأ، ستعود لمكانك. لحذف التقدّم وبدء من الصفر "
              + "استخدم خيار 'حذف الحفظ' في القائمة الرئيسية.");

        addParagraph("جاهز للبدء؟ ارجع للقائمة واضغط 'ابدأ لعبة جديدة'.");

        Button back = new Button(this);
        back.setText("رجوع");
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(20);
        root.addView(back, bp);

        setContentView(scroll);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            try {
                tts.setLanguage(new Locale("ar"));
                tts.setSpeechRate(0.95f);
            } catch (Throwable ignored) {}
            ttsReady = true;
        }
    }

    private void section(String heading, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(getColor(R.color.basir_surface));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), getColor(R.color.basir_card_stroke));
        box.setBackground(bg);

        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(getColor(R.color.basir_text));
        if (Build.VERSION.SDK_INT >= 28) h.setAccessibilityHeading(true);
        box.addView(h);

        TextView t = new TextView(this);
        t.setText(body);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTextColor(getColor(R.color.basir_text));
        t.setPadding(0, dp(8), 0, dp(8));
        t.setLineSpacing(dp(2), 1.35f);
        box.addView(t);

        Button speak = new Button(this);
        speak.setText("اقرأ هذا القسم بصوت عالٍ");
        speak.setOnClickListener(v -> {
            if (ttsReady) {
                try {
                    tts.speak(heading + ". " + body,
                            TextToSpeech.QUEUE_FLUSH, null,
                            "guide-" + System.nanoTime());
                } catch (Throwable ignored) {}
            }
        });
        box.addView(speak);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        root.addView(box, lp);
    }

    private void addTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(getColor(R.color.basir_text));
        tv.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
        if (Build.VERSION.SDK_INT >= 28) tv.setAccessibilityHeading(true);
        root.addView(tv);
    }

    private void addParagraph(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTextColor(getColor(R.color.basir_text_secondary));
        tv.setPadding(0, dp(8), 0, dp(16));
        tv.setLineSpacing(dp(2), 1.35f);
        root.addView(tv);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
