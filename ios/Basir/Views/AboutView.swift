// AboutView.swift
import SwiftUI

struct AboutView: View {
    private let contactEmail = "ubdallahalrashdee@gmail.com"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.t(
                    "بصير يساعدك في قراءة المستندات، وصف الصور، ترجمة النصوص، تنظيم محفوظاتك، والاستفادة من أدوات الذكاء الاصطناعي بطريقة آمنة وسهلة.",
                    "Basir helps you read documents, describe images, translate text, organise saved items, and use AI tools in a safe and simple way."
                ))

                Text(L10n.t(
                    "مهم: التطبيق أداة مساعدة فقط، ولا يغني عن العصا البيضاء، الطبيب، المحامي، أو خدمات الطوارئ الرسمية في المواقف الخطرة.",
                    "Important: The app is assistive only and does not replace a white cane, doctor, lawyer, or official emergency services in dangerous situations."
                ))
                .foregroundStyle(.secondary)

                Text(L10n.t(
                    "الخصوصية: لا يتم حفظ الصور أو الملفات تلقائيًا. تتم المعالجة بعد موافقة المستخدم، ويمكن حذف البيانات المحلية من الإعدادات.",
                    "Privacy: Images and files are never saved automatically. Processing happens only after you confirm, and local data can be deleted from settings."
                ))
                .foregroundStyle(.secondary)

                Group {
                    Text(L10n.t("الإصدار: ", "Version: ") + "0.1 (iOS port)")
                    Text(L10n.t("المطور: عبدالله الراشدي",
                                 "Developer: Abdullah Al-Rashidi"))
                    Text(L10n.t("البريد: ", "Email: ") + contactEmail)
                }
                .font(.callout)
                .foregroundStyle(.secondary)

                Button {
                    if let url = URL(string: "mailto:\(contactEmail)") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    HStack {
                        Image(systemName: "envelope.fill")
                        Text(L10n.t("مراسلة المطور", "Email the developer"))
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("حول التطبيق", "About"))
    }
}
