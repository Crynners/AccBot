import SwiftUI

/// Displays a cryptocurrency symbol inside a colored circle.
/// Each crypto has a characteristic brand color; unknown symbols
/// default to a neutral gray.
struct CryptoIcon: View {
    let symbol: String
    let size: CGFloat

    init(symbol: String, size: CGFloat = 32) {
        self.symbol = symbol.uppercased()
        self.size = size
    }

    var body: some View {
        ZStack {
            backgroundCircle
            Text(abbreviation)
                .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                .foregroundColor(.white)
        }
        .frame(width: size, height: size)
    }

    // MARK: - Background

    @ViewBuilder
    private var backgroundCircle: some View {
        switch symbol {
        case "SOL":
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color(hex: 0x9945FF), Color(hex: 0x14F195)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        default:
            Circle()
                .fill(cryptoColor)
        }
    }

    private var cryptoColor: Color {
        switch symbol {
        case "BTC": return Color(hex: 0xF7931A)
        case "ETH": return Color(hex: 0x627EEA)
        case "LTC": return Color(hex: 0xBFBBB6)
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

    /// Short abbreviation for display inside the circle.
    private var abbreviation: String {
        if symbol.count <= 3 {
            return symbol
        }
        return String(symbol.prefix(3))
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
