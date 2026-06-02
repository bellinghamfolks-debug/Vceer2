package com.basir.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * v2.7 — downscale + recompress an image before sending it to Gemini.
 *
 * Why
 * ───
 *   A modern phone camera produces a 12-MP JPEG that runs 3-5 MB on disk
 *   and ~6-10 MB once base64-encoded into the Gemini request body. The
 *   model itself renders that image as a thumbnail anyway (Gemini docs:
 *   images are tokenised against a 768×768 grid), so the extra
 *   resolution is pure waste — wasted bandwidth on the device's mobile
 *   data plan, wasted token quota on the user's Google account, and
 *   wasted latency in the upload + decode + tokenisation pipeline.
 *
 *   By scaling the long edge down to 1600 px and re-encoding at JPEG
 *   quality 85, the same picture lands at ~150-400 KB while keeping
 *   every detail Gemini actually uses. Typical cost reduction per image
 *   call: ~80% bytes on the wire, ~25% input tokens (because the inline
 *   image is now smaller than the auto-tile threshold).
 *
 * Why this lives in its own class
 * ───────────────────────────────
 *   The decode → rotate → scale → re-encode pipeline is tricky enough
 *   (sub-sampling, EXIF orientation, bitmap recycling) that it deserves
 *   one home with one set of tests, not three copy-pastes across the
 *   three image-using call sites in MainActivity.
 *
 * Failure mode
 * ────────────
 *   If decoding or recompressing fails for any reason (corrupt file,
 *   unsupported format, out-of-memory on a very low-end device), the
 *   caller catches the exception and falls back to sending the raw
 *   bytes. Worst case is the pre-v2.7 behaviour, never a crash.
 */
public final class ImageCompressor {

    /** Long edge target. Gemini renders against a 768-px grid; 1600 px
     *  leaves room for orientations and tiling without flooding the wire. */
    public static final int DEFAULT_MAX_LONG_EDGE_PX = 1600;

    /** v2.9.3 — math notation needs higher fidelity than scene
     *  description. Subscripts, superscripts, integral hooks, and Greek
     *  letter accents live in single-pixel pen strokes; aggressive
     *  down-scale + JPEG-85 routinely drops them. 2400-px + JPEG-92
     *  keeps the file under ~600 KB while preserving small marks. */
    public static final int MATH_MAX_LONG_EDGE_PX = 2400;
    public static final int MATH_JPEG_QUALITY     = 92;

    /** JPEG quality. 85 is the standard "indistinguishable from original
     *  at viewing distance" point; Gemini's vision tokeniser cannot tell
     *  85 from 100. Lower numbers buy bandwidth at the cost of fine OCR
     *  detail on tiny text. */
    public static final int DEFAULT_JPEG_QUALITY = 85;

    private ImageCompressor() {}

    /** Convenience overload using the default 1600-px long edge and
     *  quality 85. */
    public static byte[] compressForAi(Context ctx, Uri uri) throws Exception {
        return compress(ctx, uri, DEFAULT_MAX_LONG_EDGE_PX, DEFAULT_JPEG_QUALITY);
    }

    /** Return type for {@link #encodeForAi}. Carries the on-wire bytes
     *  plus the matching mime — after recompression an image is always
     *  "image/jpeg" regardless of the source format. */
    public static final class Encoded {
        public final byte[] bytes;
        public final String mimeType;
        public Encoded(byte[] bytes, String mimeType) {
            this.bytes = bytes;
            this.mimeType = mimeType;
        }
    }

    /**
     * One-shot helper for the three "send a user-supplied file to Gemini"
     * call sites in MainActivity. If the file is an image, compress it
     * (downscale + JPEG re-encode). Otherwise read it raw up to
     * {@code fallbackMaxBytes}. Any compression failure transparently
     * falls back to the raw-bytes path so the user keeps the pre-v2.7
     * behaviour rather than seeing an error.
     */
    public static Encoded encodeForAi(Context ctx, Uri uri, String mimeType,
                                      int fallbackMaxBytes) throws Exception {
        if (mimeType != null && mimeType.startsWith("image/")) {
            try {
                byte[] bytes = compressForAi(ctx, uri);
                return new Encoded(bytes, "image/jpeg");
            } catch (Throwable ignore) {
                // fall through to raw bytes
            }
        }
        byte[] bytes = AiClient.readUriBytes(ctx, uri, fallbackMaxBytes);
        return new Encoded(bytes, mimeType);
    }

