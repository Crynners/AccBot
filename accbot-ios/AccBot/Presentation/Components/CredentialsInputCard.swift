import SwiftUI

/// Card containing secure input fields for exchange API credentials.
/// Conditionally shows passphrase and client ID fields based on the
/// exchange requirements. Includes a validate button with loading state.
struct CredentialsInputCard: View {
    let exchange: Exchange
    let onValidate: (ExchangeCredentials) async -> Void

    @State private var apiKey = ""
    @State private var apiSecret = ""
    @State private var passphrase = ""
    @State private var clientId = ""
    @State private var isLoading = false
    @State private var showApiKey = false
    @State private var showApiSecret = false
    @FocusState private var focusedField: String?

    @Environment(\.accBotColors) private var colors

    private var isFormValid: Bool {
        let base = !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !apiSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let passOk = !exchange.requiresPassphrase
            || !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let clientOk = !exchange.requiresClientId
            || !clientId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return base && passOk && clientOk
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(String(localized: "API Credentials"))
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onSurface)
                .accessibilityAddTraits(.isHeader)

            credentialField(
                label: String(localized: "API Key"),
                fieldId: "apiKey",
                text: $apiKey,
                isSecure: !showApiKey,
                toggleVisibility: $showApiKey
            )

            credentialField(
                label: String(localized: "API Secret"),
                fieldId: "apiSecret",
                text: $apiSecret,
                isSecure: !showApiSecret,
                toggleVisibility: $showApiSecret
            )

            if exchange.requiresPassphrase {
                credentialField(
                    label: String(localized: "Passphrase"),
                    fieldId: "passphrase",
                    text: $passphrase,
                    isSecure: true,
                    toggleVisibility: nil
                )
            }

            if exchange.requiresClientId {
                credentialField(
                    label: String(localized: "Client ID"),
                    fieldId: "clientId",
                    text: $clientId,
                    isSecure: false,
                    toggleVisibility: nil
                )
            }

            validateButton
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Field

    private func credentialField(
        label: String,
        fieldId: String,
        text: Binding<String>,
        isSecure: Bool,
        toggleVisibility: Binding<Bool>?
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: Spacing.sm) {
                Group {
                    if isSecure {
                        SecureField("", text: text)
                    } else {
                        TextField("", text: text)
                    }
                }
                .focused($focusedField, equals: fieldId)
                .accessibilityLabel(label)
                .font(AccBotFonts.mono)
                .foregroundStyle(colors.onSurface)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

                if let toggleVisibility {
                    Button {
                        toggleVisibility.wrappedValue.toggle()
                    } label: {
                        Image(systemName: toggleVisibility.wrappedValue ? "eye.slash" : "eye")
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel(toggleVisibility.wrappedValue
                        ? String(localized: "Hide \(label)")
                        : String(localized: "Show \(label)"))
                }
            }
            .padding(Spacing.md)
            .background(colors.surfaceVariant.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.sm)
                    .strokeBorder(
                        focusedField == fieldId ? colors.primary : Color.clear,
                        lineWidth: 2
                    )
            )
        }
    }

    // MARK: - Validate Button

    private var validateButton: some View {
        Button {
            isLoading = true
            let credentials = ExchangeCredentials(
                exchange: exchange,
                apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
                apiSecret: apiSecret.trimmingCharacters(in: .whitespacesAndNewlines),
                passphrase: exchange.requiresPassphrase
                    ? passphrase.trimmingCharacters(in: .whitespacesAndNewlines)
                    : nil,
                clientId: exchange.requiresClientId
                    ? clientId.trimmingCharacters(in: .whitespacesAndNewlines)
                    : nil
            )
            Task {
                await onValidate(credentials)
                isLoading = false
            }
        } label: {
            HStack(spacing: Spacing.sm) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                        .scaleEffect(0.8)
                        .accessibilityLabel(String(localized: "Validating credentials"))
                }
                Text(isLoading
                     ? String(localized: "Validating...")
                     : String(localized: "Validate & Save"))
                    .font(AccBotFonts.headline)
            }
            .foregroundStyle(isFormValid ? colors.onPrimary : colors.disabledForeground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(isFormValid ? colors.primary : colors.surfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
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
            CredentialsInputCard(exchange: .binance) { _ in }
            CredentialsInputCard(exchange: .kucoin) { _ in }
            CredentialsInputCard(exchange: .coinmate) { _ in }
        }
        .padding()
    }
    .background(Color.backgroundDark)
}
