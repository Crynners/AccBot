import SwiftUI

/// Displays a fiat currency symbol as a Canvas-rendered icon matching Android vector drawables.
/// Known symbols (USD, EUR, GBP, CZK, USDT) get proper logo rendering;
/// unknown symbols fall back to a colored circle with text abbreviation.
struct FiatIcon: View {
    let symbol: String
    let size: CGFloat

    init(symbol: String, size: CGFloat = 32) {
        self.symbol = symbol.uppercased()
        self.size = size
    }

    var body: some View {
        if hasCanvasIcon {
            Canvas { context, canvasSize in
                drawIcon(context: &context, size: canvasSize)
            }
            .frame(width: size, height: size)
            .accessibilityLabel(Text(accessibilityName))
            .accessibilityAddTraits(.isImage)
        } else {
            fallbackIcon
                .accessibilityLabel(Text(accessibilityName))
                .accessibilityAddTraits(.isImage)
        }
    }

    private var accessibilityName: String {
        switch symbol {
        case "USD": return String(localized: "US Dollar")
        case "EUR": return String(localized: "Euro")
        case "GBP": return String(localized: "British Pound")
        case "CZK": return String(localized: "Czech Koruna")
        case "USDT": return String(localized: "Tether")
        default: return symbol
        }
    }

    private var hasCanvasIcon: Bool {
        ["USD", "EUR", "GBP", "CZK", "USDT"].contains(symbol)
    }

    // MARK: - Fallback (text circle)

    @Environment(\.accBotColors) private var colors

    private var fallbackIcon: some View {
        ZStack {
            Circle().fill(colors.surfaceVariant)
            Text(symbol.count <= 3 ? symbol : String(symbol.prefix(3)))
                .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                .foregroundStyle(colors.onSurface)
        }
        .frame(width: size, height: size)
    }

    // MARK: - Canvas Drawing

    /// All coordinates are in a 128x128 viewport, scaled to the actual size.
    private func drawIcon(context: inout GraphicsContext, size: CGSize) {
        let scale = size.width / 128.0

        switch symbol {
        case "USD": drawUSD(context: &context, scale: scale)
        case "EUR": drawEUR(context: &context, scale: scale)
        case "GBP": drawGBP(context: &context, scale: scale)
        case "CZK": drawCZK(context: &context, scale: scale)
        case "USDT": drawUSDT(context: &context, scale: scale)
        default: break
        }
    }

    // MARK: - USD (Green circle + white $ sign)

    private func drawUSD(context: inout GraphicsContext, scale: CGFloat) {
        let circle = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circle, with: .color(Color(hex: 0x2E7D32)))

