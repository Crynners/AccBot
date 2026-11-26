import Foundation
import XCTest
@testable import AccBot

final class SharedPlanCodecTests: XCTestCase {
    private let shareId = "7f0c1d2e-3b4a-4c5d-8e9f-0a1b2c3d4e5f"
    private let t0 = Date(timeIntervalSince1970: 1_767_258_000) // 2026-01-01 09:00 UTC
    private let day: TimeInterval = 86_400

    private func d(_ value: String) -> Decimal { Decimal(string: value, locale: Locale(identifier: "en_US_POSIX"))! }

    private lazy var plan = DcaPlan(
        id: 7, name: "Spoření", exchange: .coinmate, crypto: "BTC", fiat: "CZK",
        amount: 500, frequency: .daily, createdAt: t0
    )

    private func tx(_ id: Int64, crypto: Decimal, fiat: Decimal, status: TransactionStatus = .completed) -> Transaction {
        Transaction(
            id: id, planId: 7, exchange: .coinmate, crypto: "BTC", fiat: "CZK",
            fiatAmount: fiat, cryptoAmount: crypto, price: crypto > 0 ? fiat / crypto : 0,
            fee: 2.5, feeAsset: "CZK", status: status, exchangeOrderId: "secret-order-\(id)",
            executedAt: t0.addingTimeInterval(Double(id) * day)
        )
    }

    private lazy var transactions = [
        tx(1, crypto: d("0.0004"), fiat: 500),
        tx(2, crypto: d("0.0005"), fiat: 500),
        tx(3, crypto: d("0.0005"), fiat: 500, status: .failed),
        tx(4, crypto: 0, fiat: 500, status: .pending),
    ]

    private func snapshot(includeAmounts: Bool = true, at offset: TimeInterval = 5 * 86_400) -> SharedPlanSnapshotDto {
        SharedPlanCodec.createSnapshot(
            plan: plan, transactions: transactions, shareId: shareId, isSandbox: false,
            includeAmounts: includeAmounts, now: t0.addingTimeInterval(offset)
        )
    }

    // A fast stand-in for BackupCryptoUseCase with the same failure behaviour.
    private func fakeEncrypt(_ data: Data, _ password: String) throws -> String {
        (Data(password.utf8) + Data([0]) + data).base64EncodedString()
    }

    private func fakeDecrypt(_ payload: String, _ password: String) throws -> Data {
        guard let bytes = Data(base64Encoded: payload), let split = bytes.firstIndex(of: 0),
              String(decoding: bytes[..<split], as: UTF8.self) == password else {
            throw BackupCryptoError.wrongPassword
        }
        return bytes[(split + 1)...]
    }

    func testOrderRefMatchesAndroid() {
        XCTAssertEqual(SharedPlanCodec.orderRef(exchange: "COINMATE", orderId: "123"), "1e8411459a0b339aa00e81a3fc630a84")
    }

    func testSnapshotRoundTripKeepsOnlyFillsAndNoSecrets() throws {
        let data = try SharedPlanCodec.encodeShare(snapshot(), access: nil, password: nil, encrypt: fakeEncrypt)
        let json = String(decoding: data, as: UTF8.self)
        XCTAssertFalse(json.contains("secret-order"))

        let decoded = try SharedPlanCodec.decodeShare(data, password: nil, decrypt: fakeDecrypt)
        XCTAssertNil(decoded.credentials)
        XCTAssertEqual(decoded.plan.trades.count, 2)
        XCTAssertEqual(decoded.plan.summary.totalInvested, 1000)
        XCTAssertEqual(decoded.plan.trades.first?.ref, SharedPlanCodec.orderRef(exchange: "COINMATE", orderId: "secret-order-1"))
        XCTAssertFalse(decoded.plan.liveUpdates)
    }

