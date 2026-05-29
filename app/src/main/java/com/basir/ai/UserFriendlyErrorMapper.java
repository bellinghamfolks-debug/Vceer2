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
        return tail.isEmpty() ? friendly : friendly + "\n\n" + tail;
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
            return t.t("لم يتم إدخال مفتاح Gemini. افتح الإعدادات وأدخل المفتاح أولاً.",
                       "No Gemini API key was entered. Open Settings and add your key first.");
        }
        if (low.contains("proxy url") && low.contains("empty")) {
            return t.t("لم يتم إدخال رابط المزوّد (Proxy). افتح الإعدادات وأدخل الرابط.",
                       "No proxy URL was entered. Open Settings and add the proxy URL.");
        }
        if (low.contains("http 401") || low.contains("unauthorized")
                || low.contains("api key not valid") || low.contains("invalid_api_key")) {
            return t.t("مفتاح Gemini غير صحيح أو منتهي الصلاحية. تحقق من المفتاح في الإعدادات.",
                       "The Gemini API key is invalid or expired. Check the key in Settings.");
        }
        if (low.contains("http 403") || low.contains("forbidden")
                || low.contains("permission_denied")) {
            return t.t("المفتاح ليس له صلاحية الوصول. تأكد أن واجهة Gemini مفعّلة في حسابك على Google.",
                       "The key does not have permission. Make sure the Gemini API is enabled on your Google account.");
        }

        // --- Rate limits and server load ---
        if (low.contains("http 429") || low.contains("rate") || low.contains("quota")) {
            return t.t("تجاوزت الحد المسموح من الطلبات. انتظر دقيقة ثم أعد المحاولة.",
                       "You hit the request rate limit. Wait a minute and try again.");
        }
        if (low.contains("http 500") || low.contains("http 502")
                || low.contains("http 503") || low.contains("http 504")
                || low.contains("internal server error") || low.contains("unavailable")) {
            return t.t("خوادم Gemini مشغولة الآن. أعد المحاولة بعد قليل.",
                       "Gemini servers are busy right now. Try again in a moment.");
        }

        // --- Network ---
        if (low.contains("unknownhost") || low.contains("no address")
                || low.contains("not resolve")) {
            return t.t("لا يوجد اتصال بالإنترنت أو تعذّر الوصول إلى الخادم.",
                       "No internet connection or the server could not be reached.");
        }
        if (low.contains("timeout") || low.contains("timed out")
                || low.contains("http 408")) {
            return t.t("انتهت مهلة الاتصال. الإنترنت بطيء أو الخادم لم يرد.",
                       "The connection timed out. Your network is slow or the server did not respond.");
        }
        if (low.contains("ssl") || low.contains("handshake")
                || low.contains("trust anchor") || low.contains("cleartext")) {
            return t.t("تعذر الاتصال الآمن بالخادم. تأكد أن رابط المزوّد يبدأ بـ https.",
                       "Could not establish a secure connection. Make sure the proxy URL uses https.");
        }

        // --- Model output problems ---
        if (low.contains("unterminated") || low.contains("jsonexception")
                || low.contains("malformed json") || low.contains("parse")) {
            return t.t("أعاد النموذج إجابة غير مكتملة. تم حفظ ما أمكن. جرّب جودة أعلى أو ملفاً أصغر.",
                       "The model returned an incomplete response. We saved what we could. Try a higher quality or a smaller file.");
        }
        if (low.contains("safety") || low.contains("blocked")
                || low.contains("recitation")) {
            return t.t("رفض النموذج معالجة المحتوى لأسباب سلامة. جرّب صياغة مختلفة أو ملفاً آخر.",
                       "The model refused to process the content for safety reasons. Try a different prompt or file.");
        }

        // --- Files and storage ---
        if (low.contains("filenotfound") || low.contains("no such file")) {
            return t.t("تعذر فتح الملف. قد يكون نُقل أو حُذف أو ليس للتطبيق صلاحية الوصول إليه.",
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
            return t.t("حدث خطأ داخلي غير متوقع. إذا تكرّر، أرسل تقريراً للمطوّر.",
                       "An unexpected internal error occurred. If it repeats, send a report to the developer.");
        }

        // --- Default ---
        return t.t("تعذر إكمال العملية. تحقق من اتصال الإنترنت أو إعدادات مزود الذكاء الاصطناعي.",
                   "Could not complete the operation. Check your internet connection or AI provider settings.");
    }

    private static String safeTail(String s) {
        if (s == null) return "";
        return s.length() > 280 ? s.substring(0, 280) + "..." : s;
    }
}
