package com.basir.ai;

import android.content.Intent;
import android.net.Uri;

/**
 * v2.6 — legal-information screens (Terms of Service, Privacy Policy,
 * About) extracted from MainActivity. These are pure presentation: no AI
 * calls, no DB calls, no voice / camera / files. They only call back into
 * the host for UI primitives.
 *
 * This is the first slice of the MainActivity decomposition. Subsequent
 * slices will extract task screens (talk, vision, documents), the
 * settings screen, and the result screen — each as its own class with
 * the same Host-callback shape used here.
 */
public final class LegalScreens {

    private final BasirScreenHost host;

    public LegalScreens(BasirScreenHost host) {
        this.host = host;
    }

    public void showTerms() {
        host.resetScreen(host.t("الشروط والأحكام", "Terms and Conditions"),
                host.t("الإصدار 2 — " + host.appVersion(),
                  "Version 2 — " + host.appVersion()));

        host.addPlainText(host.t(
            "يرجى قراءة هذه الشروط بعناية قبل استخدام تطبيق بصير. باستخدامك التطبيق أو الاستمرار في استخدامه، فإنك تقر بأنك قرأت هذه الشروط وفهمتها ووافقت عليها، بما في ذلك سياسة الخصوصية وأي تعليمات أمان تظهر داخل التطبيق.\n" +
            "\n" +
            "1) التعريفات\n" +
            "• \"التطبيق\" أو \"بصير\": تطبيق مساعد يعتمد على الذكاء الاصطناعي لمساعدة المكفوفين وضعاف البصر في مهام مثل قراءة المستندات، وصف الصور والمشاهد، الترجمة، التعرف على بعض العناصر، وتحويل المحتوى إلى صيغ أكثر قابلية للوصول.\n" +
            "• \"المطوّر\": مالك التطبيق أو الجهة التي تديره أو تنشره عبر المتاجر الرسمية.\n" +
            "• \"المستخدم\": كل شخص يثبت التطبيق أو يستخدمه أو يرسل من خلاله نصاً أو صورة أو ملفاً أو طلباً صوتياً.\n" +
            "• \"خدمات خارجية\": أي خدمات تابعة لطرف ثالث يعتمد عليها التطبيق، ومنها Google Gemini API وخدمات Android المدمجة مثل التعرف الصوتي أو مشاركة الرسائل.\n" +
            "\n" +
            "2) قبول الشروط\n" +
            "باستخدامك التطبيق، فإنك توافق على الالتزام بهذه الشروط. إذا كنت لا توافق عليها، فيجب عليك التوقف عن استخدام التطبيق وحذفه من جهازك. إذا كنت تستخدم التطبيق نيابةً عن شخص آخر، فإنك تقر بأن لديك الصلاحية أو الموافقة اللازمة لذلك.\n" +
            "\n" +
            "3) أهلية الاستخدام\n" +
            "إذا كان عمرك أقل من 18 سنة، فيجب استخدام التطبيق تحت إشراف ولي الأمر أو بموافقته. يتحمل ولي الأمر أو المشرف القانوني مسؤولية متابعة استخدام القاصر للتطبيق، خصوصاً عند إرسال صور أو ملفات أو معلومات شخصية أو صحية أو تعليمية.\n" +
            "\n" +
            "4) طبيعة الخدمة\n" +
            "بصير أداة مساعدة تكميلية، وليست بديلاً عن الوسائل الأساسية أو المهنية أو الرسمية. لا يحل التطبيق محل العصا البيضاء، أو الكلب المرشد، أو المرافق البشري عند الحاجة، أو الطبيب، أو المحامي، أو المستشار المالي، أو خدمات الطوارئ الرسمية.\n" +
            "\n" +
            "5) حدود الاعتماد على مخرجات الذكاء الاصطناعي\n" +
            "قد تحتوي المخرجات على أخطاء، أو وصف غير كامل، أو ترجمة غير دقيقة، أو استنتاج غير مناسب للسياق. يجب التحقق من أي معلومة حساسة أو مؤثرة قبل التصرف بناءً عليها، وبخاصة المعلومات الطبية، القانونية، المالية، الدوائية، السلامة المرورية، أو القرارات التي قد يترتب عليها ضرر.\n" +
            "\n" +
            "6) السلامة أثناء المشي والتنقل\n" +
            "أي وضع متعلق بالمشي أو وصف البيئة أو قراءة اللوحات أو التعرف على العوائق هو مساعدة بصرية فقط. لا تعتمد على التطبيق وحده عند عبور الطرق، استخدام السلالم، ركوب المصاعد، التنقل في أماكن مزدحمة، أو الحركة في بيئات خطرة. استخدامك للتطبيق لا يلغي مسؤوليتك الشخصية في اتخاذ احتياطات السلامة المناسبة.\n" +
            "\n" +
            "7) عدم استخدام التطبيق أثناء القيادة أو الأعمال الخطرة\n" +
            "يحظر استخدام التطبيق بطريقة تشتت الانتباه أثناء قيادة السيارة أو الدراجة أو تشغيل آلات أو أداء أعمال قد تسبب خطراً عليك أو على الآخرين. يتحمل المستخدم وحده مسؤولية أي استخدام غير آمن.\n" +
            "\n" +
            "8) خدمات الطوارئ\n" +
            "قد يتضمن التطبيق ميزة طوارئ تساعدك في إرسال رسالة أو موقع تقريبي إلى جهة تختارها. هذه الميزة لا تضمن وصول الرسالة فوراً، ولا تغني عن الاتصال بالجهات الرسمية المختصة مثل الإسعاف أو الدفاع المدني أو الشرطة عند وجود خطر حقيقي. قد تتأثر ميزة الطوارئ بعوامل مثل ضعف الإنترنت، نفاد البطارية، تعطل خدمة الموقع، أو قيود الجهاز.\n" +
            "\n" +
            "9) خدمات Google Gemini والخدمات الخارجية\n" +
            "يعتمد التطبيق على Google Gemini API لمعالجة بعض الطلبات. عند إرسال نص أو صورة أو ملف للمعالجة، قد يتم إرساله إلى Google أو معالجته وفق شروط وسياسات Google ذات الصلة. باستخدامك لهذه الميزات، فإنك تقر بأن خدمات Google مستقلة عن المطوّر، وقد تتغير شروطها أو أسعارها أو توفرها أو حدود استخدامها في أي وقت.\n" +
            "\n" +
            "10) مفتاح Gemini API والتكاليف\n" +
            "إذا أدخلت مفتاح Gemini API يدوياً، فأنت مسؤول عن صحة المفتاح، وسريته، وأي تكاليف أو حدود استخدام مرتبطة به وفق حسابك لدى Google. لا تشارك مفتاحك مع أي شخص. إذا اشتبهت بتسريب المفتاح، فقم بإلغائه أو تدويره من لوحة تحكم Google فوراً.\n" +
            "\n" +
            "11) المحتوى الذي يرفعه المستخدم\n" +
            "أنت مسؤول عن أي نص أو صورة أو ملف أو تسجيل صوتي أو بيانات ترسلها عبر التطبيق. يجب ألا ترسل محتوى غير قانوني، أو مسيئاً، أو ينتهك خصوصية الغير، أو حقوق الملكية الفكرية، أو يتضمن بيانات لا تملك صلاحية معالجتها أو مشاركتها.\n" +
            "\n" +
            "12) المحتوى الحساس\n" +
            "يُنصح بعدم إرسال بيانات شديدة الحساسية إلا عند الحاجة وبالقدر الضروري، مثل التقارير الطبية، الأرقام الوطنية، المستندات المالية، العقود، الصور الشخصية، بيانات الأطفال، أو أي بيانات تخص أشخاصاً آخرين. إذا أرسلت هذه البيانات، فأنت تقر بأنك قبلت معالجتها عبر الخدمات الخارجية اللازمة لتشغيل الميزة.\n" +
            "\n" +
            "13) الاستخدامات المحظورة\n" +
            "يُحظر استخدام التطبيق في أي مما يلي:\n" +
            "• انتهاك القوانين أو حقوق الآخرين.\n" +
            "• التحايل أو الاحتيال أو التزوير أو انتحال الشخصية.\n" +
            "• إرسال محتوى ضار أو مسيء أو ينتهك الخصوصية.\n" +
            "• استخدام التطبيق لاتخاذ قرارات عالية الخطورة دون مراجعة بشرية مؤهلة.\n" +
            "• محاولة تعطيل التطبيق، أو إساءة استخدام المفاتيح، أو تجاوز حدود الخدمات الخارجية.\n" +
            "\n" +
            "14) الملكية الفكرية\n" +
            "يظل التطبيق، وتصميمه، واسمه، وشعاراته، وواجهاته، ونصوصه الأصلية، وأي عناصر مملوكة للمطوّر أو مرخصة له، محمية بالأنظمة ذات الصلة. لا يحق لك نسخ التطبيق أو إعادة بيعه أو تفكيكه أو إعادة نشره أو استخدام اسمه بطريقة توحي بعلاقة غير مصرح بها.\n" +
            "\n" +
            "15) الترخيص المحدود\n" +
            "يمنحك المطوّر ترخيصاً محدوداً، غير حصري، غير قابل للنقل، وقابلاً للإلغاء، لاستخدام التطبيق لأغراض شخصية أو تعليمية أو يومية مشروعة وفق هذه الشروط.\n" +
            "\n" +
            "16) الخصوصية\n" +
            "تخضع معالجة البيانات لسياسة الخصوصية الخاصة بتطبيق بصير. تعد سياسة الخصوصية جزءاً مكملاً لهذه الشروط، ويجب قراءتها مع هذه الوثيقة.\n" +
            "\n" +
            "17) التحديثات وتغيير الميزات\n" +
            "يجوز للمطوّر تحديث التطبيق أو تعديل أو إضافة أو إزالة أي ميزة، بما في ذلك تغيير طريقة الاتصال بالخدمات الخارجية، تحسين الأمان، تعديل واجهة الاستخدام، أو إيقاف ميزة لم تعد مستقرة أو مناسبة. قد تؤثر التحديثات على طريقة عمل بعض الخصائص.\n" +
            "\n" +
            "18) إيقاف الخدمة أو إنهاء الاستخدام\n" +
            "يجوز للمطوّر إيقاف التطبيق أو أي ميزة مؤقتاً أو نهائياً لأسباب تقنية، قانونية، أمنية، تجارية، أو بسبب تغيّر خدمات الأطراف الثالثة. كما يجوز تقييد الاستخدام عند وجود إساءة استخدام أو مخالفة جوهرية لهذه الشروط.\n" +
            "\n" +
            "19) عدم تقديم ضمانات\n" +
            "يُقدّم التطبيق كما هو وبحسب توفره. لا يضمن المطوّر أن التطبيق سيكون خالياً من الأخطاء، أو متاحاً دائماً، أو مناسباً لكل حالة استخدام، أو أن نتائجه ستكون صحيحة أو كاملة أو آمنة للاعتماد عليها وحدها.\n" +
            "\n" +
            "20) حدود المسؤولية\n" +
            "إلى أقصى حد يسمح به النظام، لا يتحمل المطوّر المسؤولية عن أي خسارة أو ضرر مباشر أو غير مباشر أو عرضي أو تبعي ينشأ عن استخدام التطبيق أو عدم القدرة على استخدامه، أو عن الاعتماد المنفرد على مخرجاته، أو عن انقطاع الخدمات الخارجية، أو عن محتوى يرسله المستخدم.\n" +
            "\n" +
            "21) التعويض\n" +
            "توافق على تعويض المطوّر وحمايته من أي مطالبات أو أضرار أو تكاليف أو مسؤوليات تنشأ عن استخدامك المخالف لهذه الشروط، أو انتهاكك حقوق الآخرين، أو إرسال محتوى لا تملك حق معالجته أو مشاركته، وذلك بالقدر الذي يسمح به النظام.\n" +
            "\n" +
            "22) متجر التطبيقات والأطراف الثالثة\n" +
            "قد يخضع تنزيل التطبيق أو تحديثه لشروط متجر التطبيقات المستخدم، مثل Google Play أو أي متجر آخر. لا يعد أي متجر تطبيقات مسؤولاً عن محتوى التطبيق أو مخرجاته، ما لم تنص شروط المتجر أو الأنظمة المعمول بها على خلاف ذلك.\n" +
            "\n" +
            "23) القانون الحاكم والاختصاص\n" +
            "تخضع هذه الشروط وتفسر وفق أنظمة المملكة العربية السعودية. وتختص الجهة القضائية المختصة في المملكة بنظر أي نزاع ينشأ عنها، ما لم يوجد نص نظامي آمر يقضي بخلاف ذلك.\n" +
            "\n" +
            "24) تعارض النسخ اللغوية\n" +
            "أُعدت النسخة العربية لتكون النسخة المرجعية داخل المملكة العربية السعودية. وتعد النسخة الإنجليزية ترجمة مساعدة، ما لم يقرر المطوّر خلاف ذلك صراحةً.\n" +
            "\n" +
            "25) تحديث الشروط\n" +
            "قد يتم تعديل هذه الشروط من وقت لآخر. استمرارك في استخدام التطبيق بعد نشر أي تحديث يعد قبولاً بالشروط المعدلة. إذا كان التعديل جوهرياً، فيُفضّل إشعار المستخدم داخل التطبيق أو عبر صفحة المتجر متى كان ذلك ممكناً.\n" +
            "\n" +
            "26) التواصل\n" +
            "لأي استفسار متعلق بهذه الشروط، يمكنك التواصل مع المطوّر عبر وسيلة التواصل المنشورة داخل التطبيق أو صفحة التطبيق في المتجر.",

            "Please read these Terms carefully before using Basir. By using or continuing to use the app, you acknowledge that you have read, understood, and agreed to these Terms, including the Privacy Policy and any safety instructions displayed inside the app.\n" +
            "\n" +
            "1) Definitions\n" +
            "• \"App\" or \"Basir\" means the AI-powered assistive application designed to help blind and low-vision users with tasks such as reading documents, describing images and scenes, translation, identifying certain items, and converting content into more accessible formats.\n" +
            "• \"Developer\" means the owner, operator, or publisher of the app through official app stores.\n" +
            "• \"User\" means any person who installs, accesses, or uses the app, or submits text, images, files, or voice requests through it.\n" +
            "• \"External Services\" means third-party services used by the app, including Google Gemini API and Android built-in services such as speech recognition or message sharing.\n" +
            "\n" +
            "2) Acceptance of Terms\n" +
            "By using the app, you agree to comply with these Terms. If you do not agree, you must stop using the app and remove it from your device. If you use the app on behalf of another person, you confirm that you have the required authority or consent to do so.\n" +
            "\n" +
            "3) Eligibility\n" +
            "If you are under 18, you must use the app under the supervision or consent of a parent or legal guardian. The parent or guardian is responsible for monitoring the minor's use of the app, especially when images, files, personal data, health data, or educational data are submitted.\n" +
            "\n" +
            "4) Nature of the Service\n" +
            "Basir is a complementary assistive tool. It is not a substitute for essential, professional, or official support, including a white cane, guide dog, human assistance when needed, a doctor, lawyer, financial adviser, or official emergency services.\n" +
            "\n" +
            "5) Limits of Reliance on AI Outputs\n" +
            "AI outputs may contain errors, incomplete descriptions, inaccurate translations, or contextually inappropriate conclusions. You must verify any sensitive or material information before acting on it, especially medical, legal, financial, medication-related, road-safety, or other high-impact information.\n" +
            "\n" +
            "6) Safety While Walking and Moving\n" +
            "Any walking, scene-description, sign-reading, or obstacle-related feature is a visual aid only. Do not rely on the app alone when crossing roads, using stairs, entering elevators, moving in crowded places, or navigating hazardous environments. Using the app does not remove your personal responsibility to take appropriate safety precautions.\n" +
            "\n" +
            "7) No Use While Driving or Performing Hazardous Activities\n" +
            "You must not use the app in a way that distracts you while driving, cycling, operating machinery, or performing any activity that may create risk to you or others. You are solely responsible for unsafe use.\n" +
            "\n" +
            "8) Emergency Features\n" +
            "The app may include an emergency feature that helps you send a message or approximate location to a contact you choose. This feature does not guarantee immediate delivery and does not replace contacting official emergency services when there is real danger. Emergency features may be affected by internet quality, battery level, location-service limitations, device restrictions, or third-party service outages.\n" +
            "\n" +
            "9) Google Gemini and External Services\n" +
            "The app uses Google Gemini API to process certain requests. When you submit text, images, or files for processing, they may be sent to or processed by Google under Google's applicable terms and policies. By using these features, you acknowledge that Google services are independent from the Developer and that their terms, pricing, availability, and usage limits may change at any time.\n" +
            "\n" +
            "10) Gemini API Key and Costs\n" +
            "If you manually enter a Gemini API key, you are responsible for its accuracy, confidentiality, and any costs or usage limits associated with it under your Google account. Do not share your API key with anyone. If you suspect that your key has been exposed, revoke or rotate it through your Google console immediately.\n" +
            "\n" +
            "11) User-Submitted Content\n" +
            "You are responsible for any text, image, file, audio recording, or data you submit through the app. You must not submit content that is unlawful, abusive, privacy-infringing, intellectual-property-infringing, or that you do not have the right to process or share.\n" +
            "\n" +
            "12) Sensitive Content\n" +
            "You should avoid submitting highly sensitive data unless necessary and limited to what is required, such as medical reports, national identifiers, financial documents, contracts, personal photos, children's data, or data relating to other people. If you submit such data, you acknowledge that it may be processed through the external services required to operate the relevant feature.\n" +
            "\n" +
            "13) Prohibited Uses\n" +
            "You must not use the app to:\n" +
            "• Violate laws or the rights of others.\n" +
            "• Commit fraud, forgery, evasion, or impersonation.\n" +
            "• Submit harmful, abusive, or privacy-infringing content.\n" +
            "• Make high-risk decisions without qualified human review.\n" +
            "• Disrupt the app, misuse API keys, or bypass third-party service limits.\n" +
            "\n" +
            "14) Intellectual Property\n" +
            "The app, its design, name, logos, interfaces, original text, and any elements owned or licensed by the Developer are protected by applicable laws. You may not copy, resell, decompile, republish, or use the app's name in a way that suggests an unauthorized relationship.\n" +
            "\n" +
            "15) Limited License\n" +
            "The Developer grants you a limited, non-exclusive, non-transferable, revocable license to use the app for lawful personal, educational, or daily-assistance purposes in accordance with these Terms.\n" +
            "\n" +
            "16) Privacy\n" +
            "Data processing is governed by Basir's Privacy Policy. The Privacy Policy is incorporated into and forms part of these Terms, and should be read together with this document.\n" +
            "\n" +
            "17) Updates and Feature Changes\n" +
            "The Developer may update the app or modify, add, or remove any feature, including changes to external-service connections, security improvements, interface changes, or removal of features that are no longer stable or appropriate. Updates may affect how certain functions behave.\n" +
            "\n" +
            "18) Suspension or Termination\n" +
            "The Developer may suspend or discontinue the app or any feature temporarily or permanently for technical, legal, security, business, or third-party-service reasons. Use may also be restricted if there is misuse or a material breach of these Terms.\n" +
            "\n" +
            "19) No Warranties\n" +
            "The app is provided as is and as available. The Developer does not guarantee that the app will be error-free, always available, suitable for every use case, or that its outputs will be accurate, complete, or safe to rely on alone.\n" +
            "\n" +
            "20) Limitation of Liability\n" +
            "To the maximum extent permitted by law, the Developer is not liable for any direct, indirect, incidental, consequential, or special loss or damage arising from use of the app, inability to use it, sole reliance on its outputs, outages of external services, or content submitted by users.\n" +
            "\n" +
            "21) Indemnity\n" +
            "To the extent permitted by law, you agree to indemnify and protect the Developer from claims, damages, costs, or liabilities arising from your breach of these Terms, violation of others' rights, or submission of content that you did not have the right to process or share.\n" +
            "\n" +
            "22) App Stores and Third Parties\n" +
            "Downloading or updating the app may also be subject to the terms of the app store used, such as Google Play or another store. App stores are not responsible for the app's content or outputs unless their own terms or applicable laws provide otherwise.\n" +
            "\n" +
            "23) Governing Law and Jurisdiction\n" +
            "These Terms are governed by and interpreted in accordance with the laws and regulations of the Kingdom of Saudi Arabia. The competent courts or authorities in the Kingdom shall have jurisdiction over disputes arising from these Terms, unless mandatory law provides otherwise.\n" +
            "\n" +
            "24) Language Conflict\n" +
            "The Arabic version is intended to be the reference version within the Kingdom of Saudi Arabia. The English version is provided as an assisting translation unless the Developer expressly states otherwise.\n" +
            "\n" +
            "25) Updates to These Terms\n" +
            "These Terms may be updated from time to time. Continued use of the app after an update is posted constitutes acceptance of the updated Terms. If a change is material, the Developer should, where practical, notify users inside the app or through the app-store page.\n" +
            "\n" +
            "26) Contact\n" +
            "For questions about these Terms, you may contact the Developer through the contact method published inside the app or on the app's store page."));

        host.addBackButton();
    }

