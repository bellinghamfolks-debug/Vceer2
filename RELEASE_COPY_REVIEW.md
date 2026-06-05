# تقرير مراجعة نصوص بصير 3.2.0

تاريخ المراجعة: 5 يونيو 2026

## النتيجة

أُنجزت مراجعة تحريرية ووظيفية شاملة لنصوص الواجهة العربية والإنجليزية، والشروط والأحكام، وسياسة الخصوصية، والوثائق المرافقة في نسختي Android وiOS. حوفظ على أسماء الملفات ومساراتها وهيكل المشروع حتى يمكن استبدال الملفات مباشرة دون نقل النصوص إلى معمارية جديدة.

هذه المراجعة صياغة تحريرية ومنتجية مبنية على السلوك الظاهر في الشفرة، وليست اعتمادًا قانونيًا نهائيًا أو ضمانًا للامتثال. يلزم اعتماد محامٍ مختص قبل النشر العام.

## ما شملته المراجعة

- العناوين، الأوصاف، الأزرار، رسائل التقدم، النجاح، الخطأ، التأكيد، الأذونات، والإعدادات.
- نصوص قارئ الشاشة وأوصاف عناصر التحكم حيث ظهرت في الملفات المعدلة.
- الإفصاحات السابقة لرفع الملفات، إدخال مفتاح Gemini، واستخدام الخادم الوسيط.
- رسائل العملات والفواتير والرياضيات والوثائق والمحتوى الطبي والقانوني والمالي.
- وضع المشي والوصف المباشر أثناء التنقل ورسائل السلامة.
- طلب المساعدة ومشاركة الموقع، مع إزالة الإيحاء بأن الرسالة تُرسل تلقائيًا.
- التخزين المحلي، سجل النشاط، الحفظ التلقائي، وحذف البيانات.
- الشروط والأحكام وسياسة الخصوصية بالعربية والإنجليزية داخل التطبيق وفي ملفات مستقلة.
- توحيد المصطلحات والنبرة بين Android وiOS، مع إبقاء الفروق الحقيقية بين المنصتين واضحة.
- تحديث ملفات README ودليل النصوص بما يطابق الإصدار 3.2.0.

## تصحيحات مهمة مرتبطة بالسلوك الفعلي

1. استُبدلت الوعود المطلقة مثل «الأدق»، «دقيق»، و«آمن» بصياغة واقعية توضح احتمال الخطأ والحاجة إلى المراجعة.
2. صُححت ميزة طلب المساعدة: التطبيق يفتح رسالة أو واجهة مشاركة للمراجعة، ولا يضمن الإرسال أو الوصول ولا يتصل بخدمات الطوارئ.
3. صُحح وصف وضع الخصوصية في Android وiOS إلى «عدم حفظ سجل النشاط»، لأن هذا الخيار لا يمنع إرسال المحتوى إلى خدمة الذكاء الاصطناعي ولا يلغي الحفظ التلقائي للنتائج.
4. عُدلت دالة تسجيل النشاط في Android حتى لا تضيف سجلات جديدة عند تفعيل خيار عدم حفظ سجل النشاط.
5. صُححت أوصاف الاتصال: Android يدعم اتصالًا مباشرًا أو خادمًا وسيطًا يحدده المستخدم، بينما iOS يستخدم الاتصال المباشر في النسخة الحالية.
6. أُضيف إفصاح واضح قبل رفع الملفات عن وجهة الملف، ودور Google أو مشغل الخادم الوسيط، ومدد الاحتفاظ التي تعلنها Google.
7. صُحح وصف التخزين الآمن: iOS يستخدم Keychain القياسي، وAndroid يستخدم حماية النظام عند توفرها مع احتمال الرجوع إلى تخزين محلي أقل حماية في حالات غير مدعومة.
8. صُححت إمكانات iOS للمستندات لتصف ما تنفذه الشفرة فعليًا: PDF حتى 60 صفحة، وTXT وCSV، والنتيجة نصية وليست ملف Word.
9. وُحد رقم إصدار iOS إلى 3.2.0 ورقم البناء إلى 54، بما يطابق Android.
10. أزيل إذن غير مستخدم لحفظ الصور من ملف Info.plist في iOS.