        // S shape
        let sPath = Path { p in
            p.move(to: CGPoint(x: 78 * scale, y: 46 * scale))
            p.addCurve(
                to: CGPoint(x: 50 * scale, y: 46 * scale),
                control1: CGPoint(x: 78 * scale, y: 39 * scale),
                control2: CGPoint(x: 50 * scale, y: 34 * scale)
            )
            p.addCurve(
                to: CGPoint(x: 78 * scale, y: 70 * scale),
                control1: CGPoint(x: 50 * scale, y: 53 * scale),
                control2: CGPoint(x: 78 * scale, y: 60 * scale)
            )
            p.addCurve(
                to: CGPoint(x: 50 * scale, y: 70 * scale),
                control1: CGPoint(x: 78 * scale, y: 77 * scale),
                control2: CGPoint(x: 50 * scale, y: 82 * scale)
            )
        }
        context.stroke(sPath, with: .color(.white),
                       style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round))

        // Vertical bar
        let bar = Path { p in
            p.move(to: CGPoint(x: 64 * scale, y: 28 * scale))
            p.addLine(to: CGPoint(x: 64 * scale, y: 88 * scale))
        }
        context.stroke(bar, with: .color(.white),
                       style: StrokeStyle(lineWidth: 4 * scale, lineCap: .round))
    }

    // MARK: - EUR (EU blue circle + white euro sign)

    private func drawEUR(context: inout GraphicsContext, scale: CGFloat) {
        let circle = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circle, with: .color(Color(hex: 0x003399)))

        // C shape (euro body)
        let cPath = Path { p in
            p.move(to: CGPoint(x: 82 * scale, y: 44 * scale))
            p.addCurve(
                to: CGPoint(x: 40 * scale, y: 58 * scale),
                control1: CGPoint(x: 70 * scale, y: 34 * scale),
                control2: CGPoint(x: 40 * scale, y: 44 * scale)
            )
            p.addCurve(
                to: CGPoint(x: 82 * scale, y: 72 * scale),
                control1: CGPoint(x: 40 * scale, y: 72 * scale),
                control2: CGPoint(x: 70 * scale, y: 82 * scale)
            )
        }
        context.stroke(cPath, with: .color(.white),
                       style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round))

        // Horizontal bars
        let topBar = Path { p in
            p.move(to: CGPoint(x: 36 * scale, y: 54 * scale))
            p.addLine(to: CGPoint(x: 68 * scale, y: 54 * scale))
        }
        context.stroke(topBar, with: .color(.white),
                       style: StrokeStyle(lineWidth: 5 * scale, lineCap: .round))

        let bottomBar = Path { p in
            p.move(to: CGPoint(x: 36 * scale, y: 66 * scale))
            p.addLine(to: CGPoint(x: 68 * scale, y: 66 * scale))
        }
        context.stroke(bottomBar, with: .color(.white),
                       style: StrokeStyle(lineWidth: 5 * scale, lineCap: .round))
    }

    // MARK: - GBP (Purple circle + white pound sign)

    private func drawGBP(context: inout GraphicsContext, scale: CGFloat) {
        let circle = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circle, with: .color(Color(hex: 0x4A235A)))

        // Pound shape
        let poundPath = Path { p in
            p.move(to: CGPoint(x: 78 * scale, y: 40 * scale))
            p.addCurve(
                to: CGPoint(x: 52 * scale, y: 50 * scale),
                control1: CGPoint(x: 68 * scale, y: 32 * scale),
                control2: CGPoint(x: 52 * scale, y: 36 * scale)
            )
            p.addLine(to: CGPoint(x: 52 * scale, y: 72 * scale))
            p.addCurve(
                to: CGPoint(x: 46 * scale, y: 86 * scale),
                control1: CGPoint(x: 52 * scale, y: 78 * scale),
                control2: CGPoint(x: 50 * scale, y: 82 * scale)
            )
            p.addLine(to: CGPoint(x: 82 * scale, y: 86 * scale))
        }
        context.stroke(poundPath, with: .color(.white),
                       style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round, lineJoin: .round))

        // Horizontal bar
        let bar = Path { p in
            p.move(to: CGPoint(x: 42 * scale, y: 62 * scale))
            p.addLine(to: CGPoint(x: 72 * scale, y: 62 * scale))
        }
        context.stroke(bar, with: .color(.white),
                       style: StrokeStyle(lineWidth: 5 * scale, lineCap: .round))
    }

    // MARK: - CZK (Blue circle + white Kc)

    private func drawCZK(context: inout GraphicsContext, scale: CGFloat) {
        let circle = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circle, with: .color(Color(hex: 0x11457E)))

        // K vertical stroke
        let kVertical = Path { p in
            p.move(to: CGPoint(x: 42 * scale, y: 36 * scale))
            p.addLine(to: CGPoint(x: 42 * scale, y: 86 * scale))
        }
        context.stroke(kVertical, with: .color(.white),
                       style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round))

        // K upper diagonal
        let kUpper = Path { p in
            p.move(to: CGPoint(x: 42 * scale, y: 62 * scale))
            p.addLine(to: CGPoint(x: 62 * scale, y: 36 * scale))
        }
        context.stroke(kUpper, with: .color(.white),
                       style: StrokeStyle(lineWidth: 5 * scale, lineCap: .round))

        // K lower diagonal
        let kLower = Path { p in
            p.move(to: CGPoint(x: 42 * scale, y: 62 * scale))
            p.addLine(to: CGPoint(x: 62 * scale, y: 86 * scale))
        }
        context.stroke(kLower, with: .color(.white),
                       style: StrokeStyle(lineWidth: 5 * scale, lineCap: .round))

        // lowercase c
        let cPath = Path { p in
            p.move(to: CGPoint(x: 84 * scale, y: 58 * scale))
            p.addCurve(
                to: CGPoint(x: 64 * scale, y: 66 * scale),
                control1: CGPoint(x: 78 * scale, y: 54 * scale),
                control2: CGPoint(x: 64 * scale, y: 54 * scale)
            )
            p.addCurve(
                to: CGPoint(x: 84 * scale, y: 74 * scale),
                control1: CGPoint(x: 64 * scale, y: 73 * scale),
                control2: CGPoint(x: 78 * scale, y: 78 * scale)
            )
        }
        context.stroke(cPath, with: .color(.white),
                       style: StrokeStyle(lineWidth: 5 * scale, lineCap: .round))
    }

    // MARK: - USDT (Teal circle + white T)

    private func drawUSDT(context: inout GraphicsContext, scale: CGFloat) {
        let circle = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circle, with: .color(Color(hex: 0x26A17B)))

        // T horizontal bar
        let topBar = Path { p in
            p.move(to: CGPoint(x: 40 * scale, y: 42 * scale))
            p.addLine(to: CGPoint(x: 88 * scale, y: 42 * scale))
        }
        context.stroke(topBar, with: .color(.white),
                       style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round))

        // T vertical bar
        let vertBar = Path { p in
            p.move(to: CGPoint(x: 64 * scale, y: 42 * scale))
            p.addLine(to: CGPoint(x: 64 * scale, y: 90 * scale))
        }
        context.stroke(vertBar, with: .color(.white),
                       style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round))
    }
}

// MARK: - Preview

#Preview {
    HStack(spacing: Spacing.md) {
        FiatIcon(symbol: "USD")
        FiatIcon(symbol: "EUR")
        FiatIcon(symbol: "GBP")
        FiatIcon(symbol: "CZK")
        FiatIcon(symbol: "USDT")
        FiatIcon(symbol: "JPY")
    }
    .padding()
    .background(Color.backgroundDark)
}
