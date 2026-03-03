import Foundation

/// Shared view model for the onboarding flow.
/// Provides exchange credential validation, storage, and first plan creation.
@MainActor
class OnboardingViewModel: ObservableObject {
    // MARK: - Exchange Setup
    @Published var selectedExchange: Exchange?
    @Published var apiKey = ""
    @Published var apiSecret = ""
    @Published var passphrase = ""
    @Published var clientId = ""

    // MARK: - First Plan
    @Published var selectedCrypto = "BTC"
    @Published var selectedFiat = "EUR"
    @Published var amount = ""
    @Published var frequency: DcaFrequency = .daily

    // MARK: - Validation State
    @Published var isValidating = false
    @Published var validationError: String?
    @Published var isValid = false
    @Published var errorMessage: String?

    // MARK: - Dependencies
    private let dependencies: AppDependencies

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
    }

    // MARK: - Exchange Credential Management

    /// Select an exchange and reset credential fields.
    func selectExchange(_ exchange: Exchange) {
        guard selectedExchange != exchange else { return }
        selectedExchange = exchange
        apiKey = ""
        apiSecret = ""
        passphrase = ""
        clientId = ""
        validationError = nil
        isValid = false
    }

    /// Validate credentials against the exchange API and save to Keychain if valid.
    func validateCredentials() async {
        guard let exchange = selectedExchange else { return }

        isValidating = true
        validationError = nil
        isValid = false

        let credentials = ExchangeCredentials(
            exchange: exchange,
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            apiSecret: apiSecret.trimmingCharacters(in: .whitespacesAndNewlines),
            passphrase: exchange.requiresPassphrase ? passphrase.trimmingCharacters(in: .whitespacesAndNewlines) : nil,
            clientId: exchange.requiresClientId ? clientId.trimmingCharacters(in: .whitespacesAndNewlines) : nil
        )

        do {
            let isSandbox = dependencies.userPreferences.sandboxMode
            let api = dependencies.exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
            let valid = try await api.validateCredentials()

            if valid {
                try saveCredentials(credentials)
                isValid = true
            } else {
                validationError = "Invalid credentials. Please check your API key and secret."
            }
        } catch {
            validationError = error.localizedDescription
        }

        isValidating = false
    }

    /// Save validated credentials to the Keychain.
    func saveCredentials(_ credentials: ExchangeCredentials) throws {
        let isSandbox = dependencies.userPreferences.sandboxMode
        try dependencies.credentialsStore.save(credentials, isSandbox: isSandbox)
    }

    // MARK: - First Plan Creation

    /// Available cryptos based on connected exchange or defaults.
    var availableCryptos: [String] {
        if let exchange = connectedExchange {
            return exchange.supportedCryptos
        }
        return ["BTC", "ETH", "SOL", "ADA", "DOT"]
    }

    /// Available fiats based on connected exchange or defaults.
    var availableFiats: [String] {
        if let exchange = connectedExchange {
            return exchange.supportedFiats
        }
        return ["EUR", "USD", "USDT", "CZK", "GBP"]
    }

    /// Whether a plan can be created with current inputs.
    var canCreatePlan: Bool {
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return false }
        return connectedExchange != nil && !selectedCrypto.isEmpty && !selectedFiat.isEmpty
    }

    /// Create the first DCA plan and insert it into the database.
    func createFirstPlan() async {
        guard let exchange = connectedExchange,
              let amountValue = Decimal(string: amount),
              amountValue > 0
        else { return }

        let now = Date()
        let nextExecution = Calendar.current.date(
            byAdding: .minute,
            value: frequency.intervalMinutes,
            to: now
        )

        let plan = DcaPlan(
            exchange: exchange,
            crypto: selectedCrypto,
            fiat: selectedFiat,
            amount: amountValue,
            frequency: frequency,
            strategy: .classic,
            isEnabled: true,
            createdAt: now,
            nextExecutionAt: nextExecution
        )

        do {
            try dependencies.activeDatabase.planDao.insert(plan)
        } catch {
            errorMessage = String(localized: "Failed to create plan: \(error.localizedDescription)")
        }
    }

    // MARK: - Private Helpers

    /// The first configured exchange, if any.
    private var connectedExchange: Exchange? {
        let isSandbox = dependencies.userPreferences.sandboxMode
        return dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox).first
    }
}
