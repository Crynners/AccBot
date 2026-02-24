import Foundation

@MainActor
class ExchangeManagementViewModel: ObservableObject {
    @Published var connectedExchanges: [Exchange] = []
    @Published var availableExchanges: [Exchange] = []
    @Published var errorMessage: String?

    private let dependencies: AppDependencies

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
    }

    // MARK: - Loading

    func loadExchanges() {
        let isSandbox = dependencies.userPreferences.sandboxMode
        let configured = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
        let allAvailable = ExchangeFilter.getAvailableExchanges(isSandboxMode: isSandbox)

        connectedExchanges = configured
        availableExchanges = allAvailable.filter { !configured.contains($0) }
    }

    // MARK: - Actions

    func deleteExchange(_ exchange: Exchange) {
        let isSandbox = dependencies.userPreferences.sandboxMode
        dependencies.credentialsStore.delete(exchange: exchange, isSandbox: isSandbox)

        // Clean up cached balances for this exchange
        do {
            let balances = try dependencies.activeDatabase.exchangeBalanceDao.getBalancesByExchange(exchange)
            // Balances will be stale; just reload the lists
            _ = balances
        } catch {
            // Non-critical error, proceed with reload
        }

        loadExchanges()
    }
}