    /**
     * v2.9.3 — high-fidelity variant of {@link #encodeForAi} for the
     * math extraction flow. Uses {@link #MATH_MAX_LONG_EDGE_PX} and
     * {@link #MATH_JPEG_QUALITY} so subscripts, superscripts, integral
     * hooks, and Greek-letter accents survive the JPEG round-trip.
     * Same failure-fallback shape — any compression error transparently
     * yields the raw bytes.
     */
    public static Encoded encodeForMath(Context ctx, Uri uri, String mimeType,
                                        int fallbackMaxBytes) throws Exception {
        if (mimeType != null && mimeType.startsWith("image/")) {
            try {
                byte[] bytes = compress(ctx, uri,
                        MATH_MAX_LONG_EDGE_PX, MATH_JPEG_QUALITY);
                return new Encoded(bytes, "image/jpeg");
            } catch (Throwable ignore) {
                // fall through to raw bytes
            }
        }
        byte[] bytes = AiClient.readUriBytes(ctx, uri, fallbackMaxBytes);
        return new Encoded(bytes, mimeType);
    }

    /**
     * Decode the image at {@code uri}, scale its long edge down to at most
     * {@code maxLongEdge}, apply the EXIF rotation tag, and re-encode as
     * JPEG at {@code jpegQuality}.
     *
     * Always returns a JPEG payload — even if the source was PNG, HEIF,
     * or anything else BitmapFactory understands. The caller's mime
     * argument to AiClient.ask should therefore be "image/jpeg".
     */
    public static byte[] compress(Context ctx, Uri uri, int maxLongEdge, int jpegQuality)
            throws Exception {

        // 1) Read just the header to learn the real dimensions, so the
        //    full decode runs with the right inSampleSize and we don't
        //    allocate a 4096×3072 ARGB bitmap on an OOM-prone device.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new Exception("Could not decode image bounds");
        }

        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        int sampleSize = 1;
        // Keep sample size a power of two — BitmapFactory rounds down to
        // one anyway, and we keep at least 2× the target so the final
        // proportional scale below has clean numbers to work with.
        while (longEdge / sampleSize > maxLongEdge * 2) sampleSize *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap raw;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            raw = BitmapFactory.decodeStream(in, null, opts);
        }
        if (raw == null) throw new Exception("Could not decode image");

        // 2) Apply EXIF orientation so portraits aren't sent sideways.
        //    ExifInterface(InputStream) needs API 24; on API 23 we leave
        //    the bitmap as-decoded (rare path, <1% of installs).
        Bitmap upright = applyExifRotationIfPossible(ctx, uri, raw);
        if (upright != raw) raw.recycle();

        // 3) Proportional scale to fit the long edge.
        int w = upright.getWidth();
        int h = upright.getHeight();
        int current = Math.max(w, h);
        Bitmap scaled;
        if (current > maxLongEdge) {
            float ratio = (float) maxLongEdge / current;
            int newW = Math.max(1, Math.round(w * ratio));
            int newH = Math.max(1, Math.round(h * ratio));
            scaled = Bitmap.createScaledBitmap(upright, newW, newH, true);
            if (scaled != upright) upright.recycle();
        } else {
            scaled = upright;
        }

        // 4) Re-encode. JPEG, not PNG — Gemini doesn't care, JPEG is
        //    ~5-10× smaller on the wire for photo content.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out);
        } finally {
            scaled.recycle();
        }
        return out.toByteArray();
    }

    private static Bitmap applyExifRotationIfPossible(Context ctx, Uri uri, Bitmap src) {
        if (Build.VERSION.SDK_INT < 24) return src;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return src;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            int degrees;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  degrees =  90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
                default: return src;
            }
            Matrix m = new Matrix();
            m.postRotate(degrees);
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Throwable ignore) {
            return src;
        }
    }
}
