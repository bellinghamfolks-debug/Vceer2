package com.basir.ai.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Custom view that renders the current Scene from a top-down perspective
 * with the player always at the center of the screen, facing UP. Then a
 * vision filter is applied as the very last step so every other element
 * (objects, NPCs, HUD) goes through the same impairment lens.
 *
 * The view is intentionally non-photorealistic — colored blocks and circles —
 * because the game's "graphics" are *not* the gameplay. They exist mainly
 * for sighted observers, screen-readers focused on the surrounding UI, and
 * the partially-sighted vision modes.
 */
public class WorldView extends View {

    private Scene scene;
    private int px, py;
    private Direction facing = Direction.NORTH;
    private VisionMode vision = VisionMode.BLIND_TOTAL;
    private String hudText = "";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap offscreen;
    private Canvas offCanvas;

    public WorldView(Context context) {
        super(context);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setFakeBoldText(true);
    }

    public void setState(Scene s, int x, int y, Direction d,
                         VisionMode mode, String hud) {
        this.scene = s;
        this.px = x;
        this.py = y;
        this.facing = d;
        this.vision = mode;
        this.hudText = hud == null ? "" : hud;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas screen) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (offscreen == null || offscreen.getWidth() != w || offscreen.getHeight() != h) {
            if (offscreen != null) offscreen.recycle();
            offscreen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            offCanvas = new Canvas(offscreen);
        }
        // Clear
        offCanvas.drawColor(Color.rgb(20, 24, 36));

        if (scene == null) {
            screen.drawBitmap(offscreen, 0, 0, null);
            return;
        }

        // Tile size: pick so 9 tiles wide fit on screen.
        int tilesAcross = 9;
        float tile = Math.min(w, h) / (float) tilesAcross;
        float cx = w / 2f;
        float cy = h / 2f;

