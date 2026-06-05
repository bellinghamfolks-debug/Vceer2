# Basir for iOS | بصير لنظام iOS

Current app version: **3.2.0**  
Build number: **54**  
Target: **iOS 17.0 or later**  
Language: **Swift 5.9+**  
UI: **SwiftUI**

## Current feature set

### Talk

- Ask Basir by typing or one-shot voice dictation.
- Translate text between supported languages.

### Vision

- Detailed image or scene description.
- Focused image alt text.
- Screenshot reading.
- Currency and receipt assistance.
- Math extraction with spoken notation and LaTeX for review.
- One-shot walking description from a captured image.

### Documents

- Select a PDF of up to 60 pages, or a TXT or CSV file.
- Extract readable text locally.
- Send the extracted text to Gemini for structure or translation.
- Display the result as copyable and shareable text.

The current iOS implementation does not create a Word file and does not keep an uploaded document for later follow-up questions.

### More

- Continuous voice conversation.
- Help-message preparation with optional approximate location.
- Local saved items for people, products, medications, and places.
- Local results archive and optional activity history.
- Settings, About, Terms and Conditions, and Privacy Policy.

## Connection behavior

The current iOS implementation uses direct HTTPS requests to Google Gemini with a user-provided API key. Proxy mode is not implemented in the active iOS provider.

The API key is stored in the standard iOS Keychain. Content selected for an AI task is sent to Google Gemini. Review the in-app Privacy Policy before using personal, confidential, or sensitive content.

## Safety behavior

- AI output may be incorrect, incomplete, or delayed.
- Walking mode describes one image and is not an independent mobility tool.
- Help messages are never sent automatically. The system message composer opens so the user can review the recipient, text, and location and then tap Send.
- Basir does not contact official emergency services.

## Build with XcodeGen

The repository includes `ios/project.yml`.

1. Install Xcode 15 or later.
2. Install XcodeGen.
3. From the `ios` folder, run `xcodegen generate`.
4. Open the generated Xcode project.
5. Set a valid development team and unique bundle identifiers for the app and share extension.
6. Test permissions, VoiceOver, and real-device camera, microphone, speech recognition, location, and message-composer behavior.

## Privacy and App Store preparation

Before submission:

- Publish a public privacy-policy URL matching the in-app policy.
- Complete App Privacy details for the app and all third-party processing.
- Keep the privacy-policy URL and support contact functional.
- Review the app’s Gemini use against Google’s current terms and licensing restrictions.
- Obtain final legal review in Saudi Arabia.

## Source map

- `Basir/ContentView.swift`: root tabs.
- `Basir/Views`: user-facing screens.
- `Basir/Networking/GeminiClient.swift`: direct Gemini client.
- `Basir/Storage/KeychainStore.swift`: API-key storage.
- `Basir/Memory/ArchiveStore.swift`: local saved data and history.
- `Basir/Views/LegalScreens.swift`: in-app legal documents.
- `ShareExtension`: share-extension target.
