// LegalScreens.swift
// In-app Terms and Privacy Policy. Embedded for offline, accessible reading.

import SwiftUI

struct TermsView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.t("الإصدار القانوني 3 — ساري من 5 يونيو 2026",
                            "Legal version 3 — Effective 5 June 2026"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(L10n.t(termsArabic, termsEnglish))
                    .font(.body)
                    .textSelection(.enabled)
                    .accessibilityLabel(L10n.t("نص الشروط والأحكام", "Terms and Conditions text"))
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("الشروط والأحكام", "Terms and Conditions"))
    }
}

struct PrivacyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.t("الإصدار القانوني 3 — سارية من 5 يونيو 2026",
                            "Legal version 3 — Effective 5 June 2026"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(L10n.t(privacyArabic, privacyEnglish))
                    .font(.body)
                    .textSelection(.enabled)
                    .accessibilityLabel(L10n.t("نص سياسة الخصوصية", "Privacy Policy text"))
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("سياسة الخصوصية", "Privacy Policy"))
    }
}

private let termsArabic = """
تاريخ النفاذ: 5 يونيو 2026
الإصدار القانوني: 3

يرجى قراءة هذه الشروط بعناية قبل استخدام تطبيق بصير. باستخدام التطبيق أو أي ميزة تعتمد على الذكاء الاصطناعي، فإنك تقر بأنك قرأت هذه الشروط وسياسة الخصوصية وفهمتهما ووافقت عليهما. لا تستخدم التطبيق إذا لم توافق.

1) التعريفات
• «بصير» أو «التطبيق»: تطبيق مساعد للمكفوفين وضعاف البصر يتيح أدوات مثل وصف الصور والمشاهد، قراءة النصوص والمستندات، الترجمة، المحادثة، حفظ النتائج، ورسائل طلب المساعدة.
• «المطوّر»: عبدالله الراشدي، بصفته ناشر التطبيق ومشغله ما لم يذكر خلاف ذلك.
• «المستخدم»: أي شخص يثبت التطبيق أو يستخدمه.
• «المحتوى»: النصوص والصور والملفات والأسئلة والمدخلات الصوتية بعد تحويلها إلى نص، وأي بيانات يختار المستخدم معالجتها.
• «الخدمات الخارجية»: خدمات مستقلة يحتاج إليها بعض وظائف التطبيق، ومنها Google Gemini وخدمات نظام التشغيل وتطبيقات المشاركة أو الرسائل والخوادم الوسيطة التي يحددها المستخدم.

2) قبول الشروط
يصبح استخدامك للتطبيق خاضعًا لهذه الشروط من لحظة تشغيله أو استخدام إحدى ميزاته. إذا استخدمت التطبيق نيابة عن شخص أو جهة، فأنت تقر بأن لديك الصلاحية اللازمة للموافقة نيابة عنها.

3) الأهلية العمرية
ميزات الذكاء الاصطناعي في التطبيق مخصصة لمن بلغ 18 عامًا فأكثر. وتنص شروط Google Gemini API السارية حاليًا على أن الخدمة موجهة للمطورين الذين يبنون باستخدام النماذج لأغراض مهنية أو تجارية، وليست للاستخدام الاستهلاكي. لا تستخدم ميزات Gemini إلا ضمن غرض يسمح به حسابك وشروط Google، ولا يجوز توجيه التطبيق إلى قاصر أو تمكينه من استخدام تلك الميزات بصورة مستقلة.

4) وصف الخدمة
بصير أداة تقنية مساعدة تسعى إلى جعل المحتوى المرئي والرقمي أكثر قابلية للوصول. تختلف الميزات المتاحة باختلاف المنصة والإصدار والجهاز والأذونات ونمط الاتصال والخدمات الخارجية.

5) طبيعة المخرجات وحدود الدقة
مخرجات الذكاء الاصطناعي احتمالية وقد تكون غير صحيحة، ناقصة، قديمة، متحيزة، أو غير ملائمة للسياق. قد يخطئ التطبيق في قراءة نص، أو تحديد عملة أو دواء أو شخص أو عائق، أو وصف موقع أو مستند. يجب مراجعة الأصل والاستعانة بشخص مؤهل عند الحاجة.

6) السلامة والتنقل
وصف المشهد ووضع المشي المباشر أدوات مساندة وليسا نظام ملاحة أو وسيلة تنقل مستقلة. لا تعتمد على التطبيق وحده لعبور الطرق، استخدام السلالم أو المصاعد، الاقتراب من المركبات، التعامل مع النار أو الآلات، أو الحركة في مكان خطر أو مزدحم. استخدم العصا البيضاء أو الكلب المرشد أو المرافق البشري أو وسيلة السلامة المناسبة.

7) الكاميرا في وضع المشي المباشر
في Android قد يعمل وضع المشي المباشر كخدمة أمامية ويحلل صورًا متتابعة حتى توقفه، وقد يستمر عند انتقال التطبيق إلى الخلفية بحسب إعدادات الجهاز. يجب إيقافه عندما لا يكون مطلوبًا، وعدم توجيه الكاميرا إلى أشخاص أو أماكن خاصة دون حق أو موافقة.

8) رسائل طلب المساعدة والطوارئ
ميزة طلب المساعدة تُنشئ رسالة في تطبيق الرسائل أو المشاركة ولا تضمن إرسالها أو وصولها. يجب على المستخدم مراجعة المستلم والنص ثم تأكيد الإرسال بنفسه. لا تحل هذه الميزة محل الاتصال بأرقام الطوارئ الرسمية، وقد تتعطل بسبب الشبكة أو البطارية أو الأذونات أو الموقع أو تطبيق الرسائل.

9) المجالات الطبية والقانونية والمالية
أي محتوى طبي أو دوائي أو قانوني أو مالي أو ضريبي أو مهني هو للمساعدة العامة فقط، وليس تشخيصًا أو وصفة أو فتوى قانونية أو استشارة مهنية. لا تتخذ قرارًا مؤثرًا اعتمادًا على التطبيق وحده.

10) أنماط الاتصال
قد يتيح Android نمطين: اتصال مباشر بخدمة Gemini باستخدام مفتاح المستخدم، أو اتصال عبر خادم وسيط يحدده المستخدم أو الجهة التي وفرت له الإعدادات. إصدار iOS الحالي يستخدم الاتصال المباشر. يتحمل مشغل أي خادم وسيط مستقل مسؤولية ممارساته وأمنه ومدة احتفاظه.

11) Google Gemini
تعتمد ميزات الذكاء الاصطناعي على Google Gemini API. يخضع استخدام Gemini لشروط Google وسياساتها وحصة الحساب وفوترته وتوفر الخدمة. Google جهة مستقلة عن المطوّر، وقد تغير النماذج أو الحدود أو الأسعار أو ممارسات المعالجة.

12) الحساب المجاني والمدفوع لدى Google
قد تختلف طريقة استخدام Google للمدخلات والمخرجات باختلاف ما إذا كانت الخدمة أو المشروع غير مدفوع أو مدفوع. وفق شروط Gemini API، قد تستخدم Google المحتوى المرسل عبر الخدمات غير المدفوعة لتحسين منتجاتها، وقد يراجعه أشخاص مخولون؛ أما الخدمات المدفوعة فلا تستخدم المدخلات والمخرجات لتحسين المنتجات وفق الشروط المنشورة، مع بقاء معالجة محدودة للأمان والامتثال. لا ترسل معلومات حساسة قبل مراجعة إعدادات حسابك وشروط Google الحالية.

13) مفتاح API والتكاليف
إذا أدخلت مفتاح Gemini API، فأنت مسؤول عن حمايته، وصحة المشروع المرتبط به، وحدود الاستخدام، وأي رسوم أو ضرائب. لا تشارك المفتاح. ألغِه أو دوّره فورًا عند الاشتباه بتسربه. لا يضمن المطوّر بقاء أي حصة مجانية.

14) محتوى المستخدم وحقوقه
تحتفظ بحقوقك في المحتوى الذي تختاره. تمنح التطبيق والخدمات الخارجية اللازمة إذنًا محدودًا لمعالجة المحتوى بقدر ما يلزم لتنفيذ طلبك. أنت مسؤول عن امتلاك حق استخدام المحتوى وعن الحصول على موافقة الأشخاص الظاهرين فيه متى كانت مطلوبة.

15) البيانات الحساسة وبيانات الغير
لا ترسل رقم هوية، أو معلومات صحية أو مالية، أو أسرارًا تجارية، أو صورًا خاصة، أو بيانات قاصر أو طرف ثالث إلا عند الضرورة وبعد فهم مسار المعالجة والحصول على الصلاحية اللازمة. استخدم أقل قدر ممكن من البيانات واحذف أو اخفِ ما لا يلزم.

16) الاستخدام المقبول والمحظور
يُحظر استخدام التطبيق في نشاط غير مشروع، أو احتيال أو تزوير أو انتحال، أو انتهاك خصوصية أو ملكية فكرية، أو مراقبة أشخاص دون حق، أو إنشاء برمجيات ضارة، أو تجاوز أنظمة الحماية أو الحصص، أو اتخاذ قرار عالي الخطورة دون مراجعة بشرية مؤهلة.

17) مخرجات الذكاء الاصطناعي وحقوق الملكية
قد تكون المخرجات مشابهة لمخرجات يحصل عليها مستخدمون آخرون، ولا يضمن المطوّر تفردها أو قابليتها للحماية أو خلوها من حقوق الغير. تقع على المستخدم مسؤولية مراجعة المخرجات قبل نشرها أو استخدامها تجاريًا.

18) ملكية التطبيق والترخيص
يملك المطوّر أو المرخصون له حقوق التطبيق وعلامته وتصميمه ونصوصه وكوده، باستثناء مكونات الطرف الثالث الخاضعة لتراخيصها. يمنح المستخدم ترخيصًا شخصيًا محدودًا، غير حصري، غير قابل للنقل، وقابلًا للإلغاء لاستخدام التطبيق بصورة مشروعة.

19) التخزين المحلي والملفات الناتجة
قد يحفظ التطبيق الإعدادات، المحفوظات، السجل، بيانات الطوارئ، ونتائج أو بيانات وصفية محليًا بحسب اختيارات المستخدم والمنصة. الملفات التي ينزلها المستخدم إلى مجلد عام، مثل التنزيلات، تبقى حتى يحذفها يدويًا. يجب على المستخدم حماية جهازه ونسخه الاحتياطية.

20) التوفر والتغييرات
قد تتوقف بعض الميزات أو تتغير بسبب الصيانة أو الاتصال أو الجهاز أو المتجر أو تحديثات Google أو الخادم الوسيط. يجوز تعديل الميزات أو النماذج أو الحدود أو إيقافها دون ضمان استمرارها بالشكل نفسه.

21) التحديثات
قد تكون التحديثات ضرورية للأمان أو التوافق. استمرار استخدام التطبيق بعد نشر شروط محدثة يعني قبولها بالقدر الذي يسمح به النظام. عند وجود تغيير جوهري، يسعى المطوّر إلى إظهاره داخل التطبيق أو صفحة المتجر.

22) إخلاء الضمان
يُقدّم التطبيق «كما هو» و«بحسب التوفر». لا يقدم المطوّر ضمانًا صريحًا أو ضمنيًا بشأن خلوه من الأخطاء، أو استمراره، أو دقة المخرجات، أو ملاءمته لغرض محدد، وذلك بالقدر الذي يسمح به النظام.

23) تحديد المسؤولية
إلى الحد الذي يسمح به النظام، لا يكون المطوّر مسؤولًا عن ضرر نتج عن الاعتماد المنفرد على مخرج، أو استخدام غير آمن، أو فقد بيانات، أو رسوم API، أو عطل جهاز أو خدمة خارجية. لا يستبعد هذا البند مسؤولية لا يجوز نظامًا استبعادها أو تقييدها.

24) تعليق الاستخدام وإنهاؤه
يجوز تقييد ميزة أو إيقافها عند إساءة الاستخدام، أو وجود خطر أمني أو قانوني، أو توقف خدمة خارجية. يمكنك إنهاء استخدامك بحذف التطبيق وإلغاء مفتاح API وحذف الملفات المحلية والعامة التي أنشأتها.

25) القانون الحاكم وأحكام عامة
تخضع هذه الشروط لأنظمة المملكة العربية السعودية، وتختص الجهة القضائية المختصة بنظر النزاع، ما لم يوجب نظام آمر خلاف ذلك. إذا تعذر تنفيذ بند، يبقى باقي البنود نافذًا. عدم التمسك بحق في مرة لا يعد تنازلًا دائمًا عنه.

26) اللغة والتواصل
النسخة العربية هي المرجع داخل المملكة العربية السعودية، والإنجليزية ترجمة مساعدة. للاستفسارات أو الطلبات المتعلقة بهذه الشروط: ubdallahalrashdee@gmail.com.
"""

