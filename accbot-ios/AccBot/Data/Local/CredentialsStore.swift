import Foundation
import Security
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "CredentialsStore")

/// iOS Keychain wrapper for storing exchange API credentials.
///
/// **Connection-keyed (v3+):** each credential set is keyed by a `connectionId`
/// (from ExchangeConnectionRecord) plus an environment prefix:
///   `credentials_v3_prod_{connectionId}` or `credentials_v3_sandbox_{connectionId}`.
///
/// The env prefix is required because production and sandbox each have their own
/// database file with independent autoincrement IDs (connection #1 in prod and
/// connection #1 in sandbox are different connections).
///
/// Migration history:
/// - v2 (legacy iOS): `credentials_{env}_{EXCHANGE}` (one set per exchange)
/// - v3 (this version): `credentials_v3_{env}_{connectionId}`
///
/// Uses kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly so background tasks
/// (BGTask, Shortcuts automations) can read credentials while the device is locked.
final class CredentialsStore {

    private let service = "com.accbot.dca.credentials"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private static let accessibilityMigrationKey = "credentials_migrated_afterFirstUnlock"
    private static let v3MigrationKey = "credentials_migrated_v3"

    init() {
        migrateAccessibilityIfNeeded()
    }

    // MARK: - Connection-keyed API (v3)

