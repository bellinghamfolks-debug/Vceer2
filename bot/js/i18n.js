/* مكفوف بوت — bilingual strings. */

export const STRINGS = {
  ar: {
    app_name: "مكفوف بوت",
    app_tagline: "البوت الذكي للمكفوفين — إدارة الإعجاب وتتبع الموضع وإحداثيات مودة.",
    back: "رجوع",
    cancel: "إلغاء",
    save: "حفظ",
    saved: "تم الحفظ.",
    delete: "حذف",
    loading: "جاري المعالجة...",
    on: "مفعّل", off: "معطّل",
    yes: "نعم", no: "لا",
    confirm: "تأكيد",

    // Tabs
    tab_home: "الرئيسية",
    tab_coords: "إحداثيات",
    tab_settings: "إعدادات",
    tab_guide: "دليل",

    // Home / status
    home_subtitle: "تابع جلستك الحالية، احفظ موضعك، وافحص الحالة الاجتماعية بسرعة.",
    status_title: "حالة الجلسة",
    last_member: "آخر عضو: ",
    processed: "عدد المعالَجين: ",
    cycle_likes: "عداد الإعجابات هذه الدورة: ",
    next_refresh_at: "تجديد عند إعجاب رقم: ",
    none: "لم يبدأ بعد",
    save_pos: "حفظ الموضع الحالي",
    pos_label: "اسم آخر عضو وصلت إليه",
    pos_saved: "تم حفظ الموضع.",
    bump_like: "زيادة عداد الإعجاب +1",
    reset_cycle: "تصفير عداد الدورة",
    state_cleared: "تم مسح حالة الجلسة.",
    clear_state: "مسح حالة الجلسة",

    // Profile check
    check_title: "فحص الحالة الاجتماعية",
    check_hint: "الصق نص الملف الشخصي للتحقق السريع. (يعمل بدون إنترنت)",
    check_placeholder: "الصق نص الحالة الاجتماعية أو الملف هنا",
    check_run: "هل أضع إعجابًا؟",
    check_yes: "نعم — مطلقة أو أرملة ✓",
    check_no: "لا — تخطّ هذا العضو ✗",
    check_unknown: "غير واضح — افتح الملف يدويًا للتأكد.",
    profile_already: "إذا ظهر 'الملف الشخصي' → العضو مضاف سابقًا، اضغط إغلاق ولا تضع إعجابًا.",

    // Coordinates
    coords_title: "الإحداثيات المحفوظة",
    coords_intro: "احفظ إحداثيات أزرار التطبيق الخارجي بطريقتين: الضغط مباشرة، أو من خلال لقطة شاشة.",
    coord_record: "تسجيل بالضغط",
    coord_record_sub: "اضغط على هذه الشاشة",
    coord_from_screenshot: "تسجيل من لقطة شاشة",
    coord_from_screenshot_sub: "أدق طريقة للتطبيقات الخارجية",
    coord_recording: "انقر الآن على الهدف...",
    coord_tap_screenshot: "انقر على الزر المطلوب في الصورة",
    coord_name_label: "اسم هذه الإحداثية",
    coord_name_hint: "مثلاً: ثلاث نقاط، زر البحث...",
    coord_saved: "تم حفظ الإحداثية.",
    coord_empty: "لا توجد إحداثيات محفوظة بعد.",
    coord_x: "X",
    coord_y: "Y",
    coord_clear_all: "حذف كل الإحداثيات",
    coord_load_mawada: "تحميل إحداثيات مودة",
    coord_mawada_added: "تم حفظ إحداثيات مودة",
    coord_mawada_exists: "إحداثيات مودة محفوظة مسبقًا.",
    coord_rename: "إعادة تسمية",

    // Settings
    set_title: "الإعدادات",
    set_like_mode: "وضع الإعجاب",
    mode_normal: "الوضع العادي",
    mode_normal_sub: "إعجاب مباشر بدون فحص",
    mode_dw: "مطلقات وأرامل فقط",
    mode_dw_sub: "فحص الحالة الاجتماعية أولًا",
    set_after_refresh: "بعد تحديث الصفحة",
    refresh_continue: "من حيث توقفت",
    refresh_continue_sub: "يتذكر آخر عضو",
    refresh_restart: "من البداية",
    refresh_restart_sub: "يبدأ من أول كل مرة",
    set_nav: "مسار التنقل",
    nav_online: "الأعضاء ← المتواجدون الآن",
    nav_online_sub: "المسار الافتراضي",
    nav_search: "ثلاث نقاط ← بحث ← بحث",
    nav_search_sub: "أول زر غير مقروء",
    set_screen: "دقة شاشة جهازك",
    set_screen_hint: "اضبط الدقة الفعلية لجوّالك ليُحسب موقع الأزرار بدقة. مثال: 1080×2340 أو 1220×2712.",
    screen_w: "العرض (بكسل)",
    screen_h: "الطول (بكسل)",
    screen_invalid: "أدخل أرقامًا صحيحة.",
    set_cycle: "إعجابات قبل التجديد",
    set_cycle_hint: "كم إعجاب قبل أن يطلب التجديد. الافتراضي 80.",
    set_voice: "الصوت",
    set_tts: "النطق الصوتي",
    set_tts_rate: "سرعة النطق",
    set_font: "حجم الخط",

    // Guide
    guide_title: "دليل الخطوات",
    guide_intro: "خطوات مرتبة حسب إعداداتك الحالية. النطق الصوتي يُساعدك للاستماع.",
    guide_step: "الخطوة ",
    guide_nav_online: "افتح مودة → الأعضاء → المتواجدون الآن",
    guide_nav_search: "افتح مودة → اضغط ثلاث نقاط (أول زر غير مقروء) → بحث → بحث",
    guide_like_normal: "اضغط إعجاب على كل عضوة مباشرة.",
    guide_like_dw: "افتح الملف الشخصي للعضوة، تحقق من الحالة الاجتماعية، ثم اضغط إعجاب فقط لو 'مطلقة' أو 'أرملة'.",
    guide_already_added: "إذا ظهر خيار 'الملف الشخصي' بعد فتح العضو → معناه مضاف سابقًا، اضغط إغلاق وتخطّاه.",
    guide_refresh: "بعد $N إعجاب، اضغط 'تجديد' الصفحة.",
    guide_continue: "بعد التجديد، ابدأ من العضو المحفوظ: $LAST",
    guide_restart: "بعد التجديد، ابدأ من بداية القائمة دائمًا.",

    // Log
    log_title: "سجل النشاط",
    log_empty: "لا توجد عمليات بعد.",
    log_clear: "مسح السجل",
    log_position: "تحديث موضع",
    log_like: "إعجاب",
    log_cycle_reset: "تصفير دورة",
    log_coord_added: "إضافة إحداثية",
    log_coord_removed: "حذف إحداثية",

    // Misc
    read_aloud: "قراءة صوتية",
    stop_read: "إيقاف القراءة",
    voice_dictation: "إملاء صوتي",
    voice_not_supported: "الإملاء غير متاح في هذا المتصفح.",
    install_hint: "للتثبيت: من Safari (iPhone) شاركة → إضافة إلى الشاشة الرئيسية. من Chrome (Android) اختر تثبيت التطبيق.",
    version: "الإصدار: 1.0.0",
    rename_to: "الاسم الجديد"
  },

  en: {
    app_name: "Mkfwf Bot",
    app_tagline: "Smart bot for blind users — like mode, position tracking, Mawada coordinates.",
    back: "Back", cancel: "Cancel", save: "Save", saved: "Saved.",
    delete: "Delete", loading: "Processing...",
    on: "On", off: "Off", yes: "Yes", no: "No", confirm: "Confirm",

    tab_home: "Home", tab_coords: "Coords", tab_settings: "Settings", tab_guide: "Guide",

    home_subtitle: "Track your current session, save position, quickly check marital status.",
    status_title: "Session Status",
    last_member: "Last member: ",
    processed: "Processed: ",
    cycle_likes: "Likes this cycle: ",
    next_refresh_at: "Refresh after like #: ",
    none: "Not started",
    save_pos: "Save current position",
    pos_label: "Last member name reached",
    pos_saved: "Position saved.",
    bump_like: "Like counter +1",
    reset_cycle: "Reset cycle counter",
    state_cleared: "Session state cleared.",
    clear_state: "Clear session state",

    check_title: "Check Marital Status",
    check_hint: "Paste profile text for a quick offline check.",
    check_placeholder: "Paste the marital-status text or profile here",
    check_run: "Should I like her?",
    check_yes: "Yes — divorced or widowed ✓",
    check_no: "No — skip this member ✗",
    check_unknown: "Unclear — open the profile manually.",
    profile_already: "If 'Profile' option appears → already added. Tap Close and skip.",

    coords_title: "Saved Coordinates",
    coords_intro: "Save target coordinates in two ways: tap on this screen, or via a screenshot.",
    coord_record: "Tap to record",
    coord_record_sub: "Tap on this screen",
    coord_from_screenshot: "Record from screenshot",
    coord_from_screenshot_sub: "Most accurate for external apps",
    coord_recording: "Tap the target now...",
    coord_tap_screenshot: "Tap the target button in the image",
    coord_name_label: "Name this coordinate",
    coord_name_hint: "e.g. Three dots, Search button...",
    coord_saved: "Coordinate saved.",
    coord_empty: "No saved coordinates yet.",
    coord_x: "X", coord_y: "Y",
    coord_clear_all: "Delete all coordinates",
    coord_load_mawada: "Load Mawada coordinates",
    coord_mawada_added: "Mawada coordinates saved",
    coord_mawada_exists: "Mawada coordinates already saved.",
    coord_rename: "Rename",

    set_title: "Settings",
    set_like_mode: "Like Mode",
    mode_normal: "Normal", mode_normal_sub: "Like directly",
    mode_dw: "Divorced & Widowed", mode_dw_sub: "Check marital status first",
    set_after_refresh: "After Page Refresh",
    refresh_continue: "Continue from last", refresh_continue_sub: "Remembers last member",
    refresh_restart: "Start from beginning", refresh_restart_sub: "Always from the top",
    set_nav: "Navigation Path",
    nav_online: "Members → Currently Online", nav_online_sub: "Default path",
    nav_search: "Three dots → Search → Search", nav_search_sub: "First unlabeled button",
    set_screen: "Your device screen resolution",
    set_screen_hint: "Set your phone's actual resolution. e.g. 1080×2340 or 1220×2712.",
    screen_w: "Width (px)", screen_h: "Height (px)",
    screen_invalid: "Enter valid numbers.",
    set_cycle: "Likes before refresh",
    set_cycle_hint: "How many likes before refresh. Default 80.",
    set_voice: "Voice", set_tts: "Speech output", set_tts_rate: "Speech rate",
    set_font: "Font size",

    guide_title: "Step Guide",
    guide_intro: "Steps ordered by your current settings. Use TTS to listen.",
    guide_step: "Step ",
    guide_nav_online: "Open Mawada → Members → Currently Online",
    guide_nav_search: "Open Mawada → Tap three dots (first unlabeled button) → Search → Search",
    guide_like_normal: "Like every member directly.",
    guide_like_dw: "Open profile, check marital status, like only if 'divorced' or 'widowed'.",
    guide_already_added: "If 'Profile' option appears → already added, tap Close and skip.",
    guide_refresh: "After $N likes, tap 'Refresh' on the page.",
    guide_continue: "After refresh, start from saved member: $LAST",
    guide_restart: "After refresh, always start from the top of the list.",

    log_title: "Activity Log",
    log_empty: "No activity yet.",
    log_clear: "Clear log",
    log_position: "Position updated",
    log_like: "Like",
    log_cycle_reset: "Cycle reset",
    log_coord_added: "Coordinate added",
    log_coord_removed: "Coordinate removed",

    read_aloud: "Read aloud", stop_read: "Stop reading",
    voice_dictation: "Voice input", voice_not_supported: "Voice input unavailable in this browser.",
    install_hint: "To install: Safari (iPhone) → Share → Add to Home Screen. Chrome (Android) → Install app.",
    version: "Version: 1.0.0",
    rename_to: "New name"
  }
};

let currentLang = "ar";

export function setLang(lang) {
  currentLang = lang === "en" ? "en" : "ar";
  document.documentElement.lang = currentLang;
  document.documentElement.dir = currentLang === "ar" ? "rtl" : "ltr";
}
export function getLang() { return currentLang; }
export function t(key) {
  const bag = STRINGS[currentLang] || STRINGS.ar;
  if (key in bag) return bag[key];
  if (key in STRINGS.ar) return STRINGS.ar[key];
  return key;
}
