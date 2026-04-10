import SwiftUI

struct AddPlanView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = AddPlanViewModel()

    @State private var showStrategyInfo = false
    @State private var showQrScanner = false
    @State private var showDiscardAlert = false
    @State private var showExperimentalDisclaimer = false
    @State private var experimentalPendingExchange: Exchange?

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
                    // Connection picker (only if multiple connections)
                    if viewModel.availableConnections.count > 1 {
                        connectionSection
                    }

                    // Crypto selection
                    cryptoSection

                    // Fiat selection
                    fiatSection

                    // Plan form content (amount, frequency, strategy, withdrawal, target)
                    PlanFormContent(
                        exchange: viewModel.selectedExchange,
                        amount: $viewModel.amount,
                        selectedFiat: viewModel.selectedFiat,
                        selectedCrypto: viewModel.selectedCrypto,
                        selectedFrequency: $viewModel.selectedFrequency,
                        cronExpression: $viewModel.cronExpression,
                        selectedStrategy: $viewModel.selectedStrategy,
                        withdrawalEnabled: $viewModel.withdrawalEnabled,
                        withdrawalAddress: $viewModel.withdrawalAddress,
                        targetAmount: $viewModel.targetAmount,
                        showStrategyInfo: $showStrategyInfo,
                        showQrScanner: $showQrScanner
                    )

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

                // Experimental exchanges toggle
                if viewModel.hasExperimentalExchanges {
                    experimentalToggle
                }

                // Request Exchange tile
                requestExchangeTile
            }
        }
    }

    // MARK: - Experimental Toggle

    private var experimentalToggle: some View {
        Button {
            if dependencies.userPreferences.showExperimentalExchanges {
                dependencies.userPreferences.showExperimentalExchanges = false
                viewModel.loadConfiguredExchanges()
            } else {
                showExperimentalDisclaimer = true
            }
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: "flask")
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.warning)

                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(String(localized: "Experimental Exchanges"))
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.onSurface)
                    Text(String(localized: "Show additional exchanges that haven't been fully tested"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                Spacer()

                Image(systemName: dependencies.userPreferences.showExperimentalExchanges ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(dependencies.userPreferences.showExperimentalExchanges ? colors.primary : colors.onSurfaceVariant)
            }
            .padding(Spacing.md)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .buttonStyle(.plain)
        .alert(String(localized: "Enable Experimental Exchanges?"), isPresented: $showExperimentalDisclaimer) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Enable")) {
                dependencies.userPreferences.showExperimentalExchanges = true
                viewModel.loadConfiguredExchanges()
            }
        } message: {
            Text(String(localized: "These exchanges haven't been fully tested with AccBot. Use at your own risk. Please report any issues on GitHub."))
        }
    }

    // MARK: - Request Exchange

    private var requestExchangeTile: some View {
        Button {
            if let url = URL(string: "https://github.com/Crynners/AccBot/issues") {
                UIApplication.shared.open(url)
            }
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: "plus.circle")
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurfaceVariant)
                Text(String(localized: "Request Exchange"))
                    .font(AccBotFonts.label)
                    .foregroundStyle(colors.onSurfaceVariant)
                Spacer()
                Image(systemName: "arrow.up.right")
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .padding(Spacing.md)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func exchangeGridItem(_ exchange: Exchange) -> some View {
        let isSelected = viewModel.selectedExchange == exchange
        return Button {
            if exchange.isStable || dependencies.userPreferences.showExperimentalExchanges {
                withAnimation(.easeInOut(duration: 0.2)) {
                    viewModel.selectExchange(exchange)
                }
            } else {
                experimentalPendingExchange = exchange
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

                if !exchange.isStable {
                    Text(String(localized: "EXPERIMENTAL"))
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(colors.warning)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(colors.warning.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 2))
                }
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

    // MARK: - Connection Selection

    private var connectionSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Connection"))

            FlowLayout(spacing: Spacing.sm) {
                ForEach(viewModel.availableConnections) { connection in
                    let isSelected = viewModel.selectedConnection?.id == connection.id
                    Button {
                        viewModel.selectedConnection = connection
                    } label: {
                        Text(connection.displayName)
                            .font(AccBotFonts.label)
                            .foregroundStyle(isSelected ? colors.onPrimary : colors.onSurface)
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.sm)
                            .background(isSelected ? colors.primary : colors.surface)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
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
                wrapping: true,
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
                wrapping: true,
                onSelect: { viewModel.selectFiat($0) }
            )
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
