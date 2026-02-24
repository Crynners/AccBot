import Foundation

/// CRON expression parsing utilities.
/// Supports subset: minute hour day-of-month month day-of-week
enum CronUtils {

    /// Calculate next execution time from a CRON expression
    static func getNextExecution(cron: String, from: Date = Date()) -> Date? {
        let parts = cron.trimmingCharacters(in: .whitespaces).split(separator: " ")
        guard parts.count == 5 else { return nil }

        let minuteSpec = String(parts[0])
        let hourSpec = String(parts[1])
        let daySpec = String(parts[2])
        let monthSpec = String(parts[3])
        let weekdaySpec = String(parts[4])

        let calendar = Calendar.current
        var candidate = calendar.date(byAdding: .minute, value: 1, to: from)!

        // Try up to 366 days ahead
        for _ in 0..<(366 * 24 * 60) {
            let components = calendar.dateComponents([.minute, .hour, .day, .month, .weekday], from: candidate)
            guard let minute = components.minute,
                  let hour = components.hour,
                  let day = components.day,
                  let month = components.month,
                  let weekday = components.weekday
            else {
                candidate = calendar.date(byAdding: .minute, value: 1, to: candidate)!
                continue
            }

            // Weekday: cron uses 0=Sun, Calendar uses 1=Sun
            let cronWeekday = weekday - 1

            if matches(value: minute, spec: minuteSpec) &&
               matches(value: hour, spec: hourSpec) &&
               matches(value: day, spec: daySpec) &&
               matches(value: month, spec: monthSpec) &&
               matches(value: cronWeekday, spec: weekdaySpec) {
                return candidate
            }

            candidate = calendar.date(byAdding: .minute, value: 1, to: candidate)!
        }

        return nil
    }

    /// Estimate the average interval in minutes for a CRON expression
    static func getIntervalMinutesEstimate(cron: String) -> Int? {
        let from = Date()
        guard let first = getNextExecution(cron: cron, from: from),
              let second = getNextExecution(cron: cron, from: first)
        else { return nil }
        return Int(second.timeIntervalSince(first) / 60)
    }

    /// Validate a CRON expression
    static func isValid(cron: String) -> Bool {
        let parts = cron.trimmingCharacters(in: .whitespaces).split(separator: " ")
        guard parts.count == 5 else { return false }
        return getNextExecution(cron: cron) != nil
    }

    /// Build a CRON expression from components
    static func buildCron(minute: Int, hour: Int, dayOfWeek: Int? = nil) -> String {
        let dow = dayOfWeek.map { "\($0)" } ?? "*"
        return "\(minute) \(hour) * * \(dow)"
    }

    // MARK: - Private

    private static func matches(value: Int, spec: String) -> Bool {
        if spec == "*" { return true }

        // Handle step (*/n)
        if spec.hasPrefix("*/") {
            guard let step = Int(spec.dropFirst(2)), step > 0 else { return false }
            return value % step == 0
        }

        // Handle range (a-b)
        if spec.contains("-") {
            let parts = spec.split(separator: "-")
            guard parts.count == 2,
                  let low = Int(parts[0]),
                  let high = Int(parts[1])
            else { return false }
            return value >= low && value <= high
        }

        // Handle list (a,b,c)
        if spec.contains(",") {
            let values = spec.split(separator: ",").compactMap { Int($0) }
            return values.contains(value)
        }

        // Exact match
        guard let exact = Int(spec) else { return false }
        return value == exact
    }
}
