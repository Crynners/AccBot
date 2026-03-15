import SwiftUI

struct EditPlanView: View {
    let planId: Int64

    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter

    @State private var selectedCrypto: String = ""
    @State private var selectedFiat: String = ""
    @State private var amount: String = ""
    @State private var selectedFrequency: DcaFrequency = .daily
    @State private var cronExpression: String = ""
    @State private var selectedStrategy: DcaStrategy = .classic
    @State private var withdrawalEnabled: Bool = false
    @State private var withdrawalAddress: String = ""
    @State private var targetAmount: String = ""
    @State private var isSubmitting: Bool = false
    @State private var errorMessage: String?
    @State private var plan: DcaPlan?
    @State private var isLoading: Bool = true

    @State private var showStrategyInfo = false
    @State private var showQrScanner = false
    @State private var showDiscardAlert = false
    // Original values for change tracking
    @State private var originalAmount: String = ""
    @State private var originalFrequency: DcaFrequency = .daily
    @State private var originalCronExpression: String = ""
    @State private var originalStrategy: DcaStrategy = .classic
    @State private var originalWithdrawalEnabled: Bool = false
    @State private var originalWithdrawalAddress: String = ""
    @State private var originalTargetAmount: String = ""

    @Environment(\.accBotColors) private var colors

    private var availableCryptos: [String] {
        plan?.exchange.supportedCryptos ?? []
    }

    private var availableFiats: [String] {
        plan?.exchange.supportedFiats ?? []
    }

    private var hasChanges: Bool {
        amount != originalAmount
            || selectedFrequency != originalFrequency
            || cronExpression != originalCronExpression
            || selectedStrategy != originalStrategy
            || withdrawalEnabled != originalWithdrawalEnabled
            || withdrawalAddress != originalWithdrawalAddress
            || targetAmount != originalTargetAmount
    }

    private var validationHint: String? {
        guard plan != nil else { return nil }
        return PlanFormContent.validate(
            amount: amount,
            selectedFiat: selectedFiat,
            exchange: plan?.exchange,
            selectedFrequency: selectedFrequency,
            cronExpression: cronExpression,
            withdrawalEnabled: withdrawalEnabled,
            withdrawalAddress: withdrawalAddress
        )
    }