    public void showPrivacy() {
        host.resetScreen(host.t("سياسة الخصوصية", "Privacy Policy"),
                host.t("الإصدار 2 — " + host.appVersion(),
                  "Version 2 — " + host.appVersion()));

        host.addPlainText(host.t(
            "نأخذ خصوصيتك بجدية. توضح هذه السياسة، بلغة مباشرة، ما البيانات التي يتعامل معها تطبيق بصير، وما الذي يبقى على جهازك، وما الذي قد يُرسل إلى خدمات خارجية مثل Google Gemini عند استخدام بعض الميزات.\n" +
            "\n" +
            "1) نطاق هذه السياسة\n" +
            "تنطبق هذه السياسة على استخدامك لتطبيق بصير والميزات المرتبطة به. لا تنطبق هذه السياسة على مواقع أو خدمات أو سياسات أطراف ثالثة، بما في ذلك خدمات Google، إلا بالقدر الذي يوضح طريقة ارتباط التطبيق بها.\n" +
            "\n" +
            "2) ملخص سريع\n" +
            "• لا يتطلب التطبيق إنشاء حساب داخل بصير.\n" +
            "• لا يستخدم التطبيق إعلانات أو معرفات إعلانية.\n" +
            "• لا يستخدم التطبيق تتبعاً تحليلياً لأغراض التسويق.\n" +
            "• أغلب البيانات تحفظ محلياً على جهازك.\n" +
            "• بعض الطلبات، مثل تحليل الصور أو الملفات أو النصوص، تُرسل إلى Google Gemini API لمعالجتها.\n" +
            "• ملفات PDF المرفوعة عبر ميزة \"اسأل عن المستند\" قد تُخزن لدى Google لمدة تصل إلى 48 ساعة وفق آلية Files API.\n" +
            "• يمكنك حذف البيانات المحلية من الإعدادات أو بحذف التطبيق.\n" +
            "\n" +
            "3) البيانات التي تُحفظ محلياً على جهازك\n" +
            "قد يحفظ التطبيق البيانات التالية محلياً فقط، بحسب استخدامك:\n" +
            "• مفتاح Gemini API إذا أدخلته يدوياً.\n" +
            "• تفضيلات الاستخدام، مثل اللغة، حجم الخط، سرعة الصوت، ونمط الاتصال.\n" +
            "• العناصر التي تحفظها يدوياً، مثل الأشخاص، المنتجات، الأدوية، الأماكن، أو الملاحظات ذات الصلة.\n" +
            "• محفوظات المستندات المحوّلة داخل مجلد التنزيلات أو المجلد الذي يحدده النظام.\n" +
            "• سجل آخر العمليات داخل التطبيق، إذا كانت الميزة مفعلة، ويكون نصياً وقابلاً للحذف من الإعدادات.\n" +
            "• جهة الطوارئ التي تضيفها يدوياً، إذا استخدمت ميزة الطوارئ.\n" +
            "\n" +
            "لا تُرسل هذه البيانات إلى خوادم المطوّر ما دام التطبيق يعمل بالنمط المحلي المباشر الموضح في هذه السياسة.\n" +
            "\n" +
            "4) البيانات التي قد تُرسل إلى Google Gemini\n" +
            "عند طلب وصف صورة، قراءة ملف، تحليل نص، ترجمة محتوى، أو استخدام ميزة تعتمد على الذكاء الاصطناعي، يرسل التطبيق المحتوى الذي اخترته فقط إلى Google Gemini API لمعالجته وإعادة النتيجة. قد يشمل ذلك:\n" +
            "• النص الذي تكتبه أو تمليه صوتياً.\n" +
            "• الصورة التي تختار التقاطها أو رفعها.\n" +
            "• الملف أو المستند الذي تختار تحليله.\n" +
            "• السؤال أو التعليمات المرتبطة بالمحتوى.\n" +
            "\n" +
            "لا يحتفظ التطبيق بنسخة لدى المطوّر من المحتوى المرسل إذا لم تكن هناك خوادم وسيطة تابعة للمطوّر. تخضع معالجة Google للبيانات لشروط وسياسات Google ذات الصلة.\n" +
            "\n" +
            "5) ميزة \"اسأل عن المستند\"\n" +
            "عند استخدام ميزة \"اسأل عن المستند\" أو أي ميزة تعتمد على رفع ملف عبر Files API، قد يتم رفع ملف PDF إلى خوادم Google لمدة تصل إلى 48 ساعة، ثم يُحذف تلقائياً وفق سياسة Google لهذه الواجهة. لا تستخدم هذه الميزة مع مستندات شديدة الحساسية إلا إذا كنت تقبل هذا النوع من المعالجة.\n" +
            "\n" +
            "6) مراقبة إساءة الاستخدام لدى الخدمات الخارجية\n" +
            "قد تحتفظ Google، وفق سياساتها الخاصة، ببعض بيانات الطلبات والمخرجات لمدة محددة لأغراض السلامة، منع إساءة الاستخدام، إنفاذ السياسات، أو الامتثال القانوني. هذه المعالجة تتم خارج سيطرة المطوّر المباشرة، ويجب مراجعة شروط وسياسات Google لفهمها بدقة.\n" +
            "\n" +
            "7) البيانات التي لا يجمعها التطبيق\n" +
            "لا يجمع التطبيق، بحسب التصميم الحالي:\n" +
            "• حسابات مستخدمين داخل بصير.\n" +
            "• كلمات مرور خاصة ببصير.\n" +
            "• بيانات إعلانية أو معرفات إعلانية.\n" +
            "• تتبعاً تحليلياً تسويقياً.\n" +
            "• قائمة جهات الاتصال كاملة.\n" +
            "• الموقع الجغرافي في الخلفية.\n" +
            "• تسجيلات صوتية دائمة لدى المطوّر.\n" +
            "• نسخاً من ملفاتك على خوادم المطوّر.\n" +
            "\n" +
            "8) الموقع الجغرافي والطوارئ\n" +
            "لا يستخدم التطبيق موقع GPS إلا في وضع الطوارئ، وعند اختيارك الصريح إرسال موقعك. في هذه الحالة، قد تُرفق إحداثيات تقريبية أو رابط موقع برسالة الطوارئ إلى الجهة التي اخترتها. لا يحصل التطبيق على قائمة جهات اتصالك كاملة، بل يتعامل فقط مع جهة الطوارئ التي تضيفها أو تختارها يدوياً.\n" +
            "\n" +
            "9) الأذونات وسبب طلبها\n" +
            "• الكاميرا: لالتقاط الصور التي تختار وصفها أو تحليلها.\n" +
            "• الميكروفون: لإدخال الأوامر أو الأسئلة صوتياً، وقد يستخدم التعرف الصوتي خدمة مدمجة في نظام Android أو خدمة خارجية بحسب الجهاز.\n" +
            "• الموقع: لاستخدامه في الطوارئ عند اختيارك الصريح.\n" +
            "• التخزين أو الملفات: لحفظ الملفات المحولة أو قراءة ملف تختاره أنت.\n" +
            "• الإنترنت: للاتصال بخدمات Gemini أو الخدمات الخارجية اللازمة لتشغيل بعض الميزات.\n" +
            "• الإشعارات، إن وُجدت: لتنبيهك بنتائج أو حالات تشغيل مهمة داخل التطبيق.\n" +
            "\n" +
            "يمكنك التحكم في كثير من هذه الأذونات من إعدادات جهازك، وقد يؤدي تعطيل بعضها إلى توقف ميزات معينة.\n" +
            "\n" +
            "10) الغرض من معالجة البيانات\n" +
            "تُستخدم البيانات فقط لتشغيل الميزات التي تطلبها، مثل وصف صورة، قراءة مستند، ترجمة نص، حفظ تفضيلاتك، إرسال رسالة طوارئ، أو تحسين قابلية الوصول داخل التطبيق. لا تُستخدم بياناتك داخل بصير لبناء ملفات إعلانية أو بيعها لأطراف ثالثة.\n" +
            "\n" +
            "11) الأساس النظامي أو سبب المعالجة\n" +
            "يعتمد التطبيق غالباً على اختيارك وطلبك الصريح للميزة، مثل اختيار صورة أو ملف أو الضغط على زر الطوارئ. وبقدر انطباق أنظمة حماية البيانات، قد يكون أساس المعالجة هو موافقتك، تنفيذ طلبك، المصلحة المشروعة في تشغيل التطبيق وأمانه، أو الالتزام النظامي عند الاقتضاء.\n" +
            "\n" +
            "12) الاحتفاظ بالبيانات\n" +
            "• البيانات المحلية تبقى على جهازك إلى أن تحذفها من الإعدادات، أو تحذف ملفات التنزيلات، أو تلغي تثبيت التطبيق.\n" +
            "• سجل آخر العمليات، إن وُجد، يكون قابلاً للحذف من الإعدادات.\n" +
            "• الملفات المحولة في مجلد التنزيلات تبقى حتى تحذفها أنت من جهازك.\n" +
            "• المحتوى المرسل إلى Google يخضع لمدد الاحتفاظ وسياسات Google، ومنها التخزين المؤقت لبعض الملفات وفق طريقة الرفع المستخدمة.\n" +
            "\n" +
            "13) حذف البيانات والتحكم بها\n" +
            "يمكنك التحكم في بياناتك عبر:\n" +
            "• الإعدادات داخل التطبيق: استخدام خيار مسح المحفوظات أو العناصر المحفوظة، إن توفر.\n" +
            "• مدير الملفات: حذف الملفات المحولة من مجلد التنزيلات.\n" +
            "• إعدادات الجهاز: إلغاء الأذونات مثل الكاميرا، الميكروفون، الموقع، أو التخزين.\n" +
            "• إلغاء تثبيت التطبيق: يحذف غالبية بيانات التطبيق المحلية، مع بقاء الملفات التي حفظتها خارج مساحة التطبيق مثل مجلد التنزيلات إلى أن تحذفها يدوياً.\n" +
            "\n" +
            "14) حقوقك\n" +
            "بقدر ما تنطبق أنظمة حماية البيانات، قد يكون لك الحق في طلب الوصول إلى بياناتك، تصحيحها، حذفها، تقييد معالجتها، سحب موافقتك، الاعتراض على بعض صور المعالجة، أو تقديم شكوى إلى الجهة المختصة. نظراً لأن معظم البيانات محفوظة محلياً على جهازك، فإن أسرع طريقة لممارسة كثير من هذه الحقوق هي من خلال إعدادات التطبيق والجهاز.\n" +
            "\n" +
            "15) مشاركة البيانات مع أطراف ثالثة\n" +
            "لا يبيع المطوّر بياناتك. قد تتم مشاركة أو إرسال البيانات فقط بالقدر اللازم لتشغيل الميزات التي تطلبها، مثل إرسال المحتوى إلى Google Gemini API، استخدام خدمات نظام Android، مشاركة رسالة طوارئ عبر تطبيق مراسلة تختاره، أو الامتثال لطلب نظامي صحيح عند الاقتضاء.\n" +
            "\n" +
            "16) النقل خارج المملكة\n" +
            "عند استخدام Google Gemini أو أي خدمة خارجية عالمية، قد تُعالج البيانات أو تُنقل خارج المملكة العربية السعودية بحسب بنية الخدمة وسياساتها. باستخدامك الميزات التي تتطلب تلك الخدمات، فإنك تقر بإمكان حدوث هذا النقل بالقدر اللازم لتشغيل الميزة.\n" +
            "\n" +
            "17) أمن البيانات\n" +
            "يعتمد التطبيق على تخزين محلي داخل جهازك وعلى آليات الأمان التي يوفرها نظام التشغيل. ومع ذلك، لا توجد وسيلة تخزين أو نقل إلكتروني آمنة بنسبة 100%. عليك حماية جهازك بكلمة مرور أو بصمة، وعدم مشاركة مفتاح API، وتجنب رفع بيانات شديدة الحساسية إلا عند الحاجة.\n" +
            "\n" +
            "18) مفتاح API\n" +
            "إذا أدخلت مفتاح Gemini API يدوياً، فيُخزن محلياً على جهازك لاستخدامه في الاتصال بالخدمة. أنت مسؤول عن المحافظة عليه، وعن أي رسوم أو حدود استخدام مرتبطة به لدى Google. إذا فقدت السيطرة عليه، قم بإلغائه أو تدويره من حسابك لدى Google.\n" +
            "\n" +
            "19) بيانات الأطفال\n" +
            "لا يستهدف التطبيق جمع بيانات الأطفال عمداً. إذا استخدم طفل التطبيق، فيجب أن يكون ذلك بإشراف ولي الأمر أو بموافقته، خصوصاً عند رفع صور أو مستندات أو بيانات تعليمية أو صحية.\n" +
            "\n" +
            "20) القرارات الآلية\n" +
            "قد يقدم التطبيق مخرجات مولدة آلياً، لكنه لا ينبغي أن يكون المصدر الوحيد لاتخاذ قرارات مؤثرة قانونياً أو طبياً أو مالياً أو تعليمياً أو متعلقة بالسلامة. يجب وجود مراجعة بشرية مؤهلة عند الحاجة.\n" +
            "\n" +
            "21) روابط وخدمات الأطراف الثالثة\n" +
            "قد يحتوي التطبيق أو صفحته على روابط أو يعتمد على خدمات خارجية. لسنا مسؤولين عن ممارسات الخصوصية لدى تلك الجهات. ننصح بمراجعة سياسات Google وأي خدمة أخرى تستخدمها من خلال التطبيق.\n" +
            "\n" +
            "22) تحديثات هذه السياسة\n" +
            "إذا تغيّرت ممارساتنا بشأن البيانات أو أضيفت ميزات جديدة تؤثر على الخصوصية، فسيتم تحديث هذه السياسة. استمرارك في استخدام التطبيق بعد تحديث السياسة يعني قبولك للنسخة المعدلة بالقدر الذي يسمح به النظام.\n" +
            "\n" +
            "23) التواصل\n" +
            "لأي سؤال أو طلب متعلق بالخصوصية، يمكنك التواصل مع المطوّر عبر وسيلة التواصل المنشورة داخل التطبيق أو صفحة التطبيق في المتجر.\n" +
            "\n" +
            "24) تعارض النسخ اللغوية\n" +
            "أُعدت النسخة العربية لتكون النسخة المرجعية داخل المملكة العربية السعودية. وتعد النسخة الإنجليزية ترجمة مساعدة، ما لم يقرر المطوّر خلاف ذلك صراحةً.",

            "We take your privacy seriously. This Policy explains, in direct language, what data Basir handles, what remains on your device, and what may be sent to external services such as Google Gemini when you use certain features.\n" +
            "\n" +
            "1) Scope of This Policy\n" +
            "This Policy applies to your use of Basir and its related features. It does not apply to third-party websites, services, or policies, including Google services, except to the extent this Policy explains how the app connects with them.\n" +
            "\n" +
            "2) Quick Summary\n" +
            "• The app does not require you to create a Basir account.\n" +
            "• The app does not use ads or advertising identifiers.\n" +
            "• The app does not use marketing analytics tracking.\n" +
            "• Most data is stored locally on your device.\n" +
            "• Some requests, such as image, file, or text analysis, are sent to Google Gemini API for processing.\n" +
            "• PDFs uploaded through the \"Ask about document\" feature may be stored by Google for up to 48 hours under the Files API mechanism.\n" +
            "• You can delete local data through settings or by uninstalling the app.\n" +
            "\n" +
            "3) Data Stored Locally on Your Device\n" +
            "Depending on how you use the app, the following data may be stored locally only:\n" +
            "• Your Gemini API key, if you enter it manually.\n" +
            "• Preferences such as language, font size, speech rate, and connection mode.\n" +
            "• Items you manually save, such as people, products, medications, places, or related notes.\n" +
            "• Converted-document history in your Downloads folder or the folder selected by the operating system.\n" +
            "• Recent activity log inside the app, if enabled, which is text-only and can be deleted from settings.\n" +
            "• The emergency contact you manually add, if you use the emergency feature.\n" +
            "\n" +
            "This data is not sent to the Developer's servers as long as the app operates in the direct local mode described in this Policy.\n" +
            "\n" +
            "4) Data That May Be Sent to Google Gemini\n" +
            "When you request image description, file reading, text analysis, translation, or another AI-powered feature, the app sends only the content you chose to Google Gemini API for processing and returns the result. This may include:\n" +
            "• Text you type or dictate.\n" +
            "• An image you choose to capture or upload.\n" +
            "• A file or document you choose to analyze.\n" +
            "• The question or instruction associated with the content.\n" +
            "\n" +
            "The app does not keep a Developer-side copy of submitted content if no Developer-operated intermediary servers are used. Google's processing is governed by Google's applicable terms and policies.\n" +
            "\n" +
            "5) \"Ask about Document\" Feature\n" +
            "When you use the \"Ask about document\" feature or any feature that uploads a file through the Files API, your PDF may be uploaded to Google's servers for up to 48 hours and then automatically deleted according to Google's policy for that interface. Do not use this feature with highly sensitive documents unless you accept this form of processing.\n" +
            "\n" +
            "6) Abuse Monitoring by External Services\n" +
            "Google may, under its own policies, retain certain request and output data for a limited period for safety, abuse prevention, policy enforcement, or legal compliance. This processing is outside the Developer's direct control, and you should review Google's terms and policies to understand it accurately.\n" +
            "\n" +
            "7) Data the App Does Not Collect\n" +
            "Under the current design, the app does not collect:\n" +
            "• Basir user accounts.\n" +
            "• Basir-specific passwords.\n" +
            "• Advertising data or advertising identifiers.\n" +
            "• Marketing analytics tracking.\n" +
            "• Your full contact list.\n" +
            "• Background GPS location.\n" +
            "• Permanent voice recordings held by the Developer.\n" +
            "• Copies of your files on the Developer's servers.\n" +
            "\n" +
            "8) Location and Emergency Mode\n" +
            "The app does not use GPS location except in Emergency mode and only when you expressly choose to send your location. In that case, an approximate coordinate or location link may be attached to an emergency message sent to the contact you selected. The app does not access your full contact list and only uses the emergency contact you manually add or choose.\n" +
            "\n" +
            "9) Permissions and Why We Request Them\n" +
            "• Camera: to capture photos you choose to describe or analyze.\n" +
            "• Microphone: to enter commands or questions by voice; speech recognition may use an Android built-in service or an external service depending on your device.\n" +
            "• Location: for Emergency mode only when you expressly choose to use it.\n" +
            "• Storage or files: to save converted files or read a file you choose.\n" +
            "• Internet: to connect to Gemini or other external services required for certain features.\n" +
            "• Notifications, if present: to alert you about important results or app status.\n" +
            "\n" +
            "You can control many of these permissions through your device settings. Disabling some permissions may stop certain features from working.\n" +
            "\n" +
            "10) Purpose of Processing\n" +
            "Data is used only to operate the features you request, such as describing an image, reading a document, translating text, saving preferences, sending an emergency message, or improving accessibility inside the app. Basir does not use your data to build advertising profiles or sell it to third parties.\n" +
            "\n" +
            "11) Legal Basis or Reason for Processing\n" +
            "The app generally relies on your explicit choice and request for a feature, such as selecting an image or file or pressing the emergency button. To the extent data-protection laws apply, processing may be based on your consent, performance of your request, legitimate interest in operating and securing the app, or legal obligation where applicable.\n" +
            "\n" +
            "12) Data Retention\n" +
            "• Local data remains on your device until you delete it from settings, delete files from Downloads, or uninstall the app.\n" +
            "• Recent activity logs, if present, can be deleted from settings.\n" +
            "• Converted files in your Downloads folder remain until you delete them manually.\n" +
            "• Content sent to Google is governed by Google's retention periods and policies, including temporary storage for certain files depending on the upload method used.\n" +
            "\n" +
            "13) Deleting and Controlling Your Data\n" +
            "You can control your data through:\n" +
            "• App settings: use the clear history or saved items option, if available.\n" +
            "• File manager: delete converted files from Downloads.\n" +
            "• Device settings: revoke permissions such as camera, microphone, location, or storage.\n" +
            "• Uninstalling the app: this deletes most local app data, while files saved outside the app's private space, such as Downloads, may remain until you delete them manually.\n" +
            "\n" +
            "14) Your Rights\n" +
            "To the extent data-protection laws apply, you may have the right to request access, correction, deletion, restriction, withdrawal of consent, objection to certain processing, or to file a complaint with the competent authority. Because most data is stored locally on your device, the fastest way to exercise many of these rights is through the app and device settings.\n" +
            "\n" +
            "15) Sharing Data With Third Parties\n" +
            "The Developer does not sell your data. Data may be shared or transmitted only as necessary to operate the features you request, such as sending content to Google Gemini API, using Android system services, sharing an emergency message through a messaging app you choose, or complying with a valid legal request where applicable.\n" +
            "\n" +
            "16) International Transfers\n" +
            "When you use Google Gemini or another global external service, data may be processed in or transferred outside the Kingdom of Saudi Arabia according to that service's infrastructure and policies. By using features that require those services, you acknowledge that such transfer may occur to the extent necessary to operate the feature.\n" +
            "\n" +
            "17) Data Security\n" +
            "The app relies on local storage on your device and the security mechanisms provided by your operating system. However, no electronic storage or transmission method is 100% secure. You should protect your device with a passcode or biometric lock, avoid sharing your API key, and avoid uploading highly sensitive data unless necessary.\n" +
            "\n" +
            "18) API Key\n" +
            "If you manually enter a Gemini API key, it is stored locally on your device for use in connecting to the service. You are responsible for protecting it and for any fees or usage limits associated with it under your Google account. If you lose control of it, revoke or rotate it from your Google account.\n" +
            "\n" +
            "19) Children's Data\n" +
            "The app is not designed to intentionally collect children's data. If a child uses the app, it must be under parental or guardian supervision or consent, especially when uploading images, documents, educational data, or health data.\n" +
            "\n" +
            "20) Automated Outputs\n" +
            "The app may provide automatically generated outputs, but it should not be the sole source for legally, medically, financially, educationally, or safety-significant decisions. Qualified human review should be used when needed.\n" +
            "\n" +
            "21) Third-Party Links and Services\n" +
            "The app or its store page may contain links to, or rely on, external services. We are not responsible for the privacy practices of those third parties. You should review the policies of Google and any other service you use through the app.\n" +
            "\n" +
            "22) Updates to This Policy\n" +
            "If our data practices change or new features affecting privacy are added, this Policy will be updated. Continued use of the app after an update means you accept the revised Policy to the extent permitted by law.\n" +
            "\n" +
            "23) Contact\n" +
            "For privacy questions or requests, you may contact the Developer through the contact method published inside the app or on the app's store page.\n" +
            "\n" +
            "24) Language Conflict\n" +
            "The Arabic version is intended to be the reference version within the Kingdom of Saudi Arabia. The English version is provided as an assisting translation unless the Developer expressly states otherwise."));

        host.addBackButton();
    }

