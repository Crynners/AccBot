import SwiftUI

/// Displays a cryptocurrency symbol as a Canvas-rendered icon matching Android vector drawables.
/// Known symbols (BTC, ETH, SOL, ADA, DOT, LTC) get proper logo rendering;
/// unknown symbols fall back to a colored circle with text abbreviation.
struct CryptoIcon: View {
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
        case "BTC": return String(localized: "Bitcoin")
        case "ETH": return String(localized: "Ethereum")
        case "SOL": return String(localized: "Solana")
        case "ADA": return String(localized: "Cardano")
        case "DOT": return String(localized: "Polkadot")
        case "LTC": return String(localized: "Litecoin")
        case "XRP": return "XRP"
        case "DOGE": return String(localized: "Dogecoin")
        case "LINK": return String(localized: "Chainlink")
        case "AVAX": return String(localized: "Avalanche")
        case "MATIC": return String(localized: "Polygon")
        default: return symbol
        }
    }

    private var hasCanvasIcon: Bool {
        ["BTC", "ETH", "SOL", "ADA", "DOT", "LTC"].contains(symbol)
    }

    // MARK: - Fallback (text circle)

    private var fallbackIcon: some View {
        ZStack {
            backgroundCircle
            Text(abbreviation)
                .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
    }

    private var backgroundCircle: some View {
        Circle()
            .fill(cryptoColor)
    }

    private var cryptoColor: Color {
        switch symbol {
        case "BTC": return Color(hex: 0xF7931A)
        case "ETH": return Color(hex: 0x627EEA)
        case "LTC": return Color(hex: 0x345D9D)
        case "ADA": return Color(hex: 0x0033AD)
        case "DOT": return Color(hex: 0xE6007A)
        case "XRP": return Color(hex: 0x23292F)
        case "DOGE": return Color(hex: 0xC2A633)
        case "LINK": return Color(hex: 0x2A5ADA)
        case "AVAX": return Color(hex: 0xE84142)
        case "MATIC": return Color(hex: 0x8247E5)
        default: return Color(hex: 0x6B7280)
        }
    }

    private var abbreviation: String {
        if symbol.count <= 3 {
            return symbol
        }
        return String(symbol.prefix(3))
    }

    // MARK: - Canvas Drawing

    /// All coordinates are in a 128x128 viewport, scaled to the actual size.
    private func drawIcon(context: inout GraphicsContext, size: CGSize) {
        let scale = size.width / 128.0

        switch symbol {
        case "BTC": drawBTC(context: &context, scale: scale)
        case "ETH": drawETH(context: &context, scale: scale)
        case "SOL": drawSOL(context: &context, scale: scale)
        case "ADA": drawADA(context: &context, scale: scale)
        case "DOT": drawDOT(context: &context, scale: scale)
        case "LTC": drawLTC(context: &context, scale: scale)
        default: break
        }
    }

    // MARK: - BTC (Orange circle + white B with vertical bars, evenOdd)

    private func drawBTC(context: inout GraphicsContext, scale: CGFloat) {
        // Orange circle
        let circlePath = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circlePath, with: .color(Color(hex: 0xF7931A)))

        // Vertical bars
        let bars = Path { p in
            p.addRect(CGRect(x: 54.5 * scale, y: 33 * scale, width: 3 * scale, height: 9 * scale))
            p.addRect(CGRect(x: 70.5 * scale, y: 33 * scale, width: 3 * scale, height: 9 * scale))
            p.addRect(CGRect(x: 54.5 * scale, y: 86 * scale, width: 3 * scale, height: 9 * scale))
            p.addRect(CGRect(x: 70.5 * scale, y: 86 * scale, width: 3 * scale, height: 9 * scale))
        }
        context.fill(bars, with: .color(.white))

        // B shape with counter holes (evenOdd fill rule)
        let bShape = Path { p in
            // Outer B shape
            p.move(to: CGPoint(x: 48 * scale, y: 42 * scale))
            p.addLine(to: CGPoint(x: 68 * scale, y: 42 * scale))
            p.addQuadCurve(
                to: CGPoint(x: 80 * scale, y: 54 * scale),
                control: CGPoint(x: 80 * scale, y: 42 * scale)
            )
            p.addQuadCurve(
                to: CGPoint(x: 73 * scale, y: 64 * scale),
                control: CGPoint(x: 80 * scale, y: 60 * scale)
            )
            p.addQuadCurve(
                to: CGPoint(x: 84 * scale, y: 76 * scale),
                control: CGPoint(x: 84 * scale, y: 66 * scale)
            )
            p.addQuadCurve(
                to: CGPoint(x: 70 * scale, y: 88 * scale),
                control: CGPoint(x: 84 * scale, y: 88 * scale)
            )
            p.addLine(to: CGPoint(x: 48 * scale, y: 88 * scale))
            p.closeSubpath()

            // Top counter hole
            p.move(to: CGPoint(x: 56 * scale, y: 50 * scale))
            p.addLine(to: CGPoint(x: 66 * scale, y: 50 * scale))
            p.addQuadCurve(
                to: CGPoint(x: 66 * scale, y: 58 * scale),
                control: CGPoint(x: 72 * scale, y: 54 * scale)
            )
            p.addLine(to: CGPoint(x: 56 * scale, y: 58 * scale))
            p.closeSubpath()

            // Bottom counter hole
            p.move(to: CGPoint(x: 56 * scale, y: 66 * scale))
            p.addLine(to: CGPoint(x: 68 * scale, y: 66 * scale))
            p.addQuadCurve(
                to: CGPoint(x: 68 * scale, y: 79 * scale),
                control: CGPoint(x: 76 * scale, y: 72.5 * scale)
            )
            p.addLine(to: CGPoint(x: 56 * scale, y: 79 * scale))
            p.closeSubpath()
        }
        context.fill(bShape, with: .color(.white), style: FillStyle(eoFill: true))
    }

    // MARK: - ETH (Blue-purple circle + white diamond)

    private func drawETH(context: inout GraphicsContext, scale: CGFloat) {
        // Blue-purple circle
        let circlePath = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circlePath, with: .color(Color(hex: 0x627EEA)))

        // Diamond top (0.9 alpha)
        let topDiamond = Path { p in
            p.move(to: CGPoint(x: 64 * scale, y: 28 * scale))
            p.addLine(to: CGPoint(x: 42 * scale, y: 64 * scale))
            p.addLine(to: CGPoint(x: 64 * scale, y: 54 * scale))
            p.addLine(to: CGPoint(x: 86 * scale, y: 64 * scale))
            p.closeSubpath()
        }
        context.fill(topDiamond, with: .color(.white.opacity(0.9)))

        // Diamond bottom (0.6 alpha)
        let bottomDiamond = Path { p in
            p.move(to: CGPoint(x: 64 * scale, y: 100 * scale))
            p.addLine(to: CGPoint(x: 42 * scale, y: 64 * scale))
            p.addLine(to: CGPoint(x: 64 * scale, y: 74 * scale))
            p.addLine(to: CGPoint(x: 86 * scale, y: 64 * scale))
            p.closeSubpath()
        }
        context.fill(bottomDiamond, with: .color(.white.opacity(0.6)))
    }

    // MARK: - SOL (Purple circle + white S stroke)

    private func drawSOL(context: inout GraphicsContext, scale: CGFloat) {
        // Purple circle
        let circlePath = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circlePath, with: .color(Color(hex: 0x9945FF)))

        // S curve stroke
        let sPath = Path { p in
            p.move(to: CGPoint(x: 78 * scale, y: 48 * scale))
            // Top arc (right to left, curving through top)
            p.addCurve(
                to: CGPoint(x: 50 * scale, y: 48 * scale),
                control1: CGPoint(x: 78 * scale, y: 38 * scale),
                control2: CGPoint(x: 50 * scale, y: 38 * scale)
            )
            // Middle transition (S-curve from top-left to bottom-right)
            p.addCurve(
                to: CGPoint(x: 78 * scale, y: 72 * scale),
                control1: CGPoint(x: 50 * scale, y: 58 * scale),
                control2: CGPoint(x: 78 * scale, y: 62 * scale)
            )
            // Bottom arc (right to left, curving through bottom)
            p.addCurve(
                to: CGPoint(x: 50 * scale, y: 72 * scale),
                control1: CGPoint(x: 78 * scale, y: 82 * scale),
                control2: CGPoint(x: 50 * scale, y: 82 * scale)
            )
        }
        context.stroke(sPath, with: .color(.white), lineWidth: 6 * scale, options: StrokeOptions(lineCap: .round))
    }

    // MARK: - ADA (Blue circle + white A stroke + crossbar)

    private func drawADA(context: inout GraphicsContext, scale: CGFloat) {
        // Blue circle
        let circlePath = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circlePath, with: .color(Color(hex: 0x0033AD)))

        // A letter
        let aPath = Path { p in
            p.move(to: CGPoint(x: 46 * scale, y: 86 * scale))
            p.addLine(to: CGPoint(x: 64 * scale, y: 36 * scale))
            p.addLine(to: CGPoint(x: 82 * scale, y: 86 * scale))
        }
        context.stroke(aPath, with: .color(.white), lineWidth: 6 * scale, options: StrokeOptions(lineCap: .round, lineJoin: .round))

        // A crossbar
        let crossbar = Path { p in
            p.move(to: CGPoint(x: 52 * scale, y: 70 * scale))
            p.addLine(to: CGPoint(x: 76 * scale, y: 70 * scale))
        }
        context.stroke(crossbar, with: .color(.white), lineWidth: 5 * scale, options: StrokeOptions(lineCap: .round))
    }

    // MARK: - DOT (Pink circle + 7 white dots)

    private func drawDOT(context: inout GraphicsContext, scale: CGFloat) {
        // Pink circle
        let circlePath = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circlePath, with: .color(Color(hex: 0xE6007A)))

        // Center dot (radius 8)
        let center = Path(ellipseIn: CGRect(x: 56 * scale, y: 56 * scale, width: 16 * scale, height: 16 * scale))
        context.fill(center, with: .color(.white))

        // Top dot (radius 7, alpha 0.8)
        let top = Path(ellipseIn: CGRect(x: 57 * scale, y: 28 * scale, width: 14 * scale, height: 14 * scale))
        context.fill(top, with: .color(.white.opacity(0.8)))

        // Bottom dot (radius 7, alpha 0.8)
        let bottom = Path(ellipseIn: CGRect(x: 57 * scale, y: 86 * scale, width: 14 * scale, height: 14 * scale))
        context.fill(bottom, with: .color(.white.opacity(0.8)))

        // Left dot (radius 6, alpha 0.6)
        let left = Path(ellipseIn: CGRect(x: 30 * scale, y: 50 * scale, width: 12 * scale, height: 12 * scale))
        context.fill(left, with: .color(.white.opacity(0.6)))

        // Right dot (radius 6, alpha 0.6)
        let right = Path(ellipseIn: CGRect(x: 86 * scale, y: 50 * scale, width: 12 * scale, height: 12 * scale))
        context.fill(right, with: .color(.white.opacity(0.6)))

        // Bottom-left dot (radius 6, alpha 0.6)
        let bottomLeft = Path(ellipseIn: CGRect(x: 36 * scale, y: 74 * scale, width: 12 * scale, height: 12 * scale))
        context.fill(bottomLeft, with: .color(.white.opacity(0.6)))

        // Bottom-right dot (radius 6, alpha 0.6)
        let bottomRight = Path(ellipseIn: CGRect(x: 80 * scale, y: 74 * scale, width: 12 * scale, height: 12 * scale))
        context.fill(bottomRight, with: .color(.white.opacity(0.6)))
    }

    // MARK: - LTC (Silver-blue circle + white L + diagonal slash)

    private func drawLTC(context: inout GraphicsContext, scale: CGFloat) {
        // Silver-blue circle
        let circlePath = Path(ellipseIn: CGRect(x: 8 * scale, y: 8 * scale, width: 112 * scale, height: 112 * scale))
        context.fill(circlePath, with: .color(Color(hex: 0x345D9D)))

        // L letter
        let lPath = Path { p in
            p.move(to: CGPoint(x: 54 * scale, y: 36 * scale))
            p.addLine(to: CGPoint(x: 54 * scale, y: 86 * scale))
            p.addLine(to: CGPoint(x: 82 * scale, y: 86 * scale))
        }
        context.stroke(lPath, with: .color(.white), lineWidth: 6 * scale, options: StrokeOptions(lineCap: .round, lineJoin: .round))

        // Diagonal slash (0.7 alpha)
        let slash = Path { p in
            p.move(to: CGPoint(x: 44 * scale, y: 72 * scale))
            p.addLine(to: CGPoint(x: 72 * scale, y: 56 * scale))
        }
        context.stroke(slash, with: .color(.white.opacity(0.7)), lineWidth: 4 * scale, options: StrokeOptions(lineCap: .round))
    }

    /// Helper for Canvas stroke options
    fileprivate struct StrokeOptions: Sendable {
        let lineCap: CGLineCap
        let lineJoin: CGLineJoin

        init(lineCap: CGLineCap = .butt, lineJoin: CGLineJoin = .miter) {
            self.lineCap = lineCap
            self.lineJoin = lineJoin
        }
    }
}

// MARK: - Canvas stroke extension

private extension GraphicsContext {
    mutating func stroke(_ path: Path, with shading: Shading, lineWidth: CGFloat, options: CryptoIcon.StrokeOptions) {
        stroke(
            path,
            with: shading,
            style: StrokeStyle(lineWidth: lineWidth, lineCap: options.lineCap, lineJoin: options.lineJoin)
        )
    }
}

// MARK: - Preview

#Preview {
    HStack(spacing: Spacing.md) {
        CryptoIcon(symbol: "BTC")
        CryptoIcon(symbol: "ETH")
        CryptoIcon(symbol: "SOL")
        CryptoIcon(symbol: "ADA")
        CryptoIcon(symbol: "DOT")
        CryptoIcon(symbol: "LTC")
    }
    .padding()
    .background(Color.backgroundDark)
}
