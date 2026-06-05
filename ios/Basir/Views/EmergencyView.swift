// EmergencyView.swift
// Prepares a message in the system composer. The user must review and send it.

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

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.t(
                    "تنبيه: لا يرسل بصير أي رسالة تلقائيًا ولا يتصل بخدمات الطوارئ. سيُفتح تطبيق الرسائل لتراجع المستلم والنص والموقع ثم تضغط إرسال بنفسك.",
                    "Notice: Basir does not send any message automatically or contact emergency services. The messaging app opens so you can review the recipient, text, and location and then tap Send yourself."
                ))
                .font(.callout)
                .foregroundStyle(.secondary)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.orange.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 12))

                if settings.emergencyContact.isEmpty {
                    Label(L10n.t("لم تُحفظ جهة مساعدة. أضف رقمًا من الإعدادات أولًا.",
                                  "No help contact is saved. Add a number in Settings first."),
                           systemImage: "exclamationmark.triangle.fill")
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.yellow.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    Text(L10n.t("جهة المساعدة المحفوظة: ",
                                 "Saved help contact: ") + settings.emergencyContact)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }

                Button {
                    Task { await prepareHelpRequest() }
                } label: {
                    Label(L10n.t("فتح رسالة طلب مساعدة", "Open a help message"),
                          systemImage: "sos.circle.fill")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity, minHeight: 64)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .disabled(settings.emergencyContact.isEmpty)
                .accessibilityHint(L10n.t(
                    "يحاول إضافة موقع تقريبي ثم يفتح تطبيق الرسائل. لا يتم الإرسال تلقائيًا.",
                    "Attempts to add an approximate location, then opens Messages. Nothing is sent automatically."
                ))

                Button {
                    playLocatorSound()
                } label: {
                    Label(L10n.t("تشغيل نداء صوتي ثلاث مرات", "Play a locator call three times"),
                          systemImage: "speaker.wave.3.fill")
                        .frame(maxWidth: .infinity, minHeight: 56)
                }
                .buttonStyle(.bordered)

                Button {
                    Task { await prepareLocationMessage() }
                } label: {
                    Label(L10n.t("فتح رسالة بموقعي التقريبي", "Open a message with my approximate location"),
                          systemImage: "location.fill")
                        .frame(maxWidth: .infinity, minHeight: 56)
                }
                .buttonStyle(.bordered)
                .disabled(settings.emergencyContact.isEmpty)
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("طلب المساعدة", "Help request"))
        .sheet(isPresented: $showMessageComposer) {
            if MFMessageComposeViewController.canSendText() {
                MessageComposer(
                    body: smsBody,
                    recipients: smsRecipients,
                    onResult: { _ in showMessageComposer = false }
                )
            } else {
                ShareSheet(items: [smsBody])
            }
        }
    }

    private func playLocatorSound() {
        let line = L10n.t("أنا هنا وأحتاج إلى مساعدة.", "I am here and need help.")
        for _ in 0..<3 { tts.speak(line, utteranceId: "locator") }
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }

    private func prepareHelpRequest() async {
        let coord = await location.fetchOnce()?.coordinate
        let mapsLink = coord.map { LocationService.mapsLink(for: $0) }
        let preface = L10n.t("أحتاج إلى مساعدة. هذا موقعي التقريبي: ",
                              "I need help. This is my approximate location: ")
        smsBody = preface + (mapsLink ?? L10n.t("الموقع غير متاح حاليًا.",
                                                "Location is currently unavailable."))
        smsRecipients = [settings.emergencyContact]
        showMessageComposer = true
    }

    private func prepareLocationMessage() async {
        let coord = await location.fetchOnce()?.coordinate
        let mapsLink = coord.map { LocationService.mapsLink(for: $0) }
        smsBody = L10n.t("هذا موقعي التقريبي: ", "This is my approximate location: ")
            + (mapsLink ?? L10n.t("الموقع غير متاح حاليًا.",
                                   "Location is currently unavailable."))
        smsRecipients = [settings.emergencyContact]
        showMessageComposer = true
    }
}

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

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
