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
                                "التقط صورة أو اخترها من المعرض لتحصل على وصف دقيق ومنظم.",
                                "Take a photo or pick one from the gallery for a detailed structured description."
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
                                "وصف قصير ومنظم يصلح كوصف بديل للصورة.",
                                "A short structured description suitable as image alt text."
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
                                "شرح عناصر الشاشة وتوضيح ما يظهر فيها واقتراح الخطوة التالية عند الحاجة.",
                                "Explain screen elements, describe what appears, and suggest the next step when helpful."
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
                                "التقط صورة للعملة أو الفاتورة وسأقرأ الفئة أو الإجمالي بسرعة ووضوح.",
                                "Take a photo of currency or a receipt and I will read the denomination or total quickly and clearly."
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
                                "صوّر معادلات أو سبورة أو صفحة كتاب — يستخرج بصير كل المعادلات بصيغة منطوقة قابلة للقراءة بقارئ الشاشة، مع الحفاظ على LaTeX للمراجعة.",
                                "Photograph equations, a whiteboard, or a textbook page — Basir extracts every equation in spoken form for the screen reader, with LaTeX preserved for verification."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        WalkingModeView()
                    } label: {
                        BasirCard(
                            icon: "figure.walk",
                            title: L10n.t("وضع المشي", "Walking mode"),
                            description: L10n.t(
                                "التقط ما أمامك بضغطة واحدة، واستمع إلى وصف موجز، ثم كرر للمشهد التالي.",
                                "Capture what is ahead in one tap, hear a brief description, then repeat for the next scene."
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
