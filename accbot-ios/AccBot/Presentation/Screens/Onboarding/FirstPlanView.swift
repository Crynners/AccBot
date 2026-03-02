import SwiftUI

/// First DCA plan creation during onboarding.
struct FirstPlanView: View {
    let onNext: () -> Void

    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors
    @StateObject private var viewModel = FirstPlanViewModel()
    @State private var showSkipConfirmation = false

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: Spacing.xxl) {
                    // Header
                    VStack(spacing: Spacing.sm) {
                        Image(systemName: "calendar.badge.plus")
                            .font(AccBotFonts.displayLarge)
                            .foregroundStyle(colors.primary)
                            .accessibilityHidden(true)

                        Text(String(localized: "Create Your First DCA Plan"))
                            .font(AccBotFonts.titleLarge)
                            .foregroundStyle(colors.onSurface)

                        Text(String(localized: "Set up automatic recurring purchases."))
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                    .padding(.top, Spacing.xxl)

                    // Crypto selection
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text(String(localized: "Cryptocurrency"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurface)

                        ChipGroup(
                            items: viewModel.availableCryptos,
                            selected: viewModel.selectedCrypto,
                            onSelect: { viewModel.selectedCrypto = $0 }
                        )
                    }

                    // Fiat selection
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text(String(localized: "Fiat Currency"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurface)

                        ChipGroup(
                            items: viewModel.availableFiats,
                            selected: viewModel.selectedFiat,
                            onSelect: { viewModel.selectedFiat = $0 }
                        )
                    }

                    // Amount input
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text(String(localized: "Amount per Purchase"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurface)

                        HStack(spacing: Spacing.sm) {
                            TextField("0", text: $viewModel.amount)
                                .font(AccBotFonts.titleMedium)
                                .foregroundStyle(colors.onSurface)
                                .keyboardType(.decimalPad)
                                .padding(Spacing.md)
                                .background(colors.surface)
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

                            Text(viewModel.selectedFiat)
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.primary)
                        }

                        // Amount presets
                        HStack(spacing: Spacing.sm) {
                            ForEach(viewModel.amountPresets, id: \.self) { preset in
                                Button(action: { viewModel.amount = "\(preset)" }) {
                                    Text("\(preset)")
                                        .font(AccBotFonts.label)
                                        .foregroundStyle(
                                            viewModel.amount == "\(preset)" ? colors.onPrimary : colors.primary
                                        )
                                        .padding(.horizontal, Spacing.md)
                                        .padding(.vertical, Spacing.sm)
                                        .frame(minHeight: 44)
                                        .background(
                                            viewModel.amount == "\(preset)"
                                                ? colors.primary
                                                : colors.primary.opacity(0.15)
                                        )
                                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                                }
                                .accessibilityAddTraits(viewModel.amount == "\(preset)" ? .isSelected : [])
                            }
                        }
                    }

                    // Frequency picker
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text(String(localized: "Frequency"))
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurface)

                        ForEach(viewModel.frequencyOptions, id: \.self) { frequency in
                            FrequencyRow(
                                frequency: frequency,
                                isSelected: viewModel.selectedFrequency == frequency,
                                isRecommended: frequency == .daily,
                                onTap: { viewModel.selectedFrequency = frequency }
                            )
                        }
                    }

                    Spacer(minLength: Spacing.xxl)

                    // Error message
                    if let error = viewModel.errorMessage {
                        HStack(spacing: Spacing.sm) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(colors.error)
                                .accessibilityHidden(true)
                            Text(error)
                                .font(AccBotFonts.bodySmall)
                                .foregroundStyle(colors.error)
                        }
                        .padding(Spacing.md)
                        .background(colors.error.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }

                    // Action buttons
                    VStack(spacing: Spacing.md) {
                        Button(action: {
                            Task {
                                let success = await viewModel.createPlan(dependencies: dependencies)
                                if success {
                                    onNext()
                                }
                            }
                        }) {
                            Text(String(localized: "Create Plan"))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(viewModel.canCreatePlan ? colors.onPrimary : colors.disabledForeground)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, Spacing.lg)
                                .background(viewModel.canCreatePlan ? colors.primary : colors.disabledBackground)
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                        }
                        .disabled(!viewModel.canCreatePlan)

                        Button { showSkipConfirmation = true } label: {
                            Text(String(localized: "Skip for Now"))
                                .font(AccBotFonts.body)
                                .foregroundStyle(colors.primary)
                                .padding(.vertical, Spacing.sm)
                                .frame(minHeight: 44)
                        }
                        .alert(String(localized: "Skip Plan Creation?"), isPresented: $showSkipConfirmation) {
                            Button(String(localized: "Cancel"), role: .cancel) {}
                            Button(String(localized: "Skip")) { onNext() }
                        } message: {
                            Text(String(localized: "You can create DCA plans later from the Dashboard."))
                        }
                    }
                    .padding(.bottom, Spacing.xxl)
                }
                .padding(.horizontal, Spacing.xxl)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.configure(with: dependencies)
        }
    }
}

