import SwiftUI

struct AddPlanView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = AddPlanViewModel()

    @State private var showStrategyInfo = false
    @State private var showQrScanner = false
    @State private var showDiscardAlert = false

    @Environment(\.accBotColors) private var colors

    private let exchangeColumns = Array(
        repeating: GridItem(.flexible(), spacing: Spacing.md),
        count: 3
    )

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                // Exchange selection
                exchangeSection

                if viewModel.selectedExchange != nil {
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

                    // Goal tracking (optional target amount)
                    targetAmountSection

                    // Monthly cost estimate
                    if let estimate = viewModel.monthlyCostEstimate {
                        monthlyCostCard(estimate)
                    }

                    // Error message
                    if let error = viewModel.errorMessage {
                        ErrorBanner(message: error)
                    }

                    // Create button
                    createButton
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
            .maxFormWidth()
        }
        .scrollDismissesKeyboard(.interactively)
        .background(colors.background)
        .navigationTitle(String(localized: "Create DCA Plan"))
        .navigationBarTitleDisplayMode(.large)
        .navigationBarBackButtonHidden(viewModel.hasChanges)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(String(localized: "Done")) {
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
            }
            if viewModel.hasChanges {
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
        .onAppear {
            viewModel.setup(dependencies)
        }
        .onChange(of: viewModel.validationHint) { newHint in
            if let hint = newHint {
                UIAccessibility.post(notification: .announcement, argument: hint)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            viewModel.loadConfiguredExchanges()
        }
        .sheet(isPresented: $showStrategyInfo) {
            StrategyInfoSheet()
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerSheet(
                title: String(localized: "Scan Wallet QR"),
                onScanned: { code in
                    viewModel.withdrawalAddress = cleanQrValue(code)
                }
            )
        }
    }

    // MARK: - Exchange Selection

    private var exchangeSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Exchange"))

            if viewModel.availableExchanges.isEmpty {
                VStack(spacing: Spacing.md) {
                    HStack(spacing: Spacing.sm) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(colors.warning)
                        Text(String(localized: "No exchanges configured. Add one in Settings."))
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.warning)
                    }

                    Button {
                        router.navigate(to: .exchangeManagement)
                    } label: {
                        HStack(spacing: Spacing.sm) {
                            Image(systemName: "building.columns")
                            Text(String(localized: "Manage Exchanges"))
                        }
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(colors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }
                }
                .padding(Spacing.md)
                .background(colors.warning.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            } else {
                LazyVGrid(columns: exchangeColumns, spacing: Spacing.md) {
                    ForEach(viewModel.availableExchanges) { exchange in
                        exchangeGridItem(exchange)
                    }
                }
            }
        }
    }

    private func exchangeGridItem(_ exchange: Exchange) -> some View {
        let isSelected = viewModel.selectedExchange == exchange
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                viewModel.selectExchange(exchange)
            }
        } label: {
            VStack(spacing: Spacing.sm) {
                Image(exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 40, height: 40)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

                Text(exchange.displayName)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.xs)
            .background(isSelected ? colors.primary.opacity(0.15) : colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(isSelected ? colors.primary : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(exchange.displayName)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
        .accessibilityValue(isSelected ? String(localized: "Selected") : String(localized: "Not selected"))
    }

    // MARK: - Crypto Selection

    private var cryptoSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Cryptocurrency"))

            SelectableChipGroup(
                items: viewModel.availableCryptos,
                selection: viewModel.selectedCrypto,
                label: { $0 },
                icon: { CryptoIcon(symbol: $0, size: 18) },
                onSelect: { viewModel.selectedCrypto = $0 }
            )
        }
    }

    // MARK: - Fiat Selection

    private var fiatSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Fiat Currency"))

            SelectableChipGroup(
                items: viewModel.availableFiats,
                selection: viewModel.selectedFiat,
                label: { $0 },
                icon: { FiatIcon(symbol: $0, size: 18) },
                onSelect: { viewModel.selectedFiat = $0 }
            )
        }
    }

    // MARK: - Amount Input

    private var amountSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Amount per Purchase"))

            HStack(spacing: Spacing.sm) {
                TextField("0", text: $viewModel.amount)
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
                    .accessibilityLabel(String(localized: "Amount per purchase"))
                    .onChange(of: viewModel.amount) { newValue in
                        let filtered = newValue.filter { $0.isNumber || $0 == "." || $0 == "," }
                        let normalized = filtered.replacingOccurrences(of: ",", with: ".")
                        // Allow only one decimal separator
                        let parts = normalized.split(separator: ".", maxSplits: 2)
                        let sanitized = parts.count > 1
                            ? "\(parts[0]).\(parts.dropFirst().joined())"
                            : normalized
                        if sanitized != newValue {
                            viewModel.amount = sanitized
                        }
                    }

                Text(viewModel.selectedFiat)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.primary)
            }

            // Preset buttons
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(viewModel.amountPresets, id: \.self) { preset in
                        let isPresetSelected = viewModel.amount == "\(preset)"
                        Button {
                            viewModel.amount = "\(preset)"
                        } label: {
                            Text("\(preset)")
                                .font(AccBotFonts.label)
                                .foregroundStyle(
                                    isPresetSelected
                                        ? colors.onPrimary
                                        : colors.primary
                                )
                                .padding(.horizontal, Spacing.md)
                                .padding(.vertical, Spacing.sm)
                                .frame(minWidth: 44, minHeight: 44)
                                .background(
                                    isPresetSelected
                                        ? colors.primary
                                        : colors.primary.opacity(0.15)
                                )
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        }
                        .accessibilityAddTraits(isPresetSelected ? .isSelected : [])
                    }
                }
            }

            // Min order size
            if let minDisplay = viewModel.minOrderSizeDisplay {
                let isBelowMin: Bool = {
                    guard let amountValue = Decimal(string: viewModel.amount),
                          let exchange = viewModel.selectedExchange,
                          let minSize = exchange.minOrderSize[viewModel.selectedFiat] else { return false }
                    return amountValue > 0 && amountValue < minSize
                }()
                Text(String(localized: "Minimum order: \(minDisplay)"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(isBelowMin ? colors.error : colors.onSurfaceVariant)
            }
        }
    }

    // MARK: - Frequency

    private var frequencySection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            ScheduleBuilder(
                selectedFrequency: $viewModel.selectedFrequency,
                cronExpression: $viewModel.cronExpression
            )
        }
    }

    // MARK: - Strategy

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
        let isSelected = viewModel.selectedStrategy.dbString == strategy.dbString
        return Button {
            viewModel.selectedStrategy = strategy
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

    // MARK: - Withdrawal

    private var withdrawalSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Toggle(isOn: $viewModel.withdrawalEnabled) {
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

            if viewModel.withdrawalEnabled {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Wallet Address"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)

                    HStack(spacing: Spacing.sm) {
                        TextField(
                            String(localized: "Enter wallet address"),
                            text: $viewModel.withdrawalAddress
                        )
                        .font(AccBotFonts.mono)
                        .foregroundStyle(colors.onSurface)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(Spacing.md)
                        .background(colors.surfaceVariant.opacity(0.5))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        .overlay(
                            RoundedRectangle(cornerRadius: CornerRadius.sm)
                                .strokeBorder(
                                    walletAddressBorderColor,
                                    lineWidth: 1
                                )
                        )
                        .accessibilityLabel(String(localized: "Wallet address"))

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
                // Wallet address validation hint
                if !viewModel.withdrawalAddress.isEmpty && viewModel.withdrawalAddress.trimmingCharacters(in: .whitespaces).count < 26 {
                    Text(String(localized: "Address looks too short"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.warning)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
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
                    text: $viewModel.targetAmount
                )
                .font(AccBotFonts.titleMedium)
                .foregroundStyle(colors.onSurface)
                .keyboardType(.decimalPad)
                .padding(Spacing.md)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                .accessibilityLabel(String(localized: "Target crypto amount"))
                .onChange(of: viewModel.targetAmount) { newValue in
                    let filtered = newValue.filter { $0.isNumber || $0 == "." || $0 == "," }
                    let normalized = filtered.replacingOccurrences(of: ",", with: ".")
                    let parts = normalized.split(separator: ".", maxSplits: 2)
                    let sanitized = parts.count > 1
                        ? "\(parts[0]).\(parts.dropFirst().joined())"
                        : normalized
                    if sanitized != newValue {
                        viewModel.targetAmount = sanitized
                    }
                }

                Text(viewModel.selectedCrypto)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.primary)
            }

            Text(String(localized: "Shows a progress bar on the dashboard to visualize your goal"))
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
        }
    }

    // MARK: - Monthly Cost Estimate

    private func monthlyCostCard(_ estimate: Decimal) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Estimated Monthly Cost"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)

                Text("~\(AccBotFormatters.formatFiat(estimate, symbol: viewModel.selectedFiat))")
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.primary)
            }

            Spacer()

            Image(systemName: "calendar")
                .font(AccBotFonts.titleMedium)
                .foregroundStyle(colors.primary)
                .accessibilityHidden(true)
        }
        .padding(Spacing.lg)
        .background(colors.primary.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Create Button

    private var createButton: some View {
        VStack(spacing: Spacing.sm) {
            if let hint = viewModel.validationHint, !viewModel.isValid {
                Text(hint)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                Task {
                    let success = await viewModel.createPlan()
                    if success {
                        router.pop()
                    }
                }
            } label: {
                HStack(spacing: Spacing.sm) {
                    if viewModel.isSubmitting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                            .scaleEffect(0.8)
                    }
                    Text(viewModel.isSubmitting
                         ? String(localized: "Creating...")
                         : String(localized: "Create Plan"))
                        .font(AccBotFonts.headline)
                }
                .foregroundStyle(viewModel.isValid ? colors.onPrimary : colors.disabledForeground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
                .background(viewModel.isValid ? colors.primary : colors.disabledBackground)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
            .disabled(!viewModel.isValid || viewModel.isSubmitting)
        }
        .padding(.bottom, Spacing.xxl)
    }

    // MARK: - Helpers

    private var walletAddressBorderColor: Color {
        let addr = viewModel.withdrawalAddress.trimmingCharacters(in: .whitespaces)
        if addr.isEmpty { return Color.clear }
        if addr.count >= 26 { return colors.success }
        return colors.warning
    }

    private var amountIsBelowMin: Bool {
        guard let amountValue = Decimal(string: viewModel.amount),
              let exchange = viewModel.selectedExchange,
              let minSize = exchange.minOrderSize[viewModel.selectedFiat] else { return false }
        return amountValue > 0 && amountValue < minSize
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AccBotFonts.headline)
            .foregroundStyle(colors.onSurface)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        AddPlanView()
    }
}
