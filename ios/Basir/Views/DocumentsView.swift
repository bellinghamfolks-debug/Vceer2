// DocumentsView.swift  (Documents tab)
// PDF / DOCX / PPTX conversion and translation entry points.
//
// Note: in this scaffold the actual conversion pipeline is NOT
// implemented (see README for why — iOS background-processing limits
// require a different strategy than Android's foreground service). The
// cards remain so the layout matches the Android version; tapping them
// shows a friendly "coming soon" message.

import SwiftUI

struct DocumentsView: View {
    @State private var showSoon = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SectionHeader(L10n.t("المستندات والتحويل",
                                          "Documents and conversion"))
                    NavigationLink {
                        DocumentConvertView()
                    } label: {
                        BasirCard(
                            icon: "doc.richtext.fill",
                            title: L10n.t("قراءة وتحويل المستندات",
                                          "Read and convert documents"),
                            description: L10n.t(
                                "حوّل ملفات PDF و PowerPoint و Word إلى نص منظم، مع وصف للصور والجداول.",
                                "Convert PDF, PowerPoint, and Word files into structured text with image and table descriptions."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        VoiceConversationView()
                    } label: {
                        BasirCard(
                            icon: "waveform.and.mic",
                            title: L10n.t("محادثة صوتية مستمرة",
                                          "Continuous voice conversation"),
                            description: L10n.t(
                                "تحدّث مع بصير بسلاسة — استمع للإجابة وسيستعدّ تلقائياً للسؤال التالي.",
                                "Talk with Basir smoothly — listen to the answer and Basir gets ready for the next question."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    Button {
                        showSoon = true
                    } label: {
                        BasirCard(
                            icon: "questionmark.bubble.fill",
                            title: L10n.t("اسأل عن آخر مستند",
                                          "Ask about the latest document"),
                            description: L10n.t(
                                "اطرح أي سؤال عن المستند الذي حوّلته للتو.",
                                "Ask any question about the document you just converted."
                            )
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        TranslateView()
                    } label: {
                        BasirCard(
                            icon: "globe",
                            title: L10n.t("ترجمة وشرح", "Translate and explain"),
                            description: L10n.t(
                                "ترجم النصوص والملفات، وافهم المعنى والنبرة والسياق.",
                                "Translate text and files, and understand meaning, tone, and context."
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
            }
            .navigationTitle(L10n.t("المستندات", "Documents"))
            .alert(L10n.t("قيد التطوير", "In development"), isPresented: $showSoon) {
                Button(L10n.t("حسناً", "OK"), role: .cancel) {}
            } message: {
                Text(L10n.t(
                    "تحويل المستندات على iOS يحتاج آلية معالجة خلفية مختلفة عن نظام أندرويد. سيُضاف في الإصدار التالي.",
                    "Document conversion on iOS needs a different background-processing strategy than Android. It will arrive in the next release."
                ))
            }
        }
    }
}
