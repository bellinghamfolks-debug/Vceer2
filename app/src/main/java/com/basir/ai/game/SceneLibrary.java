package com.basir.ai.game;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hand-authored locations for "حياة كفيف". The world is intentionally
 * compact (a dozen scenes) but interconnected — bedroom → corridor → street
 * → bus stop → university → classroom → cafeteria → library → home — so
 * the player feels they're moving through a continuous day, not menu-hopping.
 *
 * Each scene is a multi-line string. Characters:
 *   '#' = wall   ' ' = floor   'D' = door   'S' = stairs   'X' = exit
 *   '.' = carpet ',' = grass   '=' = road   '+' = crosswalk '~' = puddle
 *
 * Objects (NPCs, items, exits-to-other-scenes) are added imperatively below
 * so they can carry Arabic labels and dialogue identifiers.
 */
public final class SceneLibrary {

    private static final Map<String, Scene> SCENES = new LinkedHashMap<>();

    public static Map<String, Scene> all() {
        if (SCENES.isEmpty()) build();
        return SCENES;
    }

    public static Scene get(String id) {
        return all().get(id);
    }

    private static void build() {
        addBedroom();
        addCorridor();
        addStreet();
        addBusStop();
        addUniversityEntrance();
        addClassroom();
        addCafeteria();
        addLibrary();
        addAssistiveShop();
    }

    private static void put(Scene s) { SCENES.put(s.id, s); }

    // ----- 1. Bedroom -----
    private static void addBedroom() {
        String[] layout = {
            "########",
            "#      #",
            "#      #",
            "#      #",
            "#      D",
            "#      #",
            "########",
        };
        Scene s = new Scene("bedroom", "غرفة نومك",
                "غرفة نومك. تسمع صوت منبّه خفيف، ورائحة قهوة من المطبخ.",
                layout, 2, 3, Direction.EAST, 0);
        s.objects.add(SceneObject.item("bed", "السرير",
                "سرير ناعم. هذا المكان الذي استيقظت منه للتو.", 1, 1, "rest"));
        s.objects.add(SceneObject.item("alarm", "منبّه",
                "منبّه ناطق على الطاولة. يقول: الساعة السابعة صباحًا.",
                3, 1, "alarm"));
        s.objects.add(SceneObject.item("phone", "هاتفك",
                "هاتفك. مساعد الذكاء الاصطناعي جاهز.", 4, 2, "phone"));
        s.objects.add(SceneObject.item("wardrobe", "خزانة الملابس",
                "خزانتك. تلبس ملابسك اليومية.", 5, 1, "dress"));
        s.objects.add(SceneObject.item("cane", "عصا التنقّل",
                "عصا بيضاء قابلة للطي. خذها معك دائمًا.", 1, 3, "take_cane"));
        s.objects.add(SceneObject.exit("door_corridor", "ممرّ البيت", 7, 4, "corridor"));
        put(s);
    }

    // ----- 2. Corridor / Living room -----
    private static void addCorridor() {
        String[] layout = {
            "##########",
            "D        #",
            "#        #",
            "#        #",
            "#        D",
            "#        #",
            "##########",
        };
        Scene s = new Scene("corridor", "ممرّ البيت",
                "ممرّ منزلك. تسمع صوت تلفاز في الصالة، وأمّك تتحدث في الهاتف.",
                layout, 1, 1, Direction.EAST, 100);
        s.objects.add(SceneObject.exit("to_bedroom", "غرفة النوم", 0, 1, "bedroom"));
        s.objects.add(SceneObject.npc("mother", "أمّك",
                "أمّك تبتسم. تقول: 'انتبه لنفسك يا حبيبي، تذكّر الحافلة.'",
                3, 2, "dlg_mother"));
        s.objects.add(SceneObject.item("breakfast", "إفطار خفيف",
                "خبز وجبن وكوب شاي. وجبة سريعة.", 5, 3, "eat"));
        s.objects.add(SceneObject.exit("to_street", "باب البيت", 9, 4, "street"));
        put(s);
    }

