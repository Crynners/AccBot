import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: SettingsViewModel
    @Environment(\.isSandboxMode) var isSandboxMode

    init() {
        _viewModel = StateObject(wrappedValue: SettingsViewModel(
            dependencies: AppDependencies()
        ))
    }

    var body: some View {
        Form {
            exchangeAccountsSection
            systemSection
            dcaAlertsSection
            backgroundSection
            securitySection
            aboutSection
            developerSection
            dataManagementSection
            dangerZoneSection
        }
        .scrollContentBackground(.hidden)
        .background(Color.backgroundDark)
        .navigationTitle("Settings")
        .alert("Confirm Delete", isPresented: $viewModel.showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                viewModel.executeDelete()
            }
        } message: {
            Text(viewModel.deleteTarget?.message ?? "")
        }
        .alert("Restart Required", isPresented: $viewModel.showLanguageRestart) {
            Button("OK") {}
        } message: {
            Text("Please restart the app for the language change to take effect.")
        }
        .onAppear {
            viewModel.loadConnectedExchanges()
        }
    }

    // MARK: - Exchange Accounts

    private var exchangeAccountsSection: some View {
        Section {
            if viewModel.connectedExchanges.isEmpty {
                Text("No exchanges connected")
                    .foregroundColor(.onSurfaceVariantColor)
            } else {
                ForEach(viewModel.connectedExchanges, id: \.self) { exchange in
                    HStack {
                        Image(exchange.logoName)
                            .resizable()
                            .frame(width: 24, height: 24)
                        Text(exchange.displayName)
                        Spacer()
                        Circle()
                            .fill(Color.accentTeal)
                            .frame(width: 8, height: 8)
                    }
                    .listRowBackground(Color.surfaceDark)
                }
            }

            Button {
                router.navigate(to: .exchangeManagement)
            } label: {
                Label("Manage Exchanges", systemImage: "arrow.triangle.2.circlepath")
            }
            .listRowBackground(Color.surfaceDark)
        } header: {
            Text("Exchange Accounts")
        }
    }

    // MARK: - System

    private var systemSection: some View {
        Section {
            Picker("Theme", selection: $dependencies.userPreferences.appTheme) {
                ForEach(AppTheme.allCases, id: \.self) { theme in
                    Text(theme.displayName).tag(theme)
                }
            }
            .listRowBackground(Color.surfaceDark)

            Menu {
                Button("System Default") { viewModel.setLanguage("") }
                Button("English") { viewModel.setLanguage("en") }
                Button("Czech") { viewModel.setLanguage("cs") }
            } label: {
                HStack {
                    Text("Language")
                    Spacer()
                    Text(languageDisplayName)
                        .foregroundColor(.onSurfaceVariantColor)
                }
            }
            .listRowBackground(Color.surfaceDark)
        } header: {
            Text("System")
        }
    }

    private var languageDisplayName: String {
        switch dependencies.userPreferences.appLanguage {
        case "en": return "English"
        case "cs": return "Czech"
        default: return "System"
        }
    }

    // MARK: - DCA Alerts

    private var dcaAlertsSection: some View {
        Section {
            Toggle("Notifications", isOn: $dependencies.userPreferences.notificationsEnabled)
                .listRowBackground(Color.surfaceDark)

            if dependencies.userPreferences.notificationsEnabled {
                Toggle("Purchase Alerts", isOn: $dependencies.userPreferences.purchaseNotifications)
                    .listRowBackground(Color.surfaceDark)
                Toggle("Error Alerts", isOn: $dependencies.userPreferences.errorNotifications)
                    .listRowBackground(Color.surfaceDark)
                Toggle("Weekly Summary", isOn: $dependencies.userPreferences.weeklySummaryNotifications)
                    .listRowBackground(Color.surfaceDark)
            }

            HStack {
                Text("Low Balance Warning")
                Spacer()
                Text("\(dependencies.userPreferences.lowBalanceThresholdDays) days")
                    .foregroundColor(.onSurfaceVariantColor)
            }
            .listRowBackground(Color.surfaceDark)

            Slider(
                value: Binding(
                    get: { Double(dependencies.userPreferences.lowBalanceThresholdDays) },
                    set: { dependencies.userPreferences.lowBalanceThresholdDays = Int($0) }
                ),
                in: 1...14,
                step: 1
            )
            .tint(isSandboxMode ? .sandboxPrimary : .accentTeal)
            .listRowBackground(Color.surfaceDark)

            Button {
                router.navigate(to: .exchangeManagement)
            } label: {
                Label("Withdrawal Thresholds", systemImage: "arrow.up.forward")
            }
            .listRowBackground(Color.surfaceDark)
        } header: {
            Text("DCA Alerts")
        }
    }

    // MARK: - Background Execution

    private var backgroundSection: some View {
        Section {
            HStack {
                Image(systemName: "info.circle")
                    .foregroundColor(.warningOrange)
                Text("iOS executes DCA plans approximately. Open the app daily for reliable execution.")
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(.onSurfaceVariantColor)
            }
            .listRowBackground(Color.surfaceDark)

            HStack {
                Text("Last Background Run")
                Spacer()
                Text(viewModel.lastBackgroundRunText)
                    .foregroundColor(.onSurfaceVariantColor)
            }
            .listRowBackground(Color.surfaceDark)
        } header: {
            Text("Background Execution")
        }
    }

    // MARK: - Security

    private var securitySection: some View {
        Section {
            if viewModel.biometricType != .none {
                Toggle(isOn: $dependencies.userPreferences.biometricLockEnabled) {
                    Label(viewModel.biometricLabel, systemImage: viewModel.biometricIcon)
                }
                .listRowBackground(Color.surfaceDark)
            } else {
                HStack {
                    Label("Biometric Lock", systemImage: "lock")
                    Spacer()
                    Text("Not Available")
                        .foregroundColor(.onSurfaceVariantColor)
                }
                .listRowBackground(Color.surfaceDark)
            }
        } header: {
            Text("Security")
        }
    }

    // MARK: - About

    private var aboutSection: some View {
        Section {
            HStack {
                Text("Version")
                Spacer()
                Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                    .foregroundColor(.onSurfaceVariantColor)
            }
            .listRowBackground(Color.surfaceDark)

            HStack {
                Text("Build")
                Spacer()
                Text(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1")
                    .foregroundColor(.onSurfaceVariantColor)
            }
            .listRowBackground(Color.surfaceDark)
        } header: {
            Text("About")
        }
    }

    // MARK: - Developer

    private var developerSection: some View {
        Section {
            Toggle("Sandbox Mode", isOn: $dependencies.userPreferences.sandboxMode)
                .tint(.warningOrange)
                .listRowBackground(Color.surfaceDark)

            if dependencies.userPreferences.sandboxMode {
                Text("Using testnet APIs. No real funds will be used.")
                    .font(AccBotFonts.caption)
                    .foregroundColor(.warningOrange)
                    .listRowBackground(Color.surfaceDark)
            }
        } header: {
            Text("Developer")
        }
    }

    // MARK: - Data Management

    private var dataManagementSection: some View {
        Section {
            Button {
                viewModel.confirmDelete(.plans)
            } label: {
                Label("Delete All Plans", systemImage: "trash")
                    .foregroundColor(.warningOrange)
            }
            .listRowBackground(Color.surfaceDark)

            Button {
                viewModel.confirmDelete(.transactions)
            } label: {
                Label("Delete All Transactions", systemImage: "trash")
                    .foregroundColor(.warningOrange)
            }
            .listRowBackground(Color.surfaceDark)

            Button {
                viewModel.confirmDelete(.notifications)
            } label: {
                Label("Delete All Notifications", systemImage: "trash")
                    .foregroundColor(.warningOrange)
            }
            .listRowBackground(Color.surfaceDark)
        } header: {
            Text("Data Management")
        }
    }

    // MARK: - Danger Zone

    private var dangerZoneSection: some View {
        Section {
            Button {
                viewModel.confirmDelete(.allData)
            } label: {
                Label("Delete All Data & Reset", systemImage: "exclamationmark.triangle.fill")
                    .foregroundColor(.errorRed)
            }
            .listRowBackground(Color.surfaceDark)
        } header: {
            Text("Danger Zone")
        }
    }
}
