import SwiftUI
import LocalAuthentication

/// Security overview screen explaining how AccBot protects user data.
struct SecurityView: View {
    let onNext: () -> Void

    @EnvironmentObject var dependencies: AppDependencies
    @State private var biometricEnabled = false
    @State private var biometricType: BiometricType = .none

    var body: some View {
        ZStack {
            Color.backgroundDark
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: Spacing.xxl) {
                    // Header icon
                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 64))
                        .foregroundColor(.accentTeal)
                        .padding(.top, Spacing.xxxl)

                    Text("Your Security Matters")
                        .font(AccBotFonts.titleLarge)
                        .foregroundColor(.white)

                    Text("AccBot is designed with a decentralized, security-first architecture.")
                        .font(AccBotFonts.body)
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.lg)

                    // Security features
                    VStack(spacing: Spacing.md) {
                        SecurityFeatureRow(
                            icon: "iphone.and.arrow.forward",
                            title: "Local-Only Storage",
                            description: "All data stays on your device. Nothing is sent to external servers."
                        )
                        SecurityFeatureRow(
                            icon: "key.fill",
                            title: "Keychain Encryption",
                            description: "API credentials are encrypted using iOS Keychain with device-only access."
                        )
                        SecurityFeatureRow(
                            icon: "icloud.slash.fill",
                            title: "No Cloud Backup",
                            description: "Sensitive data is excluded from iCloud and iTunes backups."
                        )
                        SecurityFeatureRow(
                            icon: "network",
                            title: "Direct Exchange Communication",
                            description: "AccBot communicates directly with exchanges via HTTPS. No middleman."
                        )
                        SecurityFeatureRow(
                            icon: "faceid",
                            title: "Optional Biometric Lock",
                            description: "Protect the app with Face ID or Touch ID for an extra layer of security."
                        )
                    }
                    .padding(.horizontal, Spacing.sm)

                    // Biometric toggle
                    if biometricType != .none {
                        HStack {
                            Image(systemName: biometricType == .faceID ? "faceid" : "touchid")
                                .font(.system(size: 24))
                                .foregroundColor(.accentTeal)

                            VStack(alignment: .leading, spacing: Spacing.xxs) {
                                Text("Enable \(biometricType.displayName)")
                                    .font(AccBotFonts.headline)
                                    .foregroundColor(.white)
                                Text("Require authentication to open AccBot")
                                    .font(AccBotFonts.caption)
                                    .foregroundColor(.white.opacity(0.6))
                            }

                            Spacer()

                            Toggle("", isOn: $biometricEnabled)
                                .tint(.accentTeal)
                                .labelsHidden()
                        }
                        .padding(Spacing.lg)
                        .background(Color.surfaceDark)
                        .cornerRadius(CornerRadius.md)
                        .padding(.horizontal, Spacing.sm)
                    }

                    Spacer(minLength: Spacing.xxl)

                    // Continue button
                    Button(action: {
                        dependencies.userPreferences.biometricLockEnabled = biometricEnabled
                        onNext()
                    }) {
                        Text("I Understand, Continue")
                            .font(AccBotFonts.headline)
                            .foregroundColor(.backgroundDark)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Spacing.lg)
                            .background(Color.accentTeal)
                            .cornerRadius(CornerRadius.md)
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

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundColor(.accentTeal)
                .frame(width: 36, height: 36)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(title)
                    .font(AccBotFonts.headline)
                    .foregroundColor(.white)

                Text(description)
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(.white.opacity(0.6))
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
        }
        .padding(Spacing.md)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.sm)
    }
}
