package com.basir.ai.game;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Persistent state for "حياة كفيف". Saved in a private SharedPreferences
 * file (no PII, no analytics, just the player's progress). Save/Load are
 * idempotent and tolerant of missing keys, so adding new stats later
 * doesn't break old saves.
 */
public class GameState {
    private static final String PREFS = "blind_life_save_v1";

    public VisionMode vision = VisionMode.BLIND_TOTAL;
    public String currentScene = "bedroom";
    public int playerX = 2;
    public int playerY = 3;
    public Direction facing = Direction.EAST;

    public int day = 1;
    public int energy = 100;
    public int money = 100;

    public int mobility = 10;
    public int communication = 10;
    public int technology = 10;
    public int confidence = 10;

    public int friendship = 0;
    public int reputation = 0;

    public final Set<String> devices = new HashSet<>();
    public final Set<String> completedEvents = new HashSet<>();

    public boolean firstRun = true;

    public void clamp() {
        energy       = clip(energy, 0, 100);
        mobility     = clip(mobility, 0, 100);
        communication= clip(communication, 0, 100);
        technology   = clip(technology, 0, 100);
        confidence   = clip(confidence, 0, 100);
        friendship   = clip(friendship, 0, 100);
        reputation   = clip(reputation, -50, 100);
        if (money < 0) money = 0;
    }

    private static int clip(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public boolean hasDevice(String id) { return devices.contains(id); }

    public void save(Context ctx) {
        clamp();
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        e.putString("vision", vision.name());
        e.putString("scene", currentScene);
        e.putInt("x", playerX);
        e.putInt("y", playerY);
        e.putString("facing", facing.name());
        e.putInt("day", day);
        e.putInt("energy", energy);
        e.putInt("money", money);
        e.putInt("mobility", mobility);
        e.putInt("communication", communication);
        e.putInt("technology", technology);
        e.putInt("confidence", confidence);
        e.putInt("friendship", friendship);
        e.putInt("reputation", reputation);
        e.putStringSet("devices", devices);
        e.putStringSet("events", completedEvents);
        e.putBoolean("firstRun", firstRun);
        e.apply();
    }

    public static GameState load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        GameState g = new GameState();
        try { g.vision = VisionMode.valueOf(p.getString("vision", g.vision.name())); }
        catch (Exception ignored) {}
        g.currentScene = p.getString("scene", g.currentScene);
        g.playerX = p.getInt("x", g.playerX);
        g.playerY = p.getInt("y", g.playerY);
        try { g.facing = Direction.valueOf(p.getString("facing", g.facing.name())); }
        catch (Exception ignored) {}
        g.day = p.getInt("day", g.day);
        g.energy = p.getInt("energy", g.energy);
        g.money = p.getInt("money", g.money);
        g.mobility = p.getInt("mobility", g.mobility);
        g.communication = p.getInt("communication", g.communication);
        g.technology = p.getInt("technology", g.technology);
        g.confidence = p.getInt("confidence", g.confidence);
        g.friendship = p.getInt("friendship", g.friendship);
        g.reputation = p.getInt("reputation", g.reputation);
        Set<String> d = p.getStringSet("devices", null);
        if (d != null) g.devices.addAll(d);
        Set<String> ev = p.getStringSet("events", null);
        if (ev != null) g.completedEvents.addAll(ev);
        g.firstRun = p.getBoolean("firstRun", true);
        return g;
    }

    public static boolean hasSave(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.contains("scene");
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }
}
