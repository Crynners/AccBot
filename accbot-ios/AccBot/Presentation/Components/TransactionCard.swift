import SwiftUI

/// Card displaying a transaction with status icon, pair, amounts, date,
/// and exchange name. Color-coded by transaction status.
struct TransactionCard: View {
    let transaction: Transaction

    @Environment(\.accBotColors) private var colors

    var body: some View {
        HStack(spacing: Spacing.md) {
            statusIcon
            pairAndExchange
            Spacer()
            amountsAndDate
            chevron
        }
        .accessibilityElement(children: .combine)
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Status Icon

    private var statusIcon: some View {
        ZStack {
            Circle()
                .fill(statusColor.opacity(0.15))
                .frame(width: 44, height: 44)

            Image(systemName: statusSystemImage)
                .font(AccBotFonts.headline)
                .foregroundStyle(statusColor)
        }
        .accessibilityLabel(transaction.status.displayName)
    }

    private var statusColor: Color {
        switch transaction.status {
        case .completed: return colors.success
        case .failed: return colors.error
        case .pending: return colors.primary
        case .partial: return colors.warning
        }
    }

    private var statusSystemImage: String {
        switch transaction.status {
        case .completed: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle.fill"
        case .pending: return "clock.fill"
        case .partial: return "minus.circle.fill"
        }
    }

    // MARK: - Pair & Exchange

    private var pairAndExchange: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(transaction.pair)
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onSurface)

            Text(transaction.exchange.displayName)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            if transaction.status == .failed, let error = transaction.errorMessage {
                Text(error)
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.error)
                    .lineLimit(2)
            }
            if let warning = transaction.warningMessage {
                Text(warning)
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.warning)
                    .lineLimit(2)
            }
        }
    }

    // MARK: - Chevron

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(AccBotFonts.caption)
            .foregroundStyle(colors.onSurfaceVariant)
            .accessibilityHidden(true)
    }

    // MARK: - Amounts & Date

    private var amountsAndDate: some View {
        VStack(alignment: .trailing, spacing: Spacing.xxs) {
            Text(formattedFiatAmount)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
                .allowsTightening(true)

            if transaction.status == .completed || transaction.status == .partial {
                Text(formattedCryptoAmount)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.primary)
            }

            Text(formattedDate)
                .font(AccBotFonts.captionSmall)
                .foregroundStyle(colors.onSurfaceVariant)
        }
    }

    // MARK: - Formatting

    private var formattedFiatAmount: String {
        "-\(AccBotFormatters.formatFiat(transaction.fiatAmount, symbol: transaction.fiat))"
    }

    private var formattedCryptoAmount: String {
        "+\(AccBotFormatters.formatCrypto(transaction.cryptoAmount, symbol: transaction.crypto))"
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
