import SwiftUI

/// Card containing secure input fields for exchange API credentials.
/// Conditionally shows passphrase and client ID fields based on the
/// exchange requirements. Includes a validate button with loading state.
struct CredentialsInputCard: View {
    let exchange: Exchange
    let onValidate: (ExchangeCredentials) -> Void

    @State private var apiKey = ""
    @State private var apiSecret = ""
    @State private var passphrase = ""
    @State private var clientId = ""
    @State private var isLoading = false
    @State private var showApiKey = false
    @State private var showApiSecret = false

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    private var isFormValid: Bool {
        let base = !apiKey.trimmingCharacters(in: .whitespaces).isEmpty
            && !apiSecret.trimmingCharacters(in: .whitespaces).isEmpty
        let passOk = !exchange.requiresPassphrase
            || !passphrase.trimmingCharacters(in: .whitespaces).isEmpty
        let clientOk = !exchange.requiresClientId
            || !clientId.trimmingCharacters(in: .whitespaces).isEmpty
        return base && passOk && clientOk
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(String(localized: "API Credentials"))
                .font(AccBotFonts.headline)
                .foregroundColor(colors.onSurface)

            credentialField(
                label: String(localized: "API Key"),
                text: $apiKey,
                isSecure: !showApiKey,
                toggleVisibility: $showApiKey
            )

            credentialField(
                label: String(localized: "API Secret"),
                text: $apiSecret,
                isSecure: !showApiSecret,
                toggleVisibility: $showApiSecret
            )

            if exchange.requiresPassphrase {
                credentialField(
                    label: String(localized: "Passphrase"),
                    text: $passphrase,
                    isSecure: true,
                    toggleVisibility: nil
                )
            }

            if exchange.requiresClientId {
                credentialField(
                    label: String(localized: "Client ID"),
                    text: $clientId,
                    isSecure: false,
                    toggleVisibility: nil
                )
            }

            validateButton
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Field

    private func credentialField(
        label: String,
        text: Binding<String>,
        isSecure: Bool,
        toggleVisibility: Binding<Bool>?
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundColor(colors.onSurfaceVariant)

            HStack(spacing: Spacing.sm) {
                Group {
                    if isSecure {
                        SecureField("", text: text)
                    } else {
                        TextField("", text: text)
                    }
                }
                .font(AccBotFonts.mono)
                .foregroundColor(colors.onSurface)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

                if let toggleVisibility {
                    Button {
                        toggleVisibility.wrappedValue.toggle()
                    } label: {
                        Image(systemName: toggleVisibility.wrappedValue ? "eye.slash" : "eye")
                            .font(AccBotFonts.bodySmall)
                            .foregroundColor(colors.onSurfaceVariant)
                    }
                }
            }
            .padding(Spacing.md)
            .background(colors.surfaceVariant.opacity(0.3))
            .cornerRadius(CornerRadius.sm)
        }
    }

    // MARK: - Validate Button

    private var validateButton: some View {
        Button {
            isLoading = true
            let credentials = ExchangeCredentials(
                exchange: exchange,
                apiKey: apiKey.trimmingCharacters(in: .whitespaces),
                apiSecret: apiSecret.trimmingCharacters(in: .whitespaces),
                passphrase: exchange.requiresPassphrase
                    ? passphrase.trimmingCharacters(in: .whitespaces)
                    : nil,
                clientId: exchange.requiresClientId
                    ? clientId.trimmingCharacters(in: .whitespaces)
                    : nil
            )
            onValidate(credentials)
            isLoading = false
        } label: {
            HStack(spacing: Spacing.sm) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        .scaleEffect(0.8)
                }
                Text(isLoading
                     ? String(localized: "Validating...")
                     : String(localized: "Validate & Save"))
                    .font(AccBotFonts.headline)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(isFormValid ? colors.primary : colors.primary.opacity(0.4))
            .cornerRadius(CornerRadius.sm)
        }
        .disabled(!isFormValid || isLoading)
    }

    // MARK: - Public helpers

    /// Pre-fill credentials (e.g. when editing existing exchange setup).
    func prefilled(with credentials: ExchangeCredentials) -> Self {
        var copy = self
        copy._apiKey = State(initialValue: credentials.apiKey)
        copy._apiSecret = State(initialValue: credentials.apiSecret)
        copy._passphrase = State(initialValue: credentials.passphrase ?? "")
        copy._clientId = State(initialValue: credentials.clientId ?? "")
        return copy
    }
}

// MARK: - Preview

#Preview {
    ScrollView {
        VStack(spacing: Spacing.lg) {
            CredentialsInputCard(exchange: .binance, onValidate: { _ in })
            CredentialsInputCard(exchange: .kucoin, onValidate: { _ in })
            CredentialsInputCard(exchange: .coinmate, onValidate: { _ in })
        }
        .padding()
    }
    .background(Color.backgroundDark)
}
