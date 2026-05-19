package com.basir.ai.game;

/** A single cell type inside a Scene grid. Kept deliberately simple so the
 *  whole world can be authored as multi-line strings in {@link SceneLibrary}. */
public enum Tile {
    FLOOR(' ', false),
    WALL('#', true),
    DOOR('D', false),
    STAIRS('S', false),
    EXIT('X', false),
    CARPET('.', false),
    GRASS(',', false),
    ROAD('=', false),
    CROSSWALK('+', false),
    PUDDLE('~', false);

    public final char glyph;
    public final boolean blocks;

    Tile(char g, boolean blocks) { this.glyph = g; this.blocks = blocks; }

    public static Tile fromChar(char c) {
        for (Tile t : values()) if (t.glyph == c) return t;
        return FLOOR;
    }

    public String arabicSurface() {
        switch (this) {
            case FLOOR:     return "أرضية ناعمة";
            case WALL:      return "جدار";
            case DOOR:      return "باب";
            case STAIRS:    return "درج";
            case EXIT:      return "خروج";
            case CARPET:    return "سجاد";
            case GRASS:     return "عشب";
            case ROAD:      return "طريق سيارات";
            case CROSSWALK: return "ممر مشاة";
            case PUDDLE:    return "بركة ماء";
        }
        return "";
    }
}
