# Basir for iOS — Starter Project

**Version**: 0.1 (port-in-progress)
**Source app**: Basir Android v2.9.0 (commit 1606771)
**Target**: iOS 17.0+
**Language**: Swift 5.9+
**UI framework**: SwiftUI

This is the **starter scaffold** for porting Basir from Android to iOS.
It is NOT a complete, App-Store-ready app. It is the architectural
foundation that an iOS developer can extend over the next 4-7 months
to reach feature parity with the Android version.

## What's in this package

```
Basir/
├── BasirApp.swift                 @main entry point
├── ContentView.swift              root TabView (4 tabs)
├── Info.plist                     iOS manifest with usage descriptions
├── Localizable/
│   ├── ar.lproj/Localizable.strings   Arabic UI text
│   └── en.lproj/Localizable.strings   English UI text
├── Networking/
│   ├── GeminiClient.swift         async/await REST client for Gemini API
│   ├── GeminiPrompts.swift        prompts including math (Arabic + English)
│   └── UserFriendlyErrorMapper.swift   port of the Android error mapper
├── Storage/
│   ├── KeychainStore.swift        AES-encrypted Gemini key via Keychain
│   └── BasirSettings.swift        @AppStorage wrapper
├── Views/
│   ├── HomeView.swift             Talk tab cards
│   ├── VisionView.swift           Vision tab cards
│   ├── DocumentsView.swift        Documents tab cards
│   ├── MoreView.swift             More tab (Settings + About + Legal)
│   ├── AskBasirView.swift         conversational Q&A
│   ├── TranslateView.swift        20-language picker + translation
│   ├── DescribeImageView.swift    camera/gallery + scene description
│   ├── MathExtractView.swift      ★ math extraction (v2.9 parity)
│   ├── ResultView.swift           shared result screen
│   ├── SettingsView.swift         all preferences in one screen
│   └── LegalScreens.swift         Terms + Privacy v2 (Arabic + English)
└── Helpers/
    ├── L10n.swift                 t(arabic, english) equivalent
    └── AccessibilityModifiers.swift   VoiceOver helpers
```

## How to build

1. **Install Xcode 15+** on a Mac.
2. **Create a new iOS App project** in Xcode:
   - Product Name: `Basir`
   - Interface: SwiftUI
   - Language: Swift
   - Minimum Deployments: iOS 17.0
3. **Drag the files** in this folder into your Xcode project, organized
   into matching groups.
4. **Replace the default Info.plist** with the one provided (it has the
   privacy usage descriptions iOS requires before runtime permissions).
5. **Set up Localizations**:
   - Project → Info → Localizations → add Arabic.
   - Add `ar.lproj/Localizable.strings` to the project.
6. **Apple Developer account** ($99/year) is required to:
   - Run on a real device (free tier only allows simulator)
   - Submit to App Store

## What works in this scaffold

- ✅ Gemini API integration (direct mode with user-provided key)
- ✅ Text Q&A flow (Ask Basir)
- ✅ Text translation (20 languages)
- ✅ Math extraction from images (uses MathExtractor prompt with full
  Arabic + English few-shot examples)
- ✅ Image description (camera + gallery via PHPhotoPicker)
- ✅ Result screen with VoiceOver-friendly read-aloud
- ✅ Bilingual UI (Arabic + English) with proper RTL handling
- ✅ Settings (language, voice rate, font scale, Gemini API key)
- ✅ Terms of Service + Privacy Policy (v2 — full Arabic + English text)
- ✅ Encrypted API key via Keychain Services (kSecAttrAccessible)
- ✅ User-friendly error mapping (15 patterns ported from Android)

## What's deliberately deferred

These features need additional iOS-specific work because the iOS
platform does not allow the same patterns as Android:

- ⏸ **PDF / DOCX / PPTX conversion**: requires
  - Local PDF parsing via PDFKit (different API from Android PdfRenderer)
  - Rewriting DocxBuilder for Swift (the Java version uses ZipOutputStream;
    Swift can do this via Foundation's Compression framework but needs a port)
  - Background processing strategy — iOS does NOT allow indefinite
    foreground services. Conversion of long PDFs would need to either
    keep the user on the screen, or use URLSession's background
    download/upload APIs (only suitable for upload steps, not full
    processing).
- ⏸ **Document translation**: depends on conversion infrastructure above.
- ⏸ **Voice conversation mode (continuous)**: needs SFSpeechRecognizer
  + AVSpeechSynthesizer coordination similar to the Android
  TtsController/VoiceController pair.
- ⏸ **Walking mode**: needs AVCaptureSession + CoreLocation. Doable
  but iOS asks for stricter location permissions.
- ⏸ **Emergency mode**: SMS on iOS requires MessageUI's
  MFMessageComposeViewController (user must tap Send manually — no
  silent SMS like Android).
- ⏸ **Personal memory / archive**: needs Core Data schema port from
  the Android BasirDb.
- ⏸ **Share-into-Basir from other apps**: requires a Share Extension
  (a separate Xcode target with its own bundle and limited
  capabilities).
- ⏸ **Document Q&A**: depends on conversion infrastructure.

## Realistic completion estimate

Starting from this scaffold, an experienced iOS developer needs:

| Phase | Effort |
|---|---|
| Scaffold integration + first device build | 1 week |
| Camera + image features fully working | 2 weeks |
| Voice features (TTS + recognition + conversation) | 3 weeks |
| Document parsing (PDF via PDFKit) | 3 weeks |
| DocxBuilder port to Swift | 2 weeks |
| Background processing strategy + user education | 2 weeks |
| Share Extension target | 1 week |
| Core Data schema + archive + memory | 2 weeks |
| Emergency mode | 1 week |
| Walking mode | 2 weeks |
| Accessibility polish + VoiceOver pass | 3 weeks |
| Testing across iPhone models + iPad | 3 weeks |
| App Store review prep + submission | 2 weeks |
| **Total to feature parity with Android v2.9** | **~6 months** |

## Why iOS will FEEL different even at parity

- iOS has stricter privacy permissions. Every camera/mic/location access
  shows a system prompt the first time.
- iOS does not allow continuous background processing the way Android
  does. The conversion screen has to stay open, or use background
  upload tasks for the upload step only.
- iOS App Store review is manual; rejections happen. Expect 1-2
  rejection cycles before first approval.
- iOS users expect very polished UI. SwiftUI helps, but accessibility
  testing with VoiceOver (the iOS equivalent of TalkBack) is mandatory.

## Why we kept the same architecture decisions

- **Pure Apple frameworks, no third-party libraries**. Same approach
  as the Android version. APK was 160KB; iOS .ipa should be ~3-5MB
  (iOS bundles are larger due to architecture slicing and asset catalogs,
  but no external deps keeps it minimal).
- **Direct Gemini integration with the user's own API key** by default.
  Proxy mode can be added later by extending the AiProvider protocol.
- **Bilingual at the data layer**, not via the system's language picker.
  Same `t("ar", "en")` pattern translated as `L10n.t(arabic:english:)`
  in Swift.

## Open questions before continuing the port

1. Do you have a Mac with Xcode for development?
2. Do you have / are you willing to get an Apple Developer account?
3. Do you want to publish on the App Store, or distribute via
   TestFlight / Enterprise certificate?
4. Is the user base primarily Saudi Arabia / GCC? Affects App Store
   Connect setup, pricing, screenshots, and language defaults.

Once those are decided we can prioritize which deferred feature ships
first (Mac+Xcode → conversion next, since that's the highest-value
deferred item).