    public void showAbout() {
        host.resetScreen(host.t("حول التطبيق", "About"),
                host.t("بصير — مساعد ذكي للمكفوفين وضعاف البصر.", "Basir — a smart assistant for blind and low-vision users."));

        host.addPlainText(host.t(
                "بصير يساعدك في قراءة المستندات، وصف الصور، ترجمة النصوص، تنظيم محفوظاتك، والاستفادة من أدوات الذكاء الاصطناعي بطريقة آمنة وسهلة.\n\n" +
                "مهم: التطبيق أداة مساعدة فقط، ولا يغني عن العصا البيضاء، الطبيب، المحامي، أو خدمات الطوارئ الرسمية في المواقف الخطرة.\n\n" +
                "الخصوصية: لا يتم حفظ الصور أو الملفات تلقائيًا. تتم المعالجة بعد موافقة المستخدم، ويمكن حذف البيانات المحلية من الإعدادات.\n\n" +
                "الإصدار: " + host.appVersion() + "\n" +
                "المطور: عبدالله الراشدي\n" +
                "البريد: " + host.contactEmail(),

                "Basir helps you read documents, describe images, translate texts, organize your saved items, and use AI tools in a safe and simple way.\n\n" +
                "Important: The app is assistive only and does not replace a white cane, a doctor, a lawyer, or official emergency services in dangerous situations.\n\n" +
                "Privacy: Images and files are never saved automatically. Processing happens only after you confirm, and local data can be deleted from settings.\n\n" +
                "Version: " + host.appVersion() + "\n" +
                "Developer: Abdullah Al-Rashidi\n" +
                "Email: " + host.contactEmail()));

        host.addPrimaryButton(host.t("مراسلة المطور", "Email the developer"), v -> {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("mailto:" + host.contactEmail()));
            i.putExtra(Intent.EXTRA_SUBJECT, "Basir feedback");
            try { host.launchIntent(i); } catch (Exception e) {
                host.speak(host.t("تعذر فتح تطبيق البريد.", "Could not open the email app."));
            }
        });
        host.addOutlineButton(host.t("مشاركة التطبيق", "Share the app"), v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, host.t(
                    "تطبيق بصير — مساعد ذكي للمكفوفين. تواصل: " + host.contactEmail(),
                    "Basir — smart assistant for blind users. Contact: " + host.contactEmail()));
            host.launchIntent(Intent.createChooser(i, host.t("مشاركة", "Share")));
        });
        host.addBackButton();
    }
}
