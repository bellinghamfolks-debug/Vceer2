// ArchiveStore.swift
// Lightweight Codable-backed persistent store for personal memory
// (people / products / places) and archived AI results.
//
// Why not Core Data?
//   Core Data is the iOS-idiomatic option but adds ~400 lines of
//   schema + migration setup and brings a real cost in app startup
//   time. Basir's personal-memory volume is small (dozens of entries
//   per user, not thousands), so a JSON file in the Documents
//   directory loaded once at launch is the right tool. We can swap
//   to Core Data later if entry counts ever justify it.
//
// On-disk layout
//   <Documents>/basir_archive.json
//     {
//       "people":    [Person, ...],
//       "products":  [Product, ...],
//       "places":    [Place, ...],
//       "results":   [ArchivedResult, ...],
//       "logs":      [ActivityLog, ...]
//     }
//
// Writes are atomic (write to temp + rename) so a crash mid-write
// cannot corrupt the file.

import Foundation
import Combine

// MARK: - Records

struct Person: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var relation: String
    var notes: String
    var createdAt: Date = Date()
}

struct Product: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var barcode: String
    var notes: String
    var createdAt: Date = Date()
}

struct Place: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var description: String
    var notes: String
    var createdAt: Date = Date()
}

struct ArchivedResult: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var title: String
    var kind: String          // e.g. "image_describe", "translate", "math_extract"
    var text: String
    var summary: String
    var createdAt: Date = Date()
}

struct ActivityLog: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var type: String
    var content: String
    var createdAt: Date = Date()
}

// MARK: - Snapshot

private struct ArchiveSnapshot: Codable {
    var people: [Person] = []
    var products: [Product] = []
    var places: [Place] = []
    var results: [ArchivedResult] = []
    var logs: [ActivityLog] = []
}

// MARK: - Store

@MainActor
final class ArchiveStore: ObservableObject {
    static let shared = ArchiveStore()

    @Published private(set) var people: [Person] = []
    @Published private(set) var products: [Product] = []
    @Published private(set) var places: [Place] = []
    @Published private(set) var results: [ArchivedResult] = []
    @Published private(set) var logs: [ActivityLog] = []

    /// How many activity log + archive rows we keep. Older rows are
    /// trimmed at every save() call. Matches the Android BasirDb
    /// autoTrim contract (1000 logs / 200 documents).
    static let maxLogs = 1000
    static let maxResults = 200

    private var fileURL: URL {
        let docs = try! FileManager.default.url(for: .documentDirectory,
                                                in: .userDomainMask,
                                                appropriateFor: nil,
                                                create: true)
        return docs.appendingPathComponent("basir_archive.json")
    }

    private init() {
        load()
    }

    // MARK: - Mutations

    func addPerson(_ person: Person) {
        people.append(person)
        save()
    }
    func deletePerson(_ id: UUID) {
        people.removeAll { $0.id == id }
        save()
    }
    func addProduct(_ product: Product) {
        products.append(product)
        save()
    }
    func deleteProduct(_ id: UUID) {
        products.removeAll { $0.id == id }
        save()
    }
    func addPlace(_ place: Place) {
        places.append(place)
        save()
    }
    func deletePlace(_ id: UUID) {
        places.removeAll { $0.id == id }
        save()
    }

    func addResult(_ result: ArchivedResult) {
        guard BasirSettings.shared.autoSaveResults else { return }
        results.insert(result, at: 0)
        save()
    }
    func deleteResult(_ id: UUID) {
        results.removeAll { $0.id == id }
        save()
    }

    func appendLog(type: String, content: String) {
        guard !BasirSettings.shared.privacyMode else { return }
        logs.insert(ActivityLog(type: type, content: content), at: 0)
        save()
    }
    func clearLogs() {
        logs.removeAll()
        save()
    }

    func clearAll() {
        people.removeAll()
        products.removeAll()
        places.removeAll()
        results.removeAll()
        logs.removeAll()
        save()
    }

    // MARK: - Persistence

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let snapshot = try? JSONDecoder.iso.decode(ArchiveSnapshot.self, from: data) else {
            return
        }
        self.people = snapshot.people
        self.products = snapshot.products
        self.places = snapshot.places
        self.results = snapshot.results
        self.logs = snapshot.logs
    }

    private func save() {
        // Apply trim policy.
        if logs.count > Self.maxLogs {
            logs = Array(logs.prefix(Self.maxLogs))
        }
        if results.count > Self.maxResults {
            results = Array(results.prefix(Self.maxResults))
        }
        let snapshot = ArchiveSnapshot(
            people: people,
            products: products,
            places: places,
            results: results,
            logs: logs
        )
        // Atomic write: encode to a temp file then move into place.
        do {
            let data = try JSONEncoder.iso.encode(snapshot)
            let tmpURL = fileURL.appendingPathExtension("tmp")
            try data.write(to: tmpURL, options: [.atomic])
            if FileManager.default.fileExists(atPath: fileURL.path) {
                _ = try? FileManager.default.removeItem(at: fileURL)
            }
            try FileManager.default.moveItem(at: tmpURL, to: fileURL)
        } catch {
            // Failure to persist is non-fatal — the user just loses
            // the change on next app launch. Better than crashing.
        }
    }
}

// MARK: - Codable helpers

private extension JSONEncoder {
    static let iso: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        return e
    }()
}

private extension JSONDecoder {
    static let iso: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
}
