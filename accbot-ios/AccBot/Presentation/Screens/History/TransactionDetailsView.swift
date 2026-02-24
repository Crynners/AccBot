import SwiftUI

struct TransactionDetailsView: View {
    let transactionId: Int64
    @EnvironmentObject var dependencies: AppDependencies
    @State private var transaction: Transaction?

    var body: some View {
        ScrollView {
            if let tx = transaction {
                VStack(spacing: Spacing.lg) {
                    // Status header
                    statusHeader(tx)

                    // Details card
                    detailsCard(tx)

                    // Error/Warning
                    if let error = tx.errorMessage {
                        infoCard(title: "Error", message: error, color: .errorRed)
                    }
                    if let warning = tx.warningMessage {
                        infoCard(title: "Warning", message: warning, color: .warningOrange)
                    }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.bottom, Spacing.xxl)
            } else {
                LoadingStateView(message: "Loading transaction...")
            }
        }
        .background(Color.backgroundDark)
        .navigationTitle("Transaction Details")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadTransaction() }
    }

    private func statusHeader(_ tx: Transaction) -> some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: statusIcon(tx.status))
                .font(.system(size: 48))
                .foregroundColor(statusColor(tx.status))

            Text(tx.status.rawValue)
                .font(AccBotFonts.headline)
                .foregroundColor(statusColor(tx.status))

            Text(tx.pair)
                .font(AccBotFonts.titleMedium)
                .foregroundColor(.white)

            Text(formatDate(tx.executedAt))
                .font(AccBotFonts.bodySmall)
                .foregroundColor(.onSurfaceVariantColor)
        }
        .padding(.vertical, Spacing.xl)
    }

    private func detailsCard(_ tx: Transaction) -> some View {
        VStack(spacing: Spacing.md) {
            detailRow("Exchange", tx.exchange.displayName)
            Divider().background(Color.onSurfaceVariantColor.opacity(0.3))
            detailRow("Fiat Amount", "\(tx.fiatAmount) \(tx.fiat)")
            Divider().background(Color.onSurfaceVariantColor.opacity(0.3))
            detailRow("Crypto Amount", "\(tx.cryptoAmount) \(tx.crypto)")
            Divider().background(Color.onSurfaceVariantColor.opacity(0.3))
            detailRow("Price", "\(tx.price) \(tx.fiat)")
            Divider().background(Color.onSurfaceVariantColor.opacity(0.3))
            detailRow("Fee", "\(tx.fee) \(tx.feeAsset.isEmpty ? tx.fiat : tx.feeAsset)")
            if let orderId = tx.exchangeOrderId {
                Divider().background(Color.onSurfaceVariantColor.opacity(0.3))
                detailRow("Order ID", orderId)
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.md)
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(.onSurfaceVariantColor)
            Spacer()
            Text(value)
                .font(AccBotFonts.body)
                .foregroundColor(.white)
                .textSelection(.enabled)
        }
    }

    private func infoCard(title: String, message: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(AccBotFonts.headline)
                .foregroundColor(color)
            Text(message)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.lg)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.md)
    }

    private func statusIcon(_ status: TransactionStatus) -> String {
        switch status {
        case .completed: return "checkmark.circle.fill"
        case .failed: return "xmark.circle.fill"
        case .pending: return "clock.fill"
        case .partial: return "exclamationmark.circle.fill"
        }
    }

    private func statusColor(_ status: TransactionStatus) -> Color {
        switch status {
        case .completed: return .accentTeal
        case .failed: return .errorRed
        case .pending: return .warningOrange
        case .partial: return .warningOrange
        }
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .medium
        return formatter.string(from: date)
    }

    private func loadTransaction() {
        transaction = try? dependencies.activeDatabase.transactionDao.getById(transactionId)
    }
}

struct TransactionDetailsViewModel {
    // Intentionally empty - simple enough to not need a VM
}
