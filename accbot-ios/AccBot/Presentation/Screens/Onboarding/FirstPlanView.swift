import SwiftUI

/// First DCA plan creation during onboarding.
struct FirstPlanView: View {
    let onNext: () -> Void

    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel = FirstPlanViewModel()

    var body: some View {
        ZStack {
            Color.backgroundDark
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: Spacing.xxl) {
                    // Header
                    VStack(spacing: Spacing.sm) {
                        Image(systemName: "calendar.badge.plus")
                            .font(.system(size: 48))
                            .foregroundColor(.accentTeal)

                        Text("Create Your First DCA Plan")
                            .font(AccBotFonts.titleLarge)
                            .foregroundColor(.white)

                        Text("Set up automatic recurring purchases.")
                            .font(AccBotFonts.body)
                            .foregroundColor(.white.opacity(0.7))
                    }
                    .padding(.top, Spacing.xxl)

                    // Crypto selection
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text("Cryptocurrency")
                            .font(AccBotFonts.headline)
                            .foregroundColor(.white)

                        ChipGroup(
                            items: viewModel.availableCryptos,
                            selected: viewModel.selectedCrypto,
                            onSelect: { viewModel.selectedCrypto = $0 }
                        )
                    }

                    // Fiat selection
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text("Fiat Currency")
                            .font(AccBotFonts.headline)
                            .foregroundColor(.white)

                        ChipGroup(
                            items: viewModel.availableFiats,
                            selected: viewModel.selectedFiat,
                            onSelect: { viewModel.selectedFiat = $0 }
                        )
                    }

                    // Amount input
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text("Amount per Purchase")
                            .font(AccBotFonts.headline)
                            .foregroundColor(.white)

                        HStack(spacing: Spacing.sm) {
                            TextField("0", text: $viewModel.amount)
                                .font(AccBotFonts.titleMedium)
                                .foregroundColor(.white)
                                .keyboardType(.decimalPad)
                                .padding(Spacing.md)
                                .background(Color.surfaceDark)
                                .cornerRadius(CornerRadius.sm)

                            Text(viewModel.selectedFiat)
                                .font(AccBotFonts.headline)
                                .foregroundColor(.accentTeal)
                        }

                        // Amount presets
                        HStack(spacing: Spacing.sm) {
                            ForEach(viewModel.amountPresets, id: \.self) { preset in
                                Button(action: { viewModel.amount = "\(preset)" }) {
                                    Text("\(preset)")
                                        .font(AccBotFonts.label)
                                        .foregroundColor(
                                            viewModel.amount == "\(preset)" ? .backgroundDark : .accentTeal
                                        )
                                        .padding(.horizontal, Spacing.md)
                                        .padding(.vertical, Spacing.sm)
                                        .background(
                                            viewModel.amount == "\(preset)"
                                                ? Color.accentTeal
                                                : Color.accentTeal.opacity(0.15)
                                        )
                                        .cornerRadius(CornerRadius.sm)
                                }
                            }
                        }
                    }

                    // Frequency picker
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        Text("Frequency")
                            .font(AccBotFonts.headline)
                            .foregroundColor(.white)

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

                    // Action buttons
                    VStack(spacing: Spacing.md) {
                        Button(action: {
                            Task {
                                await viewModel.createPlan(dependencies: dependencies)
                                onNext()
                            }
                        }) {
                            Text("Create Plan")
                                .font(AccBotFonts.headline)
                                .foregroundColor(.backgroundDark)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, Spacing.lg)
                                .background(viewModel.canCreatePlan ? Color.accentTeal : Color.accentTeal.opacity(0.4))
                                .cornerRadius(CornerRadius.md)
                        }
                        .disabled(!viewModel.canCreatePlan)

                        Button(action: onNext) {
                            Text("Skip for Now")
                                .font(AccBotFonts.body)
                                .foregroundColor(.accentTeal)
                        }
                    }
                    .padding(.bottom, Spacing.xxl)
                }
                .padding(.horizontal, Spacing.xxl)
            }
        }
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

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(items, id: \.self) { item in
                    Button(action: { onSelect(item) }) {
                        Text(item)
                            .font(AccBotFonts.label)
                            .foregroundColor(selected == item ? .backgroundDark : .accentTeal)
                            .padding(.horizontal, Spacing.lg)
                            .padding(.vertical, Spacing.sm)
                            .background(
                                selected == item
                                    ? Color.accentTeal
                                    : Color.accentTeal.opacity(0.15)
                            )
                            .cornerRadius(CornerRadius.xl)
                    }
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

    var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    HStack(spacing: Spacing.sm) {
                        Text(frequency.displayName)
                            .font(AccBotFonts.headline)
                            .foregroundColor(.white)

                        if isRecommended {
                            Text("Recommended")
                                .font(AccBotFonts.captionSmall)
                                .foregroundColor(.backgroundDark)
                                .padding(.horizontal, Spacing.sm)
                                .padding(.vertical, Spacing.xxs)
                                .background(Color.accentTeal)
                                .cornerRadius(CornerRadius.sm)
                        }
                    }

                    if let warning = frequency.backgroundWarning {
                        Text(warning)
                            .font(AccBotFonts.caption)
                            .foregroundColor(.warningOrange)
                            .lineLimit(2)
                    }
                }

                Spacer()

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundColor(isSelected ? .accentTeal : .white.opacity(0.3))
            }
            .padding(Spacing.md)
            .background(isSelected ? Color.accentTeal.opacity(0.1) : Color.surfaceDark)
            .cornerRadius(CornerRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(isSelected ? Color.accentTeal : Color.clear, lineWidth: 1)
            )
        }
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

    func createPlan(dependencies: AppDependencies) async {
        guard let amountValue = Decimal(string: amount), amountValue > 0 else { return }

        let isSandbox = dependencies.userPreferences.sandboxMode
        let configuredExchanges = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
        guard let exchange = configuredExchanges.first else { return }

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
        } catch {
            // Plan creation failed silently; user can create plans from dashboard later
        }
    }
}
