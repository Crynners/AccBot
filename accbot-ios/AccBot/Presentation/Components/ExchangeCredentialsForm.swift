import SwiftUI

/// Credential fields for one exchange with a QR scanner on every field and "Scan All
/// Credentials". Used when connecting an exchange and when adding a read-only key to a plan
/// share, so both offer the same input options.
struct ExchangeCredentialsForm: View {
    let exchange: Exchange
    @Binding var apiKey: String
    @Binding var apiSecret: String
    @Binding var passphrase: String
    @Binding var clientId: String

    @State private var showQrScanner = false
    @State private var qrScanTarget: CredentialScanTarget = .apiKey
    @State private var showMultiFieldScanner = false

    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            // Scan All Credentials button (hidden for Coinmate — uses Client ID + Public/Private key)
            if exchange != .coinmate {
                Button {
                    showMultiFieldScanner = true
                } label: {
                    HStack(spacing: Spacing.sm) {
                        Image(systemName: "qrcode.viewfinder")
                        Text(String(localized: "Scan All Credentials"))
                    }
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.primary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.md)
                    .background(colors.primary.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                    .overlay(
                        RoundedRectangle(cornerRadius: CornerRadius.md)
                            .stroke(colors.primary.opacity(0.3), lineWidth: 1)
                    )
                }
                .buttonStyle(.borderless)
            }

            VStack(alignment: .leading, spacing: Spacing.lg) {
                if exchange.requiresClientId {
                    credentialField(
                        label: String(localized: "Client ID"),
                        text: $clientId,
                        placeholder: String(localized: "Enter your client ID"),
                        isSecure: false,
                        scanTarget: .clientId
                    )
                }

                credentialField(
                    label: exchange.requiresClientId
                        ? String(localized: "Public Key")
                        : String(localized: "API Key"),
                    text: $apiKey,
                    placeholder: exchange.requiresClientId
                        ? String(localized: "Enter your public key")
                        : String(localized: "Enter your API key"),
                    isSecure: false,
                    scanTarget: .apiKey
                )

                credentialField(
                    label: exchange.requiresClientId
                        ? String(localized: "Private Key")
                        : String(localized: "API Secret"),
                    text: $apiSecret,
                    placeholder: exchange.requiresClientId
                        ? String(localized: "Enter your private key")
                        : String(localized: "Enter your API secret"),
                    isSecure: true,
                    scanTarget: .apiSecret
                )

                if exchange.requiresPassphrase {
                    credentialField(
                        label: String(localized: "Passphrase"),
                        text: $passphrase,
                        placeholder: String(localized: "Enter your passphrase"),
                        isSecure: true,
                        scanTarget: .passphrase
                    )
                }
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerSheet(
                title: String(localized: "Scan Credential"),
                onScanned: { code in handleQrScan(code) }
            )
        }
        .sheet(isPresented: $showMultiFieldScanner) {
            MultiFieldScannerSheet(
                title: String(localized: "Scan All Credentials"),
                fields: multiFieldScannerFields,
                onResult: { result in
                    if let key = result["apiKey"] { apiKey = key }
                    if let secret = result["apiSecret"] { apiSecret = secret }
                    if let phrase = result["passphrase"] { passphrase = phrase }
                    if let id = result["clientId"] { clientId = id }
                }
            )
        }
    }

    private func credentialField(
        label: String,
        text: Binding<String>,
        placeholder: String,
        isSecure: Bool,
        scanTarget: CredentialScanTarget
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: Spacing.sm) {
                Group {
                    if isSecure {
                        SecureField(placeholder, text: text)
                    } else {
                        TextField(placeholder, text: text)
                    }
                }
                .font(AccBotFonts.mono)
                .foregroundStyle(colors.onSurface)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .padding(Spacing.md)
                .background(colors.surfaceVariant.opacity(0.3))
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

                Button {
                    qrScanTarget = scanTarget
                    showQrScanner = true
                } label: {
                    Image(systemName: "qrcode.viewfinder")
                        .font(AccBotFonts.iconSmall)
                        .foregroundStyle(colors.primary)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(String(localized: "Scan Credential"))
            }
        }
    }

    private var multiFieldScannerFields: [ScanTargetField] {
        var fields: [ScanTargetField] = []
        if exchange.requiresClientId {
            fields.append(ScanTargetField(id: "clientId", label: String(localized: "Client ID")))
        }
        fields.append(ScanTargetField(
            id: "apiKey",
            label: exchange.requiresClientId
                ? String(localized: "Public Key")
                : String(localized: "API Key")
        ))
        fields.append(ScanTargetField(
            id: "apiSecret",
            label: exchange.requiresClientId
                ? String(localized: "Private Key")
                : String(localized: "API Secret")
        ))
        if exchange.requiresPassphrase {
            fields.append(ScanTargetField(id: "passphrase", label: String(localized: "Passphrase")))
        }
        return fields
    }

    private func handleQrScan(_ code: String) {
        let value = code.trimmingCharacters(in: .whitespacesAndNewlines)
        switch qrScanTarget {
        case .apiKey:
            apiKey = value
        case .apiSecret:
            apiSecret = value
        case .passphrase:
            passphrase = value
        case .clientId:
            clientId = value
        case .scanAll:
            // Handled by MultiFieldScannerSheet
            break
        }
    }
}

// MARK: - Credential Scan Target

enum CredentialScanTarget {
    case apiKey
    case apiSecret
    case passphrase
    case clientId
    case scanAll
}
