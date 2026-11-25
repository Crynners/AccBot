import Foundation
import Security

/// Storage of the read-only exchange keys that came with live-update shares.
protocol SharedPlanKeyVault {
    func get(_ shareId: String) -> ExchangeCredentials?
    func put(_ shareId: String, credentials: ExchangeCredentials) throws
    func remove(_ shareId: String)
}

/// Local storage for read-only plan shares (same behaviour as Android's `SharedPlanStore`).
///
/// Received plans live as JSON files in `Application Support/shared_plans/<shareId>.json`,
/// outside GRDB, so they never mix with the user's own plans, portfolio or backups. Files
/// never contain a key: the read-only key of a live-update share is kept in the Keychain
/// under its own service, apart from the user's own exchange connections, so nothing that
/// trades or withdraws can pick it up.
///
/// The sender side keeps a stable share ID per own plan so that re-sharing replaces the
/// previous snapshot on the recipient's device instead of adding a duplicate.
@MainActor
final class SharedPlanStore: ObservableObject {
    static let shared = SharedPlanStore()

    enum SaveResult: Equatable {
        case saved(SharedPlan, replaced: Bool)
        /// A newer snapshot of the same plan is already stored; nothing changed.
        case outdated(SharedPlan)
    }

    /// Newest share first.
    @Published private(set) var plans: [SharedPlan] = []

    private let directory: URL
    private let defaults: UserDefaults
    private let keys: SharedPlanKeyVault

    init(directory: URL? = nil, defaults: UserDefaults = .standard, keys: SharedPlanKeyVault = KeychainSharedPlanKeyVault()) {
        self.directory = directory ?? FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("shared_plans", isDirectory: true)
        self.defaults = defaults
        self.keys = keys
        reload()
    }

    func get(_ shareId: String) -> SharedPlan? {
        plans.first { $0.shareId == shareId.lowercased() }
    }

    /// Store a received share. A live-update share passes its read-only key; a plain share
    /// removes any key stored for an earlier version of the same plan.
    @discardableResult
    func save(_ plan: SharedPlan, credentials: ExchangeCredentials? = nil) throws -> SaveResult {
        let existing = get(plan.shareId)
        if let existing, existing.sharedAt > plan.sharedAt {
            return .outdated(existing)
        }
        var stored = plan
        stored.liveUpdates = credentials != nil
        if credentials == nil {
            stored.verifiedReadOnly = false
        }
        if let credentials {
            try keys.put(plan.shareId, credentials: credentials)
        } else {
            keys.remove(plan.shareId)
        }
        try write(stored)
        reload()
        return .saved(stored, replaced: existing != nil)
    }

    /// Replace the stored copy (e.g. after a refresh), keeping its key.
    func update(_ plan: SharedPlan) throws {
        try write(plan)
        reload()
    }

    func credentials(_ shareId: String) -> ExchangeCredentials? {
        guard SharedPlanCodec.isValidShareId(shareId) else { return nil }
        return keys.get(shareId.lowercased())
    }

    func delete(_ shareId: String) {
        guard SharedPlanCodec.isValidShareId(shareId) else { return }
        try? FileManager.default.removeItem(at: fileURL(for: shareId.lowercased()))
        keys.remove(shareId.lowercased())
        reload()
    }

    /// Stable share ID for one of the user's own plans in the given environment.
    func shareIdForOwnPlan(_ planId: Int64, isSandbox: Bool) -> String {
        let key = "plan_share_id_\(isSandbox ? "sandbox" : "prod")_\(planId)"
        if let stored = defaults.string(forKey: key), SharedPlanCodec.isValidShareId(stored) {
            return stored
        }
        let created = UUID().uuidString.lowercased()
        defaults.set(created, forKey: key)
        return created
    }

    func reload() {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: nil
        )) ?? []
        plans = files
            .filter { $0.pathExtension == "json" }
            .compactMap { url -> SharedPlan? in
                guard let data = try? Data(contentsOf: url),
                      let plan = try? SharedPlanCodec.decode(data),
                      url.lastPathComponent == "\(plan.shareId).json" else { return nil }
                return plan
            }
            .sorted { $0.sharedAt > $1.sharedAt }
    }

    private func write(_ plan: SharedPlan) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var target = fileURL(for: plan.shareId)
        try SharedPlanCodec.encode(plan).write(
            to: target, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
        )
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? target.setResourceValues(values)
    }

    private func fileURL(for shareId: String) -> URL {
        precondition(SharedPlanCodec.isValidShareId(shareId))
        return directory.appendingPathComponent("\(shareId).json")
    }
}

/// Device-bound Keychain storage of shared read-only keys, separate from `CredentialsStore`.
struct KeychainSharedPlanKeyVault: SharedPlanKeyVault {
    private let service = "com.accbot.dca.sharedPlanKeys"

    func get(_ shareId: String) -> ExchangeCredentials? {
        var lookup = query(shareId)
        lookup[kSecReturnData as String] = true
        lookup[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(lookup as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(ExchangeCredentials.self, from: data)
    }

    func put(_ shareId: String, credentials: ExchangeCredentials) throws {
        let attributes: [String: Any] = [
            kSecValueData as String: try JSONEncoder().encode(credentials),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        var status = SecItemUpdate(query(shareId) as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query(shareId).merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw CredentialsError.saveFailed(status) }
    }

    func remove(_ shareId: String) {
        _ = SecItemDelete(query(shareId) as CFDictionary)
    }

    private func query(_ shareId: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: shareId,
        ]
    }
}
