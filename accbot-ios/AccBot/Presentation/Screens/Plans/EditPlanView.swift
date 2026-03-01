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

    private var hasChanges: Bool {
        amount != originalAmount
            || selectedFrequency != originalFrequency
            || cronExpression != originalCronExpression
            || selectedStrategy != originalStrategy
            || withdrawalEnabled != originalWithdrawalEnabled
            || withdrawalAddress != originalWithdrawalAddress
            || targetAmount != originalTargetAmount
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

        if withdrawalEnabled {
            let trimmed = withdrawalAddress.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty || trimmed.count < 26 {
                return false
            }
        }

        return true
    }

    private var validationHint: String? {
        guard plan != nil else { return nil }
        if amount.isEmpty {
            return String(localized: "Enter a purchase amount")
        }
        guard let amountValue = Decimal(string: amount), amountValue > 0 else {
            return String(localized: "Enter a valid amount greater than 0")
        }
        if let exchange = plan?.exchange,
           let minSize = exchange.minOrderSize[selectedFiat],
           amountValue < minSize {
            return String(localized: "Amount below minimum order size (\("\(minSize)") \(selectedFiat))")
        }
        if selectedFrequency == .custom && !CronUtils.isValid(cron: cronExpression) {
            return String(localized: "Enter a valid cron expression for custom frequency")
        }
        if withdrawalEnabled {
            let trimmed = withdrawalAddress.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty {
                return String(localized: "Enter a withdrawal wallet address")
            }
            if trimmed.count < 26 {
                return String(localized: "Wallet address is too short (minimum 26 characters)")
            }
        }
        return nil
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

                // Amount input
                amountSection

                // Frequency
                frequencySection

                // Strategy
                strategySection

                // Auto-withdrawal
                withdrawalSection

                // Goal tracking (optional target amount)
                targetAmountSection

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
                    .foregroundStyle(colors.onSurface)
                    .keyboardType(.decimalPad)
                    .padding(Spacing.md)
                    .background(colors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    .overlay(
                        RoundedRectangle(cornerRadius: CornerRadius.sm)
                            .strokeBorder(amountIsBelowMin ? colors.error : Color.clear, lineWidth: 1)
                    )
                    .onChange(of: amount) { newValue in
                        let filtered = newValue.filter { $0.isNumber || $0 == "." || $0 == "," }
                        let normalized = filtered.replacingOccurrences(of: ",", with: ".")
                        let parts = normalized.split(separator: ".", maxSplits: 2)
                        let sanitized = parts.count > 1
                            ? "\(parts[0]).\(parts.dropFirst().joined())"
                            : normalized
                        if sanitized != newValue {
                            amount = sanitized
                        }
                    }

                Text(selectedFiat)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.primary)
            }

            HStack(spacing: Spacing.sm) {
                ForEach(amountPresets, id: \.self) { preset in
                    Button {
                        amount = "\(preset)"
                    } label: {
                        Text("\(preset)")
                            .font(AccBotFonts.label)
                            .foregroundStyle(
                                amount == "\(preset)" ? colors.onPrimary : colors.primary
                            )
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.sm)
                            .frame(minWidth: 44, minHeight: 44)
                            .background(
                                amount == "\(preset)"
                                    ? colors.primary
                                    : colors.primary.opacity(0.15)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }
                }
            }

            if let minDisplay = minOrderSizeDisplay {
                Text(String(localized: "Minimum order: \(minDisplay)"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(amountIsBelowMin ? colors.error : colors.onSurfaceVariant)
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
                        .foregroundStyle(colors.primary)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(String(localized: "Strategy information"))
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
            .foregroundStyle(isSelected ? colors.onPrimary : colors.onSurface)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(isSelected ? colors.primary : colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.sm)
                    .stroke(isSelected ? colors.primary : colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(String(localized: "\(strategy.displayName) strategy"))
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
        .accessibilityValue(isSelected ? String(localized: "Selected") : String(localized: "Not selected"))
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
                        .foregroundStyle(colors.onSurface)
                    Text(String(localized: "Automatically withdraw to your wallet after purchase"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
            .tint(colors.primary)

            if withdrawalEnabled {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Wallet Address"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)

                    HStack(spacing: Spacing.sm) {
                        TextField(
                            String(localized: "Enter wallet address"),
                            text: $withdrawalAddress
                        )
                        .font(AccBotFonts.mono)
                        .foregroundStyle(colors.onSurface)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(Spacing.md)
                        .background(colors.surfaceVariant.opacity(0.3))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

                        Button {
                            showQrScanner = true
                        } label: {
                            Image(systemName: "qrcode.viewfinder")
                                .font(AccBotFonts.titleSmall)
                                .foregroundStyle(colors.primary)
                                .padding(Spacing.md)
                                .frame(minWidth: 44, minHeight: 44)
                                .background(colors.surface)
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        }
                        .accessibilityLabel(String(localized: "Scan wallet QR code"))
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Target Amount (Goal Tracking)

    private var targetAmountSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Target Amount (optional)"))

            HStack(spacing: Spacing.sm) {
                TextField(
                    String(localized: "e.g. 0.1"),
                    text: $targetAmount
                )
                .font(AccBotFonts.titleMedium)
                .foregroundStyle(colors.onSurface)
                .keyboardType(.decimalPad)
                .padding(Spacing.md)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                .accessibilityLabel(String(localized: "Target crypto amount"))
                .onChange(of: targetAmount) { newValue in
                    let filtered = newValue.filter { $0.isNumber || $0 == "." || $0 == "," }
                    let normalized = filtered.replacingOccurrences(of: ",", with: ".")
                    let parts = normalized.split(separator: ".", maxSplits: 2)
                    let sanitized = parts.count > 1
                        ? "\(parts[0]).\(parts.dropFirst().joined())"
                        : normalized
                    if sanitized != newValue {
                        targetAmount = sanitized
                    }
                }

                Text(selectedCrypto)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.primary)
            }

            Text(String(localized: "Shows a progress bar on the dashboard to visualize your goal"))
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
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

    private var amountIsBelowMin: Bool {
        guard let amountValue = Decimal(string: amount),
              let exchange = plan?.exchange,
              let minSize = exchange.minOrderSize[selectedFiat] else { return false }
        return amountValue > 0 && amountValue < minSize
    }

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
