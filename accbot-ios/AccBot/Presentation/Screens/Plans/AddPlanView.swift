import SwiftUI

struct AddPlanView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: AddPlanViewModel

    @State private var showStrategyInfo = false
    @State private var showQrScanner = false

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    private let exchangeColumns = Array(
        repeating: GridItem(.flexible(), spacing: Spacing.md),
        count: 3
    )

    init() {
        _viewModel = StateObject(wrappedValue: AddPlanViewModel(
            dependencies: AppDependencies()
        ))
    }

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

                    // Monthly cost estimate
                    if let estimate = viewModel.monthlyCostEstimate {
                        monthlyCostCard(estimate)
                    }

                    // Error message
                    if let error = viewModel.errorMessage {
                        errorBanner(error)
                    }

                    // Create button
                    createButton
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Create DCA Plan"))
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            viewModel.loadConfiguredExchanges()
        }
        .sheet(isPresented: $showStrategyInfo) {
            StrategyInfoSheet()
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerSheet(
                title: String(localized: "Scan Wallet QR"),
                onScanned: { code in
                    // Strip bitcoin: prefix if present
                    let address = code.hasPrefix("bitcoin:")
                        ? String(code.dropFirst("bitcoin:".count)).components(separatedBy: "?").first ?? code
                        : code
                    viewModel.withdrawalAddress = address
                }
            )
        }
    }

    // MARK: - Exchange Selection

    private var exchangeSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Exchange"))

            if viewModel.availableExchanges.isEmpty {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(colors.warning)
                    Text(String(localized: "No exchanges configured. Add one in Settings."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(colors.warning)
                }
                .padding(Spacing.md)
                .background(colors.warning.opacity(0.1))
                .cornerRadius(CornerRadius.sm)
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
                    .foregroundColor(colors.onSurface)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.xs)
            .background(isSelected ? colors.primary.opacity(0.15) : colors.surface)
            .cornerRadius(CornerRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(isSelected ? colors.primary : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Crypto Selection

    private var cryptoSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Cryptocurrency"))

            SelectableChipGroup(
                items: viewModel.availableCryptos,
                selection: viewModel.selectedCrypto,
                label: { $0 },
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
                    .foregroundColor(colors.onSurface)
                    .keyboardType(.decimalPad)
                    .padding(Spacing.md)
                    .background(colors.surface)
                    .cornerRadius(CornerRadius.sm)

                Text(viewModel.selectedFiat)
                    .font(AccBotFonts.headline)
                    .foregroundColor(colors.primary)
            }

            // Preset buttons
            HStack(spacing: Spacing.sm) {
                ForEach(viewModel.amountPresets, id: \.self) { preset in
                    Button {
                        viewModel.amount = "\(preset)"
                    } label: {
                        Text("\(preset)")
                            .font(AccBotFonts.label)
                            .foregroundColor(
                                viewModel.amount == "\(preset)"
                                    ? colors.background
                                    : colors.primary
                            )
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.sm)
                            .background(
                                viewModel.amount == "\(preset)"
                                    ? colors.primary
                                    : colors.primary.opacity(0.15)
                            )
                            .cornerRadius(CornerRadius.sm)
                    }
                }
            }

            // Min order size
            if let minDisplay = viewModel.minOrderSizeDisplay {
                Text(String(localized: "Minimum order: \(minDisplay)"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
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

    // MARK: - Withdrawal

    private var withdrawalSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Toggle(isOn: $viewModel.withdrawalEnabled) {
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

            if viewModel.withdrawalEnabled {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Wallet Address"))
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.onSurfaceVariant)

                    HStack(spacing: Spacing.sm) {
                        TextField(
                            String(localized: "Enter wallet address"),
                            text: $viewModel.withdrawalAddress
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

    // MARK: - Monthly Cost Estimate

    private func monthlyCostCard(_ estimate: Decimal) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Estimated Monthly Cost"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                let formatted = NSDecimalNumber(decimal: estimate)
                Text("~\(formatted.stringValue) \(viewModel.selectedFiat)")
                    .font(AccBotFonts.titleSmall)
                    .foregroundColor(colors.primary)
            }

            Spacer()

            Image(systemName: "calendar")
                .font(AccBotFonts.titleMedium)
                .foregroundColor(colors.primary.opacity(0.5))
        }
        .padding(Spacing.lg)
        .background(colors.primary.opacity(0.1))
        .cornerRadius(CornerRadius.md)
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

    // MARK: - Create Button

    private var createButton: some View {
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
                        .progressViewStyle(CircularProgressViewStyle(tint: colors.background))
                        .scaleEffect(0.8)
                }
                Text(viewModel.isSubmitting
                     ? String(localized: "Creating...")
                     : String(localized: "Create Plan"))
                    .font(AccBotFonts.headline)
            }
            .foregroundColor(colors.background)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(viewModel.isValid ? colors.primary : colors.primary.opacity(0.4))
            .cornerRadius(CornerRadius.md)
        }
        .disabled(!viewModel.isValid || viewModel.isSubmitting)
        .padding(.bottom, Spacing.xxl)
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AccBotFonts.headline)
            .foregroundColor(colors.onSurface)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        AddPlanView()
    }
    .preferredColorScheme(.dark)
}
