package com.basir.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;

/**
 * Renders one PDF page at a time into a high-resolution, upright JPEG.
 *
 * Why this exists:
 * Gemini is much more reliable on a single, correctly oriented page image
 * than on a whole scanned PDF where several pages can be skipped or mixed.
 * The renderer also prevents the entire PDF from becoming one giant bitmap,
 * keeping memory use bounded to one page.
 */
final class PdfPageRasterizer implements AutoCloseable {

    private static final int TARGET_LONG_EDGE = 3072;
    private static final int MAX_BITMAP_PIXELS = 12_000_000;
    private static final int ORIENTATION_SAMPLE_EDGE = 320;
    private static final int MAX_INLINE_JPEG_BYTES = 12 * 1024 * 1024;

    static final class PageImage {
        final byte[] jpegBytes;
        final int width;
        final int height;
        final int rotationDegrees;
        final boolean visuallyBlank;
        final double inkRatio;

        PageImage(byte[] jpegBytes, int width, int height,
                  int rotationDegrees, boolean visuallyBlank, double inkRatio) {
            this.jpegBytes = jpegBytes;
            this.width = width;
            this.height = height;
            this.rotationDegrees = rotationDegrees;
            this.visuallyBlank = visuallyBlank;
            this.inkRatio = inkRatio;
        }
    }

    private static final class Score {
        final double value;
        final double inkRatio;
        Score(double value, double inkRatio) {
            this.value = value;
            this.inkRatio = inkRatio;
        }
    }

    private final ParcelFileDescriptor descriptor;
    private final PdfRenderer renderer;

    PdfPageRasterizer(Context context, Uri uri) throws Exception {
        if (context == null) throw new Exception("Context is missing.");
        if (uri == null) throw new Exception("PDF source Uri is missing.");
        ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new Exception("Unable to open the PDF file.");
        PdfRenderer opened = null;
        try {
            opened = new PdfRenderer(pfd);
        } catch (Throwable t) {
            try { pfd.close(); } catch (Throwable ignore) {}
            throw new Exception("Unable to render the PDF. It may be damaged or password protected.", t);
        }
        this.descriptor = pfd;
        this.renderer = opened;
    }

    int pageCount() {
        return renderer.getPageCount();
    }

    PageImage renderPage(int oneBasedPage) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new Exception("Cancelled");
        if (oneBasedPage < 1 || oneBasedPage > pageCount()) {
            throw new Exception("PDF page is outside the valid range: " + oneBasedPage);
        }

