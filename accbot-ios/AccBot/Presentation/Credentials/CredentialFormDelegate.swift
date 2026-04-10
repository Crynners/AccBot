import SwiftUI

/// Scan target for individual QR code scanning.
enum CredentialScanTarget {
    case apiKey
    case apiSecret
    case passphrase
    case clientId
    case scanAll
}

/// Shared delegate for credential form state and validation logic.
/// Used across AddExchangeView, ExchangeSetupView, and ExchangeDetailView.
@MainActor
class CredentialFormDelegate: ObservableObject {
    // MARK: - Credential State
    @Published var selectedExchange: Exchange?
    /// When set, save overwrites this connection's credentials. When nil, a new connection is created.
    var targetConnectionId: Int64?
    @Published var apiKey = ""
    @Published var apiSecret = ""
    @Published var passphrase = ""
    @Published var clientId = ""
    @Published var isValidating = false
    @Published var validationError: String?
    @Published var isValid = false

    // MARK: - QR Scanner State
    @Published var showQrScanner = false
    @Published var qrScanTarget: CredentialScanTarget = .apiKey
    @Published var showMultiFieldScanner = false
    @Published var showBinanceQrScanner = false

    // MARK: - Computed Properties

    var canValidate: Bool {
        guard let exchange = selectedExchange else { return false }
        let hasKey = !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasSecret = !apiSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasPassphrase = !exchange.requiresPassphrase
            || !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasClientId = !exchange.requiresClientId
            || !clientId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return !isValidating && hasKey && hasSecret && hasPassphrase && hasClientId
    }

    // MARK: - Exchange Selection

    func selectExchange(_ exchange: Exchange) {
        guard selectedExchange != exchange else { return }
        selectedExchange = exchange
        resetFields()
    }

    func resetFields() {
        apiKey = ""
        apiSecret = ""
        passphrase = ""
        clientId = ""
        validationError = nil
        isValid = false
    }

    // MARK: - Validation

    /// Validate credentials against the exchange API and save to Keychain if valid.
    /// Returns `true` on success.
    @discardableResult
    func validateAndSave(
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        isSandbox: Bool,
        exchangeConnectionDao: ExchangeConnectionDao
    ) async -> Bool {
        guard let exchange = selectedExchange else { return false }

        isValidating = true
        validationError = nil
        isValid = false

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

        do {
            let api = exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
            let valid = try await api.validateCredentials()

            if valid {
                if let connectionId = targetConnectionId {
                    // Update existing connection
                    try credentialsStore.save(credentials, connectionId: connectionId, isSandbox: isSandbox)
                } else {
                    // Create new connection
                    let connectionId = try exchangeConnectionDao.insert(
                        ExchangeConnection(exchange: exchange)
                    )
                    try credentialsStore.save(credentials, connectionId: connectionId, isSandbox: isSandbox)
                }
                isValid = true
                isValidating = false
                return true
            } else {
                validationError = String(localized: "Invalid credentials. Please check your API key and secret.")
            }
        } catch let urlError as URLError where urlError.code == .notConnectedToInternet
                    || urlError.code == .networkConnectionLost
                    || urlError.code == .cannotFindHost
                    || urlError.code == .timedOut {
            validationError = String(localized: "No internet connection. Please check your network and try again.")
        } catch {
            validationError = error.localizedDescription
        }

        isValidating = false
        return false
    }

    // MARK: - Paste All from Clipboard

    func pasteAllCredentials() {
        guard let exchange = selectedExchange,
              let text = UIPasteboard.general.string else { return }
        let lines = text.components(separatedBy: .newlines)
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }

        if exchange.requiresClientId && lines.count >= 3 {
            // Coinmate: Private Key, Public Key, Client ID
            apiSecret = lines[0].trimmingCharacters(in: .whitespaces)
            apiKey = lines[1].trimmingCharacters(in: .whitespaces)
            clientId = lines[2].trimmingCharacters(in: .whitespaces)
        } else if exchange.requiresPassphrase && lines.count >= 3 {
            // KuCoin/Coinbase: API Key, API Secret, Passphrase
            apiKey = lines[0].trimmingCharacters(in: .whitespaces)
            apiSecret = lines[1].trimmingCharacters(in: .whitespaces)
            passphrase = lines[2].trimmingCharacters(in: .whitespaces)
        } else if lines.count >= 2 {
            // Other exchanges: API Key, API Secret
            apiKey = lines[0].trimmingCharacters(in: .whitespaces)
            apiSecret = lines[1].trimmingCharacters(in: .whitespaces)
        }
    }

    // MARK: - QR Scanning

    /// Parse a Binance QR code. Expected JSON: {"apiKey":"...","secretKey":"..."}.
    /// Falls back to treating the entire string as the API key.
    func handleBinanceQrScan(_ code: String) {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        if let data = trimmed.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let key = json["apiKey"] as? String {
                apiKey = key.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if let secret = json["secretKey"] as? String {
                apiSecret = secret.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        } else {
            apiKey = trimmed
        }
    }

    func handleQrScan(_ code: String) {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        switch qrScanTarget {
        case .apiKey: apiKey = trimmed
        case .apiSecret: apiSecret = trimmed
        case .passphrase: passphrase = trimmed
        case .clientId: clientId = trimmed
        case .scanAll: break
        }
    }

    func handleMultiFieldResult(_ result: [String: String]) {
        if let key = result["apiKey"] { apiKey = key }
        if let secret = result["apiSecret"] { apiSecret = secret }
        if let phrase = result["passphrase"] { passphrase = phrase }
        if let id = result["clientId"] { clientId = id }
    }

    func multiFieldScannerFields() -> [ScanTargetField] {
        guard let exchange = selectedExchange else { return [] }
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
}
