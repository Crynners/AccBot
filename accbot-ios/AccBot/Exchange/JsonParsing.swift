import Foundation

/// Parse a Decimal from a JSON dictionary value that may be String or NSNumber.
/// Returns 0 if the key is missing or unparseable.
func jsonDecimal(_ dict: [String: Any], key: String) -> Decimal {
    jsonDecimalOptional(dict, key: key) ?? 0
}

/// Parse a Decimal from a JSON dictionary value that may be String or NSNumber.
/// Returns nil if the key is missing or unparseable.
func jsonDecimalOptional(_ dict: [String: Any], key: String) -> Decimal? {
    guard let value = dict[key] else { return nil }
    if let str = value as? String {
        return Decimal(string: str)
    }
    if let num = value as? NSNumber {
        // Use stringValue to avoid Double precision artifacts
        return Decimal(string: num.stringValue)
    }
    return nil
}

/// Parse a String from a JSON dictionary value that may be String or NSNumber.
/// Returns "" if the key is missing.
func jsonString(_ dict: [String: Any], key: String) -> String {
    guard let value = dict[key] else { return "" }
    if let str = value as? String { return str }
    if let num = value as? NSNumber { return num.stringValue }
    return "\(value)"
}

/// Parse an Int64 from a JSON dictionary value that may be Int64, Int, Double, or String.
/// Returns 0 if the key is missing or unparseable.
func jsonInt64(_ dict: [String: Any], key: String) -> Int64 {
    guard let value = dict[key] else { return 0 }
    if let i = value as? Int64 { return i }
    if let i = value as? Int { return Int64(i) }
    if let n = value as? NSNumber { return n.int64Value }
    if let s = value as? String { return Int64(s) ?? 0 }
    return 0
}

/// Parse a Double from a JSON dictionary value that may be Double, Int, or String.
/// Returns 0 if the key is missing or unparseable.
func jsonDouble(_ dict: [String: Any], key: String) -> Double {
    guard let value = dict[key] else { return 0 }
    if let d = value as? Double { return d }
    if let n = value as? NSNumber { return n.doubleValue }
    if let s = value as? String { return Double(s) ?? 0 }
    return 0
}

/// Convert a Decimal to a plain string without scientific notation.
/// Swift's default "\(decimal)" can produce "1E-7" for very small values.
func decimalToPlainString(_ value: Decimal) -> String {
    NSDecimalNumber(decimal: value).stringValue
}
