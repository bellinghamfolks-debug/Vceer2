// ProxyAiProvider.swift
//
// iOS counterpart to Android's ProxyAiProvider. Forwards every
// request to an HTTPS proxy deployed by the user / their
// organisation. The proxy holds the Gemini API key, so end users
// never paste a key into their phone — exactly matching the
// /api/basir endpoint shape the Android client + server/index.js
// already use.
//
// Wire format (must stay in lockstep with Android's
// AiClient.proxyAsk + server/index.js):
//
//   POST <baseURL>/api/basir
//     Content-Type: application/json
//     X-Basir-Client-Token: <appToken>            (optional)
//   {
//     "task":         "ask" | "describe_image" | ...,
//     "input":        "...",
//     "instruction":  "...",
//     "language":     "ar" | "en",
//     "image_base64": "<optional>",
//     "mime_type":    "image/jpeg"                  (optional)
//   }
//
// Response: { "answer": "<text>" } or { "error": "<message>" }.
// For backward compat with older servers, a raw string body is also
// accepted (returned verbatim).

import Foundation

struct ProxyAiProvider: AiProvider {
    let settings: BasirSettings
    init(settings: BasirSettings = .shared) { self.settings = settings }

    func ask(task: TaskKind,
             input: String,
             instruction: String?,
             language: AppLanguage,
             imageData: Data?,
             mimeType: String?) async throws -> String {

        let (baseURL, token) = await MainActor.run {
            (settings.proxyURL.trimmingCharacters(in: .whitespacesAndNewlines),
             settings.proxyToken.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        guard !baseURL.isEmpty else {
            throw GeminiError.missingApiKey
        }
        guard let url = URL(string: Self.chatEndpoint(baseURL)) else {
            throw GeminiError.http(status: 0, body: "Invalid proxy URL")
        }

        var body: [String: Any] = [
            "task":        task.rawValue,
            "input":       input,
            "instruction": instruction ?? "",
            "language":    language == .arabic ? "ar" : "en"
        ]
        if let imageData {
            body["image_base64"] = imageData.base64EncodedString()
            body["mime_type"]    = (mimeType?.isEmpty == false) ? mimeType! : "image/jpeg"
        }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("Basir-iOS/3.2.0", forHTTPHeaderField: "User-Agent")
        if !token.isEmpty {
            req.setValue(token, forHTTPHeaderField: "X-Basir-Client-Token")
        }
        // Match Android's read timeout — proxies that forward to
        // Gemini may take a while on long prompts / large images.
        req.timeoutInterval = 120

        do {
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        } catch {
            throw GeminiError.decode("could not encode proxy body: \(error.localizedDescription)")
        }

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: req)
        } catch {
            throw GeminiError.network(error)
        }

        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let raw = String(data: data, encoding: .utf8) ?? ""
        if !(200..<300).contains(status) {
            throw GeminiError.http(status: status,
                                    body: String(raw.prefix(400)))
        }
        // Try JSON envelope first; fall back to raw text for old servers.
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let answer = json["answer"] as? String { return answer }
            if let err    = json["error"]  as? String {
                throw GeminiError.http(status: status, body: err)
            }
        }
        return raw
    }

    // MARK: - Endpoint helpers (mirror AiClient.chatEndpoint)

    private static func chatEndpoint(_ baseURL: String) -> String {
        var u = baseURL
        while u.hasSuffix("/") { u.removeLast() }
        if u.hasSuffix("/api/basir") { return u }
        return u + "/api/basir"
    }
}
