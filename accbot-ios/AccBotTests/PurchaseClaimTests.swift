import Foundation
import XCTest
@testable import AccBot

final class PurchaseClaimTests: XCTestCase {
    func testUnidentifiedPurchaseSurvivesReopeningAndBlocksThePlanUntilReviewed() throws {
        let path = try databasePath()
        let now = Date(timeIntervalSince1970: 2_000_000_000)
        let nextExecution = now.addingTimeInterval(3600)
        let planId = try createPendingPurchase(path: path, now: now, nextExecution: nextExecution)

        let db = try DcaDatabase(path: path)
        let updatedPlan = try XCTUnwrap(db.planDao.getById(planId))
        let pending = try XCTUnwrap(db.transactionDao.getPendingTransactions().first)
        XCTAssertEqual(updatedPlan.nextExecutionAt, nextExecution)
        XCTAssertTrue(pending.needsPurchaseReview)
        XCTAssertEqual(try db.planDao.beginPurchase(purchase(for: updatedPlan, at: nextExecution),
                                                    for: updatedPlan, nextExecution: nextExecution), .blocked)
        XCTAssertThrowsError(try db.planDao.delete(id: planId))
        XCTAssertThrowsError(try db.transactionDao.delete(id: pending.id))

        try db.transactionDao.resolvePurchaseReview(pending, confirmedOrderId: nil)
        XCTAssertEqual(try db.transactionDao.getById(pending.id)?.status, .failed)
        guard case .started = try db.planDao.beginPurchase(purchase(for: updatedPlan, at: nextExecution),
                                                           for: updatedPlan, nextExecution: nextExecution) else {
            return XCTFail("Expected the independently reviewed plan to run again")
        }
    }

    func testMatchingImportedPurchaseKeepsOneCompletedTrade() throws {
        let path = try databasePath()
        let now = Date(timeIntervalSince1970: 2_000_000_000)
        let planId = try createPendingPurchase(path: path, now: now, nextExecution: now.addingTimeInterval(3600))
        let db = try DcaDatabase(path: path)
        let plan = try XCTUnwrap(db.planDao.getById(planId))
        let pending = try XCTUnwrap(db.transactionDao.getPendingTransactions().first)
        let imported = purchase(for: plan, at: now, status: .completed, orderId: "confirmed-order")
        try db.transactionDao.insert(imported)

        XCTAssertThrowsError(try db.transactionDao.resolvePurchaseReview(pending, confirmedOrderId: "some-other-order"))
        try db.transactionDao.resolvePurchaseReview(pending, confirmedOrderId: "confirmed-order")
        let transactions = try db.transactionDao.getByPlanId(planId)
        XCTAssertEqual(transactions.map(\.exchangeOrderId), ["confirmed-order"])
        XCTAssertEqual(transactions.map(\.status), [.completed])
    }

    func testSettledPartialFillContributesToTargetAndPreventsAnotherOrder() throws {
        let db = try DcaDatabase(path: databasePath())
        let date = Date(timeIntervalSince1970: 2_000_000_000)
        let planId = try db.planDao.insert(DcaPlan(exchange: .coinmate, crypto: "BTC", fiat: "CZK",
                                                   amount: 500, frequency: .daily, targetAmount: 1))
        let plan = try XCTUnwrap(db.planDao.getById(planId))
        try db.transactionDao.insert(purchase(for: plan, at: date, status: .partial, orderId: "partial-order"))

        XCTAssertEqual(try db.planDao.beginPurchase(purchase(for: plan, at: date), for: plan, nextExecution: date), .targetReached)
        XCTAssertEqual(try db.planDao.getById(planId)?.isEnabled, false)
        XCTAssertTrue(try db.transactionDao.getPendingTransactions().isEmpty)
    }

    func testVerifiedReceiptReplacesThePendingPurchaseInPlace() throws {
        let path = try databasePath()
        let now = Date(timeIntervalSince1970: 2_000_000_000)
        let planId = try createPendingPurchase(path: path, now: now, nextExecution: now.addingTimeInterval(3600))
        let db = try DcaDatabase(path: path)
        let plan = try XCTUnwrap(db.planDao.getById(planId))
        let pending = try XCTUnwrap(db.transactionDao.getPendingTransactions().first)
        let receipt = purchase(for: plan, at: now, id: pending.id, status: .completed, orderId: "verified-order")

        try db.transactionDao.resolvePurchaseReview(pending, confirmedOrderId: "verified-order", confirmedPurchase: receipt)
        XCTAssertEqual(try db.transactionDao.getByPlanId(planId), [receipt])
    }

    private func createPendingPurchase(path: String, now: Date, nextExecution: Date) throws -> Int64 {
        let db = try DcaDatabase(path: path)
        let planId = try db.planDao.insert(DcaPlan(exchange: .coinmate, crypto: "BTC", fiat: "CZK",
                                                   amount: 500, frequency: .daily, nextExecutionAt: now))
        let plan = try XCTUnwrap(db.planDao.getById(planId))
        _ = try db.planDao.beginPurchase(purchase(for: plan, at: now), for: plan, nextExecution: nextExecution)
        XCTAssertEqual(try db.transactionDao.getPendingTransactions().count, 1)
        return planId
    }

    private func purchase(for plan: DcaPlan, at date: Date, id: Int64 = 0, status: TransactionStatus = .pending,
                          orderId: String? = nil) -> Transaction {
        let hasFill = status == .completed || status == .partial
        return Transaction(id: id, planId: plan.id, exchange: plan.exchange, crypto: plan.crypto, fiat: plan.fiat,
                           fiatAmount: 500, cryptoAmount: hasFill ? 1 : 0, price: hasFill ? 500 : 0,
                           fee: 0, status: status, exchangeOrderId: orderId, executedAt: date)
    }

    private func databasePath() throws -> String {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try FileManager.default.removeItem(at: directory) }
        return directory.appendingPathComponent("accbot.sqlite").path
    }
}
