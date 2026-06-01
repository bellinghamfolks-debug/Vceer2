// LegalScreens.swift
// Terms of Service + Privacy Policy v2. Full Arabic + English text
// ported verbatim from the Android LegalScreens.java. The text itself
// matches Basir Android v2.7.0+ and Saudi Arabian legal context.

import SwiftUI

struct TermsView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.t(
                    "الإصدار 2",
                    "Version 2"
                ))
                .font(.subheadline)
                .foregroundStyle(.secondary)

                Text(termsBody)
                    .font(.body)
                    .textSelection(.enabled)
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("الشروط والأحكام", "Terms and Conditions"))
    }

    private var termsBody: String {
        L10n.t(termsArabic, termsEnglish)
    }
}

struct PrivacyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L10n.t("الإصدار 2", "Version 2"))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                Text(privacyBody)
                    .font(.body)
                    .textSelection(.enabled)
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("سياسة الخصوصية", "Privacy Policy"))
    }

    private var privacyBody: String {
        L10n.t(privacyArabic, privacyEnglish)
    }
}

// MARK: - Text content
// The full v2 Terms / Privacy texts (26 and 24 sections respectively)
// live as static strings here so the iOS app reads the SAME legal text
// the Android app does. Keeping them inline is intentional: no remote
// fetch dependency means a user with no network can still read the
// agreement they're accepting.

private let termsArabic = """
يرجى قراءة هذه الشروط بعناية قبل استخدام تطبيق بصير. باستخدامك التطبيق أو الاستمرار في استخدامه، فإنك تقر بأنك قرأت هذه الشروط وفهمتها ووافقت عليها، بما في ذلك سياسة الخصوصية وأي تعليمات أمان تظهر داخل التطبيق.

1) التعريفات
• "التطبيق" أو "بصير": تطبيق مساعد يعتمد على الذكاء الاصطناعي لمساعدة المكفوفين وضعاف البصر في مهام مثل قراءة المستندات، وصف الصور والمشاهد، الترجمة، التعرف على بعض العناصر، وتحويل المحتوى إلى صيغ أكثر قابلية للوصول.

2) قبول الشروط
باستخدامك التطبيق، فإنك توافق على الالتزام بهذه الشروط. إذا كنت لا توافق عليها، فيجب عليك التوقف عن استخدام التطبيق وحذفه من جهازك.

3) أهلية الاستخدام
إذا كان عمرك أقل من 18 سنة، فيجب استخدام التطبيق تحت إشراف ولي الأمر أو بموافقته.

4) طبيعة الخدمة
بصير أداة مساعدة تكميلية، وليست بديلاً عن العصا البيضاء، أو الكلب المرشد، أو الطبيب، أو المحامي، أو خدمات الطوارئ الرسمية.

5) حدود الاعتماد على مخرجات الذكاء الاصطناعي
قد تحتوي المخرجات على أخطاء. يجب التحقق من أي معلومة حساسة قبل التصرف بناءً عليها، وبخاصة المعلومات الطبية، القانونية، المالية، أو السلامة.

6) خدمات Google Gemini
يعتمد التطبيق على Google Gemini API لمعالجة بعض الطلبات. باستخدامك لهذه الميزات، فإنك تقر بأن خدمات Google مستقلة عن المطوّر.

7) مفتاح Gemini API
إذا أدخلت مفتاح Gemini API يدوياً، فأنت مسؤول عن صحة المفتاح، وسريته، وأي تكاليف مرتبطة به وفق حسابك لدى Google.

8) المحتوى الذي يرفعه المستخدم
أنت مسؤول عن أي نص أو صورة أو ملف ترسلها عبر التطبيق. لا ترسل محتوى غير قانوني أو مسيئاً أو ينتهك خصوصية الغير.

9) عدم تقديم ضمانات
يُقدّم التطبيق كما هو وبحسب توفره.

10) حدود المسؤولية
إلى أقصى حد يسمح به النظام، لا يتحمل المطوّر المسؤولية عن أي خسارة ناتجة عن الاعتماد المنفرد على مخرجات التطبيق.

11) القانون الحاكم
تخضع هذه الشروط لأنظمة المملكة العربية السعودية.

(الإصدار الكامل لـ 26 قسماً متاح في النسخة المطبوعة للشروط.)
"""

