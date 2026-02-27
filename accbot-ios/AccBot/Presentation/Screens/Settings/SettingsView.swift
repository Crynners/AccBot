import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = SettingsViewModel()
    @Environment(\.accBotColors) private var colors
    @State private var dangerZoneExpanded = false
    @State private var showWithdrawalSheet = false

    var body: some View {
        Form {
            exchangeAccountsSection
            systemSection
            dcaAlertsSection
            backgroundSection
            securitySection
            backupSection
            aboutSection
            linksSection
            developerSection
            dangerZoneSection
        }
        .scrollContentBackground(.hidden)
        .maxFormWidth()
        .background(colors.background)
        .navigationTitle(String(localized: "Settings"))
        .alert(
            viewModel.activeAlert?.title ?? "",
            isPresented: Binding(
                get: { viewModel.activeAlert != nil },
                set: { if !$0 { viewModel.activeAlert = nil } }
            ),
            presenting: viewModel.activeAlert
        ) { alertType in
            switch alertType {
            case .deleteConfirmation:
                Button(String(localized: "Cancel"), role: .cancel) {}
                Button(String(localized: "Delete"), role: .destructive) {
                    viewModel.executeDelete()
                }
            case .languageRestart, .sandboxRestart, .deleteAllDataComplete, .error:
                Button(String(localized: "OK")) {}
            }
        } message: { alertType in
            switch alertType {
            case .deleteConfirmation:
                Text(viewModel.deleteTarget?.message ?? "")
            case .languageRestart:
                Text(String(localized: "Please restart the app for the language change to take effect."))
            case .sandboxRestart(let isSandbox):
                Text(isSandbox
                     ? String(localized: "Sandbox mode enabled. Using testnet APIs. Please close and reopen the app for changes to take effect.")
                     : String(localized: "Sandbox mode disabled. Using production APIs. Please close and reopen the app for changes to take effect."))
            case .deleteAllDataComplete:
                Text(String(localized: "All data has been deleted."))
            case .error(let msg):
                Text(msg)
            }
        }
        .onAppear {
            viewModel.setup(dependencies)
        }
        .sheet(isPresented: $showWithdrawalSheet) {
            WithdrawalThresholdsSheet(viewModel: viewModel)
        }
    }

    // MARK: - Exchange Accounts

    private var exchangeAccountsSection: some View {
        Section {
            if viewModel.connectedExchanges.isEmpty {
                Text(String(localized: "No exchanges connected"))
                    .foregroundStyle(colors.onSurfaceVariant)
            } else {
                ForEach(viewModel.connectedExchanges, id: \.self) { exchange in
                    HStack {
                        Image(exchange.logoName)
                            .resizable()
                            .frame(width: 24, height: 24)
                        Text(exchange.displayName)
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.success)
                            .accessibilityLabel(String(localized: "Connected"))
                    }
                    .listRowBackground(colors.surface)
                }
            }

            Button {
                router.navigate(to: .exchangeManagement)
            } label: {
                HStack {
                    Label(String(localized: "Manage Exchanges"), systemImage: "building.columns")
                    Spacer()
                    Text(String(localized: "\(viewModel.connectedExchanges.count) connected"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .font(AccBotFonts.captionSmall)
                }
            }
            .listRowBackground(colors.surface)
        } header: {
            Text(String(localized: "Exchange Accounts"))
        }
    }

    // MARK: - System

    private var systemSection: some View {
        Section {
            Picker(String(localized: "Theme"), selection: $dependencies.userPreferences.appTheme) {
                ForEach(AppTheme.allCases, id: \.self) { theme in
                    Text(theme.displayName).tag(theme)
                }
            }
            .listRowBackground(colors.surface)

            Menu {
                Button(String(localized: "System Default")) { viewModel.setLanguage("") }
                Button(String(localized: "English")) { viewModel.setLanguage("en") }
                Button(String(localized: "Czech")) { viewModel.setLanguage("cs") }
            } label: {
                HStack {
                    Text(String(localized: "Language"))
                    Spacer()
                    Text(languageDisplayName)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
            .accessibilityLabel(String(localized: "Select language"))
            .accessibilityValue(languageDisplayName)
            .listRowBackground(colors.surface)
        } header: {
            Text(String(localized: "System"))
        }
    }

    private var languageDisplayName: String {
        switch dependencies.userPreferences.appLanguage {
        case "en": return String(localized: "English")
        case "cs": return String(localized: "Czech")
        default: return String(localized: "System")
        }
    }

    private var buildDateString: String {
        if let dateString = Bundle.main.infoDictionary?["CFBundleBuildDate"] as? String {
            return dateString
        }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter.string(from: Date())
    }

    // MARK: - DCA Alerts

    private var dcaAlertsSection: some View {
        Section {
            HStack {
                Toggle(String(localized: "Notifications"), isOn: $dependencies.userPreferences.notificationsEnabled)
                Button {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Image(systemName: "arrow.up.forward.square")
                        .foregroundStyle(colors.primary)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "Open notification settings"))
            }
            .listRowBackground(colors.surface)

            if dependencies.userPreferences.notificationsEnabled {
                Toggle(isOn: $dependencies.userPreferences.purchaseNotifications) {
                    VStack(alignment: .leading, spacing: Spacing.xxs) {
                        Text(String(localized: "Purchase Alerts"))
                        Text(String(localized: "Get notified when DCA purchases complete"))
                            .font(AccBotFonts.captionSmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .listRowBackground(colors.surface)

                Toggle(isOn: $dependencies.userPreferences.errorNotifications) {
                    VStack(alignment: .leading, spacing: Spacing.xxs) {
                        Text(String(localized: "Error Alerts"))
                        Text(String(localized: "Get notified when DCA purchases fail"))
                            .font(AccBotFonts.captionSmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .listRowBackground(colors.surface)

                Toggle(isOn: $dependencies.userPreferences.weeklySummaryNotifications) {
                    VStack(alignment: .leading, spacing: Spacing.xxs) {
                        Text(String(localized: "Weekly Summary"))
                        Text(String(localized: "Receive weekly DCA performance summary"))
                            .font(AccBotFonts.captionSmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .listRowBackground(colors.surface)
            }

            HStack {
                Text(String(localized: "Low Balance Warning"))
                Spacer()
                Text(String(localized: "\(dependencies.userPreferences.lowBalanceThresholdDays) days"))
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .listRowBackground(colors.surface)

            Slider(
                value: Binding(
                    get: { Double(dependencies.userPreferences.lowBalanceThresholdDays) },
                    set: { dependencies.userPreferences.lowBalanceThresholdDays = Int($0) }
                ),
                in: 1...14,
                step: 1
            )
            .tint(colors.primary)
            .accessibilityLabel(String(localized: "Low balance warning threshold"))
            .accessibilityValue(String(localized: "\(dependencies.userPreferences.lowBalanceThresholdDays) days"))
            .accessibilityHint(String(localized: "Adjustable, from 1 to 14 days"))
            .listRowBackground(colors.surface)

            Button {
                viewModel.loadWithdrawalThresholds()
                showWithdrawalSheet = true
            } label: {
                HStack {
                    Label(String(localized: "Withdrawal Thresholds"), systemImage: "arrow.up.forward")
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .font(AccBotFonts.captionSmall)
                }
            }
            .listRowBackground(colors.surface)
        } header: {
            Text(String(localized: "DCA Alerts"))
        }
    }

    // MARK: - Background Execution

    private var backgroundSection: some View {
        Section {
            HStack {
                Image(systemName: "info.circle")
                    .foregroundStyle(colors.warning)
                    .accessibilityHidden(true)
                Text(String(localized: "iOS executes DCA plans approximately. Open the app daily for reliable execution."))
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .listRowBackground(colors.surface)

            HStack {
                Text(String(localized: "Last Background Run"))
                Spacer()
                Text(viewModel.lastBackgroundRunText)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .listRowBackground(colors.surface)
        } header: {
            Text(String(localized: "Background Execution"))
        }
    }

    // MARK: - Security

    private var securitySection: some View {
        Section {
            if viewModel.biometricType != .none {
                Toggle(isOn: Binding(
                    get: { dependencies.userPreferences.biometricLockEnabled },
                    set: { newValue in
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        dependencies.userPreferences.biometricLockEnabled = newValue
                    }
                )) {
                    Label(viewModel.biometricLabel, systemImage: viewModel.biometricIcon)
                }
                .listRowBackground(colors.surface)
            } else {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    HStack {
                        Label(String(localized: "Biometric Lock"), systemImage: "lock")
                        Spacer()
                        Text(String(localized: "Not Available"))
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                    Text(String(localized: "Face ID or Touch ID is not configured on this device"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .listRowBackground(colors.surface)
            }
        } header: {
            Text(String(localized: "Security"))
        }
    }

    // MARK: - Backup & Restore

    private var backupSection: some View {
        Section {
            Button {
                router.navigate(to: .backupExport)
            } label: {
                HStack {
                    Label(String(localized: "Export Backup"), systemImage: "square.and.arrow.up")
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .font(AccBotFonts.captionSmall)
                }
            }
            .listRowBackground(colors.surface)

            Button {
                router.navigate(to: .backupImport)
            } label: {
                HStack {
                    Label(String(localized: "Import Backup"), systemImage: "square.and.arrow.down")
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .font(AccBotFonts.captionSmall)
                }
            }
            .listRowBackground(colors.surface)
        } header: {
            Text(String(localized: "Backup & Restore"))
        }
    }

    // MARK: - About

    private var aboutSection: some View {
        Section {
            HStack {
                Text(String(localized: "Version"))
                Spacer()
                Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .listRowBackground(colors.surface)

            HStack {
                Text(String(localized: "Build"))
                Spacer()
                Text(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1")
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .listRowBackground(colors.surface)

            HStack {
                Text(String(localized: "Build Date"))
                Spacer()
                Text(buildDateString)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .listRowBackground(colors.surface)

            Text("Made with \u{2764}\u{FE0F} by Crynners")
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
                .frame(maxWidth: .infinity)
                .accessibilityLabel(String(localized: "Made with love by Crynners"))
                .listRowBackground(colors.surface)
        } header: {
            Text(String(localized: "About"))
        }
    }

    // MARK: - Links

    private var linksSection: some View {
        Section {
            if let docsUrl = URL(string: "https://github.com/crynners/AccBot") {
                Link(destination: docsUrl) {
                    HStack {
                        Label(String(localized: "Documentation"), systemImage: "book")
                        Spacer()
                        Image(systemName: "arrow.up.forward.square")
                            .foregroundStyle(colors.onSurfaceVariant)
                            .font(AccBotFonts.captionSmall)
                    }
                }
                .listRowBackground(colors.surface)
            }

            if let issuesUrl = URL(string: "https://github.com/crynners/AccBot/issues") {
                Link(destination: issuesUrl) {
                    HStack {
                        Label(String(localized: "Report Issue"), systemImage: "ladybug")
                        Spacer()
                        Image(systemName: "arrow.up.forward.square")
                            .foregroundStyle(colors.onSurfaceVariant)
                            .font(AccBotFonts.captionSmall)
                    }
                }
                .listRowBackground(colors.surface)
            }
        } header: {
            Text(String(localized: "Help"))
        }
    }

    // MARK: - Developer

    private var developerSection: some View {
        Section {
            Toggle(String(localized: "Sandbox Mode"), isOn: Binding(
                get: { dependencies.userPreferences.sandboxMode },
                set: { newValue in
                    dependencies.userPreferences.sandboxMode = newValue
                    viewModel.activeAlert = .sandboxRestart(isSandbox: newValue)
                }
            ))
            .tint(colors.warning)
            .listRowBackground(colors.surface)

            if dependencies.userPreferences.sandboxMode {
                Text(String(localized: "Using testnet APIs. No real funds will be used."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.warning)
                    .listRowBackground(colors.surface)
            }
        } header: {
            Text(String(localized: "Developer"))
        }
    }

    // MARK: - Danger Zone

    private var dangerZoneSection: some View {
        Section {
            DisclosureGroup(isExpanded: $dangerZoneExpanded) {
                Text(String(localized: "These actions are irreversible. All data will be permanently deleted."))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.error)
                    .listRowBackground(colors.surface)

                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    viewModel.confirmDelete(.plans)
                } label: {
                    HStack {
                        Label(String(localized: "Delete All Plans"), systemImage: "doc.text")
                            .foregroundStyle(colors.warning)
                        Spacer()
                        Text(String(localized: "\(viewModel.planCount) plans"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .disabled(viewModel.planCount == 0)
                .listRowBackground(colors.surface)

                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    viewModel.confirmDelete(.transactions)
                } label: {
                    HStack {
                        Label(String(localized: "Delete All Transactions"), systemImage: "receipt")
                            .foregroundStyle(colors.warning)
                        Spacer()
                        Text(String(localized: "\(viewModel.transactionCount) transactions"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .disabled(viewModel.transactionCount == 0)
                .listRowBackground(colors.surface)

                Button {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    viewModel.confirmDelete(.notifications)
                } label: {
                    HStack {
                        Label(String(localized: "Delete All Notifications"), systemImage: "bell.slash")
                            .foregroundStyle(colors.warning)
                        Spacer()
                        Text(String(localized: "\(viewModel.notificationCount) notifications"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .disabled(viewModel.notificationCount == 0)
                .listRowBackground(colors.surface)

                Button {
                    UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                    viewModel.confirmDelete(.allData)
                } label: {
                    Label(String(localized: "Delete All Data & Reset"), systemImage: "trash.fill")
                        .foregroundStyle(colors.error)
                }
                .listRowBackground(colors.surface)
            } label: {
                Label(String(localized: "Danger Zone"), systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(colors.error)
            }
            .tint(colors.error)
            .listRowBackground(colors.surface)
        }
    }
}

// MARK: - Withdrawal Thresholds Sheet

struct WithdrawalThresholdsSheet: View {
    @ObservedObject var viewModel: SettingsViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors
    @State private var thresholdValues: [String: String] = [:]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if viewModel.availableCryptoExchangePairs.isEmpty {
                    VStack(spacing: Spacing.lg) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(AccBotFonts.displayLarge)
                            .foregroundStyle(colors.onSurfaceVariant)

                        Text(String(localized: "No plans configured"))
                            .font(AccBotFonts.titleSmall)
                            .foregroundStyle(colors.onSurface)

                        Text(String(localized: "Set threshold amounts for automatic withdrawal alerts"))
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurfaceVariant)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(Spacing.xxl)
                } else {
                    List {
                        Section {
                            Text(String(localized: "Set threshold amounts for automatic withdrawal alerts"))
                                .font(AccBotFonts.bodySmall)
                                .foregroundStyle(colors.onSurfaceVariant)
                                .listRowBackground(colors.surface)
                        }

                        Section {
                            ForEach(viewModel.withdrawalPairIds, id: \.self) { pairId in
                                if let pair = viewModel.availableCryptoExchangePairs.first(where: { "\($0.crypto)_\($0.exchange.rawValue)" == pairId }) {
                                    thresholdRow(crypto: pair.crypto, exchange: pair.exchange)
                                }
                            }
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .background(colors.background)
            .navigationTitle(String(localized: "Withdrawal Thresholds"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button(String(localized: "Done")) {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "Cancel")) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(String(localized: "Save")) {
                        saveThresholds()
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(colors.primary)
                }
            }
        }
        .onAppear {
            loadCurrentValues()
        }
    }

    private func thresholdRow(crypto: String, exchange: Exchange) -> some View {
        let key = "\(crypto)_\(exchange.rawValue)"
        return HStack(spacing: Spacing.md) {
            CryptoIcon(symbol: crypto, size: 32)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(crypto)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)
                Text(exchange.displayName)
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            Spacer()

            HStack(spacing: Spacing.xs) {
                TextField(
                    String(localized: "Threshold amount"),
                    text: Binding(
                        get: { thresholdValues[key] ?? "" },
                        set: { newValue in
                            let filtered = newValue.filter { $0.isNumber || $0 == "." || $0 == "," }
                            thresholdValues[key] = filtered.replacingOccurrences(of: ",", with: ".")
                        }
                    )
                )
                .font(AccBotFonts.mono)
                .foregroundStyle(colors.onSurface)
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(minWidth: 80, maxWidth: 120)
                .padding(.horizontal, Spacing.sm)
                .padding(.vertical, Spacing.sm)
                .background(colors.surfaceVariant.opacity(0.3))
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

                if let value = thresholdValues[key], !value.isEmpty {
                    Button {
                        thresholdValues[key] = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(colors.onSurfaceVariant)
                            .font(AccBotFonts.body)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(String(localized: "Clear threshold"))
                }
            }
        }
        .listRowBackground(colors.surface)
    }

    private func loadCurrentValues() {
        for threshold in viewModel.withdrawalThresholds {
            let key = "\(threshold.crypto)_\(threshold.exchange.rawValue)"
            thresholdValues[key] = NSDecimalNumber(decimal: threshold.thresholdAmount).stringValue
        }
    }

    private func saveThresholds() {
        for pair in viewModel.availableCryptoExchangePairs {
            let key = "\(pair.crypto)_\(pair.exchange.rawValue)"
            if let valueStr = thresholdValues[key],
               !valueStr.isEmpty,
               let amount = Decimal(string: valueStr),
               amount > 0 {
                viewModel.setWithdrawalThreshold(
                    crypto: pair.crypto,
                    exchange: pair.exchange,
                    amount: amount
                )
            } else {
                viewModel.removeWithdrawalThreshold(
                    crypto: pair.crypto,
                    exchange: pair.exchange
                )
            }
        }
    }
}
