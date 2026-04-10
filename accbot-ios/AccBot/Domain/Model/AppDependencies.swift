import Combine
import Foundation
import SwiftUI

/// Dependency injection container.
/// Injected via @EnvironmentObject from the root AccBotApp.
@MainActor
final class AppDependencies: ObservableObject {
    /// Shared instance for background tasks (avoids creating duplicate DatabasePools).
    /// Set by AccBotApp on launch, read by DcaBackgroundService.
    static var shared: AppDependencies?

    let database: DcaDatabase
    let sandboxDatabase: DcaDatabase
    let credentialsStore: CredentialsStore
    var userPreferences: UserPreferences
    var onboardingPreferences: OnboardingPreferences
    let networkClient: NetworkClient
    let exchangeApiFactory: ExchangeApiFactory
    let marketDataService: MarketDataService
    let notificationService: NotificationService
    let dcaExecutionEngine: DcaExecutionEngine
    let connectionRepository: ExchangeConnectionRepository
    let sandboxConnectionRepository: ExchangeConnectionRepository
    private var cancellables = Set<AnyCancellable>()

    /// Get the active database based on sandbox mode
    var activeDatabase: DcaDatabase {
        userPreferences.sandboxMode ? sandboxDatabase : database
    }

    /// Get the active connection repository based on sandbox mode
    var activeConnectionRepository: ExchangeConnectionRepository {
        userPreferences.sandboxMode ? sandboxConnectionRepository : connectionRepository
    }

    init() {
        // Initialize core infrastructure
        let credentialsStore = CredentialsStore()
        let userPreferences = UserPreferences(defaults: UserDefaults(suiteName: "com.accbot.dca.preferences") ?? .standard)
        let onboardingPreferences = OnboardingPreferences()
        let networkClient = NetworkClient()
        let exchangeApiFactory = ExchangeApiFactory(
            userPreferences: userPreferences,
            networkClient: networkClient
        )

        // Initialize databases
        let database: DcaDatabase
        let sandboxDatabase: DcaDatabase
        do {
            database = try DcaDatabase.production()
            sandboxDatabase = try DcaDatabase.sandbox()
        } catch {
            fatalError("Failed to initialize database: \(error)")
        }

        // Migrate credentials from v2 (exchange-keyed) to v3 (connectionId-keyed).
        // MUST run synchronously before any credential access.
        credentialsStore.migrateToV3(prodDb: database, sandboxDb: sandboxDatabase)

        // Initialize services
        let marketDataService = MarketDataService(client: networkClient)
        let notificationService = NotificationService()
        let dcaExecutionEngine = DcaExecutionEngine(
            database: database,
            sandboxDatabase: sandboxDatabase,
            credentialsStore: credentialsStore,
            userPreferences: userPreferences,
            exchangeApiFactory: exchangeApiFactory,
            notificationService: notificationService,
            marketDataService: marketDataService
        )

        // Initialize repositories
        let connectionRepository = ExchangeConnectionRepository(
            connectionDao: database.exchangeConnectionDao,
            planDao: database.planDao,
            exchangeBalanceDao: database.exchangeBalanceDao,
            withdrawalThresholdDao: database.withdrawalThresholdDao,
            credentialsStore: credentialsStore,
            userPreferences: userPreferences
        )
        let sandboxConnectionRepository = ExchangeConnectionRepository(
            connectionDao: sandboxDatabase.exchangeConnectionDao,
            planDao: sandboxDatabase.planDao,
            exchangeBalanceDao: sandboxDatabase.exchangeBalanceDao,
            withdrawalThresholdDao: sandboxDatabase.withdrawalThresholdDao,
            credentialsStore: credentialsStore,
            userPreferences: userPreferences
        )

        // Assign properties
        self.database = database
        self.sandboxDatabase = sandboxDatabase
        self.credentialsStore = credentialsStore
        self.userPreferences = userPreferences
        self.onboardingPreferences = onboardingPreferences
        self.networkClient = networkClient
        self.exchangeApiFactory = exchangeApiFactory
        self.marketDataService = marketDataService
        self.notificationService = notificationService
        self.dcaExecutionEngine = dcaExecutionEngine
        self.connectionRepository = connectionRepository
        self.sandboxConnectionRepository = sandboxConnectionRepository

        // Forward onboardingPreferences changes so RootView re-renders
        // when onboarding completes (rare event, no perf concern).
        onboardingPreferences.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }
}
