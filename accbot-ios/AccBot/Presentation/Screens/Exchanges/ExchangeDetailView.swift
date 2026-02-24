import SwiftUI

struct ExchangeDetailView: View {
    let exchange: Exchange

    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter

    @State private var balances: [(currency: String, balance: Decimal)] = []
    @State private var isRefreshing = false
    @State private var refreshError: String?
    @State private var showDeleteConfirmation = false

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    private var isConnected: Bool {
        let isSandbox = dependencies.userPreferences.sandboxMode
        return dependencies.credentialsStore.has(exchange: exchange, isSandbox: isSandbox)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                // Exchange header
                exchangeHeader

                // Connection status
                connectionStatusCard

                // Supported pairs
                supportedPairsCard

                // Cached balances
                balancesSection

                // Refresh button
                refreshButton

                // Delete connection button
                deleteButton
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
        .background(colors.background)
        .navigationTitle(exchange.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadCachedBalances() }
        .alert(
            String(localized: "Delete Connection"),
            isPresented: $showDeleteConfirmation
        ) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Delete"), role: .destructive) {
                deleteConnection()
            }
        } message: {
            Text(String(localized: "Are you sure you want to remove the API credentials for \(exchange.displayName)? Any plans using this exchange will stop executing."))
        }
    }

    // MARK: - Exchange Header

    private var exchangeHeader: some View {
        HStack(spacing: Spacing.lg) {
            Image(exchange.logoName)
                .resizable()
                .scaledToFit()
                .frame(width: 64, height: 64)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(exchange.displayName)
                    .font(AccBotFonts.titleMedium)
                    .foregroundColor(colors.onSurface)

                HStack(spacing: Spacing.xs) {
                    Circle()
                        .fill(isConnected ? colors.success : Color.gray)
                        .frame(width: 10, height: 10)

                    Text(isConnected
                         ? String(localized: "Connected")
                         : String(localized: "Not Connected"))
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(isConnected ? colors.success : colors.onSurfaceVariant)
                }

                if exchange.sandboxSupport != .none {
                    HStack(spacing: Spacing.xs) {
                        Image(systemName: "flask")
                            .font(AccBotFonts.captionSmall)
                        Text(sandboxLabel)
                            .font(AccBotFonts.captionSmall)
                    }
                    .foregroundColor(colors.onSurfaceVariant)
                }
            }

            Spacer()
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    private var sandboxLabel: String {
        switch exchange.sandboxSupport {
        case .full: return String(localized: "Full sandbox support")
        case .paperTrading: return String(localized: "Paper trading available")
        case .futuresOnly: return String(localized: "Futures demo only")
        case .none: return ""
        }
    }

    // MARK: - Connection Status Card

    private var connectionStatusCard: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Connection Details"))
                .font(AccBotFonts.headline)
                .foregroundColor(colors.onSurface)

            HStack {
                connectionDetail(
                    icon: "key.fill",
                    label: String(localized: "API Key"),
                    value: isConnected ? String(localized: "Configured") : String(localized: "Not set")
                )
                Spacer()
                connectionDetail(
                    icon: "lock.fill",
                    label: String(localized: "API Secret"),
                    value: isConnected ? String(localized: "Configured") : String(localized: "Not set")
                )
            }

            if exchange.requiresPassphrase {
                connectionDetail(
                    icon: "textformat.abc",
                    label: String(localized: "Passphrase"),
                    value: isConnected ? String(localized: "Configured") : String(localized: "Not set")
                )
            }

            if exchange.requiresClientId {
                connectionDetail(
                    icon: "person.text.rectangle",
                    label: String(localized: "Client ID"),
                    value: isConnected ? String(localized: "Configured") : String(localized: "Not set")
                )
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    private func connectionDetail(icon: String, label: String, value: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: icon)
                .font(AccBotFonts.caption)
                .foregroundColor(colors.onSurfaceVariant)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(label)
                    .font(AccBotFonts.captionSmall)
                    .foregroundColor(colors.onSurfaceVariant)
                Text(value)
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(isConnected ? colors.success : colors.onSurfaceVariant)
            }
        }
    }

    // MARK: - Supported Pairs

    private var supportedPairsCard: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Supported Pairs"))
                .font(AccBotFonts.headline)
                .foregroundColor(colors.onSurface)

            // Cryptos
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Cryptocurrencies"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(exchange.supportedCryptos, id: \.self) { crypto in
                            HStack(spacing: Spacing.xs) {
                                CryptoIcon(symbol: crypto, size: 24)
                                Text(crypto)
                                    .font(AccBotFonts.label)
                                    .foregroundColor(colors.onSurface)
                            }
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.sm)
                            .background(colors.surfaceVariant.opacity(0.3))
                            .cornerRadius(CornerRadius.sm)
                        }
                    }
                }
            }

            // Fiats
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Fiat Currencies"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(exchange.supportedFiats, id: \.self) { fiat in
                            Text(fiat)
                                .font(AccBotFonts.label)
                                .foregroundColor(colors.primary)
                                .padding(.horizontal, Spacing.md)
                                .padding(.vertical, Spacing.sm)
                                .background(colors.primary.opacity(0.1))
                                .cornerRadius(CornerRadius.sm)
                        }
                    }
                }
            }

            // Min order sizes
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Minimum Order Sizes"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                ForEach(Array(exchange.minOrderSize.sorted(by: { $0.key < $1.key })), id: \.key) { fiat, minSize in
                    HStack {
                        Text(fiat)
                            .font(AccBotFonts.bodySmall)
                            .foregroundColor(colors.onSurface)
                        Spacer()
                        Text("\(NSDecimalNumber(decimal: minSize).stringValue) \(fiat)")
                            .font(AccBotFonts.mono)
                            .foregroundColor(colors.onSurfaceVariant)
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Balances Section

    private var balancesSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text(String(localized: "Cached Balances"))
                    .font(AccBotFonts.headline)
                    .foregroundColor(colors.onSurface)

                Spacer()

                if !balances.isEmpty {
                    Text(String(localized: "From local cache"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundColor(colors.onSurfaceVariant)
                }
            }

            if balances.isEmpty {
                HStack {
                    Image(systemName: "tray")
                        .foregroundColor(colors.onSurfaceVariant.opacity(0.5))
                    Text(String(localized: "No cached balances. Tap Refresh to fetch."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(colors.onSurfaceVariant)
                }
                .padding(Spacing.lg)
            } else {
                ForEach(balances, id: \.currency) { item in
                    HStack {
                        HStack(spacing: Spacing.sm) {
                            CryptoIcon(symbol: item.currency, size: 28)
                            Text(item.currency)
                                .font(AccBotFonts.headline)
                                .foregroundColor(colors.onSurface)
                        }

                        Spacer()

                        Text(formatBalance(item.balance, currency: item.currency))
                            .font(AccBotFonts.mono)
                            .foregroundColor(colors.onSurface)
                    }
                    .padding(.vertical, Spacing.xs)
                }
            }

            // Refresh error
            if let error = refreshError {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(colors.error)
                    Text(error)
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.error)
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Refresh Button

    private var refreshButton: some View {
        Button {
            Task { await refreshBalances() }
        } label: {
            HStack(spacing: Spacing.sm) {
                if isRefreshing {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: colors.primary))
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: "arrow.clockwise")
                }
                Text(isRefreshing
                     ? String(localized: "Refreshing...")
                     : String(localized: "Refresh Balances"))
            }
            .font(AccBotFonts.headline)
            .foregroundColor(colors.primary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(colors.primary.opacity(0.1))
            .cornerRadius(CornerRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(colors.primary.opacity(0.3), lineWidth: 1)
            )
        }
        .disabled(isRefreshing || !isConnected)
    }

    // MARK: - Delete Button

    private var deleteButton: some View {
        Button {
            showDeleteConfirmation = true
        } label: {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "trash")
                Text(String(localized: "Delete Connection"))
            }
            .font(AccBotFonts.headline)
            .foregroundColor(colors.error)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(colors.error.opacity(0.1))
            .cornerRadius(CornerRadius.md)
        }
        .padding(.bottom, Spacing.xxl)
    }

    // MARK: - Data Operations

    private func loadCachedBalances() {
        do {
            balances = try dependencies.activeDatabase.exchangeBalanceDao
                .getBalancesByExchange(exchange)
                .filter { $0.balance > 0 }
                .sorted { $0.currency < $1.currency }
        } catch {
            balances = []
        }
    }

    private func refreshBalances() async {
        guard isConnected else { return }
        let isSandbox = dependencies.userPreferences.sandboxMode

        guard let credentials = dependencies.credentialsStore.get(
            for: exchange,
            isSandbox: isSandbox
        ) else {
            refreshError = String(localized: "Credentials not found")
            return
        }

        isRefreshing = true
        refreshError = nil

        let api = dependencies.exchangeApiFactory.create(
            credentials: credentials,
            isSandbox: isSandbox
        )

        // Fetch balances for all supported currencies
        let currencies = exchange.supportedCryptos + exchange.supportedFiats
        var fetchedBalances: [(currency: String, balance: Decimal)] = []

        for currency in currencies {
            if let balance = await api.getBalance(currency: currency), balance > 0 {
                fetchedBalances.append((currency: currency, balance: balance))

                // Cache in database
                do {
                    try dependencies.activeDatabase.exchangeBalanceDao.upsert(
                        exchange: exchange,
                        currency: currency,
                        balance: balance
                    )
                } catch {
                    // Non-critical
                }
            }
        }

        balances = fetchedBalances.sorted { $0.currency < $1.currency }
        isRefreshing = false
    }

    private func deleteConnection() {
        let isSandbox = dependencies.userPreferences.sandboxMode
        dependencies.credentialsStore.delete(exchange: exchange, isSandbox: isSandbox)
        router.pop()
    }

    // MARK: - Formatting

    private func formatBalance(_ value: Decimal, currency: String) -> String {
        let number = NSDecimalNumber(decimal: value)
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal

        // Crypto gets more decimal places
        let isCrypto = exchange.supportedCryptos.contains(currency)
        formatter.minimumFractionDigits = isCrypto ? 2 : 2
        formatter.maximumFractionDigits = isCrypto ? 8 : 2

        return formatter.string(from: number) ?? number.stringValue
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ExchangeDetailView(exchange: .binance)
    }
    .preferredColorScheme(.dark)
}
