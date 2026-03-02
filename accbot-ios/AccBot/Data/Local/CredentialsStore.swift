import Foundation
import Security

/// iOS Keychain wrapper for storing exchange API credentials.
/// Maps from Android's EncryptedSharedPreferences (AES-256-GCM via Android Keystore).
/// Uses kSecAttrAccessibleWhenUnlockedThisDeviceOnly - never backed up, never migrated.
final class CredentialsStore {

    private let service = "com.accbot.dca.credentials"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    // MARK: - Public API

    func save(_ credentials: ExchangeCredentials, isSandbox: Bool) throws {
        let key = storageKey(for: credentials.exchange, isSandbox: isSandbox)
        let data = try encoder.encode(credentials)

        // Delete existing first
        deleteItem(key: key)

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw CredentialsError.saveFailed(status)
        }
    }

    func get(for exchange: Exchange, isSandbox: Bool) -> ExchangeCredentials? {
        let key = storageKey(for: exchange, isSandbox: isSandbox)

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

    func has(exchange: Exchange, isSandbox: Bool) -> Bool {
        get(for: exchange, isSandbox: isSandbox) != nil
    }

    func delete(exchange: Exchange, isSandbox: Bool) {
        let key = storageKey(for: exchange, isSandbox: isSandbox)
        deleteItem(key: key)
    }

    func getConfiguredExchanges(isSandbox: Bool) -> [Exchange] {
        Exchange.allCases.filter { has(exchange: $0, isSandbox: isSandbox) }
    }

    func clearAll(isSandbox: Bool) {
        for exchange in Exchange.allCases {
            delete(exchange: exchange, isSandbox: isSandbox)
        }
    }

    func clearAllBothEnvironments() {
        clearAll(isSandbox: false)
        clearAll(isSandbox: true)
    }

    // MARK: - Private

    private func storageKey(for exchange: Exchange, isSandbox: Bool) -> String {
        let env = isSandbox ? "sandbox" : "prod"
        return "credentials_\(env)_\(exchange.rawValue)"
    }

    private func deleteItem(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
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
