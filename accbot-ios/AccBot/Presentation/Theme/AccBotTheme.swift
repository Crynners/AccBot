import SwiftUI

// MARK: - AccBot Color Palette

extension Color {
    // Production colors — WCAG AA compliant (≥4.5:1 contrast with white text on colored backgrounds)
    static let accentTeal = Color(hex: 0x208464)       // 4.67:1 with white — WCAG AA pass
    static let accentTealVariant = Color(hex: 0x1B7357) // 5.77:1 with white — WCAG AA pass
    /// Lighter teal for text on dark backgrounds (≥4.5:1 contrast on #1A1A2E)
    static let accentTealLight = Color(hex: 0x5CCFAB)
    static let backgroundDark = Color(hex: 0x16213E)
    static let surfaceDark = Color(hex: 0x1A1A2E)
    static let surfaceVariantDark = Color(hex: 0x0F3460)
    /// Secondary text on dark surfaces — WCAG AA compliant (≥4.5:1 on #1A1A2E)
    static let onSurfaceVariantColor = Color(hex: 0xD0D0D0)
    /// Raw warning orange for dark-mode decorative use only — use `AccBotColors.warning` for themed usage
    static let warningOrange = Color(hex: 0xFFA726)

    // Sandbox colors — WCAG AA compliant (≥4.5:1 contrast with white text)
    static let sandboxPrimary = Color(hex: 0x9C6900)       // 4.69:1 with white — WCAG AA pass
    static let sandboxPrimaryVariant = Color(hex: 0x855A00) // 5.87:1 with white — WCAG AA pass

    // Light mode backgrounds
    static let backgroundLight = Color(hex: 0xF5F5F5)
    static let onBackgroundLight = Color(hex: 0x1A1A2E)
    static let surfaceVariantLight = Color(hex: 0xE0E0E0)
    static let onSurfaceVariantLight = Color(hex: 0x49454F)

    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}

// MARK: - Theme Environment Key

private struct SandboxModeKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var isSandboxMode: Bool {
        get { self[SandboxModeKey.self] }
        set { self[SandboxModeKey.self] = newValue }
    }
}

// MARK: - AccBotColors Environment Key

private struct AccBotColorsKey: EnvironmentKey {
    static let defaultValue = AccBotColors(isSandbox: false, isDark: false)
}

extension EnvironmentValues {
    var accBotColors: AccBotColors {
        get { self[AccBotColorsKey.self] }
        set { self[AccBotColorsKey.self] = newValue }
    }
}

// MARK: - Theme Color Resolver

struct AccBotColors {
    let isSandbox: Bool
    let isDark: Bool

    var primary: Color {
        isSandbox ? .sandboxPrimary : .accentTeal
    }

    var primaryVariant: Color {
        isSandbox ? .sandboxPrimaryVariant : .accentTealVariant
    }

    var background: Color {
        isDark ? .backgroundDark : .backgroundLight
    }

    var surface: Color {
        isDark ? .surfaceDark : .white
    }

    var surfaceVariant: Color {
        isDark ? .surfaceVariantDark : .surfaceVariantLight
    }

    var onBackground: Color {
        isDark ? .white : .onBackgroundLight
    }

    var onSurface: Color {
        isDark ? .white : .onBackgroundLight
    }

    var onSurfaceVariant: Color {
        isDark ? .onSurfaceVariantColor : .onSurfaceVariantLight
    }

    /// Text/icon color for use on primary-colored backgrounds (buttons, badges).
    /// Uses white in both modes for maximum contrast on darker teal/orange.
    var onPrimary: Color {
        .white
    }

    var error: Color {
        isDark ? Color(hex: 0xFF6B81) : Color(hex: 0xC62828)
    }

    var success: Color {
        isDark ? Color(hex: 0x66BB6A) : Color(hex: 0x2E7D32)
    }

    var warning: Color {
        isDark ? Color(hex: 0xFFA726) : Color(hex: 0xE65100)
    }

    /// Primary-derived text color with sufficient contrast in both modes (≥4.5:1)
    var primaryText: Color {
        isDark ? (isSandbox ? .sandboxPrimary : .accentTealLight) : primaryVariant
    }

    /// Explicit disabled background for buttons — WCAG AA 3:1 against background.
    var disabledBackground: Color {
        surfaceVariant
    }

    /// Explicit disabled foreground for text on disabled buttons — WCAG AA 3:1.
    var disabledForeground: Color {
        onSurfaceVariant
    }
}

enum AppTheme: String, CaseIterable {
    case dark
    case light
    case system

    var displayName: String {
        switch self {
        case .dark: return String(localized: "Dark")
        case .light: return String(localized: "Light")
        case .system: return String(localized: "System")
        }
    }
}

// MARK: - Spacing Constants

enum Spacing {
    static let xxs: CGFloat = 2
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 24
    static let xxxl: CGFloat = 32
}

// MARK: - Opacity Constants

enum Opacity {
    static let disabled: Double = 0.5
    static let divider: Double = 0.7
}

// MARK: - Corner Radius Constants

enum CornerRadius {
    static let xxs: CGFloat = 2
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
}

// MARK: - View Modifiers

struct CardModifier: ViewModifier {
    @Environment(\.accBotColors) private var colors

    func body(content: Content) -> some View {
        content
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }
}

extension View {
    func cardStyle() -> some View {
        modifier(CardModifier())
    }

    func maxFormWidth() -> some View {
        frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
    }
}

// MARK: - Primary Button Style

struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.accBotColors) private var colors
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(AccBotFonts.headline)
            .foregroundStyle(isEnabled ? colors.onPrimary : colors.disabledForeground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(isEnabled ? colors.primary : colors.disabledBackground)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            .scaleEffect(configuration.isPressed ? 0.98 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }
}
