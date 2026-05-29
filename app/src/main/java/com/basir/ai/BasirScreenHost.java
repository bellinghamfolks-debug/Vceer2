package com.basir.ai;

import android.content.Intent;
import android.view.View;

/**
 * v2.6 — minimal host contract for extracted screens.
 *
 * The very first slice of the MainActivity decomposition (legal screens —
 * Terms, Privacy, About) introduces this interface. Each extracted screen
 * takes a BasirScreenHost in its constructor and calls back into it for
 * the UI primitives that still live on the Activity (resetScreen,
 * addPrimaryButton, addOutlineButton, addPlainText, addBackButton,
 * speak, ...). MainActivity is the single implementation.
 *
 * The interface stays deliberately small: it only exposes what a screen
 * actually needs. Adding methods is cheap; removing them later is much
 * harder, so we resist the urge to expose "everything on the Activity"
 * here. As more screens are extracted in subsequent slices, new host
 * methods will land here one at a time.
 */
public interface BasirScreenHost {

    /** Bi-language string helper — returns Arabic or English based on
     *  the current language preference. */
    String t(String arabic, String english);

    /** Current APK versionName (e.g. "2.6.0") from PackageInfo. */
    String appVersion();

    /** Wipe and re-mount the content view with a back arrow + title +
     *  optional subtitle. After this call, addPlainText / addPrimaryButton
     *  etc. append into the freshly created scroll root. */
    void resetScreen(String title, String subtitle);

    /** Append a paragraph of plain text to the current screen. */
    void addPlainText(String text);

    /** Append a filled primary call-to-action button. */
    void addPrimaryButton(String label, View.OnClickListener listener);

    /** Append a secondary outline button. */
    void addOutlineButton(String label, View.OnClickListener listener);

    /** v2.2.2 made this a no-op (the back arrow now lives at the top of
     *  every screen via resetScreen). Kept on the interface so the legacy
     *  pattern compiles. */
    void addBackButton();

    /** Speak through the TTS controller, gated by the speech-enabled
     *  preference. No-op when speech is off. */
    void speak(String text);

    /** Passthrough to Activity.startActivity. Lets screens fire share
     *  / mailto / view intents without holding their own Activity ref. */
    void launchIntent(Intent intent);

    /** Developer contact email shown in the About screen and in
     *  the share-the-app text. */
    String contactEmail();
}
