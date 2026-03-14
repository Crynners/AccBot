import SwiftUI

/// Shared form sections for DCA plan creation and editing.
/// Renders amount, frequency, strategy, withdrawal, and target amount sections.
/// Uses Group so children inherit the parent container's layout and spacing.
struct PlanFormContent: View {
    let exchange: Exchange?
    @Binding var amount: String
    let selectedFiat: String
    let selectedCrypto: String
    @Binding var selectedFrequency: DcaFrequency
    @Binding var cronExpression: String
    @Binding var selectedStrategy: DcaStrategy
    @Binding var withdrawalEnabled: Bool
    @Binding var withdrawalAddress: String
    @Binding var targetAmount: String
    @Binding var showStrategyInfo: Bool
    @Binding var showQrScanner: Bool

    @Environment(\.accBotColors) private var colors

    // MARK: - Computed Properties

    var amountPresets: [Int] {
        let all = [5, 10, 25, 50, 100]
        guard let exchange = exchange,
              let minSize = exchange.minOrderSize[selectedFiat] else { return all }
        return all.filter { Decimal($0) >= minSize }
    }

    var minOrderSizeDisplay: String? {
        guard let exchange = exchange,
              let minSize = exchange.minOrderSize[selectedFiat] else { return nil }
        return "\(minSize) \(selectedFiat)"
    }

    var amountIsBelowMin: Bool {
        guard let amountValue = Decimal(string: amount),
              let exchange = exchange,
              let minSize = exchange.minOrderSize[selectedFiat] else { return false }
        return amountValue > 0 && amountValue < minSize
    }

    // MARK: - Body

    var body: some View {
        Group {
            amountSection
            frequencySection
            strategySection
            withdrawalSection
            targetAmountSection
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
                    .accessibilityLabel(String(localized: "Amount per purchase"))
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

            // Preset buttons
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(amountPresets, id: \.self) { preset in
                        let isPresetSelected = amount == "\(preset)"
                        Button {
                            amount = "\(preset)"
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

            // Binance lot size info
            if exchange == .binance,
               let stepSize = Exchange.binanceLotStepSize[selectedCrypto] {
                HStack(alignment: .top, spacing: Spacing.xs) {
                    Image(systemName: "info.circle")
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Text(String(localized: "Binance rounds \(selectedCrypto) orders to a step size of \(stepSize)"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }

            // Min order size
            if let minDisplay = minOrderSizeDisplay {
                Text(String(localized: "Minimum order: \(minDisplay)"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(amountIsBelowMin ? colors.error : colors.onSurfaceVariant)
            }
        }
    }

    // MARK: - Frequency Section

    private var frequencySection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            ScheduleBuilder(
                selectedFrequency: $selectedFrequency,
                cronExpression: $cronExpression
            )
        }
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
                        .background(colors.surfaceVariant.opacity(0.5))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        .overlay(
                            RoundedRectangle(cornerRadius: CornerRadius.sm)
                                .strokeBorder(walletAddressBorderColor, lineWidth: 1)
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
                if !withdrawalAddress.isEmpty
                    && withdrawalAddress.trimmingCharacters(in: .whitespaces).count < 26 {
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

    // MARK: - Target Amount Section

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

    // MARK: - Helpers

    func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AccBotFonts.headline)
            .foregroundStyle(colors.onSurface)
    }

    private var walletAddressBorderColor: Color {
        let addr = withdrawalAddress.trimmingCharacters(in: .whitespaces)
        if addr.isEmpty { return Color.clear }
        if addr.count >= 26 { return colors.success }
        return colors.warning
    }

    // MARK: - Shared Validation

    /// Returns a validation hint string if the form is invalid, or nil if valid.
    static func validate(
        amount: String,
        selectedFiat: String,
        exchange: Exchange?,
        selectedFrequency: DcaFrequency,
        cronExpression: String,
        withdrawalEnabled: Bool,
        withdrawalAddress: String
    ) -> String? {
        if amount.isEmpty {
            return String(localized: "Enter a purchase amount")
        }
        guard let amountValue = Decimal(string: amount), amountValue > 0 else {
            return String(localized: "Enter a valid amount greater than 0")
        }
        if let exchange = exchange,
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
}