    private var isValid: Bool {
        guard plan != nil, !selectedCrypto.isEmpty, !selectedFiat.isEmpty else { return false }
        return validationHint == nil
    }

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(colors.background)
            } else if plan != nil {
                editContent
            } else {
                EmptyStateView(
                    systemImage: "doc.questionmark",
                    title: String(localized: "Plan Not Found"),
                    subtitle: String(localized: "This plan may have been deleted.")
                )
                .background(colors.background)
            }
        }
        .navigationTitle(String(localized: "Edit Plan"))
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(hasChanges)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(String(localized: "Done")) {
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
            }
            if hasChanges {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showDiscardAlert = true
                    } label: {
                        HStack(spacing: Spacing.xs) {
                            Image(systemName: "chevron.left")
                                .font(AccBotFonts.headline)
                            Text(String(localized: "Back"))
                        }
                    }
                }
            }
        }
        .alert(String(localized: "Discard Changes?"), isPresented: $showDiscardAlert) {
            Button(String(localized: "Keep Editing"), role: .cancel) {}
            Button(String(localized: "Discard"), role: .destructive) {
                router.pop()
            }
        } message: {
            Text(String(localized: "You have unsaved changes. Are you sure you want to go back?"))
        }
        .onAppear { loadPlan() }
        .sheet(isPresented: $showStrategyInfo) {
            StrategyInfoSheet()
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerSheet(
                title: String(localized: "Scan Wallet QR"),
                onScanned: { code in
                    withdrawalAddress = cleanQrValue(code)
                }
            )
        }
    }

    // MARK: - Edit Content

    private var editContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                // Exchange display (read-only)
                if let plan = plan {
                    exchangeDisplay(plan)
                }

                // Crypto selection (read-only in edit mode)
                cryptoSection
                    .disabled(true)
                    .opacity(Opacity.disabled)

                // Fiat selection (read-only in edit mode)
                fiatSection
                    .disabled(true)
                    .opacity(Opacity.disabled)

                // Info text about locked fields
                HStack(spacing: Spacing.xs) {
                    Image(systemName: "info.circle")
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .accessibilityHidden(true)
                    Text(String(localized: "Exchange, pair, and fiat currency cannot be changed"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                // Plan form content (amount, frequency, strategy, withdrawal, target)
                PlanFormContent(
                    exchange: plan?.exchange,
                    amount: $amount,
                    selectedFiat: selectedFiat,
                    selectedCrypto: selectedCrypto,
                    selectedFrequency: $selectedFrequency,
                    cronExpression: $cronExpression,
                    selectedStrategy: $selectedStrategy,
                    withdrawalEnabled: $withdrawalEnabled,
                    withdrawalAddress: $withdrawalAddress,
                    targetAmount: $targetAmount,
                    showStrategyInfo: $showStrategyInfo,
                    showQrScanner: $showQrScanner
                )

                // Error message
                if let error = errorMessage {
                    ErrorBanner(message: error)
                }

                // Save button
                saveButton
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
            .maxFormWidth()
        }
        .scrollDismissesKeyboard(.interactively)
        .background(colors.background)
    }

    // MARK: - Exchange Display (read-only)

    private func exchangeDisplay(_ plan: DcaPlan) -> some View {
        HStack(spacing: Spacing.md) {
            Image(plan.exchange.logoName)
                .resizable()
                .scaledToFit()
                .frame(width: 40, height: 40)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(plan.exchange.displayName)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)
                Text(String(localized: "Exchange cannot be changed"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            Spacer()
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Crypto Section

    private var cryptoSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Cryptocurrency"))

            SelectableChipGroup(
                items: availableCryptos,
                selection: selectedCrypto,
                label: { $0 },
                icon: { CryptoIcon(symbol: $0, size: 18) },
                wrapping: true,
                onSelect: { selectedCrypto = $0 }
            )
        }
    }

    // MARK: - Fiat Section

    private var fiatSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Fiat Currency"))

            SelectableChipGroup(
                items: availableFiats,
                selection: selectedFiat,
                label: { $0 },
                icon: { FiatIcon(symbol: $0, size: 18) },
                wrapping: true,
                onSelect: { selectedFiat = $0 }
            )
        }
    }

    // MARK: - Save Button

    private var saveButton: some View {
        VStack(spacing: Spacing.sm) {
            if let hint = validationHint, !isValid {
                Text(hint)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                Task { await savePlan() }
            } label: {
                HStack(spacing: Spacing.sm) {
                    if isSubmitting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                            .scaleEffect(0.8)
                    }
                    Text(isSubmitting
                         ? String(localized: "Saving...")
                         : String(localized: "Save Changes"))
                        .font(AccBotFonts.headline)
                }
                .foregroundStyle(isValid ? colors.onPrimary : colors.disabledForeground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
                .background(isValid ? colors.primary : colors.disabledBackground)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
            .disabled(!isValid || isSubmitting)
        }
        .padding(.bottom, Spacing.xxl)
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AccBotFonts.headline)
            .foregroundStyle(colors.onSurface)
    }

    // MARK: - Data Operations

    private func loadPlan() {
        isLoading = true
        let db = dependencies.activeDatabase
        let id = planId
        Task.detached {
            do {
                guard let loaded = try db.planDao.getById(id) else {
                    await MainActor.run { isLoading = false }
                    return
                }
                await MainActor.run {
                    plan = loaded
                    selectedCrypto = loaded.crypto
                    selectedFiat = loaded.fiat
                    amount = NSDecimalNumber(decimal: loaded.amount).stringValue
                    selectedFrequency = loaded.frequency
                    cronExpression = loaded.cronExpression ?? ""
                    selectedStrategy = loaded.strategy
                    withdrawalEnabled = loaded.withdrawalEnabled
                    withdrawalAddress = loaded.withdrawalAddress ?? ""
                    targetAmount = loaded.targetAmount.map { NSDecimalNumber(decimal: $0).stringValue } ?? ""
                    // Save original values for change tracking
                    originalAmount = amount
                    originalFrequency = selectedFrequency
                    originalCronExpression = cronExpression
                    originalStrategy = selectedStrategy
                    originalWithdrawalEnabled = withdrawalEnabled
                    originalWithdrawalAddress = withdrawalAddress
                    originalTargetAmount = targetAmount
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }

    private func savePlan() async {
        guard let existingPlan = plan else { return }
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return }

        isSubmitting = true
        errorMessage = nil

        do {
            let now = Date()
            let nextExecution: Date?

            if selectedFrequency == .custom {
                nextExecution = CronUtils.getNextExecution(cron: cronExpression, from: now)
            } else {
                nextExecution = Calendar.current.date(
                    byAdding: .minute,
                    value: selectedFrequency.intervalMinutes,
                    to: now
                )
            }

            let updatedPlan = DcaPlan(
                id: existingPlan.id,
                exchange: existingPlan.exchange,
                crypto: selectedCrypto,
                fiat: selectedFiat,
                amount: amountValue,
                frequency: selectedFrequency,
                cronExpression: selectedFrequency == .custom ? cronExpression : nil,
                strategy: selectedStrategy,
                isEnabled: existingPlan.isEnabled,
                withdrawalEnabled: withdrawalEnabled,
                withdrawalAddress: withdrawalEnabled
                    ? withdrawalAddress.trimmingCharacters(in: .whitespaces)
                    : nil,
                targetAmount: targetAmount.isEmpty ? nil : Decimal(string: targetAmount),
                createdAt: existingPlan.createdAt,
                lastExecutedAt: existingPlan.lastExecutedAt,
                nextExecutionAt: nextExecution
            )

            try dependencies.activeDatabase.planDao.update(updatedPlan)
            isSubmitting = false
            UIAccessibility.post(notification: .announcement, argument: String(localized: "Plan saved successfully"))
            router.pop()
        } catch {
            errorMessage = error.localizedDescription
            isSubmitting = false
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        EditPlanView(planId: 1)
    }
}
