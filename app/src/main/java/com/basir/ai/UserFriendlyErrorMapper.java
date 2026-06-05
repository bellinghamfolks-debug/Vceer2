package com.basir.ai;

/**
 * v2.3.1 — pure helper that turns a technical exception (HTTP 401,
 * JSONException "Unterminated array", UnknownHostException, ...) into a
 * short, localised sentence the user can actually act on.
 *
 * Extracted from MainActivity.mapFriendlyError() so the same mapping can
 * be reused by any future surface (notifications, log viewer, Developer
 * Diagnostics page).
 *
 * Design notes
 * ────────────
 *   - The mapper does not depend on any Android types other than a
 *     pluggable {@link Localizer}. That means it is unit-testable on a
 *     plain JVM, no instrumentation needed.
 *   - Order matters: most specific patterns come first, generic patterns
 *     last. If you add a new case, put it BEFORE the catch-all default.
 *   - The raw exception text is appended to the friendly sentence (with
 *     {@link #map(Throwable, Localizer)}) so developers reading a bug
 *     report still see the original technical cause.
 */
public final class UserFriendlyErrorMapper {

    /** Pluggable Arabic/English picker. MainActivity passes its own {@code t()}. */
    public interface Localizer {
        String t(String arabic, String english);
    }

    private UserFriendlyErrorMapper() {}

    /**
     * Map {@code e} to a localised user-facing sentence, then append the
     * (truncated) raw cause on a new paragraph.
     */
    public static String map(Throwable e, Localizer t) {
        String raw = e == null ? "" : (e.getMessage() == null ? "" : e.getMessage());
        String friendly = mapWithoutTail(raw, e, t);
        String tail = safeTail(raw);
        return tail.isEmpty() ? friendly
                : friendly + "\n\n" + t.t("التفاصيل التقنية: ", "Technical details: ") + tail;
    }

    /** Just the localised sentence, no tail. Used when the caller wants
     *  to display the technical detail separately (e.g. in a collapsed
     *  "diagnostics" row). */
    public static String mapWithoutTail(Throwable e, Localizer t) {
        String raw = e == null ? "" : (e.getMessage() == null ? "" : e.getMessage());
        return mapWithoutTail(raw, e, t);
    }

