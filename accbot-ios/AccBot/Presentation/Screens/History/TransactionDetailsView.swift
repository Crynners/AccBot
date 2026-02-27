import SwiftUI

struct TransactionDetailsView: View {
    let transactionId: Int64
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors
    @State private var transaction: Transaction?
    @State private var loadFailed = false

    var body: some View {
        ScrollView {
            if let tx = transaction {
                VStack(spacing: Spacing.lg) {
                    // Status header
                    statusHeader(tx)

                    // Amount Received card
                    if tx.status == .completed || tx.status == .partial {
                        amountReceivedCard(tx)
                    }

                    // Details card
                    detailsCard(tx)

                    // Error/Warning
                    if let error = tx.errorMessage {
                        infoCard(title: "Error", message: error, color: colors.error)
                    }
                    if let warning = tx.warningMessage {
                        infoCard(title: "Warning", message: warning, color: colors.warning)
                    }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.bottom, Spacing.xxl)
            } else if loadFailed {
                EmptyStateView(
                    systemImage: "exclamationmark.triangle",
                    title: String(localized: "Transaction Not Found"),
                    subtitle: String(localized: "This transaction may have been deleted.")
                )
            } else {
                LoadingStateView(message: "Loading transaction...")
            }
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Transaction Details"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadTransaction() }
    }

    private func statusHeader(_ tx: Transaction) -> some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: statusIcon(tx.status))
                .font(AccBotFonts.displayLarge)
                .foregroundStyle(statusColor(tx.status))
                .accessibilityHidden(true)

            Text(tx.status.displayName)
                .font(AccBotFonts.headline)
                .foregroundStyle(statusColor(tx.status))
                .accessibilityLabel(String(localized: "Status: \(tx.status.displayName)"))

            Text(tx.pair)
                .font(AccBotFonts.titleMedium)
                .foregroundStyle(colors.onSurface)

            Text(formatDate(tx.executedAt))
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
        }
        .padding(.vertical, Spacing.xl)
    }

    private func detailsCard(_ tx: Transaction) -> some View {
        VStack(spacing: Spacing.md) {
            detailRow("Exchange", tx.exchange.displayName, icon: "building.columns")
            Divider().background(colors.onSurfaceVariant.opacity(0.3))
            detailRow("Fiat Amount", AccBotFormatters.formatFiat(tx.fiatAmount, symbol: tx.fiat), icon: "banknote")
            Divider().background(colors.onSurfaceVariant.opacity(0.3))
            detailRow("Crypto Amount", AccBotFormatters.formatCrypto(tx.cryptoAmount, symbol: tx.crypto), icon: "bitcoinsign.circle")
            Divider().background(colors.onSurfaceVariant.opacity(0.3))
            detailRow("Price", AccBotFormatters.formatFiat(tx.price, symbol: tx.fiat), icon: "chart.line.uptrend.xyaxis")
            Divider().background(colors.onSurfaceVariant.opacity(0.3))
            detailRow("Fee", AccBotFormatters.formatFiat(tx.fee, symbol: tx.feeAsset.isEmpty ? tx.fiat : tx.feeAsset), icon: "receipt")
            if let orderId = tx.exchangeOrderId {
                Divider().background(colors.onSurfaceVariant.opacity(0.3))
                orderIdRow(orderId)
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func detailRow(_ label: LocalizedStringKey, _ value: String, icon: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: icon)
                .font(AccBotFonts.label)
                .foregroundStyle(colors.primary)
                .frame(width: 20)
                .accessibilityHidden(true)
            Text(label)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
            Spacer()
            Text(value)
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurface)
                .textSelection(.enabled)
        }
    }

    private func orderIdRow(_ orderId: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "tag")
                .font(AccBotFonts.label)
                .foregroundStyle(colors.onSurfaceVariant)
                .frame(width: 20)
                .accessibilityHidden(true)
            Text(String(localized: "Order ID"))
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
            Spacer()
            Text(orderId)
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurface)
                .lineLimit(1)
            Button {
                UIPasteboard.general.string = orderId
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                UIAccessibility.post(notification: .announcement, argument: String(localized: "Copied to clipboard"))
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(AccBotFonts.label)
                    .foregroundStyle(colors.primary)
            }
            .buttonStyle(.plain)
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
            .accessibilityLabel(String(localized: "Copy order ID"))
        }
    }

    private func amountReceivedCard(_ tx: Transaction) -> some View {
        VStack(spacing: Spacing.xs) {
            Text(String(localized: "Amount Received"))
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
            Text(AccBotFormatters.formatCrypto(tx.cryptoAmount, symbol: tx.crypto))
                .font(AccBotFonts.titleLarge)
                .foregroundStyle(colors.onSurface)
            Text(AccBotFormatters.formatFiat(tx.fiatAmount, symbol: tx.fiat))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func infoCard(title: LocalizedStringKey, message: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(AccBotFonts.headline)
                .foregroundStyle(color)
            Text(message)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.lg)
        .background(color.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func statusIcon(_ status: TransactionStatus) -> String {
        switch status {
        case .completed: return "checkmark.circle.fill"
        case .failed: return "xmark.circle.fill"
        case .pending: return "clock.fill"
        case .partial: return "minus.circle.fill"
        }
    }

    private func statusColor(_ status: TransactionStatus) -> Color {
        switch status {
        case .completed: return colors.success
        case .failed: return colors.error
        case .pending: return colors.primary
        case .partial: return colors.warning
        }
    }

    private func formatDate(_ date: Date) -> String {
        AccBotFormatters.mediumDate.string(from: date)
    }

    private func loadTransaction() {
        do {
            transaction = try dependencies.activeDatabase.transactionDao.getById(transactionId)
            if transaction == nil { loadFailed = true }
        } catch {
            loadFailed = true
        }
    }
}
