# Basir AI – بصير AI

> عينك الذكية في كل مكان · Your smart eye, everywhere

Basir AI is an Android assistant for blind and low-vision users. It uses
Google Gemini for scene description, document reading, translation, and
voice conversation, with the whole UI designed around TalkBack and
high-contrast text.

The app talks to Gemini in one of two modes — the user picks at first launch:

1. **Direct mode** — the user pastes their own Gemini API key into Settings.
   The key is stored locally on the device only and never leaves it except
   to call `generativelanguage.googleapis.com`. No developer-side server.
2. **Proxy mode** — the user points the app at an HTTPS proxy they (or their
   organization) run, which holds the key server-side. Useful for managed
   deployments where end-users shouldn't see the key.

## Features

- 📷 **Scene description** — image → Gemini → alt text, obstacles, risks,
  signs, faces, text-in-image
- 📄 **Document conversion** — PDF, DOCX, PPTX, images of pages → a
  navigable Word file with real headings, lists, tables, and image
  descriptions. Uses Gemini Files API + batched generateContent so files
  up to ~1000 pages work
- 💬 **Ask Basir** — open-ended questions, screen-reader friendly answers
- 🌐 **Smart translation** — contextual Arabic ↔ English with tone notes
- 🚶 **Walking assistant** — short voice + vibration alerts. Not a cane
  replacement
- 🆘 **Emergency mode** — confirmation-gated SMS with approximate location
  to a contact you chose in advance
- 🧠 **Personal memory** — save people, products, places (local, on-device)
- 🗂 **Archive + activity log** — everything stored on the device,
  deletable from settings
- ⚙️ **Settings** — language, TTS rate, font size, vibration, privacy mode,
  auto-save, per-task quality (Fast · Balanced · Best mapped to Flash-Lite ·
  Flash · Pro)
- 🎤 **Voice commands** — navigate the app hands-free in Arabic or English

## Accessibility (TalkBack)

Basir is a tool for blind users, so its own accessibility is treated as
mission-critical, not as a finishing touch. As of v2.2.4:

- Every screen title is an accessibility heading with focus requested on
  mount, so TalkBack lands on the title after navigation
- Every home-tab card is an accessibility heading, enabling card-by-card
  swipe navigation
- Switches announce on/off state in Arabic and English
- Segmented pickers (quality, output mode) announce "selected" on change
- Vibration cues on image capture, conversion success, and conversion
  failure for users who can't see visual feedback
- All TTS / speech-recognition locales follow the UI language
- All vibration is gated by the user's "Vibration" preference

## Privacy

- `android:allowBackup="false"` — Google Drive auto-backup never copies
  the Gemini API key, personal memory, or conversation log
- `android:usesCleartextTraffic="false"` in release builds — the app
  refuses to send the API key or document content over plaintext HTTP
- No analytics, no ad SDKs, no advertising identifiers
- No GPS unless the user explicitly taps "share my location" in Emergency
- Files uploaded for the "Ask about document" feature use Gemini Files
  API and are deleted by Google after 48 hours

See `app/src/main/java/com/basir/ai/MainActivity.java#showPrivacyScreen`
for the full Arabic/English privacy text shown inside the app.

## How to build

A GitHub Actions workflow at `.github/workflows/build-apk.yml` builds a
debug APK on every push to `main`, `master`, `claude/**`, or
`mokafeefah-**`. To get an APK:

1. Push to GitHub
2. Open the **Actions** tab → **Build Basir AI APK** workflow
3. Wait ~5 minutes
4. Open the latest run → **Artifacts** → download `BasirAI-debug-apk`
5. Install the APK on an Android 6+ device

The workflow regenerates `gradle/wrapper/gradle-wrapper.jar` from a fresh
Gradle 8.5 distribution before building, so the jar doesn't need to be
checked in.

## Stack

| Layer    | Technology                                           |
|----------|------------------------------------------------------|
| App      | Android native, Java 17, framework views only        |
| Build    | AGP 8.2.2 · Gradle 8.5 · compileSdk 34 · minSdk 23   |
| CI       | GitHub Actions (Ubuntu, Temurin JDK 17)              |
| AI       | Google Gemini 2.5 (Flash-Lite · Flash · Pro)         |
| Storage  | SQLite via the framework `SQLiteOpenHelper`          |

## Versioning

| Version  | Highlights                                              |
|----------|---------------------------------------------------------|
| v2.2.5   | Stability and security pass: `allowBackup=false`, HTTPS-only release, leftover OCR strings removed, README rewritten, user-friendly error mapping |
| v2.2.4   | Accessibility pass: TalkBack focus on screen change, segmented-picker "selected" announcements, haptic cues for image capture and conversion result |
| v2.2.3   | Terms of Service and Privacy Policy rewritten to version 2 (26 + 24 sections) |
| v2.2.2   | Back button moved from the bottom of every screen to the top |
| v2.2.1   | Home redesigned with hero panel + bottom navigation     |
| v2.2     | OCR-on-touch removed, real table rendering in DOCX, inline Terms/Privacy |
| v2.0     | PDF batching via Files API, "Ask about document" follow-up Q&A |

## Contact

- 📧 ubdallahalrashdee@gmail.com
- 👤 عبدالله الراشدي · Abdullah Al-Rashidi

## License

All rights reserved. Contact the developer for licensing inquiries.
