// ShareViewController.swift
// Basir's Share Extension. Equivalent to Android's ACTION_SEND intent
// filter (added in Basir v2.8.1). Lets a user share an image, PDF, or
// text snippet from any other iOS app — Photos, Files, Safari, Mail —
// straight to Basir for analysis.
//
// iOS Share Extensions are a SEPARATE Xcode TARGET. They cannot share
// code with the main app freely — they need an App Group (in
// "Capabilities" → "App Groups") to read/write the same Keychain item
// and UserDefaults. For this scaffold we keep the extension purely
// presentational: it receives the shared item, hands it off to a deep
// link the main app handles, and dismisses.

import UIKit
import UniformTypeIdentifiers
import Social

class ShareViewController: SLComposeServiceViewController {

    override func isContentValid() -> Bool {
        // Accept anything with at least one image / PDF / text item.
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            return false
        }
        return items.contains { item in
            (item.attachments ?? []).contains { provider in
                provider.hasItemConformingToTypeIdentifier(UTType.image.identifier)
                || provider.hasItemConformingToTypeIdentifier(UTType.pdf.identifier)
                || provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier)
            }
        }
    }

    override func didSelectPost() {
        // Read the first attachment, write its contents to the App
        // Group's shared container, then open the main Basir app via
        // a URL scheme. The main app inspects that file on launch and
        // routes the user to the right flow.
        guard let item = (extensionContext?.inputItems.first as? NSExtensionItem),
              let provider = item.attachments?.first else {
            self.extensionContext?.completeRequest(returningItems: nil)
            return
        }

        // Try image first.
        if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
            provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { [weak self] data, _ in
                self?.persistAndOpen(data: data, ext: "jpg", task: "describe_image")
            }
            return
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.pdf.identifier) {
            provider.loadDataRepresentation(forTypeIdentifier: UTType.pdf.identifier) { [weak self] data, _ in
                self?.persistAndOpen(data: data, ext: "pdf", task: "convert")
            }
            return
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) { [weak self] item, _ in
                if let text = item as? String,
                   let data = text.data(using: .utf8) {
                    self?.persistAndOpen(data: data, ext: "txt", task: "ask")
                } else {
                    self?.extensionContext?.completeRequest(returningItems: nil)
                }
            }
            return
        }
        self.extensionContext?.completeRequest(returningItems: nil)
    }

    /// Write the shared data into the App Group container so the main
    /// app can pick it up, then open `basir://share/<task>?file=<name>`.
    private func persistAndOpen(data: Data?, ext: String, task: String) {
        guard let data,
              let container = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: "group.com.basir.shared"
              ) else {
            self.extensionContext?.completeRequest(returningItems: nil)
            return
        }
        let filename = "share-\(Int(Date().timeIntervalSince1970)).\(ext)"
        let url = container.appendingPathComponent(filename)
        do {
            try data.write(to: url, options: [.atomic])
        } catch {
            self.extensionContext?.completeRequest(returningItems: nil)
            return
        }
        // basir:// URL handled by BasirApp's onOpenURL.
        if let openURL = URL(string: "basir://share/\(task)?file=\(filename)") {
            openContainingApp(openURL)
        }
        self.extensionContext?.completeRequest(returningItems: nil)
    }

    /// UIApplication.open is not available from extensions; walk the
    /// responder chain to find a host that can open URLs.
    private func openContainingApp(_ url: URL) {
        var responder: UIResponder? = self
        while let r = responder {
            if let application = r as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                return
            }
            responder = r.next
        }
    }
}
