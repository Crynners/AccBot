import SwiftUI

// MARK: - AccBot Color Palette

extension Color {
    // Production colors
    static let accentTeal = Color(hex: 0x4ECCA3)
    static let accentTealVariant = Color(hex: 0x3BA67D)
    static let secondaryBlue = Color(hex: 0x0F3460)
    static let backgroundDark = Color(hex: 0x16213E)
    static let surfaceDark = Color(hex: 0x1A1A2E)
    static let surfaceVariantDark = Color(hex: 0x0F3460)
    static let onSurfaceVariantColor = Color(hex: 0xA0A0A0)
    static let errorRed = Color(hex: 0xE94560)
    static let warningOrange = Color(hex: 0xFFA726)

    // Sandbox colors (orange instead of green)
    static let sandboxPrimary = Color(hex: 0xFFA726)
    static let sandboxPrimaryVariant = Color(hex: 0xE65100)

    // Light mode backgrounds
    static let backgroundLight = Color(hex: 0xF5F5F5)
    static let onBackgroundLight = Color(hex: 0x1A1A2E)
    static let surfaceVariantLight = Color(hex: 0xE0E0E0)
    static let onSurfaceVariantLight = Color(hex: 0x666666)

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

    var error: Color { .errorRed }

    var success: Color {
        isSandbox ? .sandboxPrimary : .accentTeal
    }

    var warning: Color { .warningOrange }
}

// MARK: - Theme Environment Object

@MainActor
final class ThemeManager: ObservableObject {
    @Published var isSandboxMode: Bool = false
    @Published var appTheme: AppTheme = .system

    var isDark: Bool {
        switch appTheme {
        case .dark: return true
        case .light: return false
        case .system: return true // Default; actual check happens in view
        }
    }

    var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: isDark)
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

// MARK: - Corner Radius Constants

enum CornerRadius {
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
}

// MARK: - View Modifiers

struct CardModifier: ViewModifier {
    let isDark: Bool

    func body(content: Content) -> some View {
        content
            .padding(Spacing.lg)
            .background(isDark ? Color.surfaceDark : Color.white)
            .cornerRadius(CornerRadius.md)
    }
}

extension View {
    func cardStyle(isDark: Bool = true) -> some View {
        modifier(CardModifier(isDark: isDark))
    }
}
