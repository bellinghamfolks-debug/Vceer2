package com.basir.ai.game;

/**
 * Any non-tile thing that lives on top of a Scene's grid: an NPC, an
 * interactable item, a sound source, or an exit-to-another-scene marker.
 *
 *  - {@code interact} is the {@code id} of an action handled by
 *    {@link GameEngine} when the player double-taps while standing on or
 *    adjacent to this object.
 *  - {@code exitsTo} (optional) is the name of another scene; when set, the
 *    engine teleports the player there on interact.
 *  - {@code soundFreq} (optional, &gt;0) makes the object emit a continuous
 *    tone — used for distinctive landmarks the player tracks by ear.
 */
public class SceneObject {
    public final String id;
    public final String label;        // Arabic short label (e.g. "صديق")
    public final String description;  // Longer Arabic description for long-press
    public final int x, y;
    public final boolean blocks;
    public final String interact;
    public final String exitsTo;
    public final int soundFreq;       // 0 = silent

    public SceneObject(String id, String label, String description,
                       int x, int y, boolean blocks,
                       String interact, String exitsTo, int soundFreq) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.x = x;
        this.y = y;
        this.blocks = blocks;
        this.interact = interact;
        this.exitsTo = exitsTo;
        this.soundFreq = soundFreq;
    }

    public static SceneObject npc(String id, String label, String desc,
                                  int x, int y, String dialogueId) {
        return new SceneObject(id, label, desc, x, y, true, dialogueId, null, 0);
    }

    public static SceneObject item(String id, String label, String desc,
                                   int x, int y, String interact) {
        return new SceneObject(id, label, desc, x, y, false, interact, null, 0);
    }

    public static SceneObject exit(String id, String label, int x, int y,
                                   String exitsTo) {
        return new SceneObject(id, label, "ممر إلى " + label, x, y, false,
                "exit", exitsTo, 0);
    }

    public static SceneObject sound(String id, String label, String desc,
                                    int x, int y, int freq) {
        return new SceneObject(id, label, desc, x, y, false, null, null, freq);
    }
}
