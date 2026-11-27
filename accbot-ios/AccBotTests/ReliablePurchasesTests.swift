import Foundation
import XCTest
@testable import AccBot

/// Schedule anchoring, missed purchases and reminders for purchases iOS runs late.
final class ReliablePurchasesTests: XCTestCase {
    private let day: TimeInterval = 86_400
    private let start = Date(timeIntervalSince1970: 2_000_000_000)

    // MARK: - ScheduleMath

    func testOnTimeRunKeepsTheScheduleAndMissesNothing() {
        let result = ScheduleMath.advance(cron: nil, intervalMinutes: 1440, scheduled: start,
                                          now: start.addingTimeInterval(60))
        XCTAssertEqual(result.next, start.addingTimeInterval(day))
        XCTAssertEqual(result.missed, 0)
    }

    func testLateRunStaysOnTheOriginalTimeOfDay() {
        let result = ScheduleMath.advance(cron: nil, intervalMinutes: 1440, scheduled: start,
                                          now: start.addingTimeInterval(5 * 3600))
        XCTAssertEqual(result.next, start.addingTimeInterval(day))
        XCTAssertEqual(result.missed, 0)
    }

    func testRunDaysLateCountsTheSkippedSlots() {
        let result = ScheduleMath.advance(cron: nil, intervalMinutes: 1440, scheduled: start,
                                          now: start.addingTimeInterval(3 * day + 3600))
        XCTAssertEqual(result.missed, 3)
        XCTAssertEqual(result.next, start.addingTimeInterval(4 * day))
    }

    func testRunJustBeforeTheDueTimeMovesToTheFollowingSlot() {
        let result = ScheduleMath.advance(cron: nil, intervalMinutes: 1440, scheduled: start,
                                          now: start.addingTimeInterval(-120))
        XCTAssertEqual(result.next, start.addingTimeInterval(day))
        XCTAssertEqual(result.missed, 0)
    }

    func testLimitNeverReturnsAPastSlot() {
        let now = start.addingTimeInterval(30 * day)
        let result = ScheduleMath.advance(cron: nil, intervalMinutes: 60, scheduled: start, now: now, limit: 5)
        XCTAssertEqual(result.missed, 5)
        XCTAssertGreaterThan(result.next, now)
    }

    // MARK: - Missed purchases in the database

    func testLateClaimRecordsMissedPurchasesAndCatchUpConsumesThem() throws {
        let db = try DcaDatabase(path: databasePath())
        let planId = try db.planDao.insert(DcaPlan(exchange: .coinmate, crypto: "BTC", fiat: "CZK",
                                                   amount: 500, frequency: .daily, nextExecutionAt: start))
        var plan = try XCTUnwrap(db.planDao.getById(planId))
        let next = start.addingTimeInterval(3 * day)
        guard case .started = try db.planDao.beginPurchase(filled(plan, at: start.addingTimeInterval(2 * day)),
                                                           for: plan, nextExecution: next, missedDelta: 2) else {
            return XCTFail("Expected the late purchase to start")
        }
        XCTAssertEqual(try db.missedPurchaseDao.count(for: planId), 2)
        XCTAssertEqual(try db.missedPurchaseDao.getAll(), [planId: 2])

        plan = try XCTUnwrap(db.planDao.getById(planId))
        guard case .started = try db.planDao.beginPurchase(filled(plan, at: start.addingTimeInterval(2 * day + 60)),
                                                           for: plan, nextExecution: next, missedDelta: -1) else {
            return XCTFail("Expected the catch-up purchase to start")
        }
        XCTAssertEqual(try db.missedPurchaseDao.count(for: planId), 1)
        XCTAssertEqual(try db.planDao.getById(planId)?.nextExecutionAt, next)

        try db.missedPurchaseDao.clear(planId: planId)
        XCTAssertEqual(try db.missedPurchaseDao.count(for: planId), 0)
        plan = try XCTUnwrap(db.planDao.getById(planId))
        XCTAssertEqual(try db.planDao.beginPurchase(filled(plan, at: start.addingTimeInterval(2 * day + 120)),
                                                    for: plan, nextExecution: next, missedDelta: -1), .blocked)
    }

    func testDeletingAPlanRemovesItsMissedPurchases() throws {
        let db = try DcaDatabase(path: databasePath())
        let planId = try db.planDao.insert(DcaPlan(exchange: .coinmate, crypto: "BTC", fiat: "CZK",
                                                   amount: 500, frequency: .daily, nextExecutionAt: start))
        let plan = try XCTUnwrap(db.planDao.getById(planId))
        _ = try db.planDao.beginPurchase(filled(plan, at: start), for: plan,
                                         nextExecution: start.addingTimeInterval(day), missedDelta: 4)
        try db.planDao.delete(id: planId)
        XCTAssertEqual(try db.missedPurchaseDao.getAll(), [:])
    }

