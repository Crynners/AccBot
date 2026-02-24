import SwiftUI
import LocalAuthentication

/// Full-screen overlay blocking app until Face ID/Touch ID succeeds
struct BiometricLockView<Content: View>: View {
    let content: Content
    @State private var isUnlocked = false
    @State private var showError = false
    @State private var errorMessage = ""

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
        .onAppear {
            authenticate()
        }
    }

    private var lockScreen: some View {
        VStack(spacing: Spacing.xxl) {
            Spacer()

            Image(systemName: biometricIcon)
                .font(.system(size: 64))
                .foregroundColor(.accentTeal)

            Text("AccBot")
                .font(AccBotFonts.titleLarge)
                .foregroundColor(.accentTeal)

            Text("Authenticate to continue")
                .font(AccBotFonts.body)
                .foregroundColor(.onSurfaceVariantColor)

            if showError {
                Text(errorMessage)
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(.errorRed)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, Spacing.xxl)
            }

            Button {
                authenticate()
            } label: {
                Text("Try Again")
                    .font(AccBotFonts.headline)
                    .padding(.horizontal, Spacing.xxl)
                    .padding(.vertical, Spacing.md)
                    .background(Color.accentTeal)
                    .foregroundColor(.surfaceDark)
                    .cornerRadius(CornerRadius.sm)
            }
            .opacity(showError ? 1 : 0)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.backgroundDark)
    }

    private var biometricIcon: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        default: return "lock.fill"
        }
    }

    private func authenticate() {
        let context = LAContext()
        var error: NSError?

        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: "Unlock AccBot to access your DCA plans"
            ) { success, authError in
                DispatchQueue.main.async {
                    if success {
                        withAnimation {
                            isUnlocked = true
                        }
                    } else {
                        showError = true
                        errorMessage = authError?.localizedDescription ?? "Authentication failed"
                    }
                }
            }
        } else {
            // Biometrics not available, fall through
            isUnlocked = true
        }
    }
}
