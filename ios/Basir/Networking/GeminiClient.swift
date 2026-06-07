// GeminiClient.swift
// REST client for Google's Gemini API.
//
// Direct mode only in this scaffold; proxy mode follows the same shape
// (just a different base URL + auth header) and can be added later.
//
// Mirrors GeminiDirectClient.java with two simplifications:
//   - async/await instead of HttpURLConnection on a background thread
//   - URLSession is platform-managed (no manual connection pooling)
//
// What it does NOT yet implement:
//   - Files API upload (uploadFile / waitForFileActive) — needed for
//     batched PDF conversion only
//   - Retry policy with exponential backoff
//   - Chunked streaming responses (for very long generations)
//
// Those are well-defined deltas a later phase can fold in.

import Foundation
import UIKit

enum GeminiError: Error {
    case missingApiKey
    case http(status: Int, body: String)
    case decode(String)
    case network(Error)
    case cancelled
}

struct GeminiClient {
    static let baseURL = "https://generativelanguage.googleapis.com/v1beta"
    static let maxOutputTokens = 16384

    // MARK: - Text-only request

    static func generateText(
        apiKey: String,
        model: String,
        systemText: String,
        userMessage: String
    ) async throws -> String {
        try validate(apiKey)
        let body: [String: Any] = [
            "system_instruction": [
                "parts": [["text": systemText]]
            ],
            "contents": [[
                "role": "user",
                "parts": [["text": userMessage]]
            ]],
            "generationConfig": [
                "maxOutputTokens": maxOutputTokens,
                "temperature": 0.4
            ]
        ]
        let json = try await post(model: model, apiKey: apiKey, body: body)
        return try extractTextResponse(from: json)
    }

    // MARK: - Image + text request

    static func generateWithImage(
        apiKey: String,
        model: String,
        systemText: String,
        userMessage: String,
        imageData: Data,
        mimeType: String
    ) async throws -> String {
        try validate(apiKey)
        let base64 = imageData.base64EncodedString()
        let body: [String: Any] = [
            "system_instruction": [
                "parts": [["text": systemText]]
            ],
            "contents": [[
                "role": "user",
                "parts": [
                    ["text": userMessage],
                    ["inlineData": [
                        "mimeType": mimeType,
                        "data": base64
                    ]]
                ]
            ]],
            "generationConfig": [
                "maxOutputTokens": maxOutputTokens,
                "temperature": 0.3
            ]
        ]
        let json = try await post(model: model, apiKey: apiKey, body: body)
        return try extractTextResponse(from: json)
    }

    // MARK: - JSON-mode image request (v3.2 — live scene guidance)

    /// JSON-mode variant that returns the response as a raw String
    /// (the JSON text). Lets the AiProvider abstraction keep its
    /// `String` return type while the caller decides when to parse.
    /// Used by the Direct provider's `.liveScene` branch.
    static func generateJsonStringWithImage(
        apiKey: String,
        model: String,
        systemText: String,
        userMessage: String,
        imageData: Data,
        mimeType: String,
        maxOutputTokens: Int = 1024
    ) async throws -> String {
        try validate(apiKey)
        let base64 = imageData.base64EncodedString()
        let body: [String: Any] = [
            "system_instruction": [
                "parts": [["text": systemText]]
            ],
            "contents": [[
                "role": "user",
                "parts": [
                    ["text": userMessage],
                    ["inlineData": [
                        "mimeType": mimeType,
                        "data": base64
                    ]]
                ]
            ]],
            "generationConfig": [
                "maxOutputTokens": maxOutputTokens,
                "temperature": 0.2,
                "responseMimeType": "application/json"
            ]
        ]
        let json = try await post(model: model, apiKey: apiKey, body: body)
        return try extractTextResponse(from: json)
    }

