import SwiftUI
import LocalAuthentication

/// Security overview screen explaining how AccBot protects user data.
struct SecurityView: View {
    let onNext: () -> Void

    @EnvironmentObject var dependencies: AppDependencies
    @State private var biometricEnabled = false
    @State private var biometricType: BiometricType = .none
    @Environment(\.accBotColors) private var colors

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: Spacing.xxl) {
                    // Header icon
                    Image(systemName: "lock.shield.fill")
                        .font(AccBotFonts.iconLarge)
                        .foregroundStyle(colors.primary)
                        .accessibilityHidden(true)
                        .padding(.top, Spacing.xxxl)

                    Text(String(localized: "Your Security Matters"))
                        .font(AccBotFonts.titleLarge)
                        .foregroundStyle(colors.onSurface)

                    Text(String(localized: "AccBot is designed with a decentralized, security-first architecture."))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.lg)

                    // Security features
                    VStack(spacing: Spacing.md) {
                        SecurityFeatureRow(
                            icon: "iphone.and.arrow.forward",
                            title: String(localized: "Local-Only Storage"),
                            description: String(localized: "All data stays on your device. Nothing is sent to external servers.")
                        )
                        SecurityFeatureRow(
                            icon: "key.fill",
                            title: String(localized: "Keychain Encryption"),
                            description: String(localized: "API credentials are encrypted using iOS Keychain with device-only access.")
                        )
                        SecurityFeatureRow(
                            icon: "icloud.slash.fill",
                            title: String(localized: "No Cloud Backup"),
                            description: String(localized: "Sensitive data is excluded from iCloud and iTunes backups.")
                        )
                        SecurityFeatureRow(
                            icon: "lock.icloud",
                            title: String(localized: "Direct Exchange Communication"),
                            description: String(localized: "AccBot communicates directly with exchanges via HTTPS. No middleman.")
                        )
                        SecurityFeatureRow(
                            icon: "faceid",
                            title: String(localized: "Optional Biometric Lock"),
                            description: String(localized: "Protect the app with Face ID or Touch ID for an extra layer of security.")
                        )
                    }
                    .padding(.horizontal, Spacing.sm)

                    // Biometric toggle — always shown, disabled when not available
                    HStack {
                        Image(systemName: biometricType == .faceID ? "faceid" : biometricType == .touchID ? "touchid" : "lock")
                            .font(AccBotFonts.iconMedium)
                            .foregroundStyle(biometricType != .none ? colors.primary : colors.onSurfaceVariant)

                        VStack(alignment: .leading, spacing: Spacing.xxs) {
                            Text(biometricType != .none ? String(localized: "Enable \(biometricType.displayName)") : String(localized: "Biometric Lock"))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.onSurface)
                            Text(biometricType != .none ? String(localized: "Require authentication to open AccBot") : String(localized: "Not available on this device"))
                                .font(AccBotFonts.caption)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }

                        Spacer()

                        Toggle(biometricType != .none ? String(localized: "Enable \(biometricType.displayName)") : String(localized: "Biometric Lock"), isOn: $biometricEnabled)
                            .tint(colors.primary)
                            .labelsHidden()
                            .disabled(biometricType == .none)
                            .accessibilityLabel(biometricType != .none ? String(localized: "Enable \(biometricType.displayName)") : String(localized: "Biometric Lock"))
                    }
                    .padding(Spacing.lg)
                    .background(colors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                    .padding(.horizontal, Spacing.sm)

                    // Info tip card
                    HStack(alignment: .top, spacing: Spacing.md) {
                        Image(systemName: "lock.shield.fill")
                            .font(AccBotFonts.titleSmall)
                            .foregroundStyle(colors.primary)
                            .accessibilityHidden(true)

                        Text(String(localized: "Biometric authentication adds an extra layer of security to protect your API keys and settings"))
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(Spacing.md)
                    .background(colors.primary.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    .padding(.horizontal, Spacing.sm)

                    Spacer(minLength: Spacing.xxl)

                    // Continue button
                    Button(action: {
                        dependencies.userPreferences.biometricLockEnabled = biometricEnabled
                        onNext()
                    }) {
                        Text(String(localized: "I Understand, Continue"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Spacing.lg)
                            .background(colors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                    }
                    .padding(.bottom, Spacing.xxl)
                }
                .padding(.horizontal, Spacing.xxl)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            detectBiometricType()
        }
    }

    private func detectBiometricType() {
        let context = LAContext()
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            switch context.biometryType {
            case .faceID:
                biometricType = .faceID
            case .touchID:
                biometricType = .touchID
            default:
                biometricType = .none
            }
        } else {
            biometricType = .none
        }
    }
}

// MARK: - Supporting Types

private enum BiometricType {
    case faceID
    case touchID
    case none

    var displayName: String {
        switch self {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .none: return ""
        }
    }
}

// MARK: - Security Feature Row Component

private struct SecurityFeatureRow: View {
    let icon: String
    let title: String
    let description: String

    @Environment(\.accBotColors) private var colors

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: icon)
                .font(AccBotFonts.titleMedium)
                .foregroundStyle(colors.primary)
                .frame(width: 36, height: 36)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(title)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)

                Text(description)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
        }
        .padding(Spacing.md)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
        .accessibilityElement(children: .combine)
    }
}
