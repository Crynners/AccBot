import SwiftUI
import LocalAuthentication

/// Full-screen overlay blocking app until Face ID/Touch ID succeeds
struct BiometricLockView<Content: View>: View {
    let content: Content
    @Environment(\.accBotColors) private var colors
    @State private var isUnlocked = false
    @State private var showError = false
    @State private var errorMessage = ""
    @State private var biometricIconName = "lock.fill"
    @State private var retryCount = 0
    private let maxRetries = 5

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ZStack {
            if isUnlocked {
                content
            } else {
                lockScreen
            }
        }
        .task {
            resolveBiometricIcon()
            authenticate()
        }
    }

    private var lockScreen: some View {
        VStack(spacing: Spacing.xxl) {
            Spacer()

            Image(systemName: biometricIconName)
                .font(AccBotFonts.iconLarge)
                .foregroundStyle(colors.primary)
                .accessibilityHidden(true)

            Text("AccBot")
                .font(AccBotFonts.titleLarge)
                .foregroundStyle(colors.primary)

            Text(String(localized: "Authenticate to continue"))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)

            if showError {
                Text(errorMessage)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.error)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Spacing.xxl)

                if retryCount < maxRetries {
                    Text(String(localized: "\(maxRetries - retryCount) attempts remaining"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)

                    Button {
                        authenticate()
                    } label: {
                        Text(String(localized: "Try Again"))
                            .font(AccBotFonts.headline)
                            .padding(.horizontal, Spacing.xxl)
                            .padding(.vertical, Spacing.md)
                            .frame(minHeight: 44)
                            .background(colors.primary)
                            .foregroundStyle(colors.onPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }
                } else {
                    Text(String(localized: "Too many biometric attempts. Use device passcode instead."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.warning)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.xxl)

                    Button {
                        authenticate()
                    } label: {
                        Text(String(localized: "Use Passcode"))
                            .font(AccBotFonts.headline)
                            .padding(.horizontal, Spacing.xxl)
                            .padding(.vertical, Spacing.md)
                            .frame(minHeight: 44)
                            .background(colors.primary)
                            .foregroundStyle(colors.onPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }

                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Text(String(localized: "Open Settings"))
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.primary)
                            .frame(minHeight: 44)
                    }
                }
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(colors.background.ignoresSafeArea())
        .onAppear {
            UIAccessibility.post(notification: .announcement, argument: String(localized: "Authentication required"))
        }
    }

    private func resolveBiometricIcon() {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: biometricIconName = "faceid"
        case .touchID: biometricIconName = "touchid"
        default: biometricIconName = "lock.fill"
        }
    }

    private func authenticate() {
        let context = LAContext()
        var error: NSError?

        // After exhausting biometric retries, fall back to device passcode
        let policy: LAPolicy = retryCount >= maxRetries
            ? .deviceOwnerAuthentication
            : .deviceOwnerAuthenticationWithBiometrics

        if context.canEvaluatePolicy(policy, error: &error) {
            context.evaluatePolicy(
                policy,
                localizedReason: String(localized: "Unlock AccBot to access your DCA plans")
            ) { success, authError in
                DispatchQueue.main.async {
                    if success {
                        withAnimation {
                            isUnlocked = true
                        }
                    } else {
                        showError = true
                        retryCount += 1
                        errorMessage = authError?.localizedDescription ?? String(localized: "Authentication failed")
                    }
                }
            }
        } else {
            // Neither biometrics nor passcode available — show error with actionable guidance
            DispatchQueue.main.async {
                showError = true
                errorMessage = String(localized: "Authentication is not available. Set up Face ID, Touch ID, or a device passcode in Settings to unlock AccBot.")
            }
        }
    }
}
