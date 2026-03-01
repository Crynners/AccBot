import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = SettingsViewModel()
    @Environment(\.accBotColors) private var colors
    @State private var dangerZoneExpanded = false
    @State private var showWithdrawalSheet = false
    @State private var showChangelog = false
    @State private var showNotificationInfo = false

    var body: some View {
        Form {
            generalSection
            alertsAndSecuritySection
            dataSection
            aboutSection
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
        .sheet(isPresented: $showChangelog) {
            ChangelogView(entries: ChangelogData.entries)
        }
        .sheet(isPresented: $showNotificationInfo) {
            NotificationInfoSheet()
        }
    }

    // MARK: - General

    private var generalSection: some View {
        Section {
            // Manage Exchanges
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

            // Theme
            Picker(String(localized: "Theme"), selection: $dependencies.userPreferences.appTheme) {
                ForEach(AppTheme.allCases, id: \.self) { theme in
                    Text(theme.displayName).tag(theme)
                }
            }
            .listRowBackground(colors.surface)

            // Market Pulse
            Toggle(isOn: $dependencies.userPreferences.marketPulseEnabled) {
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(String(localized: "Market Pulse"))
                    Text(String(localized: "Show market indicators on dashboard"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
            .listRowBackground(colors.surface)

            // Language
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

            // Notifications toggle + info + system settings
            HStack {
                Toggle(String(localized: "Notifications"), isOn: $dependencies.userPreferences.notificationsEnabled)
                Button {
                    showNotificationInfo = true
                } label: {
                    Image(systemName: "questionmark.circle")
                        .foregroundStyle(colors.primary)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "Notification info"))
                Button {
                    if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(String(localized: "Open notification settings"))
            }
            .listRowBackground(colors.surface)

            // Background execution info
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
            Text(String(localized: "General"))
                .accessibilityAddTraits(.isHeader)
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

    // MARK: - Alerts & Security

    private var alertsAndSecuritySection: some View {
        Section {
            // Notification sub-toggles (only when notifications enabled)
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

            // Low Balance Warning
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

            // Withdrawal Thresholds
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

            // Biometric Lock
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
            Text(String(localized: "Alerts & Security"))
                .accessibilityAddTraits(.isHeader)
        }
    }

    // MARK: - Data

    private var dataSection: some View {
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
            Text(String(localized: "Data"))
                .accessibilityAddTraits(.isHeader)
        }
    }

    // MARK: - About

    private var aboutSection: some View {
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

            Button {
                showChangelog = true
            } label: {
                HStack {
                    Label(String(localized: "What's New"), systemImage: "star.circle")
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .font(AccBotFonts.captionSmall)
                }
            }
            .listRowBackground(colors.surface)

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
                .accessibilityAddTraits(.isHeader)
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
                    .accessibilityAddTraits(.isHeader)
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

// MARK: - Notification Info Sheet

struct NotificationInfoSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    infoRow(
                        icon: "bell.badge",
                        title: String(localized: "How Notifications Work"),
                        text: String(localized: "AccBot sends local notifications when DCA purchases complete or fail. Notifications are processed on-device — no data is sent to external servers.")
                    )

                    infoRow(
                        icon: "clock.arrow.circlepath",
                        title: String(localized: "Background Execution"),
                        text: String(localized: "iOS limits background execution to approximately every 15 minutes. For reliable DCA execution, open the app at least once daily. The app uses Background App Refresh when available.")
                    )

                    infoRow(
                        icon: "gearshape",
                        title: String(localized: "System Settings"),
                        text: String(localized: "Make sure notifications are enabled in iOS Settings → AccBot → Notifications. You can also configure alert style, sounds, and badges there.")
                    )

                    infoRow(
                        icon: "battery.75percent",
                        title: String(localized: "Battery Optimization"),
                        text: String(localized: "Disable Low Power Mode for best results. iOS may delay or skip background tasks when battery is low.")
                    )
                }
                .padding(Spacing.lg)
            }
            .background(colors.background)
            .navigationTitle(String(localized: "Notification Info"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(String(localized: "Done")) {
                        dismiss()
                    }
                }
            }
        }
    }

    private func infoRow(icon: String, title: String, text: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: icon)
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.primary)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)
                Text(text)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }
}
