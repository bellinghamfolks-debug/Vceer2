# مكفوف بوت — Mokafeefah Clicker (مُستَرَدّ + مُصلَح)

استرداد كامل للكود المصدري من نسخة APK، مع إصلاحات للمشاكل الثلاث المُبلَّغ عنها.

## المشاكل الأصلية والأسباب الجذرية

### 1) ثقيل جدًا — "كل دقيقة عضو واحد"
**الأسباب:**
- البحث عن "الكلمات الدالة على دخول الملف الشخصي" كان يُجري DFS منفصلاً لكل كلمة. مع 7 كلمات افتراضية، 7 DFSes لشجرة الواجهة في كل tick.
- استدعاءات `findAccessibilityNodeInfosByText` و DFS اليدوي تعمل معًا (مضاعف عمل).
- نداء `getString(R.string...)` داخل الـtick (محول للوصول إلى الموارد) — بطيء نسبيًا في حلقة سريعة.

**الإصلاح:** DFS واحد فقط لكل tick يتحقق من جميع الكلمات أثناء المرور. السرعة المتوقعة: 7x-10x أسرع.

### 2) يتعطل بعد 40-50 عضو
**السبب الجذري:** عدم استدعاء `AccessibilityNodeInfo.recycle()`. كل `getChild`/`getParent`/`findAccessibilityNodeInfosByText` تحتفظ بمرجع في `system_server`. بعد آلاف العمليات يرفض النظام طلبات جديدة → التطبيق يتجمّد ثم يُقتَل.

**الإصلاح:** كل عقدة تُجمَع في `tickPool` ثم تُعاد جميعها في نهاية كل tick. النتيجة: ذاكرة ثابتة، يعمل لساعات.

### 3) يكرّر التفاعل مع نفس الأعضاء
**السبب الجذري:** التطبيق كان يتتبّع إحداثيات شبكية (cx/80, cy/80) ويُفرّغ هذه الذاكرة عند كل تمرير قائمة! → أي عضو يظهر بعد scroll في نفس الموقع يُعجَب به مرة ثانية.

**الإصلاح:** قاعدة بيانات SQLite محلية (`LikedMembersDb`):
- قبل نقر "إهتمام"، يصعد البوت لـ"بطاقة" العضو ويجمع كل نص ظاهر داخلها (اسم، عمر، وزن، طول، إلخ).
- يحوّله إلى SHA-1 fingerprint.
- يبحث في قاعدة البيانات → إن وُجد، يتخطى ولا يكرر.
- إن لم يوجد، ينقر ويُسجّل بعد النجاح.

## بنية المشروع

```
mokafeefah/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mokafeefah/clicker/
│       │   ├── MainActivity.java
│       │   ├── ClickerService.java        # الخدمة المُصلَحة
│       │   └── LikedMembersDb.java        # جديد — قاعدة بيانات منع التكرار
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/{strings,colors,styles}.xml
│           ├── xml/accessibility_service_config.xml
│           └── drawable/ic_launcher.xml
├── build.gradle
├── settings.gradle
├── gradle.properties
└── .github/workflows/build-apk.yml        # بناء APK تلقائيًا
```

## كيف تبني APK

### الطريقة 1: GitHub Actions (الأسهل)
1. ادفع التغييرات إلى GitHub (تم بالفعل في فرع `mokafeefah-recovery`).
2. اذهب إلى **Actions** في GitHub → اختر **Build Mokafeefah Clicker APK**.
3. اضغط **Run workflow** على الفرع `mokafeefah-recovery`.
4. انتظر ~5 دقائق ثم حمّل الـAPK من قسم **Artifacts**.

### الطريقة 2: محليًا
```bash
cd mokafeefah
gradle :app:assembleDebug
# الناتج: app/build/outputs/apk/debug/app-debug.apk
```

## ملاحظة مهمة عن التوقيع

الـAPK الجديد يُوقَّع بمفتاح debug افتراضي مختلف عن نسختك السابقة. هذا يعني:
- **لن تستطيع التحديث "فوق" النسخة المثبتة عندك** — Android يرفض ذلك.
- **عليك إلغاء تثبيت النسخة القديمة أولًا** ثم تثبيت الجديدة.
- بياناتك (الإعدادات وقاعدة بيانات الأعضاء) **ستضيع** عند إلغاء التثبيت. هذا أمر متوقع للنسخة الجديدة.

إن أردت تحديث "فوق" نسختك، تحتاج لمفتاح التوقيع الأصلي (keystore). إن لم يكن لديك، فالحل هو تثبيت النسخة الجديدة كتطبيق جديد.

## الإصدار

- **v1 (الأصلي):** versionCode 1, versionName 1.0
- **v2 (المُسترَد + المُصلَح):** versionCode 2, versionName 1.1
