// VisionView.swift  (Vision tab)
// Image-based features: describe, alt-text, screenshot reader, currency/
// receipt reader, math extraction (v2.9 parity).

import SwiftUI

struct VisionView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeader(L10n.t("الصور والمشاهد", "Images and scenes"))

                    NavigationLink {
                        DescribeImageView(mode: .detailed)
                    } label: {
                        BasirCard(
                            icon: "photo.fill",
                            title: L10n.t("وصف صورة أو مشهد", "Describe an image or scene"),
                            description: L10n.t(
                                "التقط صورة أو اخترها من الجهاز للحصول على وصف منظم لما يظهر فيها، مع قراءة النص الظاهر عند الإمكان.",
                                "Take or choose an image to receive a structured description of what is visible, including readable text when possible."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        DescribeImageView(mode: .altText)
                    } label: {
                        BasirCard(
                            icon: "text.below.photo.fill",
                            title: L10n.t("إنشاء وصف بديل للصورة", "Generate image alt text"),
                            description: L10n.t(
                                "أنشئ وصفًا بديلًا مركزًا يشرح الغرض والمحتوى المهم دون حشو أو تخمين.",
                                "Create focused alt text that explains the purpose and important content without filler or guesswork."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        DescribeImageView(mode: .screenshot)
                    } label: {
                        BasirCard(
                            icon: "rectangle.on.rectangle.angled",
                            title: L10n.t("قراءة لقطة شاشة", "Read a screenshot"),
                            description: L10n.t(
                                "اقرأ النص الظاهر وأسماء الأزرار والرسائل، واشرح الخطوة التالية اعتمادًا على ما يظهر فقط.",
                                "Read visible text, button names, and messages, and explain the next step using only what is shown."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        DescribeImageView(mode: .currencyOrReceipt)
                    } label: {
                        BasirCard(
                            icon: "banknote.fill",
                            title: L10n.t("قراءة العملات والفواتير",
                                          "Read currency and receipts"),
                            description: L10n.t(
                                "التقط صورة واضحة للعملة أو الفاتورة لقراءة الفئة أو الإجمالي. تحقّق من الرقم قبل الدفع أو التسليم.",
                                "Take a clear photo of currency or a receipt to read the denomination or total. Verify the amount before paying or handing it over."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        DescribeImageView(mode: .medical)
                    } label: {
                        BasirCard(
                            icon: "cross.case.fill",
                            title: L10n.t("قراءة نص طبي", "Read medical text"),
                            description: L10n.t(
                                "تقرأ بصير وصفة أو نشرة دواء أو نتيجة تحليل ظاهرة في الصورة. هذه قراءة فقط؛ راجِع طبيبك أو الصيدلي قبل أي قرار.",
                                "Basir reads a prescription, drug leaflet, or lab result visible in the image. This is text reading only — consult your doctor or pharmacist before any decision."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        DescribeImageView(mode: .legal)
                    } label: {
                        BasirCard(
                            icon: "doc.text.magnifyingglass",
                            title: L10n.t("قراءة نص قانوني", "Read legal text"),
                            description: L10n.t(
                                "تقرأ بصير عقدًا أو وثيقة قانونية وتشرح بنوده بإيجاز. هذه قراءة عامة وليست رأيًا قانونيًا؛ راجِع محاميًا قبل التوقيع.",
                                "Basir reads a contract or legal document and summarizes its clauses. This is general reading, not legal advice — consult a lawyer before signing."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        DescribeImageView(mode: .table)
                    } label: {
                        BasirCard(
                            icon: "tablecells.fill",
                            title: L10n.t("قراءة جدول", "Read a table"),
                            description: L10n.t(
                                "صوّر جدولًا (جدول حصص، نتائج، مواعيد، فاتورة جداول) وستقرأه بصير صفًا بصف بترتيب يسهل سماعه.",
                                "Photograph a table (timetable, results, schedules, line-item invoice) and Basir reads it row by row in a screen-reader-friendly order."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        MathExtractView()
                    } label: {
                        BasirCard(
                            icon: "function",
                            title: L10n.t("تحليل ورقة رياضيات", "Analyze a math sheet"),
                            description: L10n.t(
                                "صوّر معادلات أو سبورة أو صفحة كتاب. يحاول بصير استخراج المعادلات بصيغة منطوقة مع LaTeX للمراجعة؛ قارِن الناتج بالصورة قبل اعتماده.",
                                "Photograph equations, a whiteboard, or a textbook page. Basir attempts to extract spoken math with LaTeX for review; compare the result with the image before relying on it."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        WalkingModeView()
                    } label: {
                        BasirCard(
                            icon: "figure.walk",
                            title: L10n.t("وضع المشي (لقطة واحدة)",
                                          "Walking mode (single shot)"),
                            description: L10n.t(
                                "التقط صورة واحدة لما أمامك واستمع إلى وصف موجز. هذه الميزة مساعدة وليست وسيلة تنقل مستقلة.",
                                "Capture one image of what is ahead and hear a brief description. This is an aid, not an independent mobility tool."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        LiveSceneGuidanceView()
                    } label: {
                        BasirCard(
                            icon: "video.fill",
                            title: L10n.t("الوصف المباشر أثناء التنقل",
                                          "Live scene guidance"),
                            description: L10n.t(
                                "وصف متواصل لما أمامك: صورة كل ثانيتين، تنبيه فوري للمخاطر باهتزاز ونطق. يعمل فقط أثناء فتح التطبيق.",
                                "Continuous scene description: one frame every two seconds with instant haptic and spoken hazard alerts. Works only while the app is open."
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
            }
            .navigationTitle(L10n.t("الرؤية", "Vision"))
        }
    }
}
