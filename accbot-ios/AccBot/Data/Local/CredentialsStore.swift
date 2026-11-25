import Foundation
import Security

/// Device-bound Keychain storage for the credentials used by background DCA.
///
/// Security trade-off: items use `AfterFirstUnlockThisDeviceOnly` (older items with
/// `WhenUnlockedThisDeviceOnly` are migrated on read). Background purchases run while the
/// device is locked, which `WhenUnlocked` would block. The cost is that after the first unlock
/// since boot the keys stay readable while the device is locked. They still never leave the
/// device: no iCloud Keychain sync and no backup restore onto another device.
final class CredentialsStore {
    private let service = "com.accbot.dca.credentials"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    func save(_ credentials: ExchangeCredentials, isSandbox: Bool) throws {
        let query = itemQuery(for: credentials.exchange, isSandbox: isSandbox)
        let attributes: [String: Any] = [
            kSecValueData as String: try encoder.encode(credentials),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
            if status == errSecDuplicateItem {
                status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
            }
        }
        guard status == errSecSuccess else {
            throw CredentialsError.saveFailed(status)
        }
    }

    func get(for exchange: Exchange, isSandbox: Bool) -> ExchangeCredentials? {
        try? load(for: exchange, isSandbox: isSandbox)
    }

    func load(for exchange: Exchange, isSandbox: Bool) throws -> ExchangeCredentials? {
        let query = itemQuery(for: exchange, isSandbox: isSandbox)
        var lookup = query
        lookup[kSecReturnData as String] = true
        lookup[kSecReturnAttributes as String] = true
        lookup[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(lookup as CFDictionary, &result)
        switch status {
        case errSecItemNotFound: return nil
        case errSecInteractionNotAllowed, errSecNotAvailable:
            throw CredentialsError.temporarilyUnavailable
        case errSecSuccess: break
        default: throw CredentialsError.loadFailed(status)
        }

        guard let item = result as? [String: Any], let data = item[kSecValueData as String] as? Data else {
            throw CredentialsError.loadFailed(errSecDecode)
        }
        let credentials = try decoder.decode(ExchangeCredentials.self, from: data)
        // Migrate an accessible legacy item in place, without replacing its contents.
        if (item[kSecAttrAccessible as String] as? String) == (kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String) {
            let protection = [kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
            _ = SecItemUpdate(query as CFDictionary, protection as CFDictionary)
        }
        return credentials
    }

    func has(exchange: Exchange, isSandbox: Bool) -> Bool {
        get(for: exchange, isSandbox: isSandbox) != nil
    }

    func delete(exchange: Exchange, isSandbox: Bool) {
        let query = itemQuery(for: exchange, isSandbox: isSandbox)
        _ = SecItemDelete(query as CFDictionary)
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

    private func itemQuery(for exchange: Exchange, isSandbox: Bool) -> [String: Any] {
        let environment = isSandbox ? "sandbox" : "prod"
        return [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "credentials_\(environment)_\(exchange.rawValue)",
        ]
    }
}

enum CredentialsError: LocalizedError {
    case saveFailed(OSStatus)
    case loadFailed(OSStatus)
    case temporarilyUnavailable

    var errorDescription: String? {
        switch self {
        case .saveFailed(let status):
            return "Failed to save credentials: OSStatus \(status)"
        case .loadFailed(let status):
            return "Failed to load credentials: OSStatus \(status)"
        case .temporarilyUnavailable:
            return String(localized: "Unlock the device to make exchange credentials available.")
        }
    }
}