        // Rotate world so player faces UP. We iterate over a viewport
        // centered on the player.
        int radius = (int) Math.ceil(Math.max(w, h) / tile) + 1;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int wx, wy;
                switch (facing) {
                    case NORTH: wx = px + dx; wy = py + dy; break;
                    case EAST:  wx = px - dy; wy = py + dx; break;
                    case SOUTH: wx = px - dx; wy = py - dy; break;
                    case WEST:  wx = px + dy; wy = py - dx; break;
                    default:    wx = px + dx; wy = py + dy;
                }
                float sx = cx + dx * tile - tile / 2f;
                float sy = cy + dy * tile - tile / 2f;
                drawTile(offCanvas, scene.tileAt(wx, wy), sx, sy, tile);
            }
        }

        // Objects on top
        for (SceneObject o : scene.objects) {
            int dx = o.x - px;
            int dy = o.y - py;
            int rx, ry;
            switch (facing) {
                case NORTH: rx = dx;  ry = dy;  break;
                case EAST:  rx = dy;  ry = -dx; break;
                case SOUTH: rx = -dx; ry = -dy; break;
                case WEST:  rx = -dy; ry = dx;  break;
                default:    rx = dx;  ry = dy;
            }
            float sx = cx + rx * tile;
            float sy = cy + ry * tile;
            drawObject(offCanvas, o, sx, sy, tile);
        }

        // Player marker (arrow pointing up)
        paint.setColor(Color.rgb(255, 220, 90));
        paint.setStyle(Paint.Style.FILL);
        Path arrow = new Path();
        float r = tile * 0.45f;
        arrow.moveTo(cx, cy - r);
        arrow.lineTo(cx - r * 0.7f, cy + r * 0.6f);
        arrow.lineTo(cx + r * 0.7f, cy + r * 0.6f);
        arrow.close();
        offCanvas.drawPath(arrow, paint);

        // North compass indicator (small N glyph)
        textPaint.setColor(Color.argb(170, 200, 220, 255));
        textPaint.setTextSize(32f);
        offCanvas.drawText("N", 20f, 50f, textPaint);

        // Apply vision filter
        applyVisionFilter(offscreen, w, h);

        // Draw to screen
        screen.drawBitmap(offscreen, 0, 0, null);

        // HUD always readable on top of filter for sighted/TalkBack overlay
        if (!hudText.isEmpty()) {
            Paint bg = new Paint();
            bg.setColor(Color.argb(190, 0, 0, 0));
            float pad = 18f;
            float fontSize = Math.max(20f, w / 28f);
            textPaint.setTextSize(fontSize);
            textPaint.setColor(Color.WHITE);
            // Wrap manually by '\n'
            String[] lines = hudText.split("\n");
            float lineH = fontSize * 1.4f;
            float boxH = pad * 2 + lineH * lines.length;
            screen.drawRect(0, h - boxH, w, h, bg);
            float baseline = h - boxH + pad + fontSize;
            for (String line : lines) {
                screen.drawText(line, pad, baseline, textPaint);
                baseline += lineH;
            }
        }
    }

    private void drawTile(Canvas c, Tile t, float sx, float sy, float size) {
        int color;
        switch (t) {
            case WALL:      color = Color.rgb( 60,  70,  90); break;
            case DOOR:      color = Color.rgb(180, 140,  80); break;
            case STAIRS:    color = Color.rgb(120, 120, 140); break;
            case EXIT:      color = Color.rgb( 90, 180,  90); break;
            case CARPET:    color = Color.rgb(120,  70,  80); break;
            case GRASS:     color = Color.rgb( 70, 130,  90); break;
            case ROAD:      color = Color.rgb( 50,  50,  55); break;
            case CROSSWALK: color = Color.rgb(200, 200, 200); break;
            case PUDDLE:    color = Color.rgb( 60, 110, 160); break;
            case FLOOR:
            default:        color = Color.rgb( 90, 100, 130); break;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(sx, sy, sx + size, sy + size, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(40, 0, 0, 0));
        paint.setStrokeWidth(1f);
        c.drawRect(sx, sy, sx + size, sy + size, paint);
    }

    private void drawObject(Canvas c, SceneObject o, float sx, float sy, float size) {
        int color;
        boolean isNpc = o.interact != null && o.interact.startsWith("dlg_");
        boolean isExit = "exit".equals(o.interact);
        if (isNpc)            color = Color.rgb(220, 130, 200);
        else if (isExit)      color = Color.rgb(110, 200, 255);
        else if (o.soundFreq > 0) color = Color.rgb(255, 180,  90);
        else                  color = Color.rgb(200, 200, 220);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        float r = size * 0.36f;
        c.drawCircle(sx, sy, r, paint);
        paint.setColor(Color.argb(140, 0, 0, 0));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        c.drawCircle(sx, sy, r, paint);
    }

    private void applyVisionFilter(Bitmap bmp, int w, int h) {
        switch (vision) {
            case BLIND_TOTAL:
                applyBlindTotal(w, h);
                break;
            case LOW_VISION_SEVERE:
                applyBlur(20f);
                applyContrastDrop();
                break;
            case BLURRY:
                applyBlur(8f);
                break;
            case CENTRAL_ONLY:
                applyKeyhole(w, h, Math.min(w, h) * 0.18f, false);
                break;
            case PERIPHERAL_ONLY:
                applyKeyhole(w, h, Math.min(w, h) * 0.30f, true);
                break;
            case SIGHTED:
            default:
                // no filter
                break;
        }
    }

    private void applyBlindTotal(int w, int h) {
        Paint p = new Paint();
        p.setColor(Color.argb(252, 0, 0, 0));
        offCanvas.drawRect(0, 0, w, h, p);
        // very faint compass dot so a sighted reviewer / TalkBack focus
        // sees *something* in the middle
        p.setColor(Color.argb(35, 255, 255, 255));
        offCanvas.drawCircle(w / 2f, h / 2f, 6f, p);
    }

    private void applyBlur(float radius) {
        // Cheap downscale-then-upscale blur — Android's BlurMaskFilter is
        // not exposed for bitmaps, and RenderEffect needs API 31+.
        int w = offscreen.getWidth();
        int h = offscreen.getHeight();
        int dw = Math.max(8, (int) (w / radius));
        int dh = Math.max(8, (int) (h / radius));
        Bitmap small = Bitmap.createScaledBitmap(offscreen, dw, dh, true);
        Bitmap blurred = Bitmap.createScaledBitmap(small, w, h, true);
        small.recycle();
        offCanvas.drawBitmap(blurred, 0, 0, null);
        blurred.recycle();
    }

    private void applyContrastDrop() {
        Paint p = new Paint();
        p.setColor(Color.argb(110, 80, 80, 90));
        offCanvas.drawRect(0, 0, offscreen.getWidth(), offscreen.getHeight(), p);
    }

    private void applyKeyhole(int w, int h, float openingRadius, boolean inverted) {
        int cx = w / 2;
        int cy = h / 2;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!inverted) {
            // Central only: dark outside, sharp center.
            float[] stops = new float[]{0f, 0.6f, 1f};
            int[] colors = new int[]{
                    Color.argb(0,   0, 0, 0),
                    Color.argb(180, 0, 0, 0),
                    Color.argb(255, 0, 0, 0)
            };
            RadialGradient g = new RadialGradient(cx, cy,
                    Math.max(openingRadius, 1f) * 3.5f, colors, stops,
                    Shader.TileMode.CLAMP);
            p.setShader(g);
            offCanvas.drawRect(0, 0, w, h, p);
        } else {
            // Peripheral only: dark center, sharp around the edges.
            float[] stops = new float[]{0f, 0.5f, 1f};
            int[] colors = new int[]{
                    Color.argb(255, 0, 0, 0),
                    Color.argb(150, 0, 0, 0),
                    Color.argb(0,   0, 0, 0)
            };
            RadialGradient g = new RadialGradient(cx, cy,
                    openingRadius * 1.8f, colors, stops,
                    Shader.TileMode.CLAMP);
            p.setShader(g);
            offCanvas.drawRect(0, 0, w, h, p);
        }
    }
}
