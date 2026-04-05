import Foundation

/// Centralized formatters to avoid allocating new formatter instances on every render.
enum AccBotFormatters {
    // MARK: - Number Formatters

    static let fiat: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.locale = .current
        return f
    }()

    static let crypto: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 8
        f.locale = .current
        return f
    }()

    static let cryptoSatoshi: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 8
        f.maximumFractionDigits = 8
        f.locale = .current
        return f
    }()

    static let cryptoCompact: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 4
        f.locale = .current
        return f
    }()

    static let signedFiat: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.positivePrefix = "+"
        f.locale = .current
        return f
    }()

    static let signedPercent: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 1
        f.maximumFractionDigits = 1
        f.positivePrefix = "+"
        f.positiveSuffix = "%"
        f.negativeSuffix = "%"
        f.locale = .current
        return f
    }()

    // MARK: - Date Formatters

    static let relative: RelativeDateTimeFormatter = {
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .abbreviated
        f.locale = .current
        return f
    }()

    static let mediumDate: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .medium
        f.timeStyle = .medium
        return f
    }()

    static let monthName: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("MMM")
        return f
    }()

    static let monthYear: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("MMM yyyy")
        return f
    }()

    // MARK: - Convenience Methods

    static func formatFiat(_ value: Decimal, symbol: String) -> String {
        let number = NSDecimalNumber(decimal: value)
        return "\(fiat.string(from: number) ?? "0") \(symbol)"
    }

    static func formatFiatValue(_ value: Decimal) -> String {
        let absValue = value < 0 ? -value : value
        let number = NSDecimalNumber(decimal: absValue)
        return fiat.string(from: number) ?? "0"
    }

    static func formatFiatPlain(_ value: Decimal) -> String {
        let number = NSDecimalNumber(decimal: value)
        return fiat.string(from: number) ?? "0"
    }

    static func formatCrypto(_ value: Decimal, symbol: String) -> String {
        let number = NSDecimalNumber(decimal: value)
        let absValue = value < 0 ? -value : value
        let formatter = absValue < 1 ? cryptoSatoshi : crypto
        return "\(formatter.string(from: number) ?? "0") \(symbol)"
    }

    static func formatCryptoPlain(_ value: Decimal) -> String {
        let number = NSDecimalNumber(decimal: value)
        let absValue = value < 0 ? -value : value
        let formatter = absValue < 1 ? cryptoSatoshi : crypto
        return formatter.string(from: number) ?? "0"
    }

    static func formatCryptoCompact(_ value: Decimal) -> String {
        let number = NSDecimalNumber(decimal: value)
        return cryptoCompact.string(from: number) ?? "0"
    }

    static func formatFiatAxisLabel(_ value: Double) -> String {
        let absValue = abs(value)
        if absValue >= 1_000_000_000 {
            let b = value / 1_000_000_000.0
            return b.truncatingRemainder(dividingBy: 1) == 0
                ? String(format: "%.0fB", b) : String(format: "%.1fB", b)
        } else if absValue >= 1_000_000 {
            let m = value / 1_000_000.0
            return m.truncatingRemainder(dividingBy: 1) == 0
                ? String(format: "%.0fM", m) : String(format: "%.1fM", m)
        } else if absValue >= 1000 {
            let k = value / 1000.0
            return k.truncatingRemainder(dividingBy: 1) == 0
                ? String(format: "%.0fk", k) : String(format: "%.1fk", k)
        } else {
            if absValue == 0 || value.truncatingRemainder(dividingBy: 1) == 0 {
                return String(format: "%.0f", value)
            } else {
                return String(format: "%.1f", value)
            }
        }
    }

    static func formatSignedPercent(_ value: Double) -> String {
        signedPercent.string(from: NSNumber(value: value)) ?? "\(value)%"
    }

    static func formatSignedPercent(_ value: Decimal) -> String {
        formatSignedPercent(NSDecimalNumber(decimal: value).doubleValue)
    }

    static func relativeDate(_ date: Date) -> String {
        relative.localizedString(for: date, relativeTo: Date())
    }

    static func timeOnly(_ date: Date) -> String {
        timeOnlyFormatter.string(from: date)
    }

    private static let timeOnlyFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .none
        f.timeStyle = .short
        return f
    }()

    static func monthNameFromComponents(month: Int, year: Int = Calendar.current.component(.year, from: Date())) -> String {
        var comps = DateComponents()
        comps.month = month
        comps.day = 1
        comps.year = year
        if let date = Calendar.current.date(from: comps) {
            return monthName.string(from: date)
        }
        return "\(month)"
    }

    static func monthYearLabel(month: Int, year: Int) -> String {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = 1
        if let date = Calendar.current.date(from: comps) {
            return monthYear.string(from: date)
        }
        return "\(month)/\(year)"
    }
}