## الملفات القانونية الجاهزة

- `legal/TERMS_AR.md`
- `legal/TERMS_EN.md`
- `legal/PRIVACY_AR.md`
- `legal/PRIVACY_EN.md`

الإصدار القانوني داخل هذه الملفات وداخل شاشات التطبيق هو الإصدار 3، وتاريخ النفاذ 5 يونيو 2026.

## الملفات المعدلة أو المضافة

- `README.md`
- `UI_TEXTS.md`
- `RELEASE_COPY_REVIEW.md`
- `app/src/main/java/com/basir/ai/ConversionService.java`
- `app/src/main/java/com/basir/ai/LegalScreens.java`
- `app/src/main/java/com/basir/ai/LiveWalkingController.java`
- `app/src/main/java/com/basir/ai/LiveWalkingService.java`
- `app/src/main/java/com/basir/ai/MainActivity.java`
- `app/src/main/java/com/basir/ai/UserFriendlyErrorMapper.java`
- `ios/Basir/Info.plist`
- `ios/Basir/Networking/GeminiClient.swift`
- `ios/Basir/Networking/UserFriendlyErrorMapper.swift`
- `ios/Basir/Views/AboutView.swift`
- `ios/Basir/Views/ArchiveView.swift`
- `ios/Basir/Views/AskBasirView.swift`
- `ios/Basir/Views/DescribeImageView.swift`
- `ios/Basir/Views/DocumentConvertView.swift`
- `ios/Basir/Views/DocumentsView.swift`
- `ios/Basir/Views/EmergencyView.swift`
- `ios/Basir/Views/HomeView.swift`
- `ios/Basir/Views/LegalScreens.swift`
- `ios/Basir/Views/MathExtractView.swift`
- `ios/Basir/Views/MemoryView.swift`
- `ios/Basir/Views/MoreView.swift`
- `ios/Basir/Views/SettingsView.swift`
- `ios/Basir/Views/TranslateView.swift`
- `ios/Basir/Views/VisionView.swift`
- `ios/Basir/Views/WalkingModeView.swift`
- `ios/README.md`
- `legal/PRIVACY_AR.md`
- `legal/PRIVACY_EN.md`
- `legal/TERMS_AR.md`
- `legal/TERMS_EN.md`
- `server/README_SERVER.md`

## نتائج الفحص الفني

- نجح تحليل الصياغة لجميع ملفات Swift باستخدام `swiftc -parse`.
- نجح تحليل ملفي Info.plist في iOS.
- نجح تحليل 11 ملف XML في Android.
- نجح فحص صياغة `server/index.js` باستخدام `node --check`.
- لم يظهر فحص Java أي خطأ صياغي من الأنواع الشائعة. تعذر إجراء ترجمة Android كاملة خارج Android SDK، وكانت أخطاء `javac` ناتجة عن غياب مكتبات Android.
- تعذر تشغيل `./gradlew assembleDebug` لأن الأرشيف الأصلي لا يحتوي الملف `gradle/wrapper/gradle-wrapper.jar`. لم يُنشأ هذا الملف أو يُستبدل تخمينًا.
- أُجري بحث عن أرقام الإصدارات القديمة، والعبارات المتعارضة، والمحارف التالفة، والتكرارات الواضحة في النصوص المعدلة.

## ملاحظات نشر حرجة قبل الإطلاق العام

### 1. قيد Gemini الحالي

تنص شروط Gemini API السارية من 23 مارس 2026 على أن المستخدم يجب أن يكون قد بلغ 18 عامًا، وألا يكون العميل موجّهًا لمن هم دون 18 عامًا. كما تنص على أن Google AI Studio وGemini API مخصصان للمطورين الذين يبنون لأغراض مهنية أو تجارية، وليس للاستخدام الاستهلاكي. وبصير في صورته الحالية تطبيق مساعد موجّه إلى مستخدمين نهائيين، لذلك يلزم حسم أساس استخدام Gemini وترخيصه وبنية تقديم الخدمة مع مختص قبل النشر العام. إضافة الإفصاح إلى الواجهة والشروط لا تعالج وحدها هذا التعارض المحتمل.

