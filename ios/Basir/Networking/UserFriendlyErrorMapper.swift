// UserFriendlyErrorMapper.swift
// Port of UserFriendlyErrorMapper.java. Same 15-pattern table, same
// Arabic/English copy, just expressed in Swift.
//
// Used at every catch site that surfaces an error to the user. Direct
// match for AlertItem messages and showResult subtitles.

import Foundation

enum UserFriendlyErrorMapper {

    /// Map any thrown error to a localised "we tried, here's what happened"
    /// sentence, followed by a truncated technical trailer for developers.
    static func map(_ error: Error) -> String {
        let raw = rawString(from: error)
        let friendly = friendlyMessage(for: raw, error: error)
        let tail = truncate(raw, max: 280)
        return tail.isEmpty ? friendly : "\(friendly)\n\n\(tail)"
    }

    static func friendlyMessage(for raw: String, error: Error) -> String {
        let low = raw.lowercased()

        // --- API key / authentication ---
        if low.contains("api key") && low.contains("empty") {
            return L10n.t("لم يتم إدخال مفتاح Gemini. افتح الإعدادات وأدخل المفتاح أولاً.",
                          "No Gemini API key was entered. Open Settings and add your key first.")
        }
        if (low.contains("http 401") || low.contains("unauthorized")
                || low.contains("api key not valid") || low.contains("invalid_api_key")) {
            return L10n.t("مفتاح Gemini غير صحيح أو منتهي الصلاحية. تحقق من المفتاح في الإعدادات.",
                          "The Gemini API key is invalid or expired. Check the key in Settings.")
        }
        if (low.contains("http 403") || low.contains("forbidden")
                || low.contains("permission_denied")) {
            return L10n.t("المفتاح ليس له صلاحية الوصول. تأكد أن واجهة Gemini مفعّلة في حسابك على Google.",
                          "The key does not have permission. Make sure the Gemini API is enabled on your Google account.")
        }

        // --- Rate limits and server load ---
        if low.contains("http 429") || low.contains("rate") || low.contains("quota") {
            return L10n.t("تجاوزت الحد المسموح من الطلبات. انتظر دقيقة ثم أعد المحاولة.",
                          "You hit the request rate limit. Wait a minute and try again.")
        }
        if low.contains("http 500") || low.contains("http 502")
                || low.contains("http 503") || low.contains("http 504")
                || low.contains("internal server error") || low.contains("unavailable") {
            return L10n.t("خوادم Gemini مشغولة الآن. أعد المحاولة بعد قليل.",
                          "Gemini servers are busy right now. Try again in a moment.")
        }

        // --- Network ---
        if low.contains("unknown host") || low.contains("a server with the specified hostname could not be found")
                || low.contains("no internet") || low.contains("not connect to") {
            return L10n.t("لا يوجد اتصال بالإنترنت أو تعذّر الوصول إلى الخادم.",
                          "No internet connection or the server could not be reached.")
        }
        if low.contains("timeout") || low.contains("timed out") || low.contains("http 408") {
            return L10n.t("انتهت مهلة الاتصال. الإنترنت بطيء أو الخادم لم يرد.",
                          "The connection timed out. Your network is slow or the server did not respond.")
        }

        // --- Model output problems ---
        if low.contains("unterminated") || low.contains("malformed json") || low.contains("decode") {
            return L10n.t("أعاد النموذج إجابة غير مكتملة. تم حفظ ما أمكن. جرّب جودة أعلى أو ملفاً أصغر.",
                          "The model returned an incomplete response. We saved what we could. Try a higher quality or a smaller file.")
        }
        if low.contains("safety") || low.contains("blocked") || low.contains("recitation") {
            return L10n.t("رفض النموذج معالجة المحتوى لأسباب سلامة. جرّب صياغة مختلفة أو ملفاً آخر.",
                          "The model refused to process the content for safety reasons. Try a different prompt or file.")
        }

        // --- Cancellation ---
        if low.contains("cancel") || low.contains("interrupted") || error is CancellationError {
            return L10n.t("تم إلغاء العملية.", "The operation was cancelled.")
        }

        if let geminiError = error as? GeminiError, case .missingApiKey = geminiError {
            return L10n.t("لم يتم إدخال مفتاح Gemini. افتح الإعدادات وأدخل المفتاح أولاً.",
                          "No Gemini API key was entered. Open Settings and add your key first.")
        }

        return L10n.t("تعذر إكمال العملية. تحقق من اتصال الإنترنت أو إعدادات مزود الذكاء الاصطناعي.",
                      "Could not complete the operation. Check your internet connection or AI provider settings.")
    }

    private static func rawString(from error: Error) -> String {
        if let gemini = error as? GeminiError {
            switch gemini {
            case .missingApiKey:                          return "API key is empty"
            case .http(let status, let body):             return "HTTP \(status): \(body)"
            case .decode(let msg):                        return msg
            case .network(let underlying):                return underlying.localizedDescription
            case .cancelled:                              return "Cancelled"
            }
        }
        return (error as NSError).localizedDescription
    }

    private static func truncate(_ s: String, max: Int) -> String {
        if s.count <= max { return s }
        return String(s.prefix(max)) + "..."
    }
}
