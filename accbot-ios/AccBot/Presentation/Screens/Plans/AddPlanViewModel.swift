import Foundation
import Combine

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
    @Published var isSubmitting: Bool = false
    @Published var errorMessage: String?

    // MARK: - Private

    private let dependencies: AppDependencies
    private var configuredExchanges: [Exchange] = []

    let amountPresets = [25, 50, 100, 250, 500]

    // MARK: - Init

    init(dependencies: AppDependencies) {
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

        // Withdrawal requires address
        if withdrawalEnabled && withdrawalAddress.trimmingCharacters(in: .whitespaces).isEmpty {
            return false
        }

        return true
    }

    // MARK: - Methods

    func loadConfiguredExchanges() {
        let isSandbox = dependencies.userPreferences.sandboxMode
        configuredExchanges = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)

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
                createdAt: now,
                nextExecutionAt: nextExecution
            )

            try dependencies.activeDatabase.planDao.insert(plan)
            isSubmitting = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isSubmitting = false
            return false
        }
    }
}