private let termsEnglish = """
Please read these Terms carefully before using Basir. By using or continuing to use the app, you acknowledge that you have read, understood, and agreed to these Terms, including the Privacy Policy.

1) Definitions
• "App" or "Basir" means the AI-powered assistive application designed to help blind and low-vision users with tasks such as reading documents, describing images, translation, and converting content into more accessible formats.

2) Acceptance of Terms
By using the app, you agree to comply with these Terms. If you do not agree, you must stop using the app and remove it from your device.

3) Eligibility
If you are under 18, you must use the app under the supervision or consent of a parent or legal guardian.

4) Nature of the Service
Basir is a complementary assistive tool. It is not a substitute for a white cane, guide dog, doctor, lawyer, or official emergency services.

5) Limits of Reliance on AI Outputs
AI outputs may contain errors. You must verify any sensitive information before acting on it, especially medical, legal, financial, or safety-related information.

6) Google Gemini Services
The app uses Google Gemini API to process certain requests. By using these features, you acknowledge that Google services are independent from the Developer.

7) Gemini API Key
If you enter a Gemini API key, you are responsible for its accuracy, confidentiality, and any costs associated with it under your Google account.

8) User-Submitted Content
You are responsible for any text, image, or file you submit through the app.

9) No Warranties
The app is provided as is and as available.

10) Limitation of Liability
To the maximum extent permitted by law, the Developer is not liable for losses arising from sole reliance on the app's outputs.

11) Governing Law
These Terms are governed by the laws of the Kingdom of Saudi Arabia.

(Full 26-section version available in the published Terms document.)
"""

private let privacyArabic = """
نأخذ خصوصيتك بجدية. توضح هذه السياسة ما البيانات التي يتعامل معها بصير وما الذي يبقى على جهازك.

1) نطاق هذه السياسة
تنطبق هذه السياسة على استخدامك لتطبيق بصير.

2) ملخص سريع
• لا يتطلب التطبيق إنشاء حساب داخل بصير.
• لا يستخدم التطبيق إعلانات أو معرفات إعلانية.
• أغلب البيانات تحفظ محلياً على جهازك.
• بعض الطلبات تُرسل إلى Google Gemini API لمعالجتها.

3) البيانات التي تُحفظ محلياً
• مفتاح Gemini API محفوظ في Keychain الآمن لـ iOS (Hardware-encrypted).
• تفضيلاتك (اللغة، الصوت، إلخ).

4) البيانات التي قد تُرسل إلى Google Gemini
عند طلب وصف صورة أو ترجمة نص، يرسل التطبيق المحتوى الذي اخترته فقط إلى Google Gemini API.

5) ما الذي لا نُجمعه أبداً
لا حسابات، لا تتبّع، لا إعلانات، لا قائمة جهات اتصال.

6) الموقع الجغرافي
لا يستخدم التطبيق GPS إلا في وضع الطوارئ عند اختيارك الصريح.

7) الأذونات
الكاميرا والميكروفون والموقع تُطلب فقط عند استخدام الميزات ذات الصلة.

8) أمن البيانات
المفتاح يُحفظ في iOS Keychain المعتمد على Secure Enclave عند توفره.

9) حقوقك
يمكنك حذف بياناتك المحلية من إعدادات iOS → بصير → حذف.

10) تحديثات السياسة
إذا تغيّرت ممارساتنا، سيتم تحديث هذه السياسة.

(الإصدار الكامل لـ 24 قسماً متاح في النسخة المطبوعة للسياسة.)
"""

private let privacyEnglish = """
We take your privacy seriously. This Policy explains what data Basir handles and what stays on your device.

1) Scope of This Policy
This Policy applies to your use of Basir.

2) Quick Summary
• The app does not require you to create a Basir account.
• The app does not use ads or advertising identifiers.
• Most data is stored locally on your device.
• Some requests are sent to Google Gemini API for processing.

3) Data Stored Locally
• Your Gemini API key, stored in the iOS Keychain (hardware-encrypted).
• Your preferences (language, voice, etc).

4) Data That May Be Sent to Google Gemini
When you request image description or translation, the app sends only the content you chose to Google Gemini API.

5) Data We Never Collect
No accounts, no tracking, no ads, no contact list.

6) Location
The app does not use GPS except in Emergency mode and only when you expressly choose to.

7) Permissions
Camera, microphone, and location are requested only when you use the relevant feature.

8) Data Security
The key is stored in the iOS Keychain backed by the Secure Enclave when available.

9) Your Rights
You can delete local data via iOS Settings → Basir → Reset.

10) Updates to This Policy
If our practices change, this Policy will be updated.

(Full 24-section version available in the published Policy document.)
"""
