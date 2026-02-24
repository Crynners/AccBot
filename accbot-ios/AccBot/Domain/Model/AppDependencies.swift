import Foundation
import SwiftUI

/// Dependency injection container.
/// Injected via @EnvironmentObject from the root AccBotApp.
@MainActor
final class AppDependencies: ObservableObject {
    let database: DcaDatabase
    let sandboxDatabase: DcaDatabase
    let credentialsStore: CredentialsStore
    let userPreferences: UserPreferences
    let onboardingPreferences: OnboardingPreferences
    let networkClient: NetworkClient
    let exchangeApiFactory: ExchangeApiFactory
    let marketDataService: MarketDataService
    let notificationService: NotificationService
    let dcaExecutionEngine: DcaExecutionEngine

    /// Get the active database based on sandbox mode
    var activeDatabase: DcaDatabase {
        userPreferences.sandboxMode ? sandboxDatabase : database
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
    }
}
