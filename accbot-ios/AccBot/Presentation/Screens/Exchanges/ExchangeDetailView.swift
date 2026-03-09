import SwiftUI

struct ExchangeDetailView: View {
    let exchange: Exchange

    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter

    @State private var balances: [(currency: String, balance: Decimal)] = []
    @State private var isRefreshing = false
    @State private var refreshError: String?
    @State private var showDeleteConfirmation = false

    // Credentials editing
    @State private var credentialsExpanded = false
    @State private var apiKey = ""
    @State private var apiSecret = ""
    @State private var passphrase = ""
    @State private var clientId = ""
    @State private var isValidating = false
    @State private var validationError: String?
    @State private var credentialsSaved = false

    // Credential visibility
    @State private var showSecret = false
    @State private var showPassphrase = false

    // QR scanner
    @State private var showQrScanner = false
    @State private var qrScanTarget: CredentialScanTarget = .apiKey
    @State private var showMultiFieldScanner = false

    // Import from API
    @State private var isImporting = false
    @State private var importProgress: String = ""
    @State private var showImportResult = false
    @State private var importResultMessage = ""
    @State private var showImportConfig = false
    @State private var importSinceDate: Date?

    @Environment(\.accBotColors) private var colors

    private var isConnected: Bool {
        let isSandbox = dependencies.userPreferences.sandboxMode
        return dependencies.credentialsStore.has(exchange: exchange, isSandbox: isSandbox)
    }

    @State private var plans: [DcaPlan] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                // Exchange header
                exchangeHeader

                // Sandbox info card (only in sandbox mode for supported exchanges)
                if colors.isSandbox && exchange.sandboxSupport == .full {
                    sandboxCredentialsInfoCard
                }

                // Collapsible credentials editing
                credentialsCard

