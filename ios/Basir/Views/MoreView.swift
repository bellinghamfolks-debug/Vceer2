// MoreView.swift  (More tab)
// Settings, Terms, Privacy, About — everything that doesn't need a
// dedicated tab.

import SwiftUI

struct MoreView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeader(L10n.t("مساعدة سريعة", "Quick help"))

                    NavigationLink {
                        EmergencyView()
                    } label: {
                        BasirCard(
                            icon: "sos.circle.fill",
                            title: L10n.t("الطوارئ والمساعدة",
                                          "Emergency and help"),
                            description: L10n.t(
                                "أرسل موقعك التقريبي أو اطلب المساعدة من جهة طوارئ محفوظة.",
                                "Share your approximate location or request help from a saved emergency contact."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    SectionHeader(L10n.t("محفوظاتي", "My data"))

                    NavigationLink {
                        MemoryView()
                    } label: {
                        BasirCard(
                            icon: "person.crop.rectangle.stack.fill",
                            title: L10n.t("محفوظاتي الخاصة",
                                          "My saved items"),
                            description: L10n.t(
                                "احفظ معلومات مهمة عن الأشخاص والمنتجات والأدوية والأماكن.",
                                "Save important information about people, products, medications, and places."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        ArchiveView()
                    } label: {
                        BasirCard(
                            icon: "tray.full.fill",
                            title: L10n.t("أرشيف النتائج", "Results archive"),
                            description: L10n.t(
                                "نتائج التحليل المحفوظة محلياً على جهازك.",
                                "Analysis results saved locally on your device."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    SectionHeader(L10n.t("التطبيق", "App"))

                    NavigationLink {
                        SettingsView()
                    } label: {
                        BasirCard(
                            icon: "gearshape.fill",
                            title: L10n.t("الإعدادات", "Settings"),
                            description: L10n.t(
                                "اللغة، الصوت، المظهر، الخصوصية، إعداد Gemini.",
                                "Language, voice, appearance, privacy, and Gemini setup."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        AboutView()
                    } label: {
                        BasirCard(
                            icon: "info.circle.fill",
                            title: L10n.t("حول التطبيق", "About"),
                            description: L10n.t(
                                "معلومات عن بصير وطرق التواصل مع المطوّر.",
                                "Information about Basir and how to contact the developer."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    SectionHeader(L10n.t("سياسات قانونية", "Legal"))

                    NavigationLink {
                        TermsView()
                    } label: {
                        BasirCard(
                            icon: "doc.text.fill",
                            title: L10n.t("الشروط والأحكام", "Terms and Conditions"),
                            description: L10n.t(
                                "شروط استخدام بصير ومسؤوليات المستخدم.",
                                "Basir terms of use and user responsibilities."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        PrivacyView()
                    } label: {
                        BasirCard(
                            icon: "hand.raised.fill",
                            title: L10n.t("سياسة الخصوصية", "Privacy Policy"),
                            description: L10n.t(
                                "كيف نتعامل مع بياناتك، وما الذي يبقى محفوظًا على جهازك فقط.",
                                "How we handle your data, and what stays only on your device."
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
            }
            .navigationTitle(L10n.t("المزيد", "More"))
        }
    }
}
