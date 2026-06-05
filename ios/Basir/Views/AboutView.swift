// AboutView.swift
import SwiftUI

struct AboutView: View {
    private let contactEmail = "ubdallahalrashdee@gmail.com"

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "—"
    }

    private var buildNumber: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "—"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.t(
                    "بصير مساعد وصول ذكي للمكفوفين وضعاف البصر. يساعد في وصف الصور، وقراءة النصوص والمستندات، والترجمة، والمحادثة، وتنظيم النتائج المحفوظة.",
                    "Basir is an AI accessibility assistant for blind and low-vision users. It helps describe images, read text and documents, translate content, support conversation, and organize saved results."
                ))

                Text(L10n.t(
                    "تنبيه سلامة: بصير أداة مساعدة، ولا يحل محل العصا البيضاء أو الكلب المرشد أو المرافق البشري أو المختص أو خدمات الطوارئ. راجع المعلومات المهمة قبل الاعتماد عليها.",
                    "Safety notice: Basir is assistive only. It does not replace a white cane, guide dog, human guide, qualified professional, or official emergency services. Verify important information before relying on it."
                ))
                .foregroundStyle(.secondary)

                Text(L10n.t(
                    "الخصوصية: لا يلزم إنشاء حساب لدى المطوّر ولا توجد إعلانات. عند استخدام الذكاء الاصطناعي، يُرسل المحتوى الذي تختاره مباشرة إلى Google Gemini. راجع سياسة الخصوصية لمعرفة التخزين المحلي ومعالجة Google للخدمات المجانية والمدفوعة.",
                    "Privacy: No Developer account is required and there are no ads. When AI is used, content you choose is sent directly to Google Gemini. Review the Privacy Policy for local storage and Google's handling of unpaid and paid services."
                ))
                .foregroundStyle(.secondary)

                Group {
                    Text(L10n.t("إصدار التطبيق: ", "App version: ") + appVersion)
                    Text(L10n.t("رقم البناء: ", "Build: ") + buildNumber)
                    Text(L10n.t("المطوّر: عبدالله الراشدي",
                                 "Developer: Abdullah Al-Rashidi"))
                    Text(L10n.t("البريد: ", "Email: ") + contactEmail)
                }
                .font(.callout)
                .foregroundStyle(.secondary)

                Button {
                    if let url = URL(string: "mailto:\(contactEmail)?subject=Basir%20feedback") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    HStack {
                        Image(systemName: "envelope.fill")
                        Text(L10n.t("مراسلة المطوّر", "Email the developer"))
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .accessibilityHint(L10n.t("يفتح تطبيق البريد برسالة جديدة.",
                                          "Opens the email app with a new message."))
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("حول بصير", "About Basir"))
    }
}