private let termsEnglish = """
Effective date: 5 June 2026
Legal version: 3

Please read these Terms carefully before using Basir. By using the app or any AI-powered feature, you acknowledge that you have read, understood, and agreed to these Terms and the Privacy Policy. Do not use the app if you do not agree.

1) Definitions
• “Basir” or the “App” means an assistive application for blind and low-vision users that offers features such as image and scene description, text and document reading, translation, conversation, saved results, and help-message preparation.
• “Developer” means Abdullah Al-Rashidi as the publisher and operator of the App unless otherwise stated.
• “User” means any person who installs or uses the App.
• “Content” means text, images, files, questions, voice input after speech-to-text conversion, and any data the User chooses to process.
• “External Services” means independent services required by some features, including Google Gemini, operating-system services, messaging or sharing apps, and proxy servers configured by the User.

2) Acceptance
Your use of the App is subject to these Terms from the moment you launch it or use a feature. If you use the App for another person or organization, you confirm that you have authority to accept these Terms on their behalf.

3) Age Eligibility
The App’s AI features are intended for users aged 18 or older. The current Google Gemini API terms also state that the service is for developers building with Google AI models for professional or business purposes, not consumer use. Use Gemini-powered features only for a purpose permitted by your account and Google’s terms, and do not direct the App to minors or make those features independently available to them.

4) Service Description
Basir is an assistive technology intended to make visual and digital content more accessible. Available features vary by platform, version, device, permissions, connection mode, and external services.

5) Nature and Accuracy of Outputs
AI outputs are probabilistic and may be incorrect, incomplete, outdated, biased, or unsuitable for the context. The App may misread text, identify a currency, medicine, person, obstacle, location, or document incorrectly. Review the original source and obtain qualified human assistance when needed.

6) Safety and Mobility
Scene description and live walking mode are supplementary tools, not navigation systems or independent mobility aids. Never rely on the App alone to cross roads, use stairs or elevators, approach vehicles, handle fire or machinery, or move through dangerous or crowded places. Use a white cane, guide dog, human guide, or other appropriate safety method.

7) Camera Use in Live Walking Mode
On Android, live walking mode may run as a foreground service and analyze a sequence of images until you stop it, and it may continue while the App is in the background depending on device settings. Stop it when it is not needed and do not point the camera at people or private places without a lawful basis or consent.

8) Help Messages and Emergencies
The help feature prepares a message in a messaging or sharing app. It does not guarantee that the message is sent or delivered. You must review the recipient and content and confirm sending yourself. It does not replace official emergency numbers and may fail because of network, battery, permission, location, or messaging-app limitations.

9) Medical, Legal, and Financial Matters
Medical, medication, legal, financial, tax, or professional content is general assistance only and is not a diagnosis, prescription, legal opinion, or professional advice. Do not make a material decision based solely on the App.

10) Connection Modes
Android may offer direct connection to Gemini using the User’s API key or connection through a proxy server configured by the User or the organization that supplied the settings. The current iOS release uses direct connection. An independent proxy operator is responsible for its own practices, security, and retention.

11) Google Gemini
AI features rely on the Google Gemini API. Gemini use is subject to Google’s terms, policies, account quota, billing, and service availability. Google is independent from the Developer and may change models, limits, prices, or processing practices.

12) Google Free and Paid Services
Google’s use of inputs and outputs may differ depending on whether the service or project is unpaid or paid. Under the published Gemini API terms, Google may use content submitted through unpaid services to improve its products and authorized people may review it. For paid services, Google states that prompts and responses are not used to improve products, while limited processing for security, abuse monitoring, and legal compliance may still occur. Do not submit sensitive information until you have reviewed your account settings and Google’s current terms.

13) API Key and Charges
If you enter a Gemini API key, you are responsible for protecting it, selecting the correct project, monitoring limits, and paying any charges or taxes. Do not share the key. Revoke or rotate it immediately if exposure is suspected. The Developer does not guarantee that any free quota will remain available.

14) User Content and Permissions
You retain your rights in Content you choose to submit. You grant the App and necessary External Services a limited permission to process it only as needed to perform your request. You are responsible for having the right to use the Content and for obtaining consent from people shown in it when required.

15) Sensitive and Third-Party Data
Do not submit identity numbers, health or financial information, trade secrets, private images, or data about a minor or another person unless necessary, authorized, and understood. Minimize the data and redact anything not needed.

16) Acceptable and Prohibited Use
You must not use the App for unlawful conduct, fraud, forgery, impersonation, privacy or intellectual-property violations, unauthorized surveillance, malware, bypassing safeguards or quotas, or making a high-risk decision without qualified human review.

17) AI Outputs and Intellectual Property
Outputs may resemble content generated for other users. The Developer does not guarantee exclusivity, protectability, or freedom from third-party rights. You are responsible for reviewing outputs before publishing or commercially using them.

18) App Ownership and License
The Developer or licensors own the App, brand, design, original copy, and code, except third-party components governed by their licenses. You receive a personal, limited, non-exclusive, non-transferable, revocable license for lawful use.

19) Local Storage and Generated Files
Depending on platform and settings, the App may store preferences, saved items, history, emergency details, results, or metadata locally. Files downloaded to a public folder, such as Downloads, remain until you delete them manually. You are responsible for protecting your device and backups.

20) Availability and Changes
Features may become unavailable or change because of maintenance, connectivity, device or store restrictions, Google updates, or proxy changes. Features, models, or limits may be modified or discontinued without a guarantee that they will remain unchanged.

21) Updates
Updates may be required for security or compatibility. Continued use after revised Terms are published constitutes acceptance to the extent permitted by law. For a material change, the Developer will seek to provide notice in the App or store listing.

22) Disclaimer of Warranties
The App is provided “as is” and “as available.” To the extent permitted by law, the Developer gives no express or implied warranty that it will be error-free, uninterrupted, accurate, or fit for a particular purpose.

23) Limitation of Liability
To the extent permitted by law, the Developer is not liable for harm caused by sole reliance on an output, unsafe use, data loss, API charges, device failure, or an External Service. Nothing in this section excludes or limits liability that cannot legally be excluded or limited.

24) Suspension and Termination
A feature may be restricted or stopped because of misuse, legal or security risk, or loss of an External Service. You may stop using the App by uninstalling it, revoking the API key, and deleting local and public files you created.

25) Governing Law and General Terms
These Terms are governed by the laws of the Kingdom of Saudi Arabia, and disputes are subject to the competent court or authority unless mandatory law requires otherwise. If a provision is unenforceable, the remaining provisions continue. Failure to enforce a right once is not a permanent waiver.

26) Language and Contact
The Arabic version is the reference version in the Kingdom of Saudi Arabia; English is an assisting translation. Questions or requests relating to these Terms may be sent to: ubdallahalrashdee@gmail.com.
"""