    func testHiddenAmountsAreLeftOut() throws {
        let data = try SharedPlanCodec.encode(snapshot(includeAmounts: false))
        let decoded = try SharedPlanCodec.decodeShare(data, password: nil, decrypt: fakeDecrypt).plan
        XCTAssertNil(decoded.amount)
        XCTAssertNil(decoded.trades.first?.cryptoAmount)
        XCTAssertNil(decoded.summary.totalInvested)
        XCTAssertNotNil(decoded.summary.avgBuyPrice)
    }

    func testLiveShareIsEncryptedAndCarriesKey() throws {
        let access = SharedPlanAccessDto(exchange: "COINMATE", apiKey: "ro-key", apiSecret: "ro-secret", clientId: "42", verifiedReadOnly: false)
        let data = try SharedPlanCodec.encodeShare(snapshot(), access: access, password: "correct horse", encrypt: fakeEncrypt)
        let outer = try JSONDecoder().decode(SharedPlanSnapshotDto.self, from: data)
        XCTAssertEqual(outer.encrypted, true)
        XCTAssertNil(outer.access)
        XCTAssertNil(outer.plan)

        XCTAssertThrowsError(try SharedPlanCodec.decodeShare(data, password: nil, decrypt: fakeDecrypt)) {
            XCTAssertEqual($0 as? SharedPlanError, .passwordRequired)
        }
        XCTAssertThrowsError(try SharedPlanCodec.decodeShare(data, password: "nope", decrypt: fakeDecrypt)) {
            XCTAssertEqual($0 as? SharedPlanError, .wrongPassword)
        }

        let decoded = try SharedPlanCodec.decodeShare(data, password: "correct horse", decrypt: fakeDecrypt)
        XCTAssertEqual(decoded.credentials?.apiKey, "ro-key")
        XCTAssertEqual(decoded.credentials?.clientId, "42")
        XCTAssertTrue(decoded.plan.liveUpdates)

        let stored = try SharedPlanCodec.encode(decoded.plan)
        XCTAssertFalse(String(decoding: stored, as: UTF8.self).contains("ro-key"))
        XCTAssertTrue(try SharedPlanCodec.decode(stored).liveUpdates)
        XCTAssertFalse(decoded.plan.fullAccessKey)
    }

    func testPlanKeyShareIsMarkedAsFullAccess() throws {
        let access = SharedPlanAccessDto(
            exchange: "COINMATE", apiKey: "plan-key", apiSecret: "plan-secret", clientId: "42",
            verifiedReadOnly: false, fullAccess: true
        )
        let data = try SharedPlanCodec.encodeShare(snapshot(), access: access, password: "correct horse", encrypt: fakeEncrypt)
        XCTAssertFalse(String(decoding: data, as: UTF8.self).contains("plan-secret"))

        let decoded = try SharedPlanCodec.decodeShare(data, password: "correct horse", decrypt: fakeDecrypt)
        XCTAssertTrue(decoded.plan.liveUpdates)
        XCTAssertTrue(decoded.plan.fullAccessKey)
        XCTAssertFalse(decoded.plan.verifiedReadOnly)

        let stored = try SharedPlanCodec.encode(decoded.plan)
        XCTAssertFalse(String(decoding: stored, as: UTF8.self).contains("plan-key"))
        XCTAssertTrue(try SharedPlanCodec.decode(stored).fullAccessKey)
    }

    func testLiveShareWithRealBackupCrypto() throws {
        let crypto = BackupCryptoUseCase(bip39: Bip39WordList())
        let access = SharedPlanAccessDto(exchange: "COINMATE", apiKey: "k", apiSecret: "s", clientId: "1")
        let data = try SharedPlanCodec.encodeShare(snapshot(), access: access, password: "long password") {
            try crypto.encrypt(plaintext: $0, passphrase: $1)
        }
        let decoded = try SharedPlanCodec.decodeShare(data, password: "long password") {
            try crypto.decrypt(base64Data: $0, passphrase: $1)
        }
        XCTAssertEqual(decoded.credentials?.apiSecret, "s")
    }

