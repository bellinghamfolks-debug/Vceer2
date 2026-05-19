package com.basir.ai.game;

import java.util.HashMap;
import java.util.Map;

/**
 * Authored dialogue tree. Each dialogue node is a (prompt, choices). A
 * choice has a label, an effect on player stats, an Arabic outcome line,
 * and an optional next node.
 *
 * Kept deliberately flat — choices affect skills, money, energy, and
 * relationships, but rarely lead to deep multi-turn trees, so it stays
 * readable and easy to extend.
 */
public final class Dialogue {

    public static final class Choice {
        public final String label;
        public final String outcome;
        public final int dCommunication;
        public final int dConfidence;
        public final int dMobility;
        public final int dTech;
        public final int dEnergy;
        public final int dMoney;
        public final int dFriendship;
        public final int dReputation;
        public final String addDevice;
        public final String next;

        public Choice(String label, String outcome,
                      int dCom, int dConf, int dMob, int dTech,
                      int dEnergy, int dMoney, int dFr, int dRep,
                      String addDevice, String next) {
            this.label = label;
            this.outcome = outcome;
            this.dCommunication = dCom;
            this.dConfidence = dConf;
            this.dMobility = dMob;
            this.dTech = dTech;
            this.dEnergy = dEnergy;
            this.dMoney = dMoney;
            this.dFriendship = dFr;
            this.dReputation = dRep;
            this.addDevice = addDevice;
            this.next = next;
        }

        public static Choice simple(String label, String outcome) {
            return new Choice(label, outcome, 0, 0, 0, 0, 0, 0, 0, 0, null, null);
        }
    }

    public static final class Node {
        public final String prompt;
        public final Choice[] choices;
        public Node(String prompt, Choice... choices) {
            this.prompt = prompt;
            this.choices = choices;
        }
    }

    private static final Map<String, Node> NODES = new HashMap<>();

    public static Node get(String id) {
        if (NODES.isEmpty()) build();
        return NODES.get(id);
    }

    private static void put(String id, Node n) { NODES.put(id, n); }

