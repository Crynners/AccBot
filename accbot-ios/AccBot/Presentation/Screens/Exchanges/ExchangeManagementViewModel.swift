import Foundation

@MainActor
class ExchangeManagementViewModel: ObservableObject {
    @Published var connectedConnections: [ExchangeConnection] = []
    @Published var availableExchanges: [Exchange] = []
    @Published var showExperimental: Bool = false
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() - call setup() in onAppear")
        }
        return d
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        showExperimental = dependencies.userPreferences.showExperimentalExchanges
        loadExchanges()
    }

    // MARK: - Loading

    func loadExchanges() {
        let isSandbox = deps.userPreferences.sandboxMode
        connectedConnections = deps.credentialsStore.getConfiguredConnections(
            isSandbox: isSandbox,
            using: deps.activeDatabase.exchangeConnectionDao
        )
        availableExchanges = ExchangeFilter.getAvailableExchanges(
            isSandboxMode: isSandbox,
            showExperimental: showExperimental
        )
    }

    func setExperimentalEnabled(_ enabled: Bool) {
        showExperimental = enabled
        deps.userPreferences.showExperimentalExchanges = enabled
        loadExchanges()
    }
}