private let privacyArabic = """
تاريخ النفاذ: 5 يونيو 2026
الإصدار القانوني: 3

توضح هذه السياسة كيف يتعامل تطبيق بصير مع البيانات. صيغت بلغة مباشرة لتكون قابلة للقراءة بقارئات الشاشة. اقرأها مع الشروط والأحكام.

1) المسؤول عن المعالجة ونطاق السياسة
المسؤول عن التطبيق: عبدالله الراشدي. البريد المخصص للاستفسارات والطلبات: ubdallahalrashdee@gmail.com. تنطبق السياسة على تطبيق بصير على Android وiOS، مع اختلاف بعض الميزات بين المنصتين، ولا تغطي ممارسات خدمة خارجية خارج سيطرة المطوّر.

2) ملخص واضح
• لا يتطلب بصير إنشاء حساب لدى المطوّر.
• لا يحتوي التطبيق على إعلانات أو معرّفات إعلانية أو بيع للبيانات.
• كثير من الإعدادات والنتائج تحفظ محليًا على جهازك.
• عند استخدام الذكاء الاصطناعي، يُرسل المحتوى الذي اخترته إلى Google Gemini مباشرة أو، في Android عند اختياره، عبر خادم وسيط مُعدّ.
• لا توجد معالجة ذكاء اصطناعي سحابية دون إرسال المحتوى إلى مزود الخدمة المستخدم.

3) البيانات التي تختار تقديمها
قد يعالج التطبيق نص السؤال أو الترجمة، الصورة أو لقطة الشاشة، الملف أو النص المستخرج منه، وصف المهمة، والمدخل الصوتي بعد تحويله إلى نص. لا يطلب التطبيق هذه البيانات إلا عند استخدامك للميزة ذات الصلة.

4) البيانات المحلية على Android
قد يحفظ Android محليًا: اللغة والصوت وإعدادات الاتصال، مفتاح API، جهة الطوارئ، المحفوظات، سجل النشاط إذا لم توقفه، نتائج محفوظة، بيانات وصفية عن المستندات، وبيانات تضيفها إلى أدوات الذاكرة مثل الأشخاص أو المنتجات أو الأماكن. تُقيد قاعدة البيانات تلقائيًا تقريبًا بآخر 1000 سجل نشاط و200 سجل مستند، وقد تبقى الملفات المنزلة خارج مساحة التطبيق.

5) البيانات المحلية على iOS
قد يحفظ iOS محليًا: اللغة والصوت والإعدادات، مفتاح API في iOS Keychain، جهة الطوارئ، المحفوظات والنتائج المحفوظة، وسجل النشاط إذا لم توقفه. قد تُنشأ نسخة مؤقتة من ملف يختاره المستخدم أثناء المعالجة، ويخضع حذفها لسير العملية ونظام التشغيل.

6) مفتاح Gemini API
يُحفظ المفتاح على الجهاز. يستخدم Android مخزن مفاتيح النظام وتشفير AES-GCM عند توفرهما، وقد يرجع في بعض الأجهزة أو الحالات غير المدعومة إلى تفضيلات محلية أقل حماية. يستخدم iOS خدمة Keychain القياسية. يُرسل المفتاح إلى Google عند الاتصال المباشر، ولا يرسله المطوّر إلى نفسه. احمِ الجهاز ولا تشارك المفتاح.

7) الاتصال المباشر بـ Google
في الاتصال المباشر، يرسل التطبيق طلبك والمحتوى المحدد ومفتاح API إلى Google Gemini عبر HTTPS. لا تمر البيانات في هذه الحالة عبر خادم يملكه المطوّر. تتحكم Google في معالجة البيانات داخل خدمتها وفق نوع حسابك ومشروعك وشروطها.

8) الاتصال عبر خادم وسيط
في Android قد تختار خادمًا وسيطًا. عندها يرسل التطبيق الطلب إلى عنوان الخادم الذي أعددته، وقد يرسل الخادم المحتوى إلى Gemini باستخدام مفتاح مخزن لديه. مشغل الخادم قادر تقنيًا على الوصول إلى البيانات المارة خلاله وتحدد سياسته مدة الاحتفاظ والأمان والموقع الجغرافي للمعالجة. الخادم النموذجي المرفق مع مشروع بصير يحذف ملفات التحويل المؤقتة بعد انتهاء الطلب، لكن لا يجوز افتراض أن كل خادم آخر يفعل ذلك.

9) استخدام Google للبيانات: الخدمة غير المدفوعة والمدفوعة
بحسب شروط Gemini API المنشورة، قد تستخدم Google المدخلات والمخرجات المرسلة عبر الخدمات غير المدفوعة لتحسين منتجاتها، وقد يراجعها أشخاص مخولون. وتذكر Google أن المدخلات والمخرجات في الخدمات المدفوعة لا تستخدم لتحسين المنتجات، مع احتفاظ أو مراجعة محدودة لأغراض منع الإساءة والالتزام القانوني. راجع تصنيف مشروعك قبل إرسال معلومات حساسة.

10) الاحتفاظ لدى Google وFiles API
عند رفع ملف عبر Gemini Files API، تذكر Google أن الملف يحذف تلقائيًا بعد 48 ساعة. كما قد تحتفظ Google ببيانات طلبات معينة لفترة محدودة لأمن الخدمة ومراقبة الإساءة؛ وتذكر وثائق مراقبة الإساءة مدة تصل إلى 55 يومًا لبعض السجلات. هذه المدد تديرها Google وقد تتغير وفق شروطها أو القانون.

11) الصور والكاميرا والملفات
لا يصل التطبيق إلى صورة أو ملف إلا عندما تختاره أو تلتقطه أو تبدأ وضعًا يحتاج الكاميرا. يُستخدم المحتوى لتنفيذ الطلب، وقد يُرسل إلى Gemini أو الخادم الوسيط بحسب نمط الاتصال. لا يعني اختيار ملف أن المطوّر يحصل على نسخة دائمة منه. الملفات الناتجة التي تحفظها في التنزيلات أو تطبيق الملفات تبقى تحت سيطرتك حتى تحذفها.

12) الميكروفون والتعرف على الكلام
يستخدم الميكروفون عند تفعيل الإملاء أو المحادثة الصوتية. قد ينفذ نظام التشغيل أو مزود التعرف الصوتي تحويل الكلام إلى نص وفق إعدادات جهازك. لا ينشئ بصير عمدًا أرشيفًا دائمًا للتسجيل الصوتي الخام، لكنه يعالج النص الناتج وقد يحفظه في السجل أو النتائج وفق إعداداتك.

13) الموقع الجغرافي
في Android قد يطلب التطبيق موقعًا لمرة واحدة عند إعداد رسالة طلب مساعدة، أو اختياريًا لتحسين سياق وضع المشي المباشر. في iOS يستخدم الموقع حاليًا لرسالة طلب المساعدة عند اختيارك. لا يرسل التطبيق الموقع تلقائيًا؛ تظهر الرسالة للمراجعة قبل الإرسال. قد يكون الموقع تقريبيًا أو غير متاح.

14) وضع المشي المباشر والكاميرا في الخلفية
في Android يمكن أن يعمل وضع المشي المباشر كخدمة أمامية ويستخدم الكاميرا دوريًا حتى توقفه. تُرسل اللقطات اللازمة للتحليل وفق نمط الاتصال. لا يستخدم بصير هذه اللقطات للإعلانات، لكن مزود الخدمة يعالجها وفق سياسته. أوقف الوضع عند انتهاء الحاجة واحترم خصوصية الآخرين.

15) أغراض المعالجة
نستخدم البيانات لتنفيذ الميزة التي طلبتها، وتخصيص اللغة والصوت، حفظ ما تطلب حفظه، إعداد رسالة مساعدة، تشخيص خطأ محلي ظاهر لك، حماية الخدمة، والامتثال لالتزام نظامي. لا نستخدمها لبناء ملف إعلاني أو للتسويق المباشر.

16) الأساس النظامي
تعتمد المعالجة أساسًا على طلبك الصريح للميزة أو موافقتك عند اختيار محتوى أو إذن، وعلى تنفيذ الخدمة التي طلبتها. وقد تستند معالجة محدودة إلى مصلحة مشروعة في الأمن ومنع الإساءة أو إلى التزام نظامي، متى انطبق ذلك ودون الإخلال بحقوقك.

17) الجهات التي قد تستلم البيانات
قد تصل البيانات إلى: Google عند استخدام Gemini؛ مشغل الخادم الوسيط الذي اخترته؛ مزود التعرف الصوتي أو خدمات النظام؛ تطبيق الرسائل أو المشاركة الذي تفتحه؛ أو جهة مختصة عند وجود طلب نظامي ملزم. لا يبيع المطوّر البيانات ولا يؤجرها.

18) نقل البيانات خارج المملكة
قد تعالج Google أو مشغل خادم وسيط البيانات في دول خارج المملكة العربية السعودية. يعتمد مكان المعالجة والضمانات على مزود الخدمة وإعدادات المشروع. لا تستخدم الميزة السحابية بمحتوى حساس ما لم تكن تقبل مسار النقل وتراجِع سياسة المزود.

19) مدة الاحتفاظ والحذف
• الإعدادات والمحفوظات والسجل المحلي: حتى تحذفها من التطبيق أو تحذف التطبيق، مع مراعاة النسخ الاحتياطية التي يديرها نظامك.
• مفتاح API: حتى تستبدله أو تحذفه أو تحذف التطبيق.
• الملفات في التنزيلات أو تطبيق الملفات: حتى تحذفها يدويًا.
• النسخ المؤقتة: خلال مدة الحاجة للمعالجة، وقد ينظفها النظام لاحقًا.
• Google أو الخادم الوسيط: وفق الفقرات السابقة وسياسة كل مزود.
حذف بيانات التطبيق لا يحذف تلقائيًا ما سبق إرساله إلى خدمة خارجية أو ملفًا محفوظًا في مجلد عام.

20) الحماية
يستخدم التطبيق HTTPS للاتصال المدعوم، وآليات التخزين الآمن المتاحة في النظام، ويعطل النسخ الاحتياطي للتطبيق والاتصال غير المشفر في إعداد Android الحالية. لا توجد وسيلة إلكترونية آمنة بنسبة 100%. استخدم قفلًا قويًا للجهاز، وحدّث النظام، وراجع الأذونات، وألغِ المفتاح عند فقد الجهاز.

21) اختياراتك وأذوناتك
يمكنك رفض إذن الكاميرا أو الميكروفون أو الموقع أو الملفات، لكن الميزة المرتبطة قد لا تعمل. يمكنك إيقاف حفظ سجل النشاط، وإيقاف حفظ نتائج التحليل تلقائيًا، ومسح البيانات المحلية، وحذف الملفات العامة يدويًا، وتغيير نمط الاتصال، وإلغاء مفتاح API من حساب Google.

22) حقوقك وطلباتك
بحسب نظام حماية البيانات الشخصية السعودي والأنظمة المنطبقة، قد تشمل حقوقك العلم بالمعالجة، والوصول إلى بياناتك، والحصول على نسخة مقروءة، والتصحيح أو الإكمال أو التحديث، وطلب الإتلاف، وسحب الموافقة عندما تكون هي الأساس، وتقديم شكوى أو طلب تعويض عند توافر شروطه. أرسل طلبك إلى البريد المذكور في الفقرة الأولى. يُتعامل مع الطلب خلال المدة النظامية، وعادة خلال 30 يومًا، وقد تمتد عند السماح نظامًا مع إشعارك بالسبب. قد نطلب ما يكفي للتحقق من الهوية وحماية البيانات.

23) القُصّر والقرارات الآلية
ميزات Gemini غير مخصصة لمن هم دون 18 عامًا. لا يستخدم بصير المخرجات لاتخاذ قرار ملزم عنك، لكنك قد تختار الاستفادة منها؛ لذلك يجب إجراء مراجعة بشرية لأي قرار مهم أو عالي المخاطر.

24) التحديثات والشكاوى واللغة
قد تُحدّث هذه السياسة عند تغير ميزة أو مزود أو متطلب نظامي، ويظهر تاريخ النفاذ أعلى الوثيقة. للاستفسار أو ممارسة حق أو تقديم شكوى إلى المطوّر: ubdallahalrashdee@gmail.com. ويجوز لك أيضًا التقدم إلى الجهة السعودية المختصة وفق القنوات الرسمية. النسخة العربية هي المرجع داخل المملكة، والإنجليزية ترجمة مساعدة.
"""

