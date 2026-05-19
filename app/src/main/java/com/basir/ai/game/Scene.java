package com.basir.ai.game;

import java.util.ArrayList;
import java.util.List;

/**
 * A grid-based scene. Each scene is a small room or street block that the
 * player walks around in step-by-step. Grids are authored as multi-line
 * strings in {@link SceneLibrary} so adding a new location is a matter of
 * drawing ASCII art and listing the objects on top.
 */
public class Scene {
    public final String id;
    public final String arabicName;
    public final String ambientDescription;
    public final int width;
    public final int height;
    public final Tile[][] tiles; // [y][x]
    public final List<SceneObject> objects = new ArrayList<>();
    public final int startX;
    public final int startY;
    public final Direction startFacing;
    public final int ambientFreq; // background hum (0 = silent)

    public Scene(String id, String arabicName, String ambientDescription,
                 String[] layout,
                 int startX, int startY, Direction startFacing,
                 int ambientFreq) {
        this.id = id;
        this.arabicName = arabicName;
        this.ambientDescription = ambientDescription;
        this.height = layout.length;
        this.width = layout[0].length();
        this.tiles = new Tile[height][width];
        for (int y = 0; y < height; y++) {
            String row = layout[y];
            for (int x = 0; x < width; x++) {
                char c = x < row.length() ? row.charAt(x) : ' ';
                tiles[y][x] = Tile.fromChar(c);
            }
        }
        this.startX = startX;
        this.startY = startY;
        this.startFacing = startFacing;
        this.ambientFreq = ambientFreq;
    }

    public Tile tileAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return Tile.WALL;
        return tiles[y][x];
    }

    public SceneObject objectAt(int x, int y) {
        for (SceneObject o : objects) {
            if (o.x == x && o.y == y) return o;
        }
        return null;
    }

    /** Returns true if a player (1x1) can stand on the given cell. */
    public boolean walkable(int x, int y) {
        Tile t = tileAt(x, y);
        if (t.blocks) return false;
        SceneObject o = objectAt(x, y);
        return o == null || !o.blocks;
    }
}