    // ----- 3. Street -----
    private static void addStreet() {
        String[] layout = {
            "##########",
            "#,,,,,,,,#",
            "D,,,,,,,,#",
            "#,,,,,,,,X",
            "==========",
            "++++++++++",
            "==========",
            ",,,,,,,,,,",
        };
        Scene s = new Scene("street", "الشارع",
                "شارع المدينة. سيارات تمرّ بسرعة، أصوات أبواق متفرقة، وريح خفيفة.",
                layout, 1, 2, Direction.EAST, 220);
        s.objects.add(SceneObject.exit("to_corridor", "بيتك", 0, 2, "corridor"));
        s.objects.add(SceneObject.exit("to_bus_stop", "موقف الحافلة", 9, 3, "bus_stop"));
        s.objects.add(SceneObject.exit("to_shop", "متجر الأجهزة المساعدة", 5, 7, "assistive_shop"));
        s.objects.add(SceneObject.sound("car1", "سيارة عابرة",
                "سيارة تمرّ من اليسار إلى اليمين.", 4, 4, 180));
        s.objects.add(SceneObject.sound("car2", "سيارة بعيدة",
                "سيارة تقترب من جهة بعيدة.", 7, 6, 140));
        s.objects.add(SceneObject.npc("stranger", "شخص في الطريق",
                "رجل ينتظر الإشارة. ربما تستطيع سؤاله عن الاتجاه.",
                6, 3, "dlg_stranger"));
        put(s);
    }

    // ----- 4. Bus stop -----
    private static void addBusStop() {
        String[] layout = {
            "##########",
            "D        #",
            "#        #",
            "#        #",
            "==========",
            "++++++++++",
            "==========",
        };
        Scene s = new Scene("bus_stop", "موقف الحافلة",
                "موقف الحافلة. ظلّ المظلّة فوقك، وأصوات حافلات تقترب وتبتعد.",
                layout, 1, 1, Direction.EAST, 160);
        s.objects.add(SceneObject.exit("to_street", "الشارع", 0, 1, "street"));
        s.objects.add(SceneObject.npc("driver", "سائق الحافلة",
                "سائق ودود. يقول لك متى تنزل عند الجامعة.", 5, 2, "dlg_driver"));
        s.objects.add(SceneObject.item("bus", "حافلة الجامعة",
                "حافلة رقم 7 تتجه إلى الجامعة. اصعد بنقرتين.",
                8, 2, "ride_bus"));
        put(s);
    }

    // ----- 5. University entrance -----
    private static void addUniversityEntrance() {
        String[] layout = {
            "##########",
            "#        D",
            "#        #",
            "#        #",
            "#        #",
            "#        #",
            "D        D",
            "##########",
        };
        Scene s = new Scene("university", "مدخل الجامعة",
                "ردهة الجامعة الكبرى. أصوات طلاب، خطوات، وإعلانات على المكبّر.",
                layout, 1, 6, Direction.EAST, 130);
        s.objects.add(SceneObject.exit("to_bus_stop", "الخارج", 0, 6, "bus_stop"));
        s.objects.add(SceneObject.exit("to_classroom", "قاعة المحاضرة", 9, 1, "classroom"));
        s.objects.add(SceneObject.exit("to_cafeteria", "الكافتيريا", 9, 6, "cafeteria"));
        s.objects.add(SceneObject.exit("to_library", "المكتبة", 4, 0, "library"));
        s.objects.add(SceneObject.npc("friend", "صديقك سامي",
                "سامي. صديق داعم منذ المدرسة. ينتظرك دائمًا قرب المدخل.",
                3, 3, "dlg_friend"));
        s.objects.add(SceneObject.npc("curious", "زميل فضولي",
                "زميل لا يعرفك جيدًا. لديه أسئلة كثيرة عن إعاقتك.",
                6, 4, "dlg_curious"));
        put(s);
    }

