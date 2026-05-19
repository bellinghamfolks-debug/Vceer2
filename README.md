# Basir AI – بصير AI

> عينك الذكية في كل مكان · Your smart eye, everywhere

Basir AI is a comprehensive assistant for blind and low-vision users.
It ships in two flavors:

- **Android app** — native Java app (this repo's `app/`).
- **Web app / iPhone PWA** — installable progressive web app (this repo's `web/`),
  served by the same Node.js proxy in `server/`.

Both wire to a secure Gemini proxy server (so no API key ever lives inside the
client).

## Features

- 📷 **Scene description** – send any image to GPT for alt text, obstacles, risks
- 📄 **Document reader** – invoices, contracts, medical notes with structured output
- 💬 **Ask Basir** – open-ended questions to GPT, screen-reader friendly answers
- 🧪 **AI Lab** – alt text, screenshot explanation, study cards, polite replies
- 🌐 **Smart translation** – contextual AR/EN with tone notes
- 🚶 **Walking assistant** – short voice + vibration alerts (does not replace a cane)
- 🆘 **Emergency mode** – one-tap SMS with approximate location
- 🧠 **Personal memory** – save people, products, places (local only, encrypted-safe)
- 🗂 **Archive & activity log** – everything stored locally on the device
- ⚙️ **Extensive settings** – language, TTS rate, font size, privacy, vibration, auto-save
- 🎤 **Voice commands** – navigate the whole app hands-free in Arabic or English

## How it builds

This repo includes a GitHub Actions workflow that builds a debug APK on every
push to `main`. To download:

1. Push to GitHub (use the **Save to Github** button on Emergent).
2. Open the **Actions** tab → **Build Basir AI APK** workflow.
3. Wait ~5 minutes for the build to finish.
4. Open the run → **Artifacts** → download `BasirAI-debug-apk`.
5. Install the APK on your Android phone.

## How the AI works

The app talks to a small Node.js proxy that holds the OpenAI API key.
See [`server/README_SERVER.md`](server/README_SERVER.md) for setup.

Once your proxy is online, open the app → **Settings → GPT Proxy setup**,
paste the URL, save, then tap **Test AI connection**.

## Web app / iPhone PWA

The same `server/` Node.js process now also serves a full progressive web app
from the `web/` folder. It mirrors the Android UI in Arabic and English and
includes every feature that does not require Android-specific APIs.

### Features in the web app

- 💬 Ask Basir (text + voice dictation)
- 📷 Image description — camera capture (`<input capture>`) or gallery upload —
  with three modes: full description, alt text, screenshot reading
- 🚶 Scene description from typed text
- 📄 Document analysis — general, invoice, legal, medical
- 🔁 Convert PDF / PPTX → screen-reader-friendly DOCX (via `/api/convert`)
- 🌐 Smart translation across 15 languages
- 🧪 Advanced tools — study cards, polite reply, table-to-text
- 🆘 Emergency — opens SMS app with location link (`sms:` URI) + locator beep
- 🧠 Local memory — people, products, places (saved in `localStorage`)
- 🗂 Archive & history log (all local)
- ⚙️ Settings — language, TTS rate, font size, vibration, auto-save, proxy URL,
  app token, quality preset
- ♿ Accessibility — RTL/LTR, large touch targets, semantic landmarks, ARIA
  live regions, screen-reader friendly results, dark mode follows system
- 📱 Installable to iPhone home screen via Safari → Share → "Add to Home Screen"

### Run it locally

```bash
cd server
cp .env.example .env          # set GEMINI_API_KEY at minimum
npm install
npm start
# open http://localhost:3000  →  the PWA is served at the root
```

The server serves the SPA from `web/` and the same `/api/basir` + `/api/convert`
endpoints used by the Android app. PNG launcher icons are generated on first
boot into `web/icons/`.

### Deploy from your phone (no computer)

The repo includes a `render.yaml` blueprint. From your phone's browser:

1. Push this repo to GitHub.
2. Sign in to [render.com](https://render.com) on your phone.
3. **New +** → **Blueprint** → pick this repo → **Apply**.
4. In the new service's **Environment**, set `GEMINI_API_KEY` to your Google
   AI Studio key. Save — Render redeploys.
5. Open the public `https://*.onrender.com` URL in Safari on your iPhone.
6. Tap the **Share** icon → **Add to Home Screen** → confirm. The PWA now
   has its own icon on the home screen and runs full-screen.

Other Node.js hosts (Railway, Fly, Glitch, Heroku-likes) work the same way:
just run `node server/index.js` and expose port 3000.

## Stack

| Layer    | Technology                                  |
|----------|---------------------------------------------|
| App      | Android Native (Java 17, framework only)    |
| Web app  | Vanilla JS modules + Service Worker · PWA   |
| Build    | AGP 8.2.2 · Gradle 8.5 · compileSdk 34      |
| CI       | GitHub Actions (Ubuntu, Temurin JDK 17)     |
| Proxy    | Node.js 18+ · Express · serves both API+PWA |
| AI       | Google Gemini (text + vision)               |

## Contact

- 📧 ubdallahalrashdee@gmail.com
- 👤 عبدالله الراشدي · Abdullah Al-Rashidi

## License

All rights reserved. Contact the developer for licensing inquiries.