    func testPlainShareMustNotCarryKey() throws {
        var leaked = snapshot()
        leaked.access = SharedPlanAccessDto(exchange: "COINMATE", apiKey: "k", apiSecret: "s")
        let data = try SharedPlanCodec.encode(leaked)
        XCTAssertThrowsError(try SharedPlanCodec.decodeShare(data, password: nil, decrypt: fakeDecrypt)) {
            XCTAssertEqual($0 as? SharedPlanError, .invalid)
        }
    }

    func testRefreshMergeSkipsSnapshotAndDuplicates() throws {
        let shared = try SharedPlanCodec.decode(SharedPlanCodec.encode(snapshot()))
        func fill(_ id: String, _ days: Double, _ crypto: Decimal, _ fiat: Decimal, crypto symbol: String = "BTC") -> HistoricalTrade {
            HistoricalTrade(
                orderId: id, timestamp: t0.addingTimeInterval(days * day), crypto: symbol, fiat: "CZK",
                cryptoAmount: crypto, fiatAmount: fiat, price: 1, fee: 1, feeAsset: "CZK", side: "BUY"
            )
        }
        let merged = RefreshSharedPlanUseCase.merge(shared, fetched: [
            fill("98765", 2, d("0.0005"), 500),    // trade ID of a snapshot order
            fill("new-1", 6, d("0.0003"), 300),    // two fills reported under one ID
            fill("new-1", 6, d("0.0002"), 200),
            fill("other", 7, 1, 1, crypto: "ETH"),
        ])
        XCTAssertEqual(merged.count, shared.trades.count + 1)
        XCTAssertEqual(merged.last?.cryptoAmount, d("0.0005"))
        XCTAssertEqual(merged.last?.fiatAmount, 500)

        var refreshed = shared
        refreshed.trades = merged
        XCTAssertEqual(RefreshSharedPlanUseCase.merge(refreshed, fetched: [fill("new-1", 6, d("0.0005"), 500)]).count, merged.count)
    }

    @MainActor
    func testStoreKeepsKeysOutOfFiles() throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: folder) }
        let vault = FakeVault()
        let store = SharedPlanStore(directory: folder, defaults: UserDefaults(suiteName: UUID().uuidString)!, keys: vault)
        let plan = try SharedPlanCodec.decode(SharedPlanCodec.encode(snapshot()))
        let key = ExchangeCredentials(exchange: .coinmate, apiKey: "read", apiSecret: "only", clientId: "1")

        try store.save(plan, credentials: key)
        XCTAssertEqual(store.credentials(shareId), key)
        XCTAssertTrue(store.plans.first?.liveUpdates == true)
        let file = try String(contentsOf: folder.appendingPathComponent("\(shareId).json"))
        XCTAssertFalse(file.contains("only"))

        let older = try SharedPlanCodec.decode(SharedPlanCodec.encode(snapshot(at: 86_400)))
        guard case .outdated = try store.save(older) else { return XCTFail("Older snapshot replaced a newer one") }

        let newer = try SharedPlanCodec.decode(SharedPlanCodec.encode(snapshot(at: 9 * 86_400)))
        try store.save(newer)
        XCTAssertNil(store.credentials(shareId))
        XCTAssertFalse(store.plans.first?.liveUpdates ?? true)

        try store.save(newer, credentials: key)
        store.delete(shareId)
        XCTAssertTrue(store.plans.isEmpty)
        XCTAssertNil(vault.get(shareId))
    }
}

private final class FakeVault: SharedPlanKeyVault {
    private var keys: [String: ExchangeCredentials] = [:]
    func get(_ shareId: String) -> ExchangeCredentials? { keys[shareId] }
    func put(_ shareId: String, credentials: ExchangeCredentials) throws { keys[shareId] = credentials }
    func remove(_ shareId: String) { keys.removeValue(forKey: shareId) }
}