        PdfRenderer.Page page = null;
        Bitmap raw = null;
        Bitmap oriented = null;
        try {
            page = renderer.openPage(oneBasedPage - 1);
            int pageWidth = Math.max(1, page.getWidth());
            int pageHeight = Math.max(1, page.getHeight());

            double scale = (double) TARGET_LONG_EDGE / Math.max(pageWidth, pageHeight);
            scale = Math.max(1.75d, Math.min(4.0d, scale));
            double pixels = pageWidth * (double) pageHeight * scale * scale;
            if (pixels > MAX_BITMAP_PIXELS) {
                scale = Math.sqrt(MAX_BITMAP_PIXELS / (pageWidth * (double) pageHeight));
            }

            int outWidth = Math.max(1, (int) Math.round(pageWidth * scale));
            int outHeight = Math.max(1, (int) Math.round(pageHeight * scale));
            raw = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
            raw.eraseColor(Color.WHITE);

            Matrix renderMatrix = new Matrix();
            renderMatrix.setScale((float) scale, (float) scale);
            page.render(raw, null, renderMatrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);

            int rotation = detectBestRotation(raw);
            oriented = rotation == 0 ? raw : rotate(raw, rotation);
            if (oriented != raw) {
                raw.recycle();
                raw = null;
            }

            Score finalScore = score(oriented);
            boolean blank = finalScore.inkRatio < 0.0025d;
            byte[] jpeg = compressJpeg(oriented);
            return new PageImage(jpeg, oriented.getWidth(), oriented.getHeight(),
                    rotation, blank, finalScore.inkRatio);
        } catch (OutOfMemoryError oom) {
            throw new Exception("The PDF page is too large to render safely on this device.", oom);
        } finally {
            if (page != null) {
                try { page.close(); } catch (Throwable ignore) {}
            }
            if (oriented != null && !oriented.isRecycled()) oriented.recycle();
            if (raw != null && !raw.isRecycled()) raw.recycle();
        }
    }

    private static byte[] compressJpeg(Bitmap bitmap) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024 * 1024);
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
            throw new Exception("Unable to encode the rendered PDF page.");
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length <= MAX_INLINE_JPEG_BYTES) return bytes;

        out.reset();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
            throw new Exception("Unable to encode the rendered PDF page.");
        }
        bytes = out.toByteArray();
        if (bytes.length > MAX_INLINE_JPEG_BYTES) {
            throw new Exception("Rendered page remains too large for safe inline processing.");
        }
        return bytes;
    }

    private static int detectBestRotation(Bitmap source) {
        int max = Math.max(source.getWidth(), source.getHeight());
        if (max <= 0) return 0;
        float factor = Math.min(1f, ORIENTATION_SAMPLE_EDGE / (float) max);
        int sw = Math.max(1, Math.round(source.getWidth() * factor));
        int sh = Math.max(1, Math.round(source.getHeight() * factor));
        Bitmap sample = Bitmap.createScaledBitmap(source, sw, sh, true);

        int[] angles = {0, 90, 180, 270};
        double bestValue = -Double.MAX_VALUE;
        int bestAngle = 0;
        double baseValue = -Double.MAX_VALUE;
        double ink = 0d;

        try {
            for (int angle : angles) {
                Bitmap candidate = angle == 0 ? sample : rotate(sample, angle);
                try {
                    Score s = score(candidate);
                    if (angle == 0) {
                        baseValue = s.value;
                        ink = s.inkRatio;
                    }
                    if (s.value > bestValue) {
                        bestValue = s.value;
                        bestAngle = angle;
                    }
                } finally {
                    if (candidate != sample && !candidate.isRecycled()) candidate.recycle();
                }
            }
        } finally {
            if (sample != source && !sample.isRecycled()) sample.recycle();
        }

        // Blank/photos-only pages do not have enough line structure for a safe
        // automatic decision. Also avoid flipping a page for tiny score noise.
        if (ink < 0.0025d || bestValue < baseValue + 0.15d) return 0;
        return bestAngle;
    }

    private static Score score(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        double[] row = new double[height];
        double[] col = new double[width];
        double totalDark = 0d;
        double topDark = 0d;
        double bottomDark = 0d;
        int topLimit = Math.max(1, (int) (height * 0.35d));
        int bottomStart = Math.min(height - 1, (int) (height * 0.65d));

        for (int y = 0; y < height; y++) {
            int base = y * width;
            for (int x = 0; x < width; x++) {
                int color = pixels[base + x];
                double luma = 0.2126d * Color.red(color)
                        + 0.7152d * Color.green(color)
                        + 0.0722d * Color.blue(color);
                double darkness = 255d - luma;
                if (darkness < 18d) darkness = 0d;
                darkness /= 255d;
                row[y] += darkness;
                col[x] += darkness;
                totalDark += darkness;
                if (y < topLimit) topDark += darkness;
                if (y >= bottomStart) bottomDark += darkness;
            }
        }

        for (int y = 0; y < height; y++) row[y] /= width;
        for (int x = 0; x < width; x++) col[x] /= height;

        double rowVar = normalizedVariance(row);
        double colVar = normalizedVariance(col);
        double horizontalLineScore = (rowVar - colVar) / (rowVar + colVar + 1e-9d);
        double meanDark = totalDark / Math.max(1d, width * (double) height);
        double topMean = topDark / Math.max(1d, topLimit * (double) width);
        double bottomMean = bottomDark /
                Math.max(1d, (height - bottomStart) * (double) width);
        double topBias = (topMean - bottomMean) / (meanDark + 1e-9d);
        topBias = Math.max(-1d, Math.min(1d, topBias));

        return new Score(3.0d * horizontalLineScore + 0.55d * topBias, meanDark);
    }

    private static double normalizedVariance(double[] values) {
        if (values.length == 0) return 0d;
        double mean = 0d;
        for (double v : values) mean += v;
        mean /= values.length;
        double variance = 0d;
        for (double v : values) {
            double d = v - mean;
            variance += d * d;
        }
        variance /= values.length;
        return variance / (mean * mean + 1e-9d);
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0,
                source.getWidth(), source.getHeight(), matrix, true);
    }

    @Override
    public void close() {
        try { renderer.close(); } catch (Throwable ignore) {}
        try { descriptor.close(); } catch (Throwable ignore) {}
    }
}
