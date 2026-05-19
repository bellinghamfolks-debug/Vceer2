package com.basir.ai.game;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

/**
 * Lightweight stereo "spatial" audio. Generates a short sine-wave PCM buffer
 * on demand and pans it left/right by attenuating the opposite channel. No
 * audio assets are shipped — the tones are synthesised at runtime, so the
 * APK stays small and the game stays portable.
 *
 * The mapping is intentionally crude (volume + pan only); for a player who
 * cannot see, the *combination* of TTS descriptions and these cues is what
 * conveys spatial information, not the audio realism alone.
 */
public class SpatialAudio {

    private static final int SAMPLE_RATE = 22050;

    public void playPanned(int freqHz, float pan, float volume, int durationMs) {
        if (freqHz <= 0 || durationMs <= 0) return;
        if (pan < -1f) pan = -1f;
        if (pan >  1f) pan =  1f;
        if (volume < 0f) volume = 0f;
        if (volume > 1f) volume = 1f;

        int frames = SAMPLE_RATE * durationMs / 1000;
        // interleaved L,R 16-bit
        short[] data = new short[frames * 2];

        // Equal-power-ish pan: -1 = full left, +1 = full right
        float left  = volume * (float) Math.cos((pan + 1f) * Math.PI / 4f);
        float right = volume * (float) Math.sin((pan + 1f) * Math.PI / 4f);

        // Fade in / out to avoid clicks (5 ms each side)
        int fade = Math.min(frames / 4, SAMPLE_RATE / 200);
        double twoPiF = 2.0 * Math.PI * freqHz / SAMPLE_RATE;
        for (int i = 0; i < frames; i++) {
            double s = Math.sin(twoPiF * i);
            float env = 1f;
            if (i < fade) env = i / (float) fade;
            else if (i > frames - fade) env = (frames - i) / (float) fade;
            short sample = (short) (s * 32000 * env);
            data[2 * i]     = (short) (sample * left);
            data[2 * i + 1] = (short) (sample * right);
        }

        int bufBytes = data.length * 2; // 16-bit samples
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) return;
        // MODE_STATIC requires the buffer to fit the data exactly. If our
        // tone is shorter than the platform minimum, pad with silence.
        int bufSize = Math.max(minBuf, bufBytes);
        if (bufSize > bufBytes) {
            short[] padded = new short[bufSize / 2];
            System.arraycopy(data, 0, padded, 0, data.length);
            data = padded;
        }

        AudioTrack track;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(SAMPLE_RATE)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build())
                        .setBufferSizeInBytes(bufSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
            } else {
                track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT, bufSize,
                        AudioTrack.MODE_STATIC);
            }
        } catch (Throwable t) {
            return;
        }
        try {
            track.write(data, 0, data.length);
            track.play();
        } catch (Throwable ignored) {}
        // Release after natural duration.
        final AudioTrack t = track;
        new Thread(() -> {
            try { Thread.sleep(durationMs + 100); } catch (InterruptedException ignored) {}
            try { t.stop(); } catch (Throwable ignored) {}
            try { t.release(); } catch (Throwable ignored) {}
        }, "BlindLifeAudio").start();
    }
}
