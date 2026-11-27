import Foundation

/// Schedule arithmetic shared by the execution engine and its tests.
///
/// iOS often runs a due purchase late (when a Shortcuts automation, a background task or
/// the user opens the app). The next purchase is therefore anchored to the plan's own
/// schedule, not to the moment the late purchase ran, and the slots that passed in
/// between are counted as missed so the user can buy them later.
enum ScheduleMath {
    /// The first schedule slot after `date`.
    static func step(cron: String?, intervalMinutes: Int, from date: Date) -> Date {
        if let cron, let next = CronUtils.getNextExecution(cron: cron, from: date), next > date {
            return next
        }
        let minutes = intervalMinutes > 0 ? intervalMinutes : 1440
        return date.addingTimeInterval(TimeInterval(minutes * 60))
    }

    /// Advances from the slot that is being executed (`scheduled`).
    /// - Returns: `next`, the first slot later than both `scheduled` and `now`, and `missed`,
    ///   the number of slots after `scheduled` that are already due at `now`.
    static func advance(
        cron: String?,
        intervalMinutes: Int,
        scheduled: Date,
        now: Date,
        limit: Int = 1000
    ) -> (next: Date, missed: Int) {
        var missed = 0
        var slot = step(cron: cron, intervalMinutes: intervalMinutes, from: scheduled)
        while slot <= now && missed < limit {
            missed += 1
            slot = step(cron: cron, intervalMinutes: intervalMinutes, from: slot)
        }
        // With the limit hit, never return a slot in the past.
        if slot <= now {
            slot = step(cron: cron, intervalMinutes: intervalMinutes, from: now)
        }
        return (slot, missed)
    }
}
