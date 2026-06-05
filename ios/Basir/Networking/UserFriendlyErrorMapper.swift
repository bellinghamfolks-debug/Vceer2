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
        return tail.isEmpty
            ? friendly
            : "\(friendly)\n\n\(L10n.t("التفاصيل التقنية: ", "Technical details: "))\(tail)"
    }

    static func friendlyMessage(for raw: String, error: Error) -> String {
        let low = raw.lowercased()

        // --- API key / authentication ---
        if low.contains("api key") && low.contains("empty") {
            return L10n.t("لم يُضف مفتاح Gemini بعد. افتح الإعدادات وأدخل مفتاح مشروعك.",
                          "No Gemini API key has been added. Open Settings and enter your project key.")
        }
        if (low.contains("http 401") || low.contains("unauthorized")
                || low.contains("api key not valid") || low.contains("invalid_api_key")) {
            return L10n.t("رفضت الخدمة مفتاح Gemini. تحقّق من المفتاح والمشروع ثم أعد المحاولة.",
                          "The service rejected the Gemini API key. Check the key and project, then try again.")
        }
        if (low.contains("http 403") || low.contains("forbidden")
                || low.contains("permission_denied")) {
            return L10n.t("لا يملك المفتاح الإذن المطلوب. تحقّق من تفعيل Gemini API وصلاحيات المشروع والفوترة عند الحاجة.",
                          "The key lacks the required permission. Check Gemini API access, project permissions, and billing if required.")
        }

        // --- Rate limits and server load ---
        if low.contains("http 429") || low.contains("rate") || low.contains("quota") {
            return L10n.t("بلغ الحساب حد الطلبات أو الحصة. انتظر قليلًا أو راجع حدود مشروعك ثم أعد المحاولة.",
                          "The account reached a request or quota limit. Wait, or review your project limits, then try again.")
        }
        if low.contains("http 500") || low.contains("http 502")
                || low.contains("http 503") || low.contains("http 504")
                || low.contains("internal server error") || low.contains("unavailable") {
            return L10n.t("خدمة المعالجة غير متاحة مؤقتًا. أعد المحاولة بعد قليل.",
                          "The processing service is temporarily unavailable. Try again shortly.")
        }

        // --- Network ---
        if low.contains("unknown host") || low.contains("a server with the specified hostname could not be found")
                || low.contains("no internet") || low.contains("not connect to") {
            return L10n.t("تعذّر الوصول إلى خدمة المعالجة. تحقّق من الإنترنت وعنوان الخادم الوسيط إن كنت تستخدمه.",
                          "The processing service could not be reached. Check your internet connection and proxy address if used.")
        }
        if low.contains("timeout") || low.contains("timed out") || low.contains("http 408") {
            return L10n.t("انتهت مهلة الاتصال قبل اكتمال الطلب. تحقّق من الشبكة ثم أعد المحاولة، وجرّب ملفًا أصغر عند الحاجة.",
                          "The request timed out before completion. Check the network and try again, using a smaller file if needed.")
        }

        // --- Model output problems ---
        if low.contains("unterminated") || low.contains("malformed json") || low.contains("decode") {
            return L10n.t("أعاد النموذج استجابة غير مكتملة أو غير قابلة للقراءة. جرّب جودة أعلى أو ملفًا أصغر.",
                          "The model returned an incomplete or unreadable response. Try a higher quality setting or a smaller file.")
        }
        if low.contains("safety") || low.contains("blocked") || low.contains("recitation") {
            return L10n.t("لم تسمح خدمة الذكاء الاصطناعي بمعالجة هذا المحتوى وفق ضوابطها. جرّب محتوى أو صياغة مختلفة.",
                          "The AI service did not allow this content under its safeguards. Try different content or wording.")
        }

        // --- Cancellation ---
        if low.contains("cancel") || low.contains("interrupted") || error is CancellationError {
            return L10n.t("تم إلغاء العملية.", "The operation was cancelled.")
        }

        if let geminiError = error as? GeminiError, case .missingApiKey = geminiError {
            return L10n.t("لم يُضف مفتاح Gemini بعد. افتح الإعدادات وأدخل مفتاح مشروعك.",
                          "No Gemini API key has been added. Open Settings and enter your project key.")
        }

        return L10n.t("تعذّر إكمال الطلب. تحقّق من الاتصال وإعداد Gemini أو الخادم الوسيط، ثم أعد المحاولة.",
                      "The request could not be completed. Check your connection and Gemini or proxy settings, then try again.")
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
