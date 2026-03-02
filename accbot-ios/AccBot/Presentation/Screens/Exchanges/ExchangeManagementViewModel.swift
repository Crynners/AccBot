import Foundation

@MainActor
class ExchangeManagementViewModel: ObservableObject {
    @Published var connectedExchanges: [Exchange] = []
    @Published var availableExchanges: [Exchange] = []
    private(set) var dependencies: AppDependencies?
    private var isSetUp = false

    private var deps: AppDependencies {
        guard let d = dependencies else {
            preconditionFailure("ViewModel used before setup() — call setup() in onAppear")
        }
        return d
    }

    func setup(_ dependencies: AppDependencies) {
        guard !isSetUp else { return }
        isSetUp = true
        self.dependencies = dependencies
        loadExchanges()
    }

    // MARK: - Loading

    func loadExchanges() {
        let isSandbox = deps.userPreferences.sandboxMode
        let configured = deps.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
        let allAvailable = ExchangeFilter.getAvailableExchanges(isSandboxMode: isSandbox)

        connectedExchanges = configured
        availableExchanges = allAvailable.filter { !configured.contains($0) }
    }
}
