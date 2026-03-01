import Foundation

/// CRON expression parsing utilities.
/// Supports subset: minute hour day-of-month month day-of-week
enum CronUtils {

    /// Calculate next execution time from a CRON expression.
    /// Uses an optimized algorithm that skips ahead by day/hour when fields don't match,
    /// instead of brute-force minute-by-minute iteration.
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

        // Try up to 366 days ahead with smart skipping
        let maxDate = calendar.date(byAdding: .day, value: 366, to: from)!

        while candidate <= maxDate {
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

            let cronWeekday = weekday - 1

            // Skip entire month if month doesn't match
            if !matches(value: month, spec: monthSpec) {
                candidate = calendar.date(byAdding: .month, value: 1, to:
                    calendar.date(from: DateComponents(
                        year: components.year, month: month, day: 1, hour: 0, minute: 0
                    ))!
                )!
                continue
            }

            // Skip entire day if day-of-month or day-of-week doesn't match
            if !matches(value: day, spec: daySpec) || !matches(value: cronWeekday, spec: weekdaySpec) {
                candidate = calendar.date(byAdding: .day, value: 1, to:
                    calendar.startOfDay(for: candidate)
                )!
                continue
            }

            // Skip to next hour if hour doesn't match
            if !matches(value: hour, spec: hourSpec) {
                candidate = calendar.date(byAdding: .hour, value: 1, to:
                    calendar.date(from: DateComponents(
                        year: components.year, month: month, day: day, hour: hour, minute: 0
                    ))!
                )!
                continue
            }

            // Check minute
            if matches(value: minute, spec: minuteSpec) {
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

    /// Build a CRON expression from simple components (single hour, optional single day-of-week)
    static func buildCron(minute: Int, hour: Int, dayOfWeek: Int? = nil) -> String {
        let dow = dayOfWeek.map { "\($0)" } ?? "*"
        return "\(minute) \(hour) * * \(dow)"
    }

    /// Build a CRON expression from advanced components (multi-hour, days-of-week/month)
    static func buildCronAdvanced(
        minute: Int,
        hours: Set<Int>,
        daysOfWeek: Set<Int>? = nil,
        daysOfMonth: Set<Int>? = nil
    ) -> String? {
        guard !hours.isEmpty else { return nil }

        let minuteField = "\(minute)"
        let hourField = hours.sorted().map { "\($0)" }.joined(separator: ",")

        if let dows = daysOfWeek, !dows.isEmpty {
            let dowField = dows.sorted().map { "\($0)" }.joined(separator: ",")
            return "\(minuteField) \(hourField) * * \(dowField)"
        }

        if let doms = daysOfMonth, !doms.isEmpty {
            let domField = doms.sorted().map { "\($0)" }.joined(separator: ",")
            return "\(minuteField) \(hourField) \(domField) * *"
        }

        // Daily
        return "\(minuteField) \(hourField) * * *"
    }

    /// Human-readable description of a CRON expression
    static func describeCron(_ expression: String) -> String? {
        let parts = expression.trimmingCharacters(in: .whitespaces).split(separator: " ")
        guard parts.count == 5 else { return nil }

        let minuteField = String(parts[0])
        let hourField = String(parts[1])
        let domField = String(parts[2])
        let monthField = String(parts[3])
        let dowField = String(parts[4])

        guard monthField == "*" else { return nil }

        let isCzech = Locale.current.language.languageCode?.identifier == "cs"

        // Pattern: */N * * * * → "Every N minutes"
        if minuteField.hasPrefix("*/"), hourField == "*", domField == "*", dowField == "*" {
            guard let n = Int(minuteField.dropFirst(2)) else { return nil }
            return isCzech ? "Každých \(n) minut" : "Every \(n) minutes"
        }

        // Pattern: M */N * * * → "Every N hours"
        if hourField.hasPrefix("*/"), domField == "*", dowField == "*" {
            guard let n = Int(hourField.dropFirst(2)) else { return nil }
            return isCzech ? "Každých \(n) hodin" : "Every \(n) hours"
        }

        // Parse specific minutes and hours
        guard let minutes = parseFieldValues(minuteField, range: 0...59), !minutes.isEmpty,
              let hours = parseFieldValues(hourField, range: 0...23), !hours.isEmpty
        else { return nil }

        let minute = minutes.first!
        let timeStrings = hours.sorted().map { String(format: "%d:%02d", $0, minute) }
        let timeList = timeStrings.joined(separator: isCzech ? " a " : " and ")

        let isDomWild = domField == "*"
        let isDowWild = dowField == "*"

        if isDomWild && isDowWild {
            // Daily
            return isCzech ? "Každý den v \(timeList)" : "Every day at \(timeList)"
        }

        if isDomWild && !isDowWild {
            // Days of week
            guard let dows = parseFieldValues(dowField, range: 0...7) else { return nil }
            let normalizedDows = Set(dows.map { $0 == 7 ? 0 : $0 })
            let dayNames = dayOfWeekNames(normalizedDows, czech: isCzech)
            return isCzech ? "\(dayNames) v \(timeList)" : "\(dayNames) at \(timeList)"
        }

        if !isDomWild && isDowWild {
            // Days of month
            guard let doms = parseFieldValues(domField, range: 1...31) else { return nil }
            let dayList = doms.sorted().map { "\($0)." }.joined(separator: ", ")
            return isCzech ? "\(dayList) každého měsíce v \(timeList)" : "\(dayList) of every month at \(timeList)"
        }

        return nil
    }

    // MARK: - Private

    private static func parseFieldValues(_ field: String, range: ClosedRange<Int>) -> [Int]? {
        if field.contains("-") || field.contains("/") || field.contains("*") { return nil }
        let numbers = field.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        var result: [Int] = []
        for num in numbers {
            guard let n = Int(num), range.contains(n) else { return nil }
            result.append(n)
        }
        return result.isEmpty ? nil : result
    }

    private static func dayOfWeekNames(_ dows: Set<Int>, czech: Bool) -> String {
        let names: [(Int, String, String)] = [
            (1, "Mon", "Po"), (2, "Tue", "Út"), (3, "Wed", "St"),
            (4, "Thu", "Čt"), (5, "Fri", "Pá"), (6, "Sat", "So"), (0, "Sun", "Ne"),
        ]
        let selected = names.filter { dows.contains($0.0) }
        let joined = selected.map { czech ? $0.2 : $0.1 }.joined(separator: ", ")
        return joined
    }

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
