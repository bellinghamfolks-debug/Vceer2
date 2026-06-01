// MemoryView.swift
// Personal memory — people, products, and places saved locally.
//
// All data stays on-device via ArchiveStore (JSON file in Documents).
// No cloud sync. Matches the Android privacy posture: zero
// developer-side knowledge of the user's saved entries.

import SwiftUI

struct MemoryView: View {
    @StateObject private var store = ArchiveStore.shared
    @State private var newSection: NewEntrySheet?

    enum NewEntrySheet: String, Identifiable {
        case person, product, place
        var id: String { rawValue }
    }

    var body: some View {
        List {
            Section(L10n.t("الأشخاص", "People")) {
                if store.people.isEmpty {
                    Text(L10n.t("لا يوجد أشخاص محفوظين.",
                                 "No people saved yet."))
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(store.people) { person in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(person.name).font(.body.bold())
                            if !person.relation.isEmpty {
                                Text(person.relation).font(.caption).foregroundStyle(.secondary)
                            }
                            if !person.notes.isEmpty {
                                Text(person.notes).font(.caption)
                            }
                        }
                        .accessibilityLabel(
                            "\(person.name). \(person.relation). \(person.notes)"
                        )
                    }
                    .onDelete { indexSet in
                        for i in indexSet { store.deletePerson(store.people[i].id) }
                    }
                }
                Button {
                    newSection = .person
                } label: {
                    Label(L10n.t("إضافة شخص", "Add person"),
                          systemImage: "person.badge.plus")
                }
            }

            Section(L10n.t("المنتجات والأدوية",
                            "Products and medications")) {
                if store.products.isEmpty {
                    Text(L10n.t("لا توجد منتجات محفوظة.",
                                 "No products saved yet."))
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(store.products) { p in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(p.name).font(.body.bold())
                            if !p.barcode.isEmpty {
                                Text(L10n.t("باركود: ", "Barcode: ") + p.barcode)
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                            if !p.notes.isEmpty {
                                Text(p.notes).font(.caption)
                            }
                        }
                        .accessibilityLabel("\(p.name). \(p.barcode). \(p.notes)")
                    }
                    .onDelete { indexSet in
                        for i in indexSet { store.deleteProduct(store.products[i].id) }
                    }
                }
                Button {
                    newSection = .product
                } label: {
                    Label(L10n.t("إضافة منتج", "Add product"),
                          systemImage: "shippingbox")
                }
            }

            Section(L10n.t("الأماكن", "Places")) {
                if store.places.isEmpty {
                    Text(L10n.t("لا توجد أماكن محفوظة.",
                                 "No places saved yet."))
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(store.places) { p in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(p.name).font(.body.bold())
                            if !p.description.isEmpty {
                                Text(p.description).font(.caption).foregroundStyle(.secondary)
                            }
                            if !p.notes.isEmpty {
                                Text(p.notes).font(.caption)
                            }
                        }
                        .accessibilityLabel("\(p.name). \(p.description). \(p.notes)")
                    }
                    .onDelete { indexSet in
                        for i in indexSet { store.deletePlace(store.places[i].id) }
                    }
                }
                Button {
                    newSection = .place
                } label: {
                    Label(L10n.t("إضافة مكان", "Add place"),
                          systemImage: "mappin.circle")
                }
            }
        }
        .navigationTitle(L10n.t("محفوظاتي", "My saved items"))
        .sheet(item: $newSection) { kind in
            NewEntrySheetView(kind: kind, store: store)
        }
    }
}

// MARK: - New entry sheet

private struct NewEntrySheetView: View {
    let kind: MemoryView.NewEntrySheet
    let store: ArchiveStore

    @State private var name: String = ""
    @State private var secondary: String = ""
    @State private var notes: String = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section(L10n.t("الاسم", "Name")) {
                    TextField(L10n.t("الاسم", "Name"), text: $name)
                }
                Section(secondaryFieldLabel) {
                    TextField(secondaryFieldLabel, text: $secondary)
                }
                Section(L10n.t("ملاحظات", "Notes")) {
                    TextField(L10n.t("ملاحظات (اختياري)", "Notes (optional)"),
                              text: $notes, axis: .vertical)
                        .lineLimit(3, reservesSpace: true)
                }
            }
            .navigationTitle(titleForKind)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.t("إلغاء", "Cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.t("حفظ", "Save")) {
                        save()
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private var titleForKind: String {
        switch kind {
        case .person:  return L10n.t("إضافة شخص", "Add person")
        case .product: return L10n.t("إضافة منتج", "Add product")
        case .place:   return L10n.t("إضافة مكان", "Add place")
        }
    }

    private var secondaryFieldLabel: String {
        switch kind {
        case .person:  return L10n.t("العلاقة", "Relationship")
        case .product: return L10n.t("الباركود", "Barcode")
        case .place:   return L10n.t("الوصف", "Description")
        }
    }

    private func save() {
        let n = name.trimmingCharacters(in: .whitespaces)
        let s = secondary.trimmingCharacters(in: .whitespaces)
        let nt = notes.trimmingCharacters(in: .whitespaces)
        switch kind {
        case .person:
            store.addPerson(Person(name: n, relation: s, notes: nt))
        case .product:
            store.addProduct(Product(name: n, barcode: s, notes: nt))
        case .place:
            store.addPlace(Place(name: n, description: s, notes: nt))
        }
    }
}
