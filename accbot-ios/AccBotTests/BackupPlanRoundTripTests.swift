import Foundation
import XCTest
@testable import AccBot

final class BackupPlanRoundTripTests: XCTestCase {
    func testNamedPlansWithIdenticalSettingsKeepTheirOwnOrdersAfterMerge() throws {
        let fixture = try makeFixture()
        let first = try fixture.database.planDao.insert(DcaPlan(
            name: "Dlouhodobé spoření", displayOrder: 4, exchange: .coinmate,
            crypto: "BTC", fiat: "CZK", amount: 150, frequency: .weekly
        ))
        let second = try fixture.database.planDao.insert(DcaPlan(
            name: "Rezerva", displayOrder: 1, exchange: .coinmate,
            crypto: "BTC", fiat: "CZK", amount: 150, frequency: .weekly
        ))
        for (planId, orderId) in [(first, "first-order"), (second, "second-order")] {
            try fixture.database.transactionDao.insert(Transaction(
                planId: planId, exchange: .coinmate, crypto: "BTC", fiat: "CZK",
                fiatAmount: 150, cryptoAmount: 1, price: 150, fee: 0,
                status: .completed, exchangeOrderId: orderId, executedAt: Date()
            ))
        }
        let options = BackupExportOptions(includeTransactions: true, encryptionMode: .password, password: "test password")
        guard case .success(let json, _, _) = try fixture.exporter.execute(options: options),
              case .previewReady(let preview, let payload) = fixture.importer.parseAndPreview(
                  envelopeJson: json, passphrase: options.password
              ) else { return XCTFail("Unable to preview the saved backup") }

        for mode in [RestoreMode.replace, .merge] {
            guard case .restoreComplete = fixture.importer.restore(
                payload: payload, environment: preview.environment, restoreMode: mode
            ) else { return XCTFail("Unable to restore the saved backup") }
            let plans = try fixture.database.planDao.getAll()
            XCTAssertEqual(plans.map(\.name), ["Rezerva", "Dlouhodobé spoření"])
            XCTAssertEqual(plans.map(\.isEnabled), [false, false])
            XCTAssertEqual(try fixture.database.transactionDao.getByPlanId(plans[0].id).map(\.exchangeOrderId), ["second-order"])
            XCTAssertEqual(try fixture.database.transactionDao.getByPlanId(plans[1].id).map(\.exchangeOrderId), ["first-order"])
        }
    }

    private func makeFixture() throws -> (
        database: DcaDatabase, exporter: CreateBackupUseCase, importer: RestoreBackupUseCase
    ) {
        let name = UUID().uuidString
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        addTeardownBlock {
            defaults.removePersistentDomain(forName: name)
            try FileManager.default.removeItem(at: folder)
        }
        let database = try DcaDatabase(path: folder.appendingPathComponent("accbot.sqlite").path)
        let preferences = UserPreferences(defaults: defaults)
        let credentials = CredentialsStore()
        let crypto = BackupCryptoUseCase(bip39: Bip39WordList())
        let collector = BackupDataCollector(database: database, credentialsStore: credentials, userPreferences: preferences)
        let restorer = BackupDataRestorer(database: database, credentialsStore: credentials, userPreferences: preferences)
        return (
            database,
            CreateBackupUseCase(collector: collector, crypto: crypto),
            RestoreBackupUseCase(restorer: restorer, crypto: crypto, userPreferences: preferences)
        )
    }
}