    private static String mapWithoutTail(String raw, Throwable e, Localizer t) {
        String low = raw == null ? "" : raw.toLowerCase();

        // --- API key / authentication ---
        if (low.contains("api key") && low.contains("empty")) {
            return t.t("لم يُضف مفتاح Gemini بعد. افتح الإعدادات وأدخل مفتاح مشروعك.",
                       "No Gemini API key has been added. Open Settings and enter your project key.");
        }
        if (low.contains("proxy url") && low.contains("empty")) {
            return t.t("لم يُضف رابط الخادم الوسيط. افتح الإعدادات وأدخل رابط HTTPS صالحًا.",
                       "No proxy URL has been added. Open Settings and enter a valid HTTPS URL.");
        }
        if (low.contains("http 401") || low.contains("unauthorized")
                || low.contains("api key not valid") || low.contains("invalid_api_key")) {
            return t.t("رفضت الخدمة مفتاح Gemini. تحقّق من المفتاح والمشروع ثم أعد المحاولة.",
                       "The service rejected the Gemini API key. Check the key and project, then try again.");
        }
        if (low.contains("http 403") || low.contains("forbidden")
                || low.contains("permission_denied")) {
            return t.t("لا يملك المفتاح الإذن المطلوب. تحقّق من تفعيل Gemini API وصلاحيات المشروع والفوترة عند الحاجة.",
                       "The key lacks the required permission. Check Gemini API access, project permissions, and billing if required.");
        }

        // --- Rate limits and server load ---
        if (low.contains("http 429") || low.contains("rate") || low.contains("quota")) {
            return t.t("بلغ الحساب حد الطلبات أو الحصة. انتظر قليلًا أو راجع حدود مشروعك ثم أعد المحاولة.",
                       "The account reached a request or quota limit. Wait, or review your project limits, then try again.");
        }
        if (low.contains("http 500") || low.contains("http 502")
                || low.contains("http 503") || low.contains("http 504")
                || low.contains("internal server error") || low.contains("unavailable")) {
            return t.t("خدمة المعالجة غير متاحة مؤقتًا. أعد المحاولة بعد قليل.",
                       "The processing service is temporarily unavailable. Try again shortly.");
        }

        // --- Network ---
        if (low.contains("unknownhost") || low.contains("no address")
                || low.contains("not resolve")) {
            return t.t("تعذّر الوصول إلى خدمة المعالجة. تحقّق من الإنترنت وعنوان الخادم الوسيط إن كنت تستخدمه.",
                       "The processing service could not be reached. Check your internet connection and proxy address if used.");
        }
        if (low.contains("timeout") || low.contains("timed out")
                || low.contains("http 408")) {
            return t.t("انتهت مهلة الاتصال قبل اكتمال الطلب. تحقّق من الشبكة ثم أعد المحاولة، وجرّب ملفًا أصغر عند الحاجة.",
                       "The request timed out before completion. Check the network and try again, using a smaller file if needed.");
        }
        if (low.contains("ssl") || low.contains("handshake")
                || low.contains("trust anchor") || low.contains("cleartext")) {
            return t.t("تعذّر الاتصال الآمن بالخادم. تأكد أن رابط المزوّد يبدأ بـ https.",
                       "Could not establish a secure connection. Make sure the proxy URL uses https.");
        }

        // --- Model output problems ---
        if (low.contains("unterminated") || low.contains("jsonexception")
                || low.contains("malformed json") || low.contains("parse")) {
            return t.t("أعاد النموذج استجابة غير مكتملة أو غير قابلة للقراءة. جرّب جودة أعلى أو ملفًا أصغر.",
                       "The model returned an incomplete or unreadable response. Try a higher quality setting or a smaller file.");
        }
        if (low.contains("safety") || low.contains("blocked")
                || low.contains("recitation")) {
            return t.t("لم تسمح خدمة الذكاء الاصطناعي بمعالجة هذا المحتوى وفق ضوابطها. جرّب محتوى أو صياغة مختلفة.",
                       "The AI service did not allow this content under its safeguards. Try different content or wording.");
        }

        // --- Files and storage ---
        if (low.contains("filenotfound") || low.contains("no such file")) {
            return t.t("تعذّر فتح الملف. قد يكون نُقل أو حُذف أو ليس للتطبيق صلاحية الوصول إليه.",
                       "Could not open the file. It may have been moved, deleted, or the app does not have permission to read it.");
        }
        if (low.contains("upload failed")) {
            return t.t("فشل رفع الملف إلى Gemini. تحقق من الإنترنت ثم أعد المحاولة.",
                       "File upload to Gemini failed. Check your internet and try again.");
        }
        if (low.contains("file is too large") || low.contains("too large")
                || low.contains("file size")) {
            return t.t("الملف أكبر من الحد المسموح. قسّم الملف إلى أجزاء أصغر.",
                       "The file is larger than allowed. Split it into smaller parts.");
        }

        // --- Cancellation ---
        if (low.contains("cancelled") || low.contains("canceled")
                || low.contains("interrupted")) {
            return t.t("تم إلغاء العملية.", "The operation was cancelled.");
        }

        // --- Internal / unexpected ---
        if (e instanceof NullPointerException
                || low.contains("nullpointerexception")
                || low.contains("classcastexception")) {
            return t.t("حدث خطأ داخلي. أعد فتح الشاشة وجرّب مرة أخرى، وأرسل التفاصيل التقنية للمطوّر إذا تكرر.",
                       "An internal error occurred. Reopen the screen and try again; if it repeats, send the technical details to the Developer.");
        }

        // --- Default ---
        return t.t("تعذّر إكمال الطلب. تحقّق من الاتصال وإعداد Gemini أو الخادم الوسيط، ثم أعد المحاولة.",
                   "The request could not be completed. Check your connection and Gemini or proxy settings, then try again.");
    }

    private static String safeTail(String s) {
        if (s == null) return "";
        return s.length() > 280 ? s.substring(0, 280) + "..." : s;
    }
}