    func save(_ credentials: ExchangeCredentials, connectionId: Int64, isSandbox: Bool) throws {
        let key = v3Key(connectionId: connectionId, isSandbox: isSandbox)
        let data = try encoder.encode(credentials)
        deleteItem(key: key)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw CredentialsError.saveFailed(status)
        }
    }

    func get(connectionId: Int64, isSandbox: Bool) -> ExchangeCredentials? {
        let key = v3Key(connectionId: connectionId, isSandbox: isSandbox)
        return getItem(key: key)
    }

    func has(connectionId: Int64, isSandbox: Bool) -> Bool {
        get(connectionId: connectionId, isSandbox: isSandbox) != nil
    }

    func delete(connectionId: Int64, isSandbox: Bool) {
        let key = v3Key(connectionId: connectionId, isSandbox: isSandbox)
        deleteItem(key: key)
    }

    // MARK: - Legacy exchange-keyed API (shims for transition)

    /// Legacy: save credentials by exchange, resolving the default connection.
    func save(_ credentials: ExchangeCredentials, isSandbox: Bool, using connectionDao: ExchangeConnectionDao) throws {
        let connection = try connectionDao.getDefaultByExchange(credentials.exchange)
        if let connection = connection {
            try save(credentials, connectionId: connection.id, isSandbox: isSandbox)
        } else {
            // Create a default connection and save
            let connectionId = try connectionDao.insert(ExchangeConnection(exchange: credentials.exchange))
            try save(credentials, connectionId: connectionId, isSandbox: isSandbox)
        }
    }

    /// Legacy: get credentials by exchange, resolving the default connection.
    func get(for exchange: Exchange, isSandbox: Bool, using connectionDao: ExchangeConnectionDao) -> ExchangeCredentials? {
        guard let connection = try? connectionDao.getDefaultByExchange(exchange) else { return nil }
        return get(connectionId: connection.id, isSandbox: isSandbox)
    }

    /// Legacy: check if exchange has credentials.
    func has(exchange: Exchange, isSandbox: Bool, using connectionDao: ExchangeConnectionDao) -> Bool {
        get(for: exchange, isSandbox: isSandbox, using: connectionDao) != nil
    }

    /// Legacy: delete credentials by exchange.
    func delete(exchange: Exchange, isSandbox: Bool, using connectionDao: ExchangeConnectionDao) {
        guard let connection = try? connectionDao.getDefaultByExchange(exchange) else { return }
        delete(connectionId: connection.id, isSandbox: isSandbox)
    }

    /// Legacy: list exchanges with stored credentials.
    func getConfiguredExchanges(isSandbox: Bool, using connectionDao: ExchangeConnectionDao) -> [Exchange] {
        guard let connections = try? connectionDao.getAll() else { return [] }
        return connections
            .filter { has(connectionId: $0.id, isSandbox: isSandbox) }
            .map { $0.exchange }
            .uniqued()
    }

    /// Get all connections that have stored credentials.
    func getConfiguredConnections(isSandbox: Bool, using connectionDao: ExchangeConnectionDao) -> [ExchangeConnection] {
        guard let connections = try? connectionDao.getAll() else { return [] }
        return connections.filter { has(connectionId: $0.id, isSandbox: isSandbox) }
    }

    func clearAll(isSandbox: Bool, using connectionDao: ExchangeConnectionDao) {
        guard let connections = try? connectionDao.getAll() else { return }
        for connection in connections {
            delete(connectionId: connection.id, isSandbox: isSandbox)
        }
        // Also clean up any remaining v2 keys
        for exchange in Exchange.allCases {
            deleteItem(key: v2Key(exchange: exchange, isSandbox: isSandbox))
        }
    }

    func clearAllBothEnvironments(using connectionDao: ExchangeConnectionDao) {
        clearAll(isSandbox: false, using: connectionDao)
        clearAll(isSandbox: true, using: connectionDao)
    }

    // MARK: - v2 -> v3 Migration

    /// Migrate credentials from v2 exchange-keyed format to v3 connectionId-keyed format.
    /// Must be called synchronously before first credential access.
    func migrateToV3(prodDb: DcaDatabase, sandboxDb: DcaDatabase) {
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: Self.v3MigrationKey) else { return }

        logger.info("Running CredentialsStore v2->v3 migration")

        do {
            try migrateV2ToV3ForEnv(db: prodDb, isSandbox: false)
            try migrateV2ToV3ForEnv(db: sandboxDb, isSandbox: true)
            defaults.set(true, forKey: Self.v3MigrationKey)
            logger.info("CredentialsStore v2->v3 migration complete")
        } catch {
            // Don't set the flag - next launch will retry
            logger.error("CredentialsStore v2->v3 migration failed, will retry: \(error.localizedDescription)")
        }
    }

    private func migrateV2ToV3ForEnv(db: DcaDatabase, isSandbox: Bool) throws {
        let connectionDao = db.exchangeConnectionDao

        for exchange in Exchange.allCases {
            let oldKey = v2Key(exchange: exchange, isSandbox: isSandbox)
            guard let credentials = getItem(key: oldKey) else { continue }

            // Find or create a default connection for this exchange
            let connectionId: Int64
            if let existing = try connectionDao.getDefaultByExchange(exchange) {
                connectionId = existing.id
            } else {
                connectionId = try connectionDao.insert(ExchangeConnection(exchange: exchange))
            }

            let newKey = v3Key(connectionId: connectionId, isSandbox: isSandbox)

            // Don't overwrite existing v3 key
            if getItem(key: newKey) != nil {
                deleteItem(key: oldKey)
                continue
            }

            // Copy to new key, delete old
            if let data = try? encoder.encode(credentials) {
                deleteItem(key: newKey)
                let query: [String: Any] = [
                    kSecClass as String: kSecClassGenericPassword,
                    kSecAttrService as String: service,
                    kSecAttrAccount as String: newKey,
                    kSecValueData as String: data,
                    kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                ]
                SecItemAdd(query as CFDictionary, nil)
            }
            deleteItem(key: oldKey)
            logger.info("Migrated credentials for \(exchange.rawValue) (sandbox=\(isSandbox)) -> connectionId=\(connectionId)")
        }
    }

    // MARK: - Private

    private func v3Key(connectionId: Int64, isSandbox: Bool) -> String {
        let env = isSandbox ? "sandbox" : "prod"
        return "credentials_v3_\(env)_\(connectionId)"
    }

    private func v2Key(exchange: Exchange, isSandbox: Bool) -> String {
        let env = isSandbox ? "sandbox" : "prod"
        return "credentials_\(env)_\(exchange.rawValue)"
    }

    private func getItem(key: String) -> ExchangeCredentials? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            return nil
        }

        return try? decoder.decode(ExchangeCredentials.self, from: data)
    }

    private func deleteItem(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }

    /// Re-save existing credentials with AfterFirstUnlock accessibility.
    /// Runs once - old items used WhenUnlocked which blocks background access.
    private func migrateAccessibilityIfNeeded() {
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: Self.accessibilityMigrationKey) else { return }
        defaults.set(true, forKey: Self.accessibilityMigrationKey)

        for exchange in Exchange.allCases {
            for isSandbox in [false, true] {
                let key = v2Key(exchange: exchange, isSandbox: isSandbox)
                if let creds = getItem(key: key) {
                    // Re-save with correct accessibility
                    deleteItem(key: key)
                    if let data = try? encoder.encode(creds) {
                        let query: [String: Any] = [
                            kSecClass as String: kSecClassGenericPassword,
                            kSecAttrService as String: service,
                            kSecAttrAccount as String: key,
                            kSecValueData as String: data,
                            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                        ]
                        SecItemAdd(query as CFDictionary, nil)
                    }
                }
            }
        }
    }
}

// MARK: - Errors

enum CredentialsError: LocalizedError {
    case saveFailed(OSStatus)
    case loadFailed(OSStatus)

    var errorDescription: String? {
        switch self {
        case .saveFailed(let status):
            return "Failed to save credentials: OSStatus \(status)"
        case .loadFailed(let status):
            return "Failed to load credentials: OSStatus \(status)"
        }
    }
}

// MARK: - Array uniqued helper

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
