// HomeView.swift  (Talk tab)
// Lists the conversational entry points.

import SwiftUI

struct HomeView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Hero()
                    SectionHeader(L10n.t("الأسئلة والمحادثة",
                                          "Questions and conversation"))
                    NavigationLink {
                        AskBasirView()
                    } label: {
                        BasirCard(
                            icon: "bubble.left.and.bubble.right.fill",
                            title: L10n.t("اسأل بصير", "Ask Basir"),
                            description: L10n.t(
                                "اكتب سؤالك أو استخدم الإملاء الصوتي. راجع المعلومات المهمة قبل الاعتماد عليها.",
                                "Type your question or use voice dictation. Verify important information before relying on it."
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
                                "ترجم النصوص بين اللغات المدعومة، مع توضيح المعنى والنبرة والسياق عند طلبك.",
                                "Translate text across supported languages, with tone and context notes when requested."
                            )
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
            }
            .navigationTitle(L10n.t("بصير", "Basir"))
        }
    }
}

// MARK: - Reusable building blocks

struct Hero: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.t("بصير", "Basir"))
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(.white)
                .accessibilityAddTraits(.isHeader)
            Text(L10n.t(
                "مساعد وصول ذكي للصور والمستندات والترجمة والمحادثة",
                "An AI accessibility assistant for images, documents, translation, and conversation"
            ))
            .font(.body)
            .foregroundStyle(.white.opacity(0.85))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
        .background(
            LinearGradient(
                colors: [Color(red: 6/255, green: 35/255, blue: 86/255),
                         Color(red: 11/255, green: 58/255, blue: 130/255)],
                startPoint: .top, endPoint: .bottom
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct SectionHeader: View {
    let title: String
    init(_ title: String) { self.title = title }
    var body: some View {
        Text(title)
            .font(.subheadline.bold())
            .foregroundStyle(.secondary)
            .padding(.top, 8)
            .accessibilityAddTraits(.isHeader)
    }
}

struct BasirCard: View {
    let icon: String
    let title: String
    let description: String

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(0.15))
                    .frame(width: 48, height: 48)
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(Color.accentColor)
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.title3.bold())
                Text(description)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 0)

            Image(systemName: "chevron.forward")
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
        .padding(18)
        .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color.primary.opacity(0.07))
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title). \(description)")
        .accessibilityAddTraits(.isHeader)
    }
}
