package com.basir.ai;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * v2.3 — TextToSpeech lifecycle, configuration, and tagged-utterance
 * routing extracted from MainActivity.
 *
 * Continuous voice mode and walking mode both rely on knowing when a TTS
 * utterance finishes, so they can launch the next recognizer turn or the
 * next camera capture. The controller hides the {@link UtteranceProgressListener}
 * detail and just calls {@link Host#onUtteranceDoneOrError(String)} with
 * the utterance id whenever any spoken line ends or errors out.
 */
public final class TtsController implements TextToSpeech.OnInitListener {

    /** Tag prefix for continuous voice conversation utterances. The host
     *  uses it to recognise that the next listen step should fire. */
    public static final String ID_PREFIX_CONVO = "convo-";

    /** Tag prefix for walking-mode utterances. The host uses it to know
     *  the next camera capture should fire. */
    public static final String ID_PREFIX_WALK = "walk-";

    /** Default utterance id for fire-and-forget speak() calls — no
     *  follow-up action is wired up for this id. */
    public static final String ID_PLAIN = "basir";

    public interface Host {
        /** {@code true} if the current UI language is English, {@code false}
         *  if Arabic. Used to pick the TTS Locale. */
        boolean isEnglish();
        /** {@code true} if the user has speech output enabled in Settings.
         *  When {@code false} every speak() call is a no-op. */
        boolean isSpeechEnabled();
        /** TTS speech rate from preferences (default ~0.95). */
        float getTtsRate();
        /** Called on the TTS engine's worker thread when init succeeds.
         *  Hosts should hop to the UI thread before touching views. */
        void onTtsReady();
        /** Called when an utterance finishes (onDone) or errors (onError).
         *  The id is what was passed to speakWithId(). The framework calls
         *  this on a worker thread, NOT the UI thread. */
        void onUtteranceDoneOrError(String utteranceId);
    }

    private final Host host;
    private TextToSpeech tts;
    private volatile boolean ready = false;

    public TtsController(Context context, Host host) {
        this.host = host;
        this.tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        ready = true;
        applyConfig();
        try {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onError(String utteranceId) {
                    if (utteranceId == null) return;
                    host.onUtteranceDoneOrError(utteranceId);
                }
                @Override public void onDone(String utteranceId) {
                    if (utteranceId == null) return;
                    host.onUtteranceDoneOrError(utteranceId);
                }
            });
        } catch (Throwable ignore) {
            // Older TTS engines may not implement UtteranceProgressListener.
            // The fallback is simply: tagged utterances don't trigger
            // their follow-up step. Continuous mode degrades to one-shot.
        }
        host.onTtsReady();
    }

    /** Push the host's current language + rate into the TTS engine. Call
     *  this whenever either preference changes. */
    public void applyConfig() {
        if (tts == null || !ready) return;
        Locale locale = host.isEnglish() ? Locale.US : new Locale("ar", "SA");
        try { tts.setLanguage(locale); } catch (Throwable ignore) {}
        try { tts.setSpeechRate(host.getTtsRate()); } catch (Throwable ignore) {}
    }

    /** Fire-and-forget plain speech. Replaces the current utterance. */
    public void speak(String text) {
        speakWithId(text, ID_PLAIN);
    }

    /** Speak {@code text} with a tagged utterance id so the host can
     *  recognise the resulting {@link Host#onUtteranceDoneOrError} call. */
    public void speakWithId(String text, String utteranceId) {
        if (!host.isSpeechEnabled() || tts == null || !ready) return;
        if (text == null || text.trim().isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    /** Stop any currently speaking utterance without shutting the engine
     *  down. Safe to call before launching speech recognition so the
     *  recogniser's mic does not pick up the tail of our own speech. */
    public void stop() {
        try { if (tts != null) tts.stop(); } catch (Throwable ignore) {}
    }

    /** Release the TTS engine. Call from {@code Activity.onDestroy}. */
    public void shutdown() {
        if (tts == null) return;
        try { tts.stop(); } catch (Throwable ignore) {}
        try { tts.shutdown(); } catch (Throwable ignore) {}
        tts = null;
        ready = false;
    }

    public boolean isReady() { return ready; }
}
