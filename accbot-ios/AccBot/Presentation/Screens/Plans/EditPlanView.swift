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
    @State private var isSubmitting: Bool = false
    @State private var errorMessage: String?
    @State private var plan: DcaPlan?
    @State private var isLoading: Bool = true

    @State private var showStrategyInfo = false
    @State private var showQrScanner = false

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    private let amountPresets = [25, 50, 100, 250, 500]

    private var availableCryptos: [String] {
        plan?.exchange.supportedCryptos ?? []
    }

    private var availableFiats: [String] {
        plan?.exchange.supportedFiats ?? []
    }

    private var minOrderSizeDisplay: String? {
        guard let exchange = plan?.exchange,
              let minSize = exchange.minOrderSize[selectedFiat] else { return nil }
        return "\(minSize) \(selectedFiat)"
    }

    private var isValid: Bool {
        guard plan != nil else { return false }
        guard !selectedCrypto.isEmpty, !selectedFiat.isEmpty else { return false }
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return false }

        if let exchange = plan?.exchange,
           let minSize = exchange.minOrderSize[selectedFiat],
           amountValue < minSize {
            return false
        }

        if selectedFrequency == .custom && !CronUtils.isValid(cron: cronExpression) {
            return false
        }

        if withdrawalEnabled && withdrawalAddress.trimmingCharacters(in: .whitespaces).isEmpty {
            return false
        }

        return true
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
        .onAppear { loadPlan() }
        .sheet(isPresented: $showStrategyInfo) {
            StrategyInfoSheet()
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerSheet(
                title: String(localized: "Scan Wallet QR"),
                onScanned: { code in
                    let address = code.hasPrefix("bitcoin:")
                        ? String(code.dropFirst("bitcoin:".count)).components(separatedBy: "?").first ?? code
                        : code
                    withdrawalAddress = address
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

                // Crypto selection
                cryptoSection

                // Fiat selection
                fiatSection

                // Amount input
                amountSection

                // Frequency
                frequencySection

                // Strategy
                strategySection

                // Auto-withdrawal
                withdrawalSection

                // Error message
                if let error = errorMessage {
                    errorBanner(error)
                }

                // Save button
                saveButton
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
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
                    .foregroundColor(colors.onSurface)
                Text(String(localized: "Exchange cannot be changed"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            }

            Spacer()
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Crypto Section

    private var cryptoSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Cryptocurrency"))

            SelectableChipGroup(
                items: availableCryptos,
                selection: selectedCrypto,
                label: { $0 },
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
                onSelect: { selectedFiat = $0 }
            )
        }
    }

    // MARK: - Amount Section

    private var amountSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Amount per Purchase"))

            HStack(spacing: Spacing.sm) {
                TextField("0", text: $amount)
                    .font(AccBotFonts.titleMedium)
                    .foregroundColor(colors.onSurface)
                    .keyboardType(.decimalPad)
                    .padding(Spacing.md)
                    .background(colors.surface)
                    .cornerRadius(CornerRadius.sm)

                Text(selectedFiat)
                    .font(AccBotFonts.headline)
                    .foregroundColor(colors.primary)
            }

            HStack(spacing: Spacing.sm) {
                ForEach(amountPresets, id: \.self) { preset in
                    Button {
                        amount = "\(preset)"
                    } label: {
                        Text("\(preset)")
                            .font(AccBotFonts.label)
                            .foregroundColor(
                                amount == "\(preset)" ? colors.background : colors.primary
                            )
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.sm)
                            .background(
                                amount == "\(preset)"
                                    ? colors.primary
                                    : colors.primary.opacity(0.15)
                            )
                            .cornerRadius(CornerRadius.sm)
                    }
                }
            }

            if let minDisplay = minOrderSizeDisplay {
                Text(String(localized: "Minimum order: \(minDisplay)"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            }
        }
    }

    // MARK: - Frequency Section

    private var frequencySection: some View {
        ScheduleBuilder(
            selectedFrequency: $selectedFrequency,
            cronExpression: $cronExpression
        )
    }

    // MARK: - Strategy Section

    private var strategySection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                sectionHeader(String(localized: "Strategy"))
                Spacer()
                Button {
                    showStrategyInfo = true
                } label: {
                    Image(systemName: "info.circle")
                        .font(AccBotFonts.body)
                        .foregroundColor(colors.primary)
                }
            }

            HStack(spacing: Spacing.sm) {
                ForEach(DcaStrategy.allStrategies, id: \.dbString) { strategy in
                    strategyButton(strategy)
                }
            }
        }
    }

    private func strategyButton(_ strategy: DcaStrategy) -> some View {
        let isSelected = selectedStrategy.dbString == strategy.dbString
        return Button {
            selectedStrategy = strategy
        } label: {
            VStack(spacing: Spacing.xs) {
                Image(systemName: strategyIcon(strategy))
                    .font(AccBotFonts.body)
                Text(strategy.displayName)
                    .font(AccBotFonts.captionSmall)
                    .lineLimit(1)
            }
            .foregroundColor(isSelected ? colors.background : colors.onSurface)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(isSelected ? colors.primary : colors.surface)
            .cornerRadius(CornerRadius.sm)
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.sm)
                    .stroke(isSelected ? colors.primary : colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func strategyIcon(_ strategy: DcaStrategy) -> String {
        switch strategy {
        case .classic: return "arrow.right"
        case .athBased: return "chart.line.uptrend.xyaxis"
        case .fearAndGreed: return "face.dashed"
        }
    }

    // MARK: - Withdrawal Section

    private var withdrawalSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Toggle(isOn: $withdrawalEnabled) {
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(String(localized: "Auto-Withdrawal"))
                        .font(AccBotFonts.headline)
                        .foregroundColor(colors.onSurface)
                    Text(String(localized: "Automatically withdraw to your wallet after purchase"))
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.onSurfaceVariant)
                }
            }
            .tint(colors.primary)

            if withdrawalEnabled {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Wallet Address"))
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.onSurfaceVariant)

                    HStack(spacing: Spacing.sm) {
                        TextField(
                            String(localized: "Enter wallet address"),
                            text: $withdrawalAddress
                        )
                        .font(AccBotFonts.mono)
                        .foregroundColor(colors.onSurface)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(Spacing.md)
                        .background(colors.surfaceVariant.opacity(0.3))
                        .cornerRadius(CornerRadius.sm)

                        Button {
                            showQrScanner = true
                        } label: {
                            Image(systemName: "qrcode.viewfinder")
                                .font(AccBotFonts.titleSmall)
                                .foregroundColor(colors.primary)
                                .padding(Spacing.md)
                                .background(colors.surface)
                                .cornerRadius(CornerRadius.sm)
                        }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Save Button

    private var saveButton: some View {
        Button {
            Task { await savePlan() }
        } label: {
            HStack(spacing: Spacing.sm) {
                if isSubmitting {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: colors.background))
                        .scaleEffect(0.8)
                }
                Text(isSubmitting
                     ? String(localized: "Saving...")
                     : String(localized: "Save Changes"))
                    .font(AccBotFonts.headline)
            }
            .foregroundColor(colors.background)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(isValid ? colors.primary : colors.primary.opacity(0.4))
            .cornerRadius(CornerRadius.md)
        }
        .disabled(!isValid || isSubmitting)
        .padding(.bottom, Spacing.xxl)
    }

    // MARK: - Error Banner

    private func errorBanner(_ message: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(colors.error)
            Text(message)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.error)
        }
        .padding(Spacing.md)
        .background(colors.error.opacity(0.1))
        .cornerRadius(CornerRadius.sm)
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AccBotFonts.headline)
            .foregroundColor(colors.onSurface)
    }

    // MARK: - Data Operations

    private func loadPlan() {
        isLoading = true
        do {
            guard let loaded = try dependencies.activeDatabase.planDao.getById(planId) else {
                isLoading = false
                return
            }
            plan = loaded
            selectedCrypto = loaded.crypto
            selectedFiat = loaded.fiat
            amount = NSDecimalNumber(decimal: loaded.amount).stringValue
            selectedFrequency = loaded.frequency
            cronExpression = loaded.cronExpression ?? ""
            selectedStrategy = loaded.strategy
            withdrawalEnabled = loaded.withdrawalEnabled
            withdrawalAddress = loaded.withdrawalAddress ?? ""
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
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
                createdAt: existingPlan.createdAt,
                lastExecutedAt: existingPlan.lastExecutedAt,
                nextExecutionAt: nextExecution
            )

            try dependencies.activeDatabase.planDao.update(updatedPlan)
            isSubmitting = false
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
    .preferredColorScheme(.dark)
}