    func testResumingAPausedPlanDoesNotCountThePauseAsMissed() throws {
        let db = try DcaDatabase(path: databasePath())
        let planId = try db.planDao.insert(DcaPlan(exchange: .coinmate, crypto: "BTC", fiat: "CZK", amount: 500,
                                                   frequency: .daily, isEnabled: false, nextExecutionAt: start))
        let resumedAt = start.addingTimeInterval(30 * day)
        try db.planDao.setEnabled(id: planId, enabled: true, now: resumedAt)
        XCTAssertEqual(try db.planDao.getById(planId)?.nextExecutionAt, resumedAt)
    }

    // MARK: - Reminders

    func testRemindersFollowTheDueTimeAndSkipThePast() {
        let plan = DcaPlan(id: 7, exchange: .coinmate, crypto: "BTC", fiat: "CZK", amount: 500,
                           frequency: .daily, nextExecutionAt: start)
        let before = PurchaseReminderScheduler.plannedReminders(plans: [plan], now: start.addingTimeInterval(-60))
        XCTAssertEqual(before.map(\.fireAt), [start.addingTimeInterval(15 * 60), start.addingTimeInterval(6 * 3600)])
        XCTAssertEqual(before.map(\.identifier), ["dca_reminder_7_1", "dca_reminder_7_2"])

        let after = PurchaseReminderScheduler.plannedReminders(plans: [plan], now: start.addingTimeInterval(3600))
        XCTAssertEqual(after.map(\.stage), [2])
        XCTAssertEqual(PurchaseReminderScheduler.latePlanIds(plans: [plan], now: start.addingTimeInterval(3600)), [7])
        XCTAssertEqual(PurchaseReminderScheduler.planId(fromIdentifier: "dca_reminder_7_2"), 7)
    }

    func testPausedPlansGetNoReminders() {
        let plan = DcaPlan(id: 3, exchange: .coinmate, crypto: "BTC", fiat: "CZK", amount: 500,
                           frequency: .daily, isEnabled: false, nextExecutionAt: start)
        XCTAssertTrue(PurchaseReminderScheduler.plannedReminders(plans: [plan], now: start).isEmpty)
        XCTAssertTrue(PurchaseReminderScheduler.latePlanIds(plans: [plan], now: start.addingTimeInterval(day)).isEmpty)
    }

    // MARK: - Widget

    func testWidgetSnapshotPicksTheNearestEnabledPlan() {
        let later = DcaPlan(id: 1, exchange: .coinmate, crypto: "BTC", fiat: "CZK", amount: 500,
                            frequency: .daily, nextExecutionAt: start.addingTimeInterval(day))
        let sooner = DcaPlan(id: 2, name: "ETH plan", exchange: .coinmate, crypto: "ETH", fiat: "CZK", amount: 300,
                             frequency: .daily, nextExecutionAt: start)
        let paused = DcaPlan(id: 3, exchange: .coinmate, crypto: "LTC", fiat: "CZK", amount: 100,
                             frequency: .daily, isEnabled: false, nextExecutionAt: start.addingTimeInterval(-day))
        let snapshot = WidgetSnapshot.make(plans: [later, sooner, paused], lastPurchaseAt: nil, isSandbox: false, now: start)
        XCTAssertEqual(snapshot.nextPurchaseAt, start)
        XCTAssertEqual(snapshot.nextPlanName, "ETH plan")
        XCTAssertEqual(snapshot.activePlanCount, 2)
        XCTAssertFalse(snapshot.isLate(at: start.addingTimeInterval(60)))
        XCTAssertTrue(snapshot.isLate(at: start.addingTimeInterval(16 * 60)))
    }

    // MARK: - Helpers

    private func filled(_ plan: DcaPlan, at date: Date) -> Transaction {
        Transaction(planId: plan.id, exchange: plan.exchange, crypto: plan.crypto, fiat: plan.fiat,
                    fiatAmount: 500, cryptoAmount: 1, price: 500, fee: 0, status: .completed,
                    exchangeOrderId: UUID().uuidString, executedAt: date)
    }

    private func databasePath() throws -> String {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try FileManager.default.removeItem(at: directory) }
        return directory.appendingPathComponent("accbot.sqlite").path
    }
}
