import SwiftUI

struct TransactionDetailsView: View {
    let transactionId: Int64
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @State private var transaction: Transaction?
    @State private var loadFailed = false
    @State private var loadedSandboxMode = false
    @State private var showConfirmNoOrder = false
    @State private var showMatchOrder = false
    @State private var orderIdToMatch = ""
    @State private var reviewError: String?
    @State private var isReviewing = false

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

                    if tx.status == .pending {
                        purchaseReviewCard(tx)
                    }

                    // Error/Warning
                    if let error = tx.errorMessage {
                        let category = ErrorCategory.of(error)
                        if let title = category.title, let advice = category.advice {
                            infoCard(
                                title: LocalizedStringKey(title),
                                message: advice + "\n" + String(localized: "Technical detail: \(error)"),
                                color: colors.error
                            )
                        } else {
                            infoCard(title: "Error", message: error, color: colors.error)
                        }
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
        .onChange(of: dependencies.userPreferences.sandboxMode) { _ in dismiss() }
        .alert("Mark as not executed?", isPresented: $showConfirmNoOrder) {
            Button("Cancel", role: .cancel) {}
            Button("Mark as not executed", role: .destructive) { resolveReview(confirmedOrderId: nil) }
        } message: {
            Text("The purchase will be marked as not executed and the plan continues on schedule. Confirm only if the exchange shows no open order and nothing was bought.")
        }
        .alert("Enter the order ID", isPresented: $showMatchOrder) {
            TextField("Order ID", text: $orderIdToMatch)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            Button("Cancel", role: .cancel) {}
            Button("Verify and save") {
                let orderId = orderIdToMatch.trimmingCharacters(in: .whitespacesAndNewlines)
                if !orderId.isEmpty { resolveReview(confirmedOrderId: orderId) }
            }
        } message: {
            Text("Copy the order ID from the exchange. AccBot verifies it and records the amount actually bought. If you already imported the purchase, use its ID from History.")
        }
    }

    private func purchaseReviewCard(_ tx: Transaction) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Label("Review purchase", systemImage: "exclamationmark.triangle")
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.warning)
            Text(tx.needsPurchaseReview
                 ? String(localized: "Open the order history for \(tx.pair) on \(tx.exchange.displayName) around \(formatDate(tx.executedAt)). What did you find? The plan is on hold until you answer.")
                 : String(localized: "The exchange accepted the order. Fill details are pending."))
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurfaceVariant)
            Button {
                orderIdToMatch = tx.exchangeOrderId ?? ""
                showMatchOrder = true
            } label: {
                Text("The purchase is there, I'll enter the order ID")
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            Button {
                showConfirmNoOrder = true
            } label: {
                Text("There is nothing on the exchange")
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            if isReviewing { ProgressView() }
            if let reviewError {
                Text(reviewError)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.error)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        .tint(colors.primary)
        .disabled(isReviewing)
    }

    private func resolveReview(confirmedOrderId: String?) {
        guard let transaction, !isReviewing else { return }
        isReviewing = true
        reviewError = nil
        Task {
            defer { isReviewing = false }
            do {
                try await dependencies.dcaExecutionEngine.resolvePurchaseReview(
                    transaction, confirmedOrderId: confirmedOrderId, isSandbox: loadedSandboxMode
                )
                loadTransaction()
                if self.transaction == nil { dismiss() }
            } catch {
                reviewError = error.localizedDescription
            }
        }
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
        loadedSandboxMode = dependencies.userPreferences.sandboxMode
        do {
            transaction = try dependencies.activeDatabase.transactionDao.getById(transactionId)
            loadFailed = transaction == nil
        } catch {
            loadFailed = true
        }
    }
}