    private static void build() {
        put("dlg_mother", new Node(
                "أمّك: 'خذ حقيبتك، الإفطار جاهز على الطاولة. لا تنسَ الباص.'",
                new Choice("أعطها قبلة وتشكرها",
                        "تبتسم لك أمّك. تشعر بالأمان.",
                        +1, +3, 0, 0, +5, 0, 0, +1, null, null),
                new Choice("اطلب منها مساعدة بسيطة",
                        "تساعدك في ترتيب الحقيبة بسرعة.",
                        +1, +1, 0, 0, +3, 0, 0, 0, null, null),
                new Choice("أكتفِ بـ 'صباح الخير' وتمضي",
                        "تذهب بسرعة. ربما كان عليك التحدث أكثر.",
                        0, -1, 0, 0, 0, 0, 0, 0, null, null)
        ));

        put("dlg_stranger", new Node(
                "الشخص: 'أحتاج مساعدة، هل تعرف اتجاه المستشفى؟'",
                new Choice("أرشده بهدوء", "تشرح له بهدوء. يشكرك ويمضي.",
                        +3, +2, 0, 0, -2, 0, 0, +2, null, null),
                new Choice("اعتذر أنك لا تعرف", "يفهم ويغادر دون مشكلة.",
                        0, 0, 0, 0, 0, 0, 0, 0, null, null),
                new Choice("اطلب منه أن يساعدك في عبور الشارع",
                        "يوافق ويقودك حتى الموقف.",
                        +2, +1, +3, 0, +2, 0, 0, +1, null, null)
        ));

        put("dlg_driver", new Node(
                "السائق: 'تبيك تنزل عند بوابة الجامعة الرئيسية؟'",
                new Choice("نعم من فضلك، نبّهني عند الوصول",
                        "يعد بأنه سينبّهك. تشعر بالاطمئنان.",
                        +1, +1, 0, 0, 0, 0, 0, 0, null, null),
                new Choice("أنا أحفظ الطريق، شكرًا",
                        "تثق بنفسك. تنزل في المحطة الصحيحة بمفردك.",
                        0, +3, +2, 0, -2, 0, 0, 0, null, null)
        ));

        put("dlg_friend", new Node(
                "سامي: 'صباح الخير! المحاضرة الأولى تأخرت ربع ساعة.'",
                new Choice("اذهبا معًا إلى الكافتيريا",
                        "تذهبان وتتحدثان عن خططكما لليوم.",
                        +2, +2, 0, 0, +3, 0, +5, +1, null, null),
                new Choice("اذهب إلى المكتبة لتراجع",
                        "تستغل الوقت في المراجعة. تشعر بالجاهزية.",
                        0, +2, 0, +2, -1, 0, 0, +1, null, null),
                new Choice("ابقَ معه في الردهة",
                        "تتعرّفان على زملاء جدد. شبكة علاقاتك تتوسّع.",
                        +3, +1, 0, 0, 0, 0, +3, +2, null, null)
        ));

        put("dlg_curious", new Node(
                "الزميل: 'بصراحة، كيف تستخدم الهاتف وأنت لا ترى؟'",
                new Choice("اشرح له بهدوء قارئ الشاشة",
                        "يستمع باهتمام. يقدّر الشرح.",
                        +4, +2, 0, +2, -1, 0, 0, +3, null, null),
                new Choice("اعتذر أنك مشغول الآن",
                        "ينسحب باحترام، لكن تشعر بفرصة ضائعة.",
                        0, -1, 0, 0, 0, 0, 0, 0, null, null),
                new Choice("ادعه يجلس معك ليجرّب بنفسه",
                        "يجلس ويجرّب. تتكوّن صداقة جديدة.",
                        +3, +3, 0, +2, -2, 0, +4, +2, null, null)
        ));

        put("dlg_teacher", new Node(
                "الأستاذ: 'هل أنت جاهز للمشاركة في النقاش اليوم؟'",
                new Choice("نعم، حضّرت ملاحظاتي بالصوت",
                        "تشارك بثقة. الأستاذ مسرور.",
                        +2, +4, 0, +1, -2, 0, 0, +3, null, null),
                new Choice("اطلب وقتًا للاستماع أكثر",
                        "يفهم ويمنحك الوقت.",
                        0, +1, 0, 0, 0, 0, 0, +1, null, null),
                new Choice("اقترح طريقة بديلة للتقييم",
                        "يقدّر فكرتك ويفكر في تطبيقها.",
                        +3, +3, 0, 0, -1, 0, 0, +4, null, null)
        ));

        put("dlg_cashier", new Node(
                "الكاشير: 'القائمة فيها 12 صنفًا، تبيني أقرأها لك؟'",
                new Choice("نعم اقرأها كلها", "يقرأها كاملة. تختار باطمئنان.",
                        +1, +1, 0, 0, 0, -10, 0, 0, null, null),
                new Choice("اطلب الأكثر مبيعًا", "يقترح وجبة شعبية لذيذة.",
                        +1, 0, 0, 0, +5, -8, 0, 0, null, null),
                new Choice("استخدم تطبيق الذكاء الاصطناعي لتصوير القائمة",
                        "تستخدم التقنية بفخر. التطبيق يقرأ القائمة لك.",
                        0, +2, 0, +3, 0, -10, 0, +1, null, null)
        ));

        put("dlg_librarian", new Node(
                "الأمين: 'عندنا كتب بريل، كتب صوتية، وأجهزة لتكبير الشاشة.'",
                new Choice("جرّب كتاب بريل", "تمرّن أصابعك. تتحسّن سرعة قراءتك.",
                        0, +1, 0, +2, -2, 0, 0, 0, null, null),
                new Choice("استمع لكتاب صوتي", "تستمع بهدوء وتتعلم بكفاءة.",
                        0, +1, 0, +1, -1, 0, 0, 0, null, null),
                new Choice("اسأل عن مجموعة مذاكرة",
                        "يعرّفك على طلاب آخرين. صداقات جديدة محتملة.",
                        +3, +2, 0, 0, -1, 0, +2, +1, null, null)
        ));

        put("dlg_shop", new Node(
                "صاحب المتجر: 'مرحبًا! أيّ جهاز يخدمك أكثر اليوم؟'",
                new Choice("أرني العصا الذكية",
                        "يصف لك العصا. تستشعر العوائق العلوية.",
                        0, +1, +1, +1, 0, 0, 0, 0, null, null),
                new Choice("أخبرني عن النظارات الإلكترونية",
                        "يشرح لك أنها تصف الوجوه والمشاهد.",
                        0, +1, 0, +2, 0, 0, 0, 0, null, null),
                new Choice("اشتركني في مساعد ذكي",
                        "يساعدك في إعداده. تستطيع تجربته الآن.",
                        +1, +1, 0, +3, 0, 0, 0, 0, null, null),
                new Choice("شكرًا، فقط أتفقّد المتجر",
                        "ينصحك بزيارة قادمة. لا ضغط.",
                        0, 0, 0, 0, 0, 0, 0, 0, null, null)
        ));
    }
}
