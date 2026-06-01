// EmergencyView.swift
// SMS-based emergency mode. iOS does not allow silent / programmatic
// SMS — every message must go through MFMessageComposeViewController,
// which presents the system Messages UI with the body pre-filled and
// requires the user to tap Send themselves. That extra tap is an
// Apple platform constraint, not a Basir design choice.

import SwiftUI
import MessageUI
import CoreLocation

struct EmergencyView: View {
    @EnvironmentObject var settings: BasirSettings
    @StateObject private var location = LocationService.shared
    @StateObject private var tts = SpeechSynthesizer.shared
    @State private var showMessageComposer = false
    @State private var smsBody: String = ""
    @State private var smsRecipients: [String] = []
    @State private var locatorTimer: Timer?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if settings.emergencyContact.isEmpty {
                    Label(L10n.t("لم تُحفظ جهة طوارئ بعد. أضفها من الإعدادات.",
                                  "No emergency contact saved yet. Add one from Settings."),
                           systemImage: "exclamationmark.triangle.fill")
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.yellow.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    Text(L10n.t("جهة الطوارئ المحفوظة: ",
                                 "Saved emergency contact: ") + settings.emergencyContact)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                Button {
                    Task { await sendHelpRequest() }
                } label: {
                    Label(
                        L10n.t("إرسال طلب مساعدة الآن", "Send help request now"),
                        systemImage: "sos.circle.fill"
                    )
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity, minHeight: 64)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .disabled(settings.emergencyContact.isEmpty)

                Button {
                    playLocatorSound()
                } label: {
                    Label(
                        L10n.t("تشغيل صوت لتحديد موقعي",
                                "Play a locator sound"),
                        systemImage: "speaker.wave.3.fill"
                    )
                    .frame(maxWidth: .infinity, minHeight: 56)
                }
                .buttonStyle(.bordered)

                Button {
                    Task { await shareLocationOnly() }
                } label: {
                    Label(
                        L10n.t("مشاركة موقعي الحالي",
                                "Share my current location"),
                        systemImage: "location.fill"
                    )
                    .frame(maxWidth: .infinity, minHeight: 56)
                }
                .buttonStyle(.bordered)
                .disabled(settings.emergencyContact.isEmpty)
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("الطوارئ", "Emergency"))
        .sheet(isPresented: $showMessageComposer) {
            if MFMessageComposeViewController.canSendText() {
                MessageComposer(
                    body: smsBody,
                    recipients: smsRecipients,
                    onResult: { _ in showMessageComposer = false }
                )
            } else {
                // Fallback for simulators / iPads without SMS hardware:
                // open a share sheet instead so the user can pick a
                // messaging app manually.
                ShareSheet(items: [smsBody])
            }
        }
    }

    private func playLocatorSound() {
        // Loud, repeated TTS in the user's language — exact behaviour
        // mirrors the Android Emergency screen "play locator sound" entry.
        let line = L10n.t("أنا هنا وأحتاج إلى مساعدة.",
                           "I am here and need help.")
        for _ in 0..<3 { tts.speak(line, utteranceId: "locator") }
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }

    private func sendHelpRequest() async {
        let coord = await location.fetchOnce()?.coordinate
        let mapsLink = coord.map { LocationService.mapsLink(for: $0) }
        let preface = L10n.t("أحتاج إلى مساعدة. موقعي التقريبي: ",
                              "I need help. My approximate location: ")
        smsBody = preface + (mapsLink ?? L10n.t("(الموقع غير متاح)",
                                                   "(location unavailable)"))
        smsRecipients = [settings.emergencyContact]
        showMessageComposer = true
    }

    private func shareLocationOnly() async {
        let coord = await location.fetchOnce()?.coordinate
        let mapsLink = coord.map { LocationService.mapsLink(for: $0) }
        smsBody = L10n.t("موقعي الحالي: ", "My current location: ")
            + (mapsLink ?? L10n.t("(الموقع غير متاح)",
                                   "(location unavailable)"))
        smsRecipients = [settings.emergencyContact]
        showMessageComposer = true
    }
}

// MARK: - MessageUI bridge

private struct MessageComposer: UIViewControllerRepresentable {
    let body: String
    let recipients: [String]
    let onResult: (MessageComposeResult) -> Void

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let vc = MFMessageComposeViewController()
        vc.messageComposeDelegate = context.coordinator
        vc.body = body
        vc.recipients = recipients
        return vc
    }
    func updateUIViewController(_ uiViewController: MFMessageComposeViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onResult: onResult) }

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let onResult: (MessageComposeResult) -> Void
        init(onResult: @escaping (MessageComposeResult) -> Void) { self.onResult = onResult }
        func messageComposeViewController(_ controller: MFMessageComposeViewController,
                                          didFinishWith result: MessageComposeResult) {
            controller.dismiss(animated: true)
            onResult(result)
        }
    }
}

// Fallback when MessageUI is unavailable (iPad without SMS, simulator).
private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