// MARK: - Chip Group

private struct ChipGroup: View {
    let items: [String]
    let selected: String
    let onSelect: (String) -> Void
    @Environment(\.accBotColors) private var colors

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(items, id: \.self) { item in
                    Button(action: { onSelect(item) }) {
                        Text(item)
                            .font(AccBotFonts.label)
                            .foregroundStyle(selected == item ? colors.onPrimary : colors.primary)
                            .padding(.horizontal, Spacing.lg)
                            .padding(.vertical, Spacing.sm)
                            .frame(minHeight: 44)
                            .background(
                                selected == item
                                    ? colors.primary
                                    : colors.primary.opacity(0.15)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.xl))
                    }
                    .accessibilityAddTraits(selected == item ? .isSelected : [])
                    .accessibilityValue(selected == item ? String(localized: "Selected") : String(localized: "Not selected"))
                }
            }
        }
    }
}

// MARK: - Frequency Row

private struct FrequencyRow: View {
    let frequency: DcaFrequency
    let isSelected: Bool
    let isRecommended: Bool
    let onTap: () -> Void
    @Environment(\.accBotColors) private var colors

    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    HStack(spacing: Spacing.sm) {
                        Text(frequency.displayName)
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onSurface)

                        if isRecommended {
                            Text(String(localized: "Recommended"))
                                .font(AccBotFonts.captionSmall)
                                .foregroundStyle(colors.onPrimary)
                                .padding(.horizontal, Spacing.sm)
                                .padding(.vertical, Spacing.xxs)
                                .background(colors.primary)
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        }
                    }

                    if let warning = frequency.backgroundWarning {
                        Text(warning)
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.warning)
                            .lineLimit(2)
                    }
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(AccBotFonts.titleMedium)
                    .foregroundStyle(isSelected ? colors.primary : colors.onSurfaceVariant)
            }
            .padding(Spacing.md)
            .background(isSelected ? colors.primary.opacity(0.1) : colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(isSelected ? colors.primary : Color.clear, lineWidth: 1)
            )
        }
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityValue(isSelected ? String(localized: "Selected") : String(localized: "Not selected"))
    }
}

// MARK: - First Plan ViewModel

@MainActor
private class FirstPlanViewModel: ObservableObject {
    @Published var selectedCrypto = "BTC"
    @Published var selectedFiat = "EUR"
    @Published var amount = ""
    @Published var selectedFrequency: DcaFrequency = .daily
    @Published var availableCryptos: [String] = ["BTC", "ETH", "SOL", "ADA", "DOT"]
    @Published var availableFiats: [String] = ["EUR", "USD", "USDT", "CZK", "GBP"]

    let amountPresets = [25, 50, 100, 250, 500]
    let frequencyOptions: [DcaFrequency] = [.daily, .weekly]

    var canCreatePlan: Bool {
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return false }
        return !selectedCrypto.isEmpty && !selectedFiat.isEmpty
    }

    func configure(with dependencies: AppDependencies) {
        let isSandbox = dependencies.userPreferences.sandboxMode
        let configuredExchanges = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)

        if let exchange = configuredExchanges.first {
            availableCryptos = exchange.supportedCryptos
            availableFiats = exchange.supportedFiats
            if !availableCryptos.contains(selectedCrypto) {
                selectedCrypto = availableCryptos.first ?? "BTC"
            }
            if !availableFiats.contains(selectedFiat) {
                selectedFiat = availableFiats.first ?? "EUR"
            }
        }
    }

    @Published var errorMessage: String?

    @discardableResult
    func createPlan(dependencies: AppDependencies) async -> Bool {
        errorMessage = nil
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return false }

        let isSandbox = dependencies.userPreferences.sandboxMode
        let configuredExchanges = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
        guard let exchange = configuredExchanges.first else {
            errorMessage = String(localized: "No exchange configured.")
            return false
        }

        let now = Date()
        let nextExecution = Calendar.current.date(
            byAdding: .minute,
            value: selectedFrequency.intervalMinutes,
            to: now
        )

        let plan = DcaPlan(
            exchange: exchange,
            crypto: selectedCrypto,
            fiat: selectedFiat,
            amount: amountValue,
            frequency: selectedFrequency,
            strategy: .classic,
            isEnabled: true,
            createdAt: now,
            nextExecutionAt: nextExecution
        )

        do {
            try dependencies.activeDatabase.planDao.insert(plan)
            return true
        } catch {
            errorMessage = String(localized: "Failed to create plan. You can create plans later from the dashboard.")
            return false
        }
    }
}
