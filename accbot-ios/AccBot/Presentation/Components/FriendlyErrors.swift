import Foundation

/// What kind of problem a raw exchange / transport error is, so the UI can say what
/// happened and what to do instead of showing e.g. "A server with the specified hostname
/// could not be found." Mirrors Android's FriendlyErrors.
enum ErrorCategory {
    case network, auth, insufficientFunds, exchangeLimit, rateLimit, exchangeUnavailable, unknown

    /// Order matters: a DNS failure while "fetching balance" is a network problem,
    /// not a funds problem, so transport patterns are checked first.
    private static let rules: [(ErrorCategory, [String])] = [
        (.network, [
            "unable to resolve host", "hostname could not be found", "could not connect", "failed to connect",
            "timed out", "timeout", "connection reset", "connection refused", "network connection was lost",
            "not connected to the internet", "offline", "ssl", "socket"
        ]),
        (.rateLimit, ["rate limit", "too many requests", "\\b429\\b", "-1003\\b", "eapi:rate", "request weight"]),
        (.exchangeUnavailable, [
            "maintenance", "service unavailable", "eservice", "\\b50[234]\\b", "temporarily unavailable",
            "system busy", "server busy"
        ]),
        (.auth, [
            "api[- _]?key", "invalid key", "signature", "unauthori[sz]ed", "\\b401\\b", "\\b403\\b",
            "permission", "access denied", "nonce", "kc-api", "authenticat", "credential",
            "-2014\\b", "-2015\\b", "-1022\\b", "ip not allowed", "whitelist"
        ]),
        (.insufficientFunds, ["insufficient", "not enough", "balance too low", "-2010\\b", "exceeds available"]),
        (.exchangeLimit, [
            "minimum", "min_notional", "notional", "lot_size", "too small", "below min", "min order",
            "order size", "precision", "-1013\\b", "price_filter", "max order"
        ])
    ]

    static func of(_ raw: String?) -> ErrorCategory {
        guard let raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return .unknown }
        for (category, patterns) in rules {
            for pattern in patterns where raw.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil {
                return category
            }
        }
        return .unknown
    }

    var title: String? {
        switch self {
        case .network: return String(localized: "No connection to the exchange")
        case .auth: return String(localized: "The exchange rejected the API key")
        case .insufficientFunds: return String(localized: "Not enough money on the exchange")
        case .exchangeLimit: return String(localized: "The order is below the exchange limit")
        case .rateLimit: return String(localized: "The exchange is limiting requests")
        case .exchangeUnavailable: return String(localized: "The exchange is unavailable")
        case .unknown: return nil
        }
    }

    var advice: String? {
        switch self {
        case .network: return String(localized: "Check your internet connection. AccBot tries again on the next run.")
        case .auth: return String(localized: "The key may have expired, lack trading permission or be limited to other IP addresses. Check it on the exchange and enter it again in Manage Exchanges.")
        case .insufficientFunds: return String(localized: "Check your balance on the exchange and top it up if needed.")
        case .exchangeLimit: return String(localized: "Raise the purchase amount in the plan so it meets the exchange minimum.")
        case .rateLimit: return String(localized: "Too many requests in a short time. Wait a few minutes and try again.")
        case .exchangeUnavailable: return String(localized: "The exchange is probably under maintenance. Try again later.")
        case .unknown: return nil
        }
    }
}

enum FriendlyErrors {
    /// One short line for lists: the category title, or the raw text when unknown.
    static func shortText(_ raw: String) -> String {
        ErrorCategory.of(raw).title ?? raw
    }

    /// "Title. Advice" for notifications; the raw text when the error is not recognized.
    static func summary(_ raw: String) -> String {
        let category = ErrorCategory.of(raw)
        guard let title = category.title, let advice = category.advice else { return raw }
        return "\(title). \(advice)"
    }
}