    /// Mirrors GeminiDirectClient.generateJsonWithImage on Android.
    /// Asks Gemini for application/json output so the response can be
    /// parsed deterministically — used by the Live Scene Guidance loop
    /// where every 2 seconds we need {hazard, path, scene} not prose.
    static func generateJsonWithImage(
        apiKey: String,
        model: String,
        systemText: String,
        userMessage: String,
        imageData: Data,
        mimeType: String,
        maxOutputTokens: Int = 1024
    ) async throws -> [String: Any] {
        try validate(apiKey)
        let base64 = imageData.base64EncodedString()
        let body: [String: Any] = [
            "system_instruction": [
                "parts": [["text": systemText]]
            ],
            "contents": [[
                "role": "user",
                "parts": [
                    ["text": userMessage],
                    ["inlineData": [
                        "mimeType": mimeType,
                        "data": base64
                    ]]
                ]
            ]],
            "generationConfig": [
                "maxOutputTokens": maxOutputTokens,
                "temperature": 0.2,
                "responseMimeType": "application/json"
            ]
        ]
        let json = try await post(model: model, apiKey: apiKey, body: body)
        let text = try extractTextResponse(from: json)
        guard let data = text.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw GeminiError.decode("response was not JSON: \(text.prefix(120))")
        }
        return obj
    }

    // MARK: - Internals

    private static func post(model: String,
                              apiKey: String,
                              body: [String: Any]) async throws -> [String: Any] {
        let urlString = "\(baseURL)/models/\(model):generateContent?key=\(apiKey)"
        guard let url = URL(string: urlString) else {
            throw GeminiError.http(status: 0, body: "Invalid URL")
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("Basir-iOS/3.2.0", forHTTPHeaderField: "User-Agent")
        req.timeoutInterval = 120

        do {
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        } catch {
            throw GeminiError.decode("could not encode request: \(error.localizedDescription)")
        }

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: req)
        } catch {
            throw GeminiError.network(error)
        }

        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let bodyString = String(data: data, encoding: .utf8) ?? ""
        if !(200..<300).contains(status) {
            throw GeminiError.http(status: status, body: bodyString)
        }
        guard let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw GeminiError.decode("response was not JSON")
        }
        return parsed
    }

    /// Pulls the first candidate's text out of a Gemini response payload.
    /// Matches the path used by GeminiDirectClient.extractTextFromResponse
    /// on Android.
    private static func extractTextResponse(from json: [String: Any]) throws -> String {
        if let candidates = json["candidates"] as? [[String: Any]],
           let first = candidates.first,
           let content = first["content"] as? [String: Any],
           let parts = content["parts"] as? [[String: Any]] {
            let texts = parts.compactMap { $0["text"] as? String }
            let joined = texts.joined()
            if !joined.isEmpty { return joined }
        }
        // Surface the safety block reason or any error message Google sent,
        // rather than returning an empty string the user can't act on.
        if let prompt = json["promptFeedback"] as? [String: Any],
           let block = prompt["blockReason"] as? String {
            throw GeminiError.decode("blocked: \(block)")
        }
        if let err = json["error"] as? [String: Any],
           let msg = err["message"] as? String {
            throw GeminiError.decode(msg)
        }
        throw GeminiError.decode("empty response")
    }

    private static func validate(_ apiKey: String) throws {
        guard !apiKey.trimmingCharacters(in: .whitespaces).isEmpty else {
            throw GeminiError.missingApiKey
        }
    }
}

// MARK: - High-level Provider abstraction

/// Mirrors AiProvider on Android. Calling code just does:
///   try await GeminiAiProvider().ask(task: .ask, input: q, ...)
/// without knowing whether the underlying transport is Direct or Proxy.
protocol AiProvider {
    func ask(task: TaskKind,
             input: String,
             instruction: String?,
             language: AppLanguage,
             imageData: Data?,
             mimeType: String?) async throws -> String
}

struct GeminiAiProvider: AiProvider {
    let settings: BasirSettings

    init(settings: BasirSettings = .shared) {
        self.settings = settings
    }

    func ask(task: TaskKind,
             input: String,
             instruction: String?,
             language: AppLanguage,
             imageData: Data?,
             mimeType: String?) async throws -> String {
        let key = KeychainStore.geminiKey()
        guard !key.isEmpty else { throw GeminiError.missingApiKey }
        let model = await MainActor.run { settings.modelFor(task: task) }
        let systemText = GeminiPrompts.systemPrompt(for: language, instruction: instruction)
        // v3.2 — Live scene guidance carries its prompt as `input`
        // (not `instruction`) so the proxy sees the full structured
        // prompt verbatim. The JSON-mode response is requested
        // server-side by responseMimeType=application/json.
        if task == .liveScene, let imageData, let mimeType {
            return try await GeminiClient.generateJsonStringWithImage(
                apiKey: key, model: model,
                systemText: systemText, userMessage: input,
                imageData: imageData, mimeType: mimeType,
                maxOutputTokens: 1024)
        }
        let userMessage = GeminiPrompts.userMessage(
            task: task, input: input,
            instruction: instruction,
            hasImage: imageData != nil
        )
        if let imageData, let mimeType {
            return try await GeminiClient.generateWithImage(
                apiKey: key, model: model,
                systemText: systemText, userMessage: userMessage,
                imageData: imageData, mimeType: mimeType
            )
        }
        return try await GeminiClient.generateText(
            apiKey: key, model: model,
            systemText: systemText, userMessage: userMessage
        )
    }
}
