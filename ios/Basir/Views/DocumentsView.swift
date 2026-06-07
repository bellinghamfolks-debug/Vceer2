// DocumentsView.swift
// iOS currently supports single-pass PDF and text-file processing.

import SwiftUI

struct DocumentsView: View {
    @State private var showUnavailable = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeader(L10n.t("قراءة المستندات وترجمتها",
                                          "Read and translate documents"))
                    NavigationLink {
                        DocumentConvertView()
                    } label: {
                        BasirCard(
                            icon: "doc.richtext.fill",
                            title: L10n.t("معالجة مستند (PDF أو Word أو PowerPoint)",
                                          "Process a document (PDF, Word, or PowerPoint)"),
                            description: L10n.t(
                                "استخرج النص من PDF حتى 500 صفحة، أو من ملف Word (DOCX) أو PowerPoint (PPTX) أو نص، ثم نظّمه أو ترجمه عبر Gemini على دفعات. يمكنك إنشاء ملف Word من النتيجة. اترك التطبيق مفتوحًا أثناء التشغيل.",
                                "Extract text from a PDF of up to 500 pages, a Word (DOCX) file, a PowerPoint (PPTX) file, or a text file, then structure or translate it with Gemini in batches. You can build a Word file from the result. Keep the app open while it runs."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    Button {
                        showUnavailable = true
                    } label: {
                        BasirCard(
                            icon: "questionmark.bubble.fill",
                            title: L10n.t("اسأل عن مستند محفوظ",
                                          "Ask about a saved document"),
                            description: L10n.t(
                                "هذه الميزة غير متاحة في إصدار iOS الحالي.",
                                "This feature is not available in the current iOS release."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    SectionHeader(L10n.t("أدوات مرتبطة", "Related tools"))

                    NavigationLink {
                        VoiceConversationView()
                    } label: {
                        BasirCard(
                            icon: "waveform.and.mic",
                            title: L10n.t("محادثة صوتية متتابعة",
                                          "Continuous voice conversation"),
                            description: L10n.t(
                                "اسأل بصوتك واستمع إلى كل إجابة، ثم يبدأ الاستماع للسؤال التالي تلقائيًا.",
                                "Ask by voice, hear each answer, and automatically begin listening for the next question."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        TranslateView()
                    } label: {
                        BasirCard(
                            icon: "globe",
                            title: L10n.t("ترجمة نص وشرح السياق", "Translate text and explain context"),
                            description: L10n.t(
                                "ترجم نصًا بين اللغات المدعومة، مع توضيح النبرة والسياق عند الحاجة.",
                                "Translate text across supported languages, with tone and context notes when useful."
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
            }
            .navigationTitle(L10n.t("المستندات", "Documents"))
            .alert(L10n.t("الميزة غير متاحة", "Feature unavailable"), isPresented: $showUnavailable) {
                Button(L10n.t("حسنًا", "OK"), role: .cancel) {}
            } message: {
                Text(L10n.t(
                    "لا يدعم إصدار iOS الحالي الاحتفاظ بملف مرفوع لطرح أسئلة لاحقة عليه. استخدم معالجة المستند للحصول على نص، ثم انسخ المقطع المطلوب إلى شاشة اسأل بصير.",
                    "The current iOS release does not keep an uploaded document for later questions. Process the document to obtain text, then copy the relevant passage into Ask Basir."
                ))
            }
        }
    }
}
