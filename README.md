# Basir AI | بصير

Basir is a bilingual accessibility assistant for blind and low-vision users. The Android app uses Google Gemini to help with images, documents, translation, questions, voice conversation, and selected mobility-support tasks.

Current Android version: **3.2.0**  
Minimum Android version: **Android 6 (API 23)**

## Android features

- Ask Basir with typed or dictated questions.
- Continuous voice conversation.
- Detailed image and scene description.
- Focused image alt text.
- Screenshot, currency, receipt, invoice, legal-text, medical-text, table, and math assistance.
- One-shot walking description and Android live scene guidance.
- PDF and PowerPoint conversion to a screen-reader-friendly Word document.
- Follow-up questions about the latest converted document.
- Text and document translation.
- Local saved items for people, products, medications, and places.
- Local results archive and optional activity history.
- Help-message preparation with an approximate location when permission is granted. The user reviews and sends the message manually.

AI output can be wrong or delayed. Basir is not a navigation system, medical device, legal adviser, financial adviser, or emergency service.

## Connection modes

### Direct connection

The user enters a Gemini API key. Requests go from the device to Google Gemini over HTTPS. The key and selected content are not routed through a developer-owned server in this mode.

### Manually configured proxy

The user enters an HTTPS proxy URL and, optionally, an app token. Requests and files are sent to that proxy. Its operator can technically access data passing through it, and its own privacy, security, and retention rules apply.

The `server` folder contains a sample proxy. Its temporary conversion uploads are deleted after processing, but this behavior must not be assumed for a different proxy.

## Privacy posture

- No developer account is required inside Basir.
- No ads, advertising identifiers, or analytics SDKs are included in this project.
- Android release traffic is HTTPS-only.
- Android backup is disabled for app data.
- Settings, saved items, archive entries, and activity history are primarily local.
- Activity-history saving and automatic result saving are separate controls.
- Files saved to Downloads remain until the user deletes them.
- Content selected for an AI task is sent to Gemini directly or through the configured proxy.
- Gemini Files API uploads are controlled by Google and are automatically deleted after 48 hours according to Google documentation.

Full legal text is available in the app and in:

- `legal/TERMS_AR.md`
- `legal/TERMS_EN.md`
- `legal/PRIVACY_AR.md`
- `legal/PRIVACY_EN.md`

## Accessibility

The UI is designed for TalkBack and large text. Main screens use clear headings, consolidated focus targets, explicit switch states, spoken progress, and vibration cues controlled by the user’s settings. Critical actions are described by their real outcome, especially help messages and external data transfer.

## Build

The project uses Java 17, Android Gradle Plugin 8.2.2, and Gradle 8.5.

The uploaded source package does not include `gradle/wrapper/gradle-wrapper.jar`. The GitHub Actions workflow regenerates the wrapper JAR from Gradle 8.5 before building.

To build through GitHub Actions:

1. Push the project to GitHub.
2. Open **Actions**.
3. Run **Build Basir AI APK**.
4. Download the generated APK artifact after the workflow completes.

## iOS

The `ios` folder contains a SwiftUI implementation with a different feature set. Read `ios/README.md` before building or presenting feature claims.

## Legal and publishing note

The included terms and privacy policy are a product draft based on the reviewed application behavior. They require final approval from qualified Saudi legal counsel before public release. Store declarations, public privacy-policy URLs, and platform AI policies must also be completed separately.

## Contact

Abdullah Al-Rashidi  
ubdallahalrashdee@gmail.com

All rights reserved.
