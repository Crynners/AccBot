import SwiftUI

enum AccBotFonts {
    // Dynamic Type-aware fonts that scale with user's accessibility text size settings.
    // Uses text style-relative sizing so iOS can scale them automatically.
    static let displayLarge = Font.system(.largeTitle, weight: .bold)
    static let title = Font.system(.title, weight: .semibold)
    static let titleLarge = Font.system(.title, weight: .bold)
    static let titleMedium = Font.system(.title2, weight: .bold)
    static let titleSmall = Font.system(.title3, weight: .semibold)
    static let headline = Font.system(.headline, weight: .semibold)
    static let body = Font.system(.body, weight: .regular)
    static let bodySmall = Font.system(.subheadline, weight: .regular)
    static let caption = Font.system(.caption, weight: .regular)
    static let captionSmall = Font.system(.caption2, weight: .regular)
    static let label = Font.system(.subheadline, weight: .medium)
    static let mono = Font.system(.body, design: .monospaced)
    static let monoSmall = Font.system(.caption, design: .monospaced)

    // Icon sizes for SF Symbols — use Dynamic Type-aware text styles
    // so they scale alongside user's Accessibility text size settings.
    static let iconXL = Font.system(.largeTitle).weight(.regular)
    static let iconLarge = Font.system(.largeTitle).weight(.regular)
    static let iconMedium = Font.system(.title3).weight(.regular)
    static let iconSmall = Font.system(.headline).weight(.regular)
}

// MARK: - Scaled Icon Sizes

/// Provides `@ScaledMetric` icon sizes for use in `.frame()` modifiers.
/// Usage: `@ScaledIcon private var iconSize` then `iconSize.xl`, `.large`, etc.
struct ScaledIconSize {
    @ScaledMetric(relativeTo: .largeTitle) var xl: CGFloat = 80
    @ScaledMetric(relativeTo: .largeTitle) var large: CGFloat = 64
    @ScaledMetric(relativeTo: .title3) var medium: CGFloat = 24
    @ScaledMetric(relativeTo: .headline) var small: CGFloat = 20
}

/// Property wrapper shorthand for `ScaledIconSize`.
typealias ScaledIcon = ScaledIconSize
