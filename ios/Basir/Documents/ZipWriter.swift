// ZipWriter.swift
//
// Counterpart to ZipReader — writes a minimal STORE-method ZIP
// archive. Used by DocxWriter to package the OOXML parts
// (document.xml, [Content_Types].xml, _rels/.rels, etc.) into a
// .docx file the user can open in Word, Pages, or Google Docs.
//
// Why STORE only (no DEFLATE)
// ───────────────────────────
// Compressed entries would shrink the output, but Word / Pages /
// Google Docs all happily open STORED OOXML packages, and the
// content we write (a few KB of XML for the average converted
// document) doesn't compress meaningfully anyway. Keeping the
// writer STORE-only keeps it under 120 lines with zero
// dependencies — matching the project's vendored-only posture.
//
// Format references (PKWARE APPNOTE.TXT):
//   - 4.3.7 Local File Header
//   - 4.3.12 Central Directory Record
//   - 4.3.16 End of Central Directory Record

import Foundation

struct ZipWriter {

    struct Entry {
        let name: String
        let data: Data
    }

    private var entries: [Entry] = []

    mutating func addFile(name: String, data: Data) {
        entries.append(Entry(name: name, data: data))
    }

    mutating func addFile(name: String, utf8: String) {
        if let data = utf8.data(using: .utf8) {
            entries.append(Entry(name: name, data: data))
        }
    }

    /// Serialise every entry into a single ZIP archive blob ready to
    /// write to disk.
    func archive() -> Data {
        var out = Data()
        var centralDir = Data()
        let (dosTime, dosDate) = Self.dosNow()

        for entry in entries {
            let nameData = entry.name.data(using: .utf8) ?? Data()
            let crc = Self.crc32(entry.data)
            let size = UInt32(entry.data.count)
            let offset = UInt32(out.count)

            // Local File Header
            out.append(le32(0x04034b50))           // signature
            out.append(le16(20))                   // version needed (2.0)
            out.append(le16(0))                    // general purpose flags
            out.append(le16(0))                    // method = STORE
            out.append(le16(dosTime))              // mod time
            out.append(le16(dosDate))              // mod date
            out.append(le32(crc))                  // crc32
            out.append(le32(size))                 // compressed size = size
            out.append(le32(size))                 // uncompressed size
            out.append(le16(UInt16(nameData.count)))
            out.append(le16(0))                    // extra field length
            out.append(nameData)
            out.append(entry.data)

            // Central Directory File Header
            centralDir.append(le32(0x02014b50))     // signature
            centralDir.append(le16(20))             // version made by
            centralDir.append(le16(20))             // version needed
            centralDir.append(le16(0))              // general purpose flags
            centralDir.append(le16(0))              // method = STORE
            centralDir.append(le16(dosTime))        // mod time
            centralDir.append(le16(dosDate))        // mod date
            centralDir.append(le32(crc))            // crc32
            centralDir.append(le32(size))           // compressed size
            centralDir.append(le32(size))           // uncompressed size
            centralDir.append(le16(UInt16(nameData.count)))
            centralDir.append(le16(0))              // extra
            centralDir.append(le16(0))              // comment
            centralDir.append(le16(0))              // disk number
            centralDir.append(le16(0))              // internal attrs
            centralDir.append(le32(0))              // external attrs
            centralDir.append(le32(offset))         // local header offset
            centralDir.append(nameData)
        }

        let cdOffset = UInt32(out.count)
        let cdSize   = UInt32(centralDir.count)
        out.append(centralDir)

        // End of Central Directory Record
        out.append(le32(0x06054b50))                // signature
        out.append(le16(0))                         // disk number
        out.append(le16(0))                         // disk where CD starts
        out.append(le16(UInt16(entries.count)))     // entries on this disk
        out.append(le16(UInt16(entries.count)))     // total entries
        out.append(le32(cdSize))                    // CD size
        out.append(le32(cdOffset))                  // CD offset
        out.append(le16(0))                         // comment length

        return out
    }

    /// Convenience: write directly to a destination URL, atomically.
    func write(to url: URL) throws {
        try archive().write(to: url, options: .atomic)
    }

    // MARK: - Helpers

    private func le16(_ v: UInt16) -> Data {
        var le = v.littleEndian
        return Data(bytes: &le, count: 2)
    }
    private func le32(_ v: UInt32) -> Data {
        var le = v.littleEndian
        return Data(bytes: &le, count: 4)
    }

    /// Returns (dosTime, dosDate) for "now" in UTC. DOS encoding:
    ///   time = (hour << 11) | (minute << 5) | (second / 2)
    ///   date = ((year-1980) << 9) | (month << 5) | day
    private static func dosNow() -> (UInt16, UInt16) {
        let cal = Calendar(identifier: .gregorian)
        let parts = cal.dateComponents(
            [.year, .month, .day, .hour, .minute, .second],
            from: Date())
        let year   = max(1980, parts.year   ?? 1980)
        let month  = parts.month  ?? 1
        let day    = parts.day    ?? 1
        let hour   = parts.hour   ?? 0
        let minute = parts.minute ?? 0
        let second = (parts.second ?? 0) / 2
        let date = UInt16(((year - 1980) << 9) | (month << 5) | day)
        let time = UInt16((hour << 11) | (minute << 5) | second)
        return (time, date)
    }

    // CRC-32 table-based implementation. Used by every ZIP entry.
    private static let crcTable: [UInt32] = {
        var t = [UInt32](repeating: 0, count: 256)
        for n in 0..<256 {
            var c = UInt32(n)
            for _ in 0..<8 {
                c = (c & 1) != 0 ? 0xedb88320 ^ (c >> 1) : c >> 1
            }
            t[n] = c
        }
        return t
    }()

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xffffffff
        for byte in data {
            let idx = Int((crc ^ UInt32(byte)) & 0xff)
            crc = (crc >> 8) ^ crcTable[idx]
        }
        return crc ^ 0xffffffff
    }
}
