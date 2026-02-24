import Foundation

enum CredentialValidationResult {
    case success
    case error(String)
}

/// Validates exchange credentials via API and saves to Keychain if valid.
/// Shared across AddPlan, AddExchange, and Onboarding flows.
final class ValidateAndSaveCredentialsUseCase {
    private let exchangeApiFactory: ExchangeApiFactory
    private let credentialsStore: CredentialsStore
    private let userPreferences: UserPreferences

    init(exchangeApiFactory: ExchangeApiFactory, credentialsStore: CredentialsStore, userPreferences: UserPreferences) {
        self.exchangeApiFactory = exchangeApiFactory
        self.credentialsStore = credentialsStore
        self.userPreferences = userPreferences
    }

    func execute(
        exchange: Exchange,
        apiKey: String,
        apiSecret: String,
        passphrase: String? = nil,
        clientId: String? = nil
    ) async -> CredentialValidationResult {
        let trimmedKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedSecret = apiSecret.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !trimmedKey.isEmpty, !trimmedSecret.isEmpty else {
            return .error("Please enter both API key and secret")
        }

        if exchange == .coinmate, (clientId ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return .error("Please enter your Client ID")
        }

        let credentials = ExchangeCredentials(
            exchange: exchange,
            apiKey: trimmedKey,
            apiSecret: trimmedSecret,
            passphrase: passphrase?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
            clientId: clientId?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        )

        do {
            let isSandbox = userPreferences.isSandboxMode()
            let api = exchangeApiFactory.create(credentials: credentials)
            let isValid = try await api.validateCredentials()

            if isValid {
                try credentialsStore.save(credentials, isSandbox: isSandbox)
                return .success
            } else {
                let hint = isSandbox
                    ? " Make sure you are using API keys generated on the exchange's sandbox/testnet (not production keys)."
                    : ""
                return .error("Invalid API credentials.\(hint)")
            }
        } catch {
            let isSandbox = userPreferences.isSandboxMode()
            let hint = isSandbox
                ? "\n\nNote: Sandbox mode requires separate API keys from the exchange's testnet environment."
                : ""
            return .error("\(error.localizedDescription)\(hint)")
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
