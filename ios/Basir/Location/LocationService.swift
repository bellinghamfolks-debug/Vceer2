// LocationService.swift
// CoreLocation wrapper for emergency mode and walking mode.
//
// One-shot fetch only — Basir does not need background location and
// must not ask for "Always" authorisation (Apple rejects apps that
// request more access than they justify).

import Foundation
import CoreLocation

@MainActor
final class LocationService: NSObject, ObservableObject {
    static let shared = LocationService()

    enum AuthState { case undetermined, denied, restricted, whenInUse, always }

    @Published private(set) var authorization: AuthState = .undetermined
    @Published private(set) var lastKnownCoordinate: CLLocationCoordinate2D?
    @Published private(set) var lastError: String?

    private let manager = CLLocationManager()
    private var oneShotContinuation: CheckedContinuation<CLLocation?, Never>?

    override private init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        updateAuthState()
    }

    func requestWhenInUse() {
        manager.requestWhenInUseAuthorization()
    }

    /// Fetch one location reading, asking for permission first if needed.
    /// Returns nil on denial or timeout.
    func fetchOnce(timeout: TimeInterval = 8) async -> CLLocation? {
        if authorization == .undetermined {
            manager.requestWhenInUseAuthorization()
            try? await Task.sleep(nanoseconds: 600_000_000)
        }
        guard authorization == .whenInUse || authorization == .always else {
            return nil
        }
        // Race the requestLocation callback against a timeout.
        return await withTaskGroup(of: CLLocation?.self) { group in
            group.addTask {
                await withCheckedContinuation { [weak self] cont in
                    Task { @MainActor [weak self] in
                        guard let self else { cont.resume(returning: nil); return }
                        self.oneShotContinuation = cont
                        self.manager.requestLocation()
                    }
                }
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
                return nil
            }
            for await result in group {
                group.cancelAll()
                return result
            }
            return nil
        }
    }

    /// Format a coordinate as a Google Maps link (works on any
    /// messaging app, no Apple-specific URL scheme).
    static func mapsLink(for coord: CLLocationCoordinate2D) -> String {
        "https://maps.google.com/?q=\(coord.latitude),\(coord.longitude)"
    }

    private func updateAuthState() {
        switch manager.authorizationStatus {
        case .notDetermined:       authorization = .undetermined
        case .restricted:          authorization = .restricted
        case .denied:              authorization = .denied
        case .authorizedWhenInUse: authorization = .whenInUse
        case .authorizedAlways:    authorization = .always
        @unknown default:          authorization = .undetermined
        }
    }
}

extension LocationService: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in self.updateAuthState() }
    }

    nonisolated func locationManager(_ manager: CLLocationManager,
                                      didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            if let loc = locations.last {
                self.lastKnownCoordinate = loc.coordinate
                self.oneShotContinuation?.resume(returning: loc)
                self.oneShotContinuation = nil
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager,
                                      didFailWithError error: Error) {
        Task { @MainActor in
            self.lastError = error.localizedDescription
            self.oneShotContinuation?.resume(returning: nil)
            self.oneShotContinuation = nil
        }
    }
}
