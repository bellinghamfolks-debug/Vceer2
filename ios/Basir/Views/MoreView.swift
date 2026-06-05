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
                                "جهّز رسالة طلب مساعدة لجهة محفوظة، مع موقع تقريبي عند السماح. ستراجع الرسالة وتؤكد إرسالها بنفسك.",
                                "Prepare a help message for a saved contact, with approximate location when permitted. You review and send it yourself."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    SectionHeader(L10n.t("المحفوظات المحلية", "Local saved data"))

                    NavigationLink {
                        MemoryView()
                    } label: {
                        BasirCard(
                            icon: "person.crop.rectangle.stack.fill",
                            title: L10n.t("محفوظاتي الخاصة",
                                          "My saved items"),
                            description: L10n.t(
                                "نظّم ملاحظات محلية عن الأشخاص والمنتجات والأدوية والأماكن للرجوع إليها لاحقًا.",
                                "Organize local notes about people, products, medications, and places for later reference."
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
                                "استعرض النتائج التي اخترت حفظها محليًا على هذا الجهاز.",
                                "Review results you chose to save locally on this device."
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
                                "اللغة، والصوت، والخصوصية، وإعداد Gemini، وجهة طلب المساعدة.",
                                "Language, voice, privacy, Gemini setup, and the help contact."
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
                                "اعرف ما يُحفظ محليًا وما يُرسل إلى Google Gemini.",
                                "Learn what is stored locally and what is sent to Google Gemini."
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
