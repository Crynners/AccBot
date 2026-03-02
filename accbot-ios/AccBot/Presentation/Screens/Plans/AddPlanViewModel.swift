import Foundation
import Combine
import UIKit

@MainActor
class AddPlanViewModel: ObservableObject {
    // MARK: - Published Properties

    @Published var selectedExchange: Exchange?
    @Published var selectedCrypto: String = "BTC"
    @Published var selectedFiat: String = "EUR"
    @Published var amount: String = ""
    @Published var selectedFrequency: DcaFrequency = .daily
    @Published var cronExpression: String = ""
    @Published var selectedStrategy: DcaStrategy = .classic
    @Published var withdrawalEnabled: Bool = false
    @Published var withdrawalAddress: String = ""
    @Published var targetAmount: String = ""
    @Published var isSubmitting: Bool = false
    @Published var errorMessage: String?

    // MARK: - Private

    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }
    private var configuredExchanges: [Exchange] = []

    let amountPresets = [25, 50, 100, 250, 500]

    // MARK: - Init

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        loadConfiguredExchanges()
    }

    // MARK: - Computed Properties

    var availableExchanges: [Exchange] {
        configuredExchanges
    }

    var availableCryptos: [String] {
        selectedExchange?.supportedCryptos ?? []
    }

    var availableFiats: [String] {
        selectedExchange?.supportedFiats ?? []
    }

    var minOrderSizeDisplay: String? {
        guard let exchange = selectedExchange,
              let minSize = exchange.minOrderSize[selectedFiat] else { return nil }
        return "\(minSize) \(selectedFiat)"
    }

    var monthlyCostEstimate: Decimal? {
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return nil }
        let intervalMinutes: Int
        if selectedFrequency == .custom {
            guard let est = CronUtils.getIntervalMinutesEstimate(cron: cronExpression), est > 0 else {
                return nil
            }
            intervalMinutes = est
        } else {
            intervalMinutes = selectedFrequency.intervalMinutes
            guard intervalMinutes > 0 else { return nil }
        }
        let minutesPerMonth: Decimal = 43200 // 30 days * 24 hours * 60 minutes
        let executionsPerMonth = minutesPerMonth / Decimal(intervalMinutes)
        return amountValue * executionsPerMonth
    }

    var hasChanges: Bool {
        selectedExchange != nil || !amount.isEmpty || withdrawalEnabled || !withdrawalAddress.isEmpty
    }

    var isValid: Bool {
        guard selectedExchange != nil else { return false }
        guard !selectedCrypto.isEmpty, !selectedFiat.isEmpty else { return false }
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return false }

        // Check minimum order size
        if let exchange = selectedExchange,
           let minSize = exchange.minOrderSize[selectedFiat],
           amountValue < minSize {
            return false
        }

        // Custom frequency requires valid cron
        if selectedFrequency == .custom && !CronUtils.isValid(cron: cronExpression) {
            return false
        }

        // Withdrawal requires a valid address (minimum 26 characters for crypto addresses)
        if withdrawalEnabled {
            let trimmed = withdrawalAddress.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || trimmed.count < 26 {
                return false
            }
        }

        return true
    }

    /// Returns a user-facing hint explaining why the form is invalid, or nil if valid.
    var validationHint: String? {
        guard selectedExchange != nil else { return nil } // don't show hint until exchange picked
        if amount.isEmpty {
            return String(localized: "Enter a purchase amount")
        }
        guard let amountValue = Decimal(string: amount), amountValue > 0 else {
            return String(localized: "Enter a valid amount greater than 0")
        }
        if let exchange = selectedExchange,
           let minSize = exchange.minOrderSize[selectedFiat],
           amountValue < minSize {
            return String(localized: "Amount below minimum order size (\("\(minSize)") \(selectedFiat))")
        }
        if selectedFrequency == .custom && !CronUtils.isValid(cron: cronExpression) {
            return String(localized: "Enter a valid cron expression for custom frequency")
        }
        if withdrawalEnabled {
            let trimmed = withdrawalAddress.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty {
                return String(localized: "Enter a withdrawal wallet address")
            }
            if trimmed.count < 26 {
                return String(localized: "Wallet address is too short (minimum 26 characters)")
            }
        }
        return nil
    }

    // MARK: - Methods

    func loadConfiguredExchanges() {
        let isSandbox = deps.userPreferences.sandboxMode
        configuredExchanges = deps.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)

        // Auto-select first exchange if only one configured
        if configuredExchanges.count == 1 {
            selectExchange(configuredExchanges[0])
        }
    }

    func selectExchange(_ exchange: Exchange) {
        selectedExchange = exchange

        // Reset crypto/fiat if not supported by this exchange
        if !exchange.supportedCryptos.contains(selectedCrypto) {
            selectedCrypto = exchange.supportedCryptos.first ?? "BTC"
        }
        if !exchange.supportedFiats.contains(selectedFiat) {
            selectedFiat = exchange.supportedFiats.first ?? "EUR"
        }
    }

    func createPlan() async -> Bool {
        guard isValid, let exchange = selectedExchange else { return false }
        guard let amountValue = Decimal(string: amount) else { return false }

        isSubmitting = true
        errorMessage = nil

        do {
            let now = Date()
            let nextExecution: Date?

            if selectedFrequency == .custom {
                nextExecution = CronUtils.getNextExecution(cron: cronExpression, from: now)
            } else {
                nextExecution = Calendar.current.date(
                    byAdding: .minute,
                    value: selectedFrequency.intervalMinutes,
                    to: now
                )
            }

            let plan = DcaPlan(
                exchange: exchange,
                crypto: selectedCrypto,
                fiat: selectedFiat,
                amount: amountValue,
                frequency: selectedFrequency,
                cronExpression: selectedFrequency == .custom ? cronExpression : nil,
                strategy: selectedStrategy,
                isEnabled: true,
                withdrawalEnabled: withdrawalEnabled,
                withdrawalAddress: withdrawalEnabled
                    ? withdrawalAddress.trimmingCharacters(in: .whitespaces)
                    : nil,
                targetAmount: targetAmount.isEmpty ? nil : Decimal(string: targetAmount),
                createdAt: now,
                nextExecutionAt: nextExecution
            )

            try deps.activeDatabase.planDao.insert(plan)
            isSubmitting = false
            announceForVoiceOver(String(localized: "DCA plan created successfully"))
            return true
        } catch {
            errorMessage = error.localizedDescription
            isSubmitting = false
            announceForVoiceOver(String(localized: "Error: \(error.localizedDescription)"))
            return false
        }
    }

    private func announceForVoiceOver(_ message: String) {
        UIAccessibility.post(notification: .announcement, argument: message)
    }
}
