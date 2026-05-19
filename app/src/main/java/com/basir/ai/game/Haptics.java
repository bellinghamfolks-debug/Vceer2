package com.basir.ai.game;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Centralised vibration patterns used by the game. */
public final class Haptics {

    private final Vibrator vib;

    public Haptics(Context ctx) {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) ctx
                    .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = vm != null ? vm.getDefaultVibrator()
                           : (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        }
        this.vib = v;
    }

    public boolean available() {
        return vib != null && vib.hasVibrator();
    }

    /** Single short pulse: a feature is close by. */
    public void pulseNear() {
        playPattern(new long[]{0, 35}, new int[]{0, 80});
    }

    /** Two short pulses: a passable obstacle (door, person). */
    public void pulseObstacle() {
        playPattern(new long[]{0, 30, 60, 30}, new int[]{0, 110, 0, 110});
    }

    /** Long buzz: real danger — stairs going down, road, edge. */
    public void pulseDanger() {
        playPattern(new long[]{0, 200, 80, 200}, new int[]{0, 255, 0, 255});
    }

    /** Climbing pattern for stairs. */
    public void pulseStairs() {
        playPattern(new long[]{0, 40, 60, 60, 60, 80},
                new int[]{0, 100, 0, 140, 0, 180});
    }

    /** Soft tap acknowledging a step or interaction. */
    public void tap() {
        playPattern(new long[]{0, 18}, new int[]{0, 60});
    }

    /** Reward feedback for purchases / dialogue successes. */
    public void reward() {
        playPattern(new long[]{0, 40, 50, 90}, new int[]{0, 150, 0, 220});
    }

    private void playPattern(long[] timings, int[] amps) {
        if (vib == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
            } else {
                vib.vibrate(timings, -1);
            }
        } catch (Throwable ignored) {}
    }
}
