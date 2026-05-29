package com.basir.ai;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;

import java.util.ArrayList;

/**
 * v2.3 — speech recognition launch + result extraction extracted from
 * MainActivity. The system speech recogniser is launched as a sub-Activity
 * via {@link Activity#startActivityForResult}, so the host Activity still
 * owns the result-receiving lifecycle and just forwards {@link Intent}s
 * here for parsing.
 */
public final class VoiceController {

    /** Request code shared between {@link #launch(String)} and
     *  {@link Activity#onActivityResult(int, int, Intent)}. The host must
     *  test against this value and route the result to
     *  {@link #extractResult(Intent)}. */
    public static final int REQ_VOICE = 1002;

    public interface Host {
        /** UI language. Used to pick the recogniser locale. */
        boolean isEnglish();
    }

    private final Activity activity;
    private final Host host;

    public VoiceController(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    /**
     * Launch the system speech recogniser. Returns {@code true} if the
     * launch succeeded, {@code false} if the device has no recogniser
     * installed (the host should speak a user-facing error in that case).
     *
     * The recogniser appears as a full-screen Activity, which is the
     * accessibility-friendly choice — TalkBack reads the prompt and the
     * recognised text out of the box.
     */
    public boolean launch(String spokenPrompt) {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                    host.isEnglish() ? "en-US" : "ar-SA");
            if (spokenPrompt != null) {
                i.putExtra(RecognizerIntent.EXTRA_PROMPT, spokenPrompt);
            }
            activity.startActivityForResult(i, REQ_VOICE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Pull the best-confidence transcript out of the recogniser's result
     *  Intent. Returns {@code null} if the user cancelled or the result was
     *  empty. */
    public static String extractResult(Intent data) {
        if (data == null) return null;
        ArrayList<String> res =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (res == null || res.isEmpty()) return null;
        return res.get(0);
    }
}
