import Foundation

/// Simple onboarding completion tracking
final class OnboardingPreferences: ObservableObject {
    private let defaults: UserDefaults

    @Published var onboardingCompleted: Bool {
        didSet { defaults.set(onboardingCompleted, forKey: Keys.onboardingCompleted) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.onboardingCompleted = defaults.bool(forKey: Keys.onboardingCompleted)
    }

    private enum Keys {
        static let onboardingCompleted = "onboarding_completed"
    }
}
