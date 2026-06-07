// ZipReader.swift
//
// Minimal pure-Swift ZIP archive reader. Enough to extract NAMED
// files out of a .docx / .pptx — which are both ZIP-packaged XML.
// We do NOT implement compression (only decompression), encryption,
// or the streaming API; we read a small archive that already fits
// in memory.
//
// Why a custom reader (and not a third-party library)
// ───────────────────────────────────────────────────
// Apple's `Compression` framework gives us DEFLATE on iOS, but the
// public Foundation API does NOT include a ZIP archive parser. The
// alternatives (ZIPFoundation, Zip, etc.) would each add a SwiftPM
// dependency that fights the project's "vendored, zero-dependency"
// posture inherited from the Android side. ~180 lines of pure
// Swift here keep the iOS port as self-contained as the Android
// app already is.
//
// What it supports
//   - DEFLATE-compressed entries (the only method DOCX / PPTX use
//     in practice).
//   - STORE entries (uncompressed). Some apps emit these for tiny
//     XML pieces inside an OOXML archive.
//   - ZIP64 is NOT supported. DOCX / PPTX of any realistic size
//     stay under the 4 GB ZIP32 limit — orders of magnitude
//     larger than the 60-page documents iOS targets.
//
// Format references (for the curious maintainer):
//   - APPNOTE.TXT from PKWARE, sections 4.3.7 (Local File Header)
//     and 4.3.16 (End of Central Directory Record).

import Foundation
import Compression

enum ZipError: Error, LocalizedError {
    case notAZip
    case truncated
    case entryNotFound(String)
    case unsupportedMethod(UInt16)
    case decompressFailed

    var errorDescription: String? {
        switch self {
        case .notAZip:                  return "Not a ZIP file."
        case .truncated:                return "ZIP file is truncated."
        case .entryNotFound(let name):  return "Entry not found: \(name)"
        case .unsupportedMethod(let m): return "Unsupported compression method: \(m)"
        case .decompressFailed:         return "Decompression failed."
        }
    }
}

struct ZipReader {

    private let data: Data
    private let entries: [String: Entry]

    private struct Entry {
        let method: UInt16          // 0 = stored, 8 = deflate
        let compressedSize: UInt32
        let uncompressedSize: UInt32
        let localHeaderOffset: UInt32
    }

    init(url: URL) throws {
        let raw = try Data(contentsOf: url)
        try self.init(data: raw)
    }

    init(data: Data) throws {
        self.data = data
        self.entries = try Self.scanCentralDirectory(data)
    }

    /// Names of every file in the archive (no order guarantees).
    var fileNames: [String] { Array(entries.keys) }

    /// Returns the decompressed bytes for `name`, or throws if the
    /// entry isn't in the archive.
    func read(_ name: String) throws -> Data {
        guard let entry = entries[name] else {
            throw ZipError.entryNotFound(name)
        }
        return try decompress(entry)
    }

    /// Returns true when the archive contains an entry called `name`.
    func contains(_ name: String) -> Bool { entries[name] != nil }

    // MARK: - Central Directory scan

    private static let sigEOCD: UInt32 = 0x06054b50
    private static let sigCDFH: UInt32 = 0x02014b50
    private static let sigLFH:  UInt32 = 0x04034b50