                // Import from API
                if exchange.supportsApiImport && isConnected {
                    importFromApiCard
                }

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
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            plans = (try? dependencies.activeDatabase.planDao.getPlansByExchange(exchange)) ?? []
            loadCachedBalances()
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerSheet(
                title: String(localized: "Scan Credential"),
                onScanned: { code in
                    handleQrScan(code)
                }
            )
        }
        .sheet(isPresented: $showMultiFieldScanner) {
            MultiFieldScannerSheet(
                title: String(localized: "Scan All Credentials"),
                fields: multiFieldScannerFields(),
                onResult: { result in
                    if let key = result["apiKey"] {
                        apiKey = key
                    }
                    if let secret = result["apiSecret"] {
                        apiSecret = secret
                    }
                    if let phrase = result["passphrase"] {
                        passphrase = phrase
                    }
                    if let id = result["clientId"] {
                        clientId = id
                    }
                }
            )
        }
        .alert(
            String(localized: "Delete Connection"),
            isPresented: $showDeleteConfirmation
        ) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Delete"), role: .destructive) {
                deleteConnection()
            }
        } message: {
            let planCount = plans.count
            if planCount > 0 {
                Text(String(localized: "Are you sure you want to remove the API credentials for \(exchange.displayName)? \(planCount) plan(s) using this exchange will stop executing."))
            } else {
                Text(String(localized: "Are you sure you want to remove the API credentials for \(exchange.displayName)? Any plans using this exchange will stop executing."))
            }
        }
        .alert(
            String(localized: "Import Complete"),
            isPresented: $showImportResult
        ) {
            Button(String(localized: "OK")) {}
        } message: {
            Text(importResultMessage)
        }
        .sheet(isPresented: $showImportConfig) {
            ImportConfigSheet(sinceDate: $importSinceDate) {
                Task { await importFromApi(sinceDate: importSinceDate) }
            }
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
                    .foregroundStyle(colors.onSurface)

                HStack(spacing: Spacing.xs) {
                    Circle()
                        .fill(isConnected ? colors.success : colors.onSurfaceVariant)
                        .frame(width: 10, height: 10)
                        .accessibilityLabel(isConnected ? String(localized: "Connected") : String(localized: "Not connected"))

                    Text(isConnected
                         ? String(localized: "Connected")
                         : String(localized: "Not Connected"))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(isConnected ? colors.success : colors.onSurfaceVariant)
                }

                if exchange.sandboxSupport != .none {
                    HStack(spacing: Spacing.xs) {
                        Image(systemName: "flask")
                            .font(AccBotFonts.captionSmall)
                        Text(sandboxLabel)
                            .font(AccBotFonts.captionSmall)
                    }
                    .foregroundStyle(colors.onSurfaceVariant)
                }
            }

            Spacer()
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private var sandboxLabel: String {
        switch exchange.sandboxSupport {
        case .full: return String(localized: "Full sandbox support")
        case .paperTrading: return String(localized: "Paper trading available")
        case .futuresOnly: return String(localized: "Futures demo only")
        case .none: return ""
        }
    }

    // MARK: - Credentials Card (Collapsible)

    private var credentialsCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Clickable header
            Button {
                withAnimation(.easeInOut(duration: 0.25)) {
                    credentialsExpanded.toggle()
                }
            } label: {
                HStack {
                    Image(systemName: "key.fill")
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.primary)

                    Text(String(localized: "API Credentials"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)

                    Spacer()

                    HStack(spacing: Spacing.sm) {
                        Circle()
                            .fill(isConnected ? colors.success : colors.onSurfaceVariant)
                            .frame(width: 8, height: 8)
                            .accessibilityLabel(isConnected ? String(localized: "Configured") : String(localized: "Not configured"))

                        Text(isConnected
                             ? String(localized: "Configured")
                             : String(localized: "Not set"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(isConnected ? colors.success : colors.onSurfaceVariant)
                    }

                    Image(systemName: "chevron.right")
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .rotationEffect(.degrees(credentialsExpanded ? 90 : 0))
                }
                .padding(Spacing.lg)
            }
            .buttonStyle(.plain)
            .accessibilityHint(credentialsExpanded
                ? String(localized: "Double tap to collapse credentials")
                : String(localized: "Double tap to expand credentials"))

            // Expandable content
            if credentialsExpanded {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Divider()
                        .background(colors.surfaceVariant.opacity(0.3))

                    // Scan All Credentials button
                    Button {
                        showMultiFieldScanner = true
                    } label: {
                        HStack(spacing: Spacing.sm) {
                            Image(systemName: "qrcode.viewfinder")
                            Text(String(localized: "Scan All Credentials"))
                        }
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.sm)
                        .background(colors.primary.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        .overlay(
                            RoundedRectangle(cornerRadius: CornerRadius.sm)
                                .stroke(colors.primary.opacity(0.3), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)

                    if exchange.requiresClientId {
                        credentialField(
                            label: String(localized: "Client ID"),
                            text: $clientId,
                            placeholder: String(localized: "Enter your client ID"),
                            isSecure: false,
                            scanTarget: .clientId
                        )
                    }

                    credentialField(
                        label: exchange.requiresClientId
                            ? String(localized: "Public Key")
                            : String(localized: "API Key"),
                        text: $apiKey,
                        placeholder: exchange.requiresClientId
                            ? String(localized: "Enter your public key")
                            : String(localized: "Enter your API key"),
                        isSecure: false,
                        showContent: .constant(true),
                        scanTarget: .apiKey
                    )

                    credentialField(
                        label: exchange.requiresClientId
                            ? String(localized: "Private Key")
                            : String(localized: "API Secret"),
                        text: $apiSecret,
                        placeholder: exchange.requiresClientId
                            ? String(localized: "Enter your private key")
                            : String(localized: "Enter your API secret"),
                        isSecure: true,
                        showContent: $showSecret,
                        scanTarget: .apiSecret
                    )

                    if exchange.requiresPassphrase {
                        credentialField(
                            label: String(localized: "Passphrase"),
                            text: $passphrase,
                            placeholder: String(localized: "Enter your passphrase"),
                            isSecure: true,
                            showContent: $showPassphrase,
                            scanTarget: .passphrase
                        )
                    }

                    // Validation error
                    if let error = validationError {
                        HStack(spacing: Spacing.sm) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(colors.error)
                            Text(error)
                                .font(AccBotFonts.caption)
                                .foregroundStyle(colors.error)
                        }
                    }

                    // Success message
                    if credentialsSaved {
                        HStack(spacing: Spacing.sm) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(colors.success)
                            Text(String(localized: "Credentials validated and saved successfully!"))
                                .font(AccBotFonts.caption)
                                .foregroundStyle(colors.success)
                        }
                    }

                    // Save button
                    Button {
                        Task { await validateAndSaveCredentials() }
                    } label: {
                        HStack(spacing: Spacing.sm) {
                            if isValidating {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                                    .scaleEffect(0.8)
                            }
                            Text(isValidating
                                 ? String(localized: "Validating...")
                                 : String(localized: "Save"))
                                .font(AccBotFonts.headline)
                        }
                        .foregroundStyle(canSaveCredentials ? colors.onPrimary : colors.disabledForeground)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(canSaveCredentials ? colors.primary : colors.disabledBackground)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    }
                    .disabled(!canSaveCredentials)
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.bottom, Spacing.lg)
            }
        }
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Sandbox Credentials Info Card

    private var sandboxCredentialsInfoCard: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "flask.fill")
                    .foregroundStyle(colors.warning)
                Text(String(localized: "Testnet Credentials Required"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.warning)
            }

            Text(String(localized: "Sandbox mode requires testnet API keys from \(exchange.displayName). Production keys will NOT work."))
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)

            VStack(alignment: .leading, spacing: Spacing.sm) {
                ForEach(Array(sandboxSteps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: Spacing.sm) {
                        Text("\(index + 1).")
                            .font(AccBotFonts.bodySmall)
                            .fontWeight(.bold)
                            .foregroundStyle(colors.onSurface)
                            .frame(width: 24, alignment: .leading)
                        Text(step)
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurface)
                    }
                }
            }

            if let url = sandboxTestnetUrl {
                Button {
                    UIApplication.shared.open(url)
                } label: {
                    HStack(spacing: Spacing.sm) {
                        Image(systemName: "safari")
                        Text(String(localized: "Open Testnet"))
                    }
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.warning)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.sm)
                    .background(colors.warning.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    .overlay(
                        RoundedRectangle(cornerRadius: CornerRadius.sm)
                            .stroke(colors.warning.opacity(0.3), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(Spacing.lg)
        .background(colors.warning.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private var sandboxSteps: [String] {
        switch exchange {
        case .binance:
            return [
                String(localized: "Go to Binance Testnet"),
                String(localized: "Log in with your GitHub account"),
                String(localized: "Click 'Generate HMAC_SHA256 Key'"),
                String(localized: "Copy the API Key and Secret Key"),
                String(localized: "Paste them in the fields below"),
            ]
        case .kucoin:
            return [
                String(localized: "Go to KuCoin Sandbox"),
                String(localized: "Create a sandbox account"),
                String(localized: "Navigate to API Management"),
                String(localized: "Create a new API key"),
                String(localized: "Copy Key, Secret, and Passphrase"),
                String(localized: "Paste them in the fields below"),
            ]
        case .coinbase:
            return [
                String(localized: "Go to Coinbase Exchange Sandbox"),
                String(localized: "Create a sandbox account"),
                String(localized: "Navigate to API settings"),
                String(localized: "Create a new API key"),
                String(localized: "Copy Key, Secret, and Passphrase"),
                String(localized: "Paste them in the fields below"),
            ]
        default:
            return []
        }
    }

    private var sandboxTestnetUrl: URL? {
        switch exchange {
        case .binance: return URL(string: "https://testnet.binance.vision/")
        case .kucoin: return URL(string: "https://sandbox.kucoin.com/")
        case .coinbase: return URL(string: "https://public.sandbox.exchange.coinbase.com/")
        default: return nil
        }
    }

    private var canSaveCredentials: Bool {
        let hasKey = !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasSecret = !apiSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasPassphrase = !exchange.requiresPassphrase
            || !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasClientId = !exchange.requiresClientId
            || !clientId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return !isValidating && hasKey && hasSecret && hasPassphrase && hasClientId
    }

    private func credentialField(
        label: String,
        text: Binding<String>,
        placeholder: String,
        isSecure: Bool,
        showContent: Binding<Bool> = .constant(true),
        scanTarget: CredentialScanTarget? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: Spacing.sm) {
                HStack(spacing: 0) {
                    Group {
                        if isSecure && !showContent.wrappedValue {
                            SecureField(placeholder, text: text)
                        } else {
                            TextField(placeholder, text: text)
                        }
                    }
                    .font(AccBotFonts.mono)
                    .foregroundStyle(colors.onSurface)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)

                    if isSecure {
                        Button {
                            showContent.wrappedValue.toggle()
                        } label: {
                            Image(systemName: showContent.wrappedValue ? "eye.slash" : "eye")
                                .font(AccBotFonts.body)
                                .foregroundStyle(colors.onSurfaceVariant)
                        }
                        .buttonStyle(.plain)
                        .padding(.leading, Spacing.sm)
                        .accessibilityLabel(String(localized: "Toggle secret visibility"))
                    }
                }
                .padding(Spacing.md)
                .background(colors.surfaceVariant.opacity(0.3))
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

                if let target = scanTarget {
                    Button {
                        qrScanTarget = target
                        showQrScanner = true
                    } label: {
                        Image(systemName: "qrcode.viewfinder")
                            .font(AccBotFonts.iconSmall)
                            .foregroundStyle(colors.primary)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(String(localized: "Scan QR code for \(label)"))
                }
            }
        }
    }

    // MARK: - Import from API Card

    private var importFromApiCard: some View {
        Button {
            showImportConfig = true
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: "cloud.fill")
                    .font(AccBotFonts.iconMedium)
                    .foregroundStyle(colors.primary)
                    .frame(width: 40)

                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(String(localized: "Import from API"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onSurface)

                    if plans.isEmpty {
                        Text(String(localized: "No plans for this exchange"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    } else {
                        Text(String(localized: "Import for \(plans.count) plans"))
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }

                Spacer()

                if isImporting {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: colors.primary))
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: "chevron.right")
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .buttonStyle(.plain)
        .disabled(isImporting || plans.isEmpty)
    }

    // MARK: - Supported Pairs

    private var supportedPairsCard: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Supported Pairs"))
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onSurface)

            // Cryptos
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Cryptocurrencies"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(exchange.supportedCryptos, id: \.self) { crypto in
                            HStack(spacing: Spacing.xs) {
                                CryptoIcon(symbol: crypto, size: 24)
                                Text(crypto)
                                    .font(AccBotFonts.label)
                                    .foregroundStyle(colors.onSurface)
                            }
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.sm)
                            .background(colors.surfaceVariant.opacity(0.3))
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        }
                    }
                }
            }

            // Fiats
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Fiat Currencies"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(exchange.supportedFiats, id: \.self) { fiat in
                            Text(fiat)
                                .font(AccBotFonts.label)
                                .foregroundStyle(colors.primary)
                                .padding(.horizontal, Spacing.md)
                                .padding(.vertical, Spacing.sm)
                                .background(colors.primary.opacity(0.1))
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        }
                    }
                }
            }

            // Min order sizes
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Minimum Order Sizes"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)

                ForEach(Array(exchange.minOrderSize.sorted(by: { $0.key < $1.key })), id: \.key) { fiat, minSize in
                    HStack {
                        Text(fiat)
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurface)
                        Spacer()
                        Text(AccBotFormatters.formatFiat(minSize, symbol: fiat))
                            .font(AccBotFonts.mono)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Balances Section

    private var balancesSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text(String(localized: "Cached Balances"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)

                Spacer()

                if !balances.isEmpty {
                    Text(String(localized: "From local cache"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
            }

            if balances.isEmpty {
                HStack {
                    Image(systemName: "tray")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .accessibilityHidden(true)
                    Text(String(localized: "No cached balances. Tap Refresh to fetch."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .padding(Spacing.lg)
            } else {
                ForEach(balances, id: \.currency) { item in
                    HStack {
                        HStack(spacing: Spacing.sm) {
                            CryptoIcon(symbol: item.currency, size: 28)
                            Text(item.currency)
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.onSurface)
                        }

                        Spacer()

                        Text(formatBalance(item.balance, currency: item.currency))
                            .font(AccBotFonts.mono)
                            .foregroundStyle(colors.onSurface)
                    }
                    .padding(.vertical, Spacing.xs)
                }
            }

            // Refresh error
            if let error = refreshError {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(colors.error)
                    Text(error)
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.error)
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
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
            .foregroundStyle(colors.primary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(colors.primary.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
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
            .foregroundStyle(colors.error)
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .background(colors.error.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
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

    private func multiFieldScannerFields() -> [ScanTargetField] {
        var fields: [ScanTargetField] = []
        if exchange.requiresClientId {
            fields.append(ScanTargetField(id: "clientId", label: String(localized: "Client ID")))
        }
        fields.append(ScanTargetField(
            id: "apiKey",
            label: exchange.requiresClientId
                ? String(localized: "Public Key")
                : String(localized: "API Key")
        ))
        fields.append(ScanTargetField(
            id: "apiSecret",
            label: exchange.requiresClientId
                ? String(localized: "Private Key")
                : String(localized: "API Secret")
        ))
        if exchange.requiresPassphrase {
            fields.append(ScanTargetField(id: "passphrase", label: String(localized: "Passphrase")))
        }
        return fields
    }

    private func handleQrScan(_ code: String) {
        switch qrScanTarget {
        case .apiKey:
            apiKey = code.trimmingCharacters(in: .whitespacesAndNewlines)
        case .apiSecret:
            apiSecret = code.trimmingCharacters(in: .whitespacesAndNewlines)
        case .passphrase:
            passphrase = code.trimmingCharacters(in: .whitespacesAndNewlines)
        case .clientId:
            clientId = code.trimmingCharacters(in: .whitespacesAndNewlines)
        case .scanAll:
            // Handled by MultiFieldScannerSheet now
            break
        }
    }

    private func validateAndSaveCredentials() async {
        isValidating = true
        validationError = nil
        credentialsSaved = false

        let credentials = ExchangeCredentials(
            exchange: exchange,
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            apiSecret: apiSecret.trimmingCharacters(in: .whitespacesAndNewlines),
            passphrase: exchange.requiresPassphrase
                ? passphrase.trimmingCharacters(in: .whitespacesAndNewlines)
                : nil,
            clientId: exchange.requiresClientId
                ? clientId.trimmingCharacters(in: .whitespacesAndNewlines)
                : nil
        )

        let isSandbox = dependencies.userPreferences.sandboxMode

        do {
            let api = dependencies.exchangeApiFactory.create(
                credentials: credentials,
                isSandbox: isSandbox
            )
            let valid = try await api.validateCredentials()

            if valid {
                try dependencies.credentialsStore.save(credentials, isSandbox: isSandbox)
                credentialsSaved = true
                // Auto-dismiss after 3 seconds
                Task {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    withAnimation { credentialsSaved = false }
                }
            } else {
                validationError = String(localized: "Invalid credentials. Please check your API key and secret.")
            }
        } catch {
            validationError = error.localizedDescription
        }

        isValidating = false
    }

    private func importFromApi(sinceDate: Date? = nil) async {
        guard !plans.isEmpty else { return }
        let isSandbox = dependencies.userPreferences.sandboxMode

        guard let credentials = dependencies.credentialsStore.get(
            for: exchange,
            isSandbox: isSandbox
        ) else {
            importResultMessage = String(localized: "Credentials not found")
            showImportResult = true
            return
        }

        isImporting = true

        let api = dependencies.exchangeApiFactory.create(
            credentials: credentials,
            isSandbox: isSandbox
        )

        var totalImported = 0
        var totalSkipped = 0

        let importUseCase = ImportTradeHistoryUseCase(
            transactionDao: dependencies.activeDatabase.transactionDao
        )

        for plan in plans {
            let stream = importUseCase.importFromApi(
                api: api,
                planId: plan.id,
                crypto: plan.crypto,
                fiat: plan.fiat,
                exchange: exchange,
                sinceDate: sinceDate
            )

            for await progress in stream {
                switch progress {
                case .fetching(let page, let fetched):
                    importProgress = String(localized: "Importing...") + " \(plan.pair) - p\(page) (\(fetched))"
                case .complete(let imported, let skipped):
                    totalImported += imported
                    totalSkipped += skipped
                case .error(let message):
                    importResultMessage = message
                    showImportResult = true
                    isImporting = false
                    return
                default:
                    break
                }
            }
        }

        isImporting = false

        if totalImported == 0 && totalSkipped == 0 {
            importResultMessage = String(localized: "No new transactions found")
        } else {
            importResultMessage = String(localized: "\(totalImported) new transactions imported, \(totalSkipped) skipped")
        }
        showImportResult = true
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
        // Clean up cached balances
        try? dependencies.activeDatabase.exchangeBalanceDao.deleteByExchange(exchange)
        router.pop()
    }

    // MARK: - Formatting

    private func formatBalance(_ value: Decimal, currency: String) -> String {
        let isCrypto = exchange.supportedCryptos.contains(currency)
        return isCrypto
            ? AccBotFormatters.formatCryptoPlain(value)
            : AccBotFormatters.formatFiatPlain(value)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ExchangeDetailView(exchange: .binance)
    }
}
