// ArchiveView.swift
// Browse and re-read past AI results. Filtered by kind so a user can
// jump straight to all their math extractions, all translations, etc.

import SwiftUI

struct ArchiveView: View {
    @StateObject private var store = ArchiveStore.shared
    @State private var filter: String = "all"

    private var filtered: [ArchivedResult] {
        if filter == "all" { return store.results }
        return store.results.filter { $0.kind == filter }
    }

    var body: some View {
        VStack(alignment: .leading) {
            Picker(L10n.t("التصفية", "Filter"), selection: $filter) {
                Text(L10n.t("الكل", "All")).tag("all")
                Text(L10n.t("ترجمة", "Translation")).tag("translate")
                Text(L10n.t("وصف صورة", "Image description")).tag("describe_image")
                Text(L10n.t("رياضيات", "Math")).tag("math_extract")
                Text(L10n.t("سؤال", "Question")).tag("ask")
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if filtered.isEmpty {
                ContentUnavailableView(
                    L10n.t("لا توجد نتائج محفوظة بعد.",
                           "No saved results yet."),
                    systemImage: "tray",
                    description: Text(L10n.t(
                        "النتائج تُحفظ تلقائياً عند تفعيل الحفظ التلقائي في الإعدادات.",
                        "Results auto-save when auto-save is enabled in Settings."
                    ))
                )
            } else {
                List {
                    ForEach(filtered) { result in
                        NavigationLink {
                            ArchivedResultDetail(result: result)
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(result.title).font(.body.bold())
                                    Spacer()
                                    Text(result.createdAt, style: .date)
                                        .font(.caption2)
                                        .foregroundStyle(.tertiary)
                                }
                                if !result.summary.isEmpty {
                                    Text(result.summary)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(2)
                                }
                            }
                            .accessibilityLabel("\(result.title). \(result.summary)")
                        }
                    }
                    .onDelete { indexSet in
                        for i in indexSet { store.deleteResult(filtered[i].id) }
                    }
                }
            }
        }
        .navigationTitle(L10n.t("أرشيف النتائج", "Results archive"))
    }
}

private struct ArchivedResultDetail: View {
    let result: ArchivedResult

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(result.title)
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                Text(result.createdAt, style: .date)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Divider()
                Text(result.text)
                    .textSelection(.enabled)
                    .accessibilityLabel(result.text)
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("نتيجة محفوظة", "Saved result"))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ShareLink(item: result.text) {
                    Image(systemName: "square.and.arrow.up")
                }
            }
        }
    }
}
