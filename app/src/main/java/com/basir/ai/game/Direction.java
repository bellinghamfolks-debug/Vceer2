package com.basir.ai.game;

/** The four cardinal facings a player can take inside a scene's grid. */
public enum Direction {
    NORTH, EAST, SOUTH, WEST;

    public Direction turnLeft() {
        switch (this) {
            case NORTH: return WEST;
            case WEST:  return SOUTH;
            case SOUTH: return EAST;
            case EAST:  return NORTH;
        }
        return this;
    }

    public Direction turnRight() {
        switch (this) {
            case NORTH: return EAST;
            case EAST:  return SOUTH;
            case SOUTH: return WEST;
            case WEST:  return NORTH;
        }
        return this;
    }

    public int dx() {
        switch (this) {
            case EAST:  return 1;
            case WEST:  return -1;
            default:    return 0;
        }
    }

    public int dy() {
        switch (this) {
            case NORTH: return -1;
            case SOUTH: return 1;
            default:    return 0;
        }
    }

    public String arabicName() {
        switch (this) {
            case NORTH: return "شمالًا";
            case EAST:  return "شرقًا";
            case SOUTH: return "جنوبًا";
            case WEST:  return "غربًا";
        }
        return name();
    }

    /** Spoken short label (e.g. "أمامك") given the player's current facing
     *  and the relative offset (dx, dy) of an object. Returns one of
     *  أمامك / خلفك / يمينك / يسارك or null if the object is on the player. */
    public String relativeLabelArabic(int dx, int dy) {
        // Rotate the world so the player is facing NORTH.
        int rx = dx, ry = dy;
        switch (this) {
            case NORTH: break;
            case EAST:  { int t = rx; rx = ry;  ry = -t; break; }
            case SOUTH: { rx = -rx; ry = -ry;            break; }
            case WEST:  { int t = rx; rx = -ry; ry = t;  break; }
        }
        if (rx == 0 && ry == 0) return null;
        if (Math.abs(ry) >= Math.abs(rx)) {
            return ry < 0 ? "أمامك" : "خلفك";
        }
        return rx > 0 ? "يمينك" : "يسارك";
    }
}