    // ----- 6. Classroom -----
    private static void addClassroom() {
        String[] layout = {
            "##########",
            "#........#",
            "#........#",
            "#........#",
            "#........#",
            "#........#",
            "D........#",
            "##########",
        };
        Scene s = new Scene("classroom", "قاعة المحاضرة",
                "القاعة هادئة. الأستاذ يتحدث بصوت واضح. مكيّف يهمهم في الزاوية.",
                layout, 1, 6, Direction.EAST, 90);
        s.objects.add(SceneObject.exit("to_university", "الردهة", 0, 6, "university"));
        s.objects.add(SceneObject.npc("teacher", "الأستاذ",
                "أستاذك. متفهّم ولكنه يطلب منك المشاركة.",
                7, 1, "dlg_teacher"));
        s.objects.add(SceneObject.item("seat", "مقعدك في الأمام",
                "مقعد محجوز لك في الصف الأول. اجلس بنقرتين.",
                3, 2, "sit"));
        s.objects.add(SceneObject.item("recorder", "مسجّل صوتي",
                "تقنية تساعدك في مراجعة المحاضرة لاحقًا.",
                5, 3, "record"));
        put(s);
    }

    // ----- 7. Cafeteria -----
    private static void addCafeteria() {
        String[] layout = {
            "##########",
            "#        #",
            "#        #",
            "#        #",
            "#        #",
            "D        #",
            "##########",
        };
        Scene s = new Scene("cafeteria", "الكافتيريا",
                "أصوات صحون، ضحكات، آلة قهوة تطحن. روائح طعام دافئة.",
                layout, 1, 5, Direction.EAST, 240);
        s.objects.add(SceneObject.exit("to_university", "الردهة", 0, 5, "university"));
        s.objects.add(SceneObject.npc("cashier", "موظّف الكاشير",
                "يساعدك في قراءة القائمة وحساب المال.",
                7, 1, "dlg_cashier"));
        s.objects.add(SceneObject.item("meal", "وجبة الغداء",
                "ساندويتش وعصير. تعيد لك بعض الطاقة.",
                5, 3, "eat"));
        put(s);
    }

    // ----- 8. Library -----
    private static void addLibrary() {
        String[] layout = {
            "##########",
            "#........#",
            "#........#",
            "#........#",
            "#........#",
            "#........#",
            "#....D...#",
            "##########",
        };
        Scene s = new Scene("library", "المكتبة",
                "المكتبة. صمت تقريبًا. صوت صفحات تُقلَّب، وحاسوب يهمس.",
                layout, 5, 6, Direction.NORTH, 70);
        s.objects.add(SceneObject.exit("to_university", "الردهة", 5, 6, "university"));
        s.objects.add(SceneObject.npc("librarian", "أمين المكتبة",
                "يعرض عليك كتبًا بأحرف بارزة وكتبًا صوتية.",
                3, 1, "dlg_librarian"));
        s.objects.add(SceneObject.item("audiobook", "كتاب صوتي",
                "كتاب صوتي عن مادة المحاضرة. مراجعة ممتازة.",
                7, 2, "study"));
        s.objects.add(SceneObject.item("braille_book", "كتاب بطريقة بريل",
                "كتاب بحروف بارزة. تمرّن أصابعك على القراءة.",
                2, 4, "study_braille"));
        put(s);
    }

    // ----- 9. Assistive devices shop -----
    private static void addAssistiveShop() {
        String[] layout = {
            "##########",
            "#        #",
            "#        #",
            "#        #",
            "D        #",
            "##########",
        };
        Scene s = new Scene("assistive_shop", "متجر الأجهزة المساعدة",
                "متجر صغير دافئ. صاحبه يشرح لك كل جهاز قبل البيع.",
                layout, 1, 4, Direction.EAST, 80);
        s.objects.add(SceneObject.exit("to_street", "الشارع", 0, 4, "street"));
        s.objects.add(SceneObject.npc("shopkeeper", "صاحب المتجر",
                "خبير في الأجهزة المساعدة. يعطيك نصائح صادقة.",
                7, 1, "dlg_shop"));
        s.objects.add(SceneObject.item("smart_cane", "عصا ذكية",
                "عصا تستشعر العوائق على مستوى الصدر. سعرها 200.",
                3, 2, "buy_smart_cane"));
        s.objects.add(SceneObject.item("smart_glasses", "نظارات إلكترونية",
                "نظارات تصف ما تراه بصوت في أذنك. سعرها 500.",
                5, 2, "buy_glasses"));
        s.objects.add(SceneObject.item("ai_assistant", "مساعد ذكاء اصطناعي",
                "اشتراك ذكاء اصطناعي يصف لك ما حولك بشكل مفصّل. سعره 150.",
                7, 3, "buy_ai"));
        put(s);
    }
}