    private static func scanCentralDirectory(_ data: Data) throws -> [String: Entry] {
        guard data.count > 22 else { throw ZipError.truncated }
        // End-of-central-directory has a max comment size of 0xFFFF,
        // so it lives in the last 65557 bytes. Scan backwards for
        // the signature.
        let startSearch = max(0, data.count - 22 - 65535)
        var eocdOffset: Int? = nil
        for i in stride(from: data.count - 22, through: startSearch, by: -1) {
            if data.readUInt32LE(at: i) == sigEOCD {
                eocdOffset = i
                break
            }
        }
        guard let eocd = eocdOffset else { throw ZipError.notAZip }

        let totalEntries = data.readUInt16LE(at: eocd + 10)
        let cdSize       = data.readUInt32LE(at: eocd + 12)
        let cdOffset     = Int(data.readUInt32LE(at: eocd + 16))
        guard cdOffset + Int(cdSize) <= data.count else {
            throw ZipError.truncated
        }

        var map: [String: Entry] = [:]
        map.reserveCapacity(Int(totalEntries))

        var p = cdOffset
        let cdEnd = cdOffset + Int(cdSize)
        while p < cdEnd {
            guard data.readUInt32LE(at: p) == sigCDFH else {
                throw ZipError.truncated
            }
            let method           = data.readUInt16LE(at: p + 10)
            let compressedSize   = data.readUInt32LE(at: p + 20)
            let uncompressedSize = data.readUInt32LE(at: p + 24)
            let nameLen          = Int(data.readUInt16LE(at: p + 28))
            let extraLen         = Int(data.readUInt16LE(at: p + 30))
            let commentLen       = Int(data.readUInt16LE(at: p + 32))
            let localOffset      = data.readUInt32LE(at: p + 42)

            let nameStart = p + 46
            let nameEnd = nameStart + nameLen
            guard nameEnd <= data.count else { throw ZipError.truncated }
            let name = String(data: data.subdata(in: nameStart..<nameEnd),
                              encoding: .utf8) ?? ""

            // Directories end with "/" and have zero-byte content;
            // skip them to keep the map small.
            if !name.hasSuffix("/") && !name.isEmpty {
                map[name] = Entry(method: method,
                                  compressedSize: compressedSize,
                                  uncompressedSize: uncompressedSize,
                                  localHeaderOffset: localOffset)
            }
            p = nameEnd + extraLen + commentLen
        }
        return map
    }

    // MARK: - Per-entry decompression

    private func decompress(_ entry: Entry) throws -> Data {
        let lfhOffset = Int(entry.localHeaderOffset)
        guard lfhOffset + 30 <= data.count,
              data.readUInt32LE(at: lfhOffset) == Self.sigLFH else {
            throw ZipError.truncated
        }
        // The LFH name + extra length can differ from the CDFH ones
        // (some packers normalise differently), so we read them again
        // here to find the actual compressed-payload offset.
        let lfhNameLen  = Int(data.readUInt16LE(at: lfhOffset + 26))
        let lfhExtraLen = Int(data.readUInt16LE(at: lfhOffset + 28))
        let payloadStart = lfhOffset + 30 + lfhNameLen + lfhExtraLen
        let payloadEnd   = payloadStart + Int(entry.compressedSize)
        guard payloadEnd <= data.count else { throw ZipError.truncated }

        let payload = data.subdata(in: payloadStart..<payloadEnd)
        switch entry.method {
        case 0:
            return payload
        case 8:
            return try Self.inflate(payload,
                                     expectedSize: Int(entry.uncompressedSize))
        default:
            throw ZipError.unsupportedMethod(entry.method)
        }
    }

    /// DEFLATE → raw bytes via Apple's Compression framework.
    private static func inflate(_ src: Data, expectedSize: Int) throws -> Data {
        // Buffer slightly above the declared uncompressed size; some
        // packers under-report by a byte or two.
        let cap = max(expectedSize + 16, 4096)
        var dst = Data(count: cap)
        let written = src.withUnsafeBytes { srcRaw -> Int in
            dst.withUnsafeMutableBytes { dstRaw -> Int in
                guard let srcPtr = srcRaw.baseAddress?
                        .assumingMemoryBound(to: UInt8.self),
                      let dstPtr = dstRaw.baseAddress?
                        .assumingMemoryBound(to: UInt8.self) else {
                    return 0
                }
                return compression_decode_buffer(
                    dstPtr, cap,
                    srcPtr, srcRaw.count,
                    nil, COMPRESSION_ZLIB)
            }
        }
        guard written > 0 else { throw ZipError.decompressFailed }
        dst.removeSubrange(written..<dst.count)
        return dst
    }
}

// MARK: - Little-endian helpers

private extension Data {
    func readUInt16LE(at offset: Int) -> UInt16 {
        let b0 = UInt16(self[offset])
        let b1 = UInt16(self[offset + 1])
        return b0 | (b1 << 8)
    }
    func readUInt32LE(at offset: Int) -> UInt32 {
        let b0 = UInt32(self[offset])
        let b1 = UInt32(self[offset + 1])
        let b2 = UInt32(self[offset + 2])
        let b3 = UInt32(self[offset + 3])
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24)
    }
}