private let privacyEnglish = """
Effective date: 5 June 2026
Legal version: 3

This Policy explains how Basir handles data. It is written in direct, screen-reader-friendly language. Read it together with the Terms and Conditions.

1) Controller and Scope
The person responsible for the App is Abdullah Al-Rashidi. Privacy questions and requests may be sent to ubdallahalrashdee@gmail.com. This Policy applies to Basir on Android and iOS, noting that features differ by platform. It does not govern an external service outside the Developer’s control.

2) Clear Summary
• Basir does not require an account with the Developer.
• The App contains no ads, advertising identifiers, or sale of data.
• Many settings and results are stored locally on your device.
• When you use AI, the Content you select is sent directly to Google Gemini or, on Android when selected, through a configured proxy server.
• Cloud AI processing cannot occur without sending Content to the provider being used.

3) Data You Choose to Provide
The App may process question or translation text, an image or screenshot, a file or text extracted from it, task instructions, and voice input after speech-to-text conversion. The App requests such data only when you use the related feature.

4) Local Data on Android
Android may locally store language, voice and connection settings, the API key, emergency contact, saved items, activity history unless disabled, saved results, document metadata, and information you add to memory tools such as people, products, or places. The database is automatically limited to approximately the latest 1,000 activity records and 200 document records. Downloaded files may remain outside the App’s private storage.

5) Local Data on iOS
iOS may locally store language, voice and other settings, the API key in the standard iOS Keychain, emergency contact, saved items and results, and activity history unless disabled. A temporary copy of a selected file may be created during processing, with deletion governed by the workflow and operating system.

6) Gemini API Key
The key is stored on the device. Android uses the system keystore and AES-GCM encryption when available, but may fall back in unsupported circumstances to less protected local preferences. iOS uses the standard Keychain. In direct mode, the key is sent to Google and is not sent to the Developer. Protect your device and do not share the key.

7) Direct Connection to Google
In direct mode, the App sends your request, selected Content, and API key to Google Gemini over HTTPS. The data does not pass through a server owned by the Developer. Google controls processing within its service according to your account type, project, and its terms.

8) Proxy Connection
On Android, you may select a proxy server. The App then sends the request to the address you configured, and that server may send the Content to Gemini using a key stored by the server. The operator can technically access data passing through it and its policy determines retention, security, and processing location. The sample proxy included with the Basir project deletes temporary conversion files after a request, but you must not assume that every other proxy does the same.

9) Google Data Use: Unpaid and Paid Services
Under the published Gemini API terms, Google may use inputs and outputs submitted through unpaid services to improve its products, and authorized people may review them. Google states that inputs and outputs in paid services are not used to improve products, although limited retention or review may still occur for abuse prevention and legal compliance. Confirm your project type before submitting sensitive information.

10) Google Retention and the Files API
When a file is uploaded through the Gemini Files API, Google states that it is automatically deleted after 48 hours. Google may also retain certain request data for a limited period for service security and abuse monitoring; its abuse-monitoring documentation describes a period of up to 55 days for some logs. Google controls these periods and may change them under its terms or applicable law.

11) Images, Camera, and Files
The App accesses an image or file only when you select or capture it or start a camera-dependent mode. The Content is used to perform the request and may be sent to Gemini or the configured proxy depending on connection mode. Selecting a file does not mean the Developer receives a permanent copy. Generated files saved to Downloads or the Files app remain under your control until you delete them.

12) Microphone and Speech Recognition
The microphone is used when you start dictation or voice conversation. The operating system or speech-recognition provider may convert audio to text under your device settings. Basir does not intentionally create a permanent archive of raw voice recordings, but the resulting text may be processed and saved in activity history or results according to your settings.

13) Location
On Android, the App may request a one-time location when preparing a help message or, optionally, to improve context in live walking mode. On iOS, location is currently used for a help message when you choose it. The App does not send location automatically; the message is displayed for review before sending. Location may be approximate or unavailable.

14) Live Walking Mode and Background Camera
On Android, live walking mode can run as a foreground service and use the camera periodically until you stop it. Images needed for analysis are sent according to the selected connection mode. Basir does not use them for advertising, but the service provider processes them under its policy. Stop the mode when finished and respect other people’s privacy.

15) Purposes
Data is used to perform the feature you requested, personalize language and voice, save items you ask to save, prepare a help message, display local error information, secure the service, and comply with a legal obligation. It is not used to build an advertising profile or for direct marketing.

16) Legal Basis
Processing primarily relies on your express request for a feature or your choice to select Content or grant permission, and on performing the service you requested. Limited processing may also rely on a legitimate interest in security and abuse prevention or a legal obligation where applicable and without overriding your rights.

17) Recipients
Data may be received by Google when Gemini is used; the operator of a proxy you selected; a speech-recognition or operating-system service; the messaging or sharing app you open; or a competent authority under a binding lawful request. The Developer does not sell or rent data.

18) International Transfers
Google or a proxy operator may process data outside the Kingdom of Saudi Arabia. The processing location and safeguards depend on the provider and project settings. Do not use a cloud feature for sensitive Content unless you accept the transfer path and have reviewed the provider’s policy.

19) Retention and Deletion
• Local settings, saved items, and history: until you delete them in the App or uninstall the App, subject to operating-system backups you control.
• API key: until replaced or removed, or until the App is uninstalled.
• Files in Downloads or the Files app: until you delete them manually.
• Temporary copies: for the processing period and potentially until later operating-system cleanup.
• Google or a proxy: as described above and under each provider’s policy.
Deleting App data does not automatically delete data already sent to an external service or a file stored in a public folder.

20) Security
The App uses HTTPS for supported connections and available operating-system secure-storage mechanisms. The current Android configuration disables App backup and cleartext network traffic. No electronic storage or transmission method is completely secure. Use a strong device lock, keep the system updated, review permissions, and revoke the key if the device is lost.

21) Your Choices and Permissions
You may refuse camera, microphone, location, or file permissions, but the related feature may not work. You can disable activity-history saving and automatic result saving, clear local data, manually delete public files, change connection mode, and revoke the API key in your Google account.

22) Your Rights and Requests
Under the Saudi Personal Data Protection Law and other applicable laws, your rights may include being informed, accessing data, obtaining a readable copy, correction, completion or update, requesting destruction, withdrawing consent where consent is the basis, and lodging a complaint or seeking compensation where the legal conditions are met. Send a request to the email in section 1. Requests will be handled within the legally required period, ordinarily within 30 days, with an extension where legally permitted and with notice and reasons. Sufficient information may be requested to verify identity and protect the data.

23) Minors and Automated Outputs
Gemini-powered features are not intended for users under 18. Basir does not itself make a binding decision about you, but you may choose to use generated output. Qualified human review is required for any important or high-risk decision.

24) Updates, Complaints, and Language
This Policy may be updated when a feature, provider, or legal requirement changes. The effective date appears at the top. Privacy questions, rights requests, or complaints to the Developer may be sent to ubdallahalrashdee@gmail.com. You may also complain to the competent Saudi authority through its official channels. The Arabic version is the reference version in the Kingdom; English is an assisting translation.
"""