### 2. الإبلاغ عن محتوى الذكاء الاصطناعي في Google Play

تطلب سياسة Google Play من التطبيقات التي تنشئ محتوى بالذكاء الاصطناعي توفير وسيلة داخل التطبيق للإبلاغ عن المحتوى المسيء أو الإشارة إليه دون مغادرة التطبيق. لم تُضف هذه الميزة لأن الطلب الحالي يخص النصوص، وهي متطلب وظيفي يحتاج شاشة أو إجراء إرسال فعليًا قبل النشر على Google Play.

### 3. صفحة سياسة خصوصية عامة

تحتاج متاجر التطبيقات إلى رابط عام صالح لسياسة الخصوصية. الملفات المستقلة جاهزة للنشر، لكن يجب استضافتها على عنوان عام ثابت، ثم وضع الرابط في Google Play Console وApp Store Connect وربطه داخل التطبيق عند الحاجة.

### 4. إفصاحات المتاجر

يجب تحديث قسم Data Safety في Google Play وإجابات App Privacy في App Store Connect لتشمل البيانات التي يرسلها المستخدم إلى Google أو إلى خادم وسيط، وأذونات الكاميرا والميكروفون والموقع والملفات، وسلوك الشركاء الخارجيين. لا يكفي وجود سياسة خصوصية داخل التطبيق إذا كانت إفصاحات المتجر مختلفة.

### 5. الكاميرا والخلفية في Android

الوصف المباشر قد يستخدم الكاميرا دوريًا عبر خدمة أمامية. يلزم اختبار الإفصاح البارز والموافقة والأذونات على أجهزة وإصدارات Android المستهدفة، ومراجعة متطلبات Google Play الخاصة بالوصول الحساس وسلوك الخلفية.

### 6. المراجعة القانونية السعودية

ينبغي أن يراجع محامٍ سعودي مختص النصوص، وخصوصًا صفة المسؤول عن المعالجة، أساس نقل البيانات خارج المملكة، التزامات مزودي الخدمة، آلية ممارسة الحقوق، وتحديد المسؤولية. لا ينبغي نشر الشروط والسياسة بوصفهما نهائيتين قبل هذه المراجعة.

## المصادر الرسمية التي روجعت

- شروط Gemini API الإضافية: https://ai.google.dev/gemini-api/terms
- Gemini Files API: https://ai.google.dev/gemini-api/docs/files
- مراقبة إساءة استخدام Gemini API: https://ai.google.dev/gemini-api/docs/usage-policies
- اللائحة التنفيذية لنظام حماية البيانات الشخصية: https://dgp.sdaia.gov.sa/wps/portal/pdp/knowledgecenter/details/PDPL2/
- الدليل الاسترشادي لسياسة الخصوصية: https://dgp.sdaia.gov.sa/wps/portal/pdp/knowledgecenter/details/ElaborationandDevelopingPrivacyPolicyGuideline/
- سياسة المحتوى المنشأ بالذكاء الاصطناعي في Google Play: https://support.google.com/googleplay/android-developer/answer/13985936?hl=en
- متطلبات سياسة الخصوصية في Google Play: https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
- قسم Data Safety في Google Play: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- إرشادات مراجعة App Store: https://developer.apple.com/app-store/review/guidelines/
- تفاصيل خصوصية App Store: https://developer.apple.com/app-store/app-privacy-details/
- إدخال رابط سياسة الخصوصية في App Store Connect: https://developer.apple.com/help/app-store-connect/manage-app-information/manage-app-privacy/

## طريقة الإدراج

للتحديث الجزئي، انسخ محتويات حزمة الملفات المعدلة فقط فوق المشروع الأصلي مع الحفاظ على المسارات. وللاستخدام الكامل، افتح حزمة المشروع الكامل كما هي. لا يلزم تغيير أسماء الحزم أو الأنواع أو الملفات.
