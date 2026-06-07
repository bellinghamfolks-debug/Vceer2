// LiveSceneGuidanceView.swift
//
// SwiftUI surface for the iOS port of Android's "Live walking mode".
// One big Start / Stop button, two live regions (status + last spoken
// line), and an optional GPS-hint toggle. Background = the foreground
// scene-guidance loop, which iOS allows only while the app is
// active (Apple prohibits camera access from the background, and we
// don't fight that — we just tear down cleanly on .scenePhase change).
//
// VoiceOver: status + last-spoken-line are .accessibilityLiveRegion
// equivalents so a screen-reader user hears each new hazard /
// description without manual focus moves.

import SwiftUI

struct LiveSceneGuidanceView: View {

    @StateObject private var controller = LiveSceneGuidanceController(
        arabic: BasirSettings.shared.language == .arabic,
        useGps: false)
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("live_scene_use_gps") private var useGps: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L10n.t(
                    "تنبيه: هذه أداة مساعدة، لا تُغني عن العصا أو الكلب المرشد. لا تستخدمها وحدها لعبور الطرق أو السلالم. قد يتأخر الوصف أو يخطئ.",
                    "Note: this is an aid, not a substitute for a cane or guide dog. Do not use it alone to cross streets or stairs. Descriptions may be delayed or wrong."
                ))
                .font(.callout)
                .foregroundStyle(.secondary)

                statusCard

                lastLineCard

                if let error = controller.errorMessage {
                    Text(error)
                        .foregroundStyle(.red)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.red.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .accessibilityAddTraits(.isStaticText)
                }

                Toggle(L10n.t("استخدام الموقع لتحسين الوصف",
                              "Use location to improve descriptions"),
                       isOn: $useGps)
                    .font(.callout)
                    .disabled(controller.isRunning)

                primaryButton

                Text(L10n.t(
                    "بصير على iPhone يعمل فقط أثناء وجود التطبيق في المقدمة وفتح الشاشة. حين يدخل التطبيق إلى الخلفية تُغلق الجلسة تلقائيًا.",
                    "Live scene guidance on iPhone runs only while the app is in the foreground with the screen on. The session ends automatically if the app is backgrounded."
                ))
                .font(.footnote)
                .foregroundStyle(.tertiary)
            }
            .padding(20)
        }
        .navigationTitle(L10n.t("الوصف المباشر أثناء التنقل",
                                  "Live scene guidance"))
        .onChange(of: scenePhase) { _, phase in
            // iOS prohibits camera in background. Tear down so the OS
            // doesn't kill us with a black-frame warning and so the
            // user knows the session ended.
            if phase != .active { controller.stop() }
        }
        .onDisappear { controller.stop() }
    }

    // MARK: - Sub-views

    @ViewBuilder
    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.t("الحالة", "Status"))
                .font(.subheadline.bold())
                .foregroundStyle(.secondary)
            Text(controller.lastStatus.isEmpty
                 ? L10n.t("جاهز للبدء.", "Ready to start.")
                 : controller.lastStatus)
                .font(.body)
                .foregroundStyle(statusColor)
                .accessibilityLabel(controller.lastStatus.isEmpty
                                    ? L10n.t("جاهز للبدء.", "Ready to start.")
                                    : controller.lastStatus)
                .accessibilityAddTraits(.updatesFrequently)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var lastLineCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L10n.t("آخر تنبيه منطوق",
                         "Last spoken alert"))
                .font(.subheadline.bold())
                .foregroundStyle(.secondary)
            Text(controller.lastLine.isEmpty ? "—" : controller.lastLine)
                .font(.title3.bold())
                .accessibilityLabel(controller.lastLine.isEmpty ? "" : controller.lastLine)
                .accessibilityAddTraits(.updatesFrequently)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var primaryButton: some View {
        if controller.isRunning {
            Button(role: .destructive) {
                controller.stop()
            } label: {
                Text(L10n.t("إيقاف", "Stop"))
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .background(Color.red)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .accessibilityHint(L10n.t(
                "يوقف الوصف المباشر ويغلق الكاميرا.",
                "Stops the live scene guidance and closes the camera."))
        } else {
            Button {
                // Re-apply current settings each start so the GPS
                // toggle takes effect on the next session.
                let arabic = BasirSettings.shared.language == .arabic
                controller.configure(arabic: arabic, useGps: useGps)
                controller.start()
            } label: {
                Text(L10n.t("بدء الوصف المباشر",
                             "Start live scene guidance"))
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity, minHeight: 60)
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .accessibilityHint(L10n.t(
                "يبدأ التقاط صورة كل ثانيتين ووصف ما أمامك بصوت.",
                "Captures one frame every two seconds and speaks a description."))
        }
    }

    private var statusColor: Color {
        switch controller.hazardLevel.lowercased() {
        case "stop":    return Color(red: 0.84, green: 0.16, blue: 0.16)
        case "caution": return Color(red: 0.93, green: 0.61, blue: 0.0)
        default:        return .primary
        }
    }
}
