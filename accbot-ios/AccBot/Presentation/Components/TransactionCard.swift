import SwiftUI

/// Card displaying a transaction with status icon, pair, amounts, date,
/// and exchange name. Color-coded by transaction status.
struct TransactionCard: View {
    let transaction: Transaction

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    var body: some View {
        HStack(spacing: Spacing.md) {
            statusIcon
            pairAndExchange
            Spacer()
            amountsAndDate
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Status Icon

    private var statusIcon: some View {
        ZStack {
            Circle()
                .fill(statusColor.opacity(0.15))
                .frame(width: 40, height: 40)

            Image(systemName: statusSystemImage)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(statusColor)
        }
    }

    private var statusColor: Color {
        switch transaction.status {
        case .completed: return colors.success
        case .failed: return colors.error
        case .pending: return colors.warning
        case .partial: return colors.warning
        }
    }

    private var statusSystemImage: String {
        switch transaction.status {
        case .completed: return "checkmark"
        case .failed: return "xmark"
        case .pending: return "clock"
        case .partial: return "exclamationmark.triangle"
        }
    }

    // MARK: - Pair & Exchange

    private var pairAndExchange: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(transaction.pair)
                .font(AccBotFonts.headline)
                .foregroundColor(colors.onSurface)

            Text(transaction.exchange.displayName)
                .font(AccBotFonts.caption)
                .foregroundColor(colors.onSurfaceVariant)
        }
    }

    // MARK: - Amounts & Date

    private var amountsAndDate: some View {
        VStack(alignment: .trailing, spacing: Spacing.xxs) {
            Text(formattedFiatAmount)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.onSurface)

            if transaction.status == .completed || transaction.status == .partial {
                Text(formattedCryptoAmount)
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.primary)
            }

            Text(formattedDate)
                .font(AccBotFonts.captionSmall)
                .foregroundColor(colors.onSurfaceVariant)
        }
    }

    // MARK: - Formatting

    private var formattedFiatAmount: String {
        let amount = NSDecimalNumber(decimal: transaction.fiatAmount)
        return "\(amount.stringValue) \(transaction.fiat)"
    }

    private var formattedCryptoAmount: String {
        let amount = NSDecimalNumber(decimal: transaction.cryptoAmount)
        return "+\(amount.stringValue) \(transaction.crypto)"
    }

    private var formattedDate: String {
        transaction.executedAt.formatted(date: .abbreviated, time: .shortened)
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: Spacing.sm) {
        TransactionCard(transaction: Transaction(
            planId: 1,
            exchange: .binance,
            crypto: "BTC",
            fiat: "EUR",
            fiatAmount: 50,
            cryptoAmount: 0.00058,
            price: 86206.90,
            fee: 0.05,
            status: .completed
        ))

        TransactionCard(transaction: Transaction(
            planId: 1,
            exchange: .kraken,
            crypto: "ETH",
            fiat: "USD",
            fiatAmount: 100,
            cryptoAmount: 0,
            price: 0,
            fee: 0,
            status: .failed,
            errorMessage: "Insufficient balance"
        ))

        TransactionCard(transaction: Transaction(
            planId: 1,
            exchange: .coinmate,
            crypto: "BTC",
            fiat: "CZK",
            fiatAmount: 500,
            cryptoAmount: 0,
            price: 0,
            fee: 0,
            status: .pending
        ))
    }
    .padding()
    .background(Color.backgroundDark)
}
