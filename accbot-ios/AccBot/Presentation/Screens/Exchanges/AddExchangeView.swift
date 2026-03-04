import SwiftUI

// MARK: - Wizard Step

private enum ExchangeSetupStep: Int, CaseIterable {
    case selection = 0
    case instructions = 1
    case credentials = 2
    case success = 3

    var title: LocalizedStringKey {
        switch self {
        case .selection: return "Select Exchange"
        case .instructions: return "Instructions"
        case .credentials: return "Credentials"
        case .success: return "Success"
        }
    }
}

struct AddExchangeView: View {
    let preselectedExchange: Exchange?

    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter

    @State private var currentStep: ExchangeSetupStep = .selection
    @State private var selectedExchange: Exchange?
    @State private var apiKey = ""
    @State private var apiSecret = ""
    @State private var passphrase = ""
    @State private var clientId = ""
    @State private var isValidating = false
    @State private var validationError: String?

    // QR scanner state
    @State private var showQrScanner = false
    @State private var qrScanTarget: CredentialScanTarget = .apiKey
    @State private var showMultiFieldScanner = false

    // Import state
    @State private var showImportOffer = false
    @State private var isApiImporting = false
    @State private var apiImportProgress = ""
    @State private var showImportResult = false
    @State private var importResultMessage = ""

    @Environment(\.accBotColors) private var colors

    private let exchangeColumns = Array(
        repeating: GridItem(.flexible(), spacing: Spacing.md),
        count: 2
    )

    private var currentExchange: Exchange? {
        selectedExchange ?? preselectedExchange
    }

    private var canValidate: Bool {
        guard let exchange = currentExchange else { return false }
        let hasKey = !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasSecret = !apiSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasPassphrase = !exchange.requiresPassphrase
            || !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        let hasClientId = !exchange.requiresClientId
            || !clientId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return !isValidating && hasKey && hasSecret && hasPassphrase && hasClientId
    }

    var body: some View {
        VStack(spacing: 0) {
            // Progress indicator (hidden on success step)
            if currentStep != .success {
                progressBar
            }

            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xxl) {
                    switch currentStep {
                    case .selection:
                        selectionStep
                    case .instructions:
                        if let exchange = currentExchange {
                            instructionsStep(exchange)
                        }
                    case .credentials:
                        if let exchange = currentExchange {
                            credentialsStep(exchange)
                        }
                    case .success:
                        if let exchange = currentExchange {
                            successStep(exchange)
                        }
                    }
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.lg)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .background(colors.background)
        .navigationTitle(currentStep.title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(currentStep != .selection)
        .toolbar {
            if currentStep != .selection && currentStep != .success {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        goBack()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(AccBotFonts.label)
                            Text(String(localized: "Back"))
                        }
                    }
                }
            }
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
            if let exchange = currentExchange {
                MultiFieldScannerSheet(
                    title: String(localized: "Scan All Credentials"),
                    fields: multiFieldScannerFields(for: exchange),
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
        }
        .alert(
            String(localized: "Import Complete"),
            isPresented: $showImportResult
        ) {
            Button(String(localized: "OK")) {}
        } message: {
            Text(importResultMessage)
        }
        .onAppear {
            if let exchange = preselectedExchange {
                selectedExchange = exchange
                currentStep = .instructions
            }
        }
    }

    // MARK: - Progress Bar

    private var progressBar: some View {
        let stepNumber = currentStep.rawValue + 1
        let totalSteps = ExchangeSetupStep.allCases.count
        let progress = Float(stepNumber) / Float(totalSteps)
        return ProgressView(value: progress)
            .tint(colors.primary)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.sm)
            .accessibilityLabel(String(localized: "Step \(stepNumber) of \(totalSteps)"))
    }

    // MARK: - Navigation

    private func goBack() {
        withAnimation(.easeInOut(duration: 0.2)) {
            switch currentStep {
            case .instructions:
                if preselectedExchange != nil {
                    router.pop()
                } else {
                    currentStep = .selection
                }
            case .credentials:
                currentStep = .instructions
            default:
                break
            }
        }
    }

    private func goToStep(_ step: ExchangeSetupStep) {
        withAnimation(.easeInOut(duration: 0.2)) {
            currentStep = step
        }
    }

    // MARK: - Step 1: Selection

    private var selectionStep: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(String(localized: "Choose an exchange to connect"))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)

            let isSandbox = dependencies.userPreferences.sandboxMode
            let available = ExchangeFilter.getAvailableExchanges(isSandboxMode: isSandbox)
            let alreadyConfigured = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
            let unconfigured = available.filter { !alreadyConfigured.contains($0) }

            if unconfigured.isEmpty {
                EmptyStateView(
                    systemImage: "checkmark.circle",
                    title: String(localized: "All Exchanges Connected"),
                    subtitle: String(localized: "You have connected all available exchanges")
                )
            } else {
                LazyVGrid(columns: exchangeColumns, spacing: Spacing.md) {
                    ForEach(unconfigured) { exchange in
                        exchangeGridItem(exchange)
                    }
                }
            }
        }
    }

    private func exchangeGridItem(_ exchange: Exchange) -> some View {
        Button {
            selectedExchange = exchange
            apiKey = ""
            apiSecret = ""
            passphrase = ""
            clientId = ""
            validationError = nil
            goToStep(.instructions)
        } label: {
            VStack(spacing: Spacing.sm) {
                Image(exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 48, height: 48)
                    .clipShape(Circle())

                Text(exchange.displayName)
                    .font(AccBotFonts.label)
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)

                Text(String(localized: "\(exchange.supportedCryptos.count) cryptos"))
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .padding(.horizontal, Spacing.xs)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Step 2: Instructions

    private func instructionsStep(_ exchange: Exchange) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxl) {
            // Exchange header
            HStack(spacing: Spacing.lg) {
                Image(exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 56, height: 56)
                    .clipShape(Circle())

                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(String(localized: "Set up \(exchange.displayName)"))
                        .font(AccBotFonts.titleSmall)
                        .foregroundStyle(colors.onSurface)

                    Text(exchange.supportedCryptos.joined(separator: ", "))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.primary)
                }
                Spacer()
            }

            // Numbered instruction steps
            let steps = instructionSteps(for: exchange)
            VStack(alignment: .leading, spacing: Spacing.md) {
                ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: Spacing.md) {
                        ZStack {
                            Circle()
                                .fill(colors.primary.opacity(0.15))
                                .frame(width: 28, height: 28)
                            Text("\(index + 1)")
                                .font(AccBotFonts.label)
                                .foregroundStyle(colors.primary)
                        }
                        .frame(width: 28)

                        Text(step)
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.onSurface)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))

            // Open API page button
            if let url = apiPageUrl(for: exchange) {
                Button {
                    UIApplication.shared.open(url)
                } label: {
                    HStack(spacing: Spacing.sm) {
                        Image(systemName: "safari")
                        Text(String(localized: "Open API Page"))
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
                .buttonStyle(.plain)
            }

            // Security tip
            securityTipCard

            // "I have my keys" button
            Button {
                goToStep(.credentials)
            } label: {
                Text(String(localized: "I Have My Keys"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.lg)
                    .background(colors.primary)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
        }
    }

    private var securityTipCard: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: colors.isSandbox ? "info.circle.fill" : "exclamationmark.shield.fill")
                .font(AccBotFonts.iconSmall)
                .foregroundStyle(colors.isSandbox ? colors.primary : colors.warning)

            Text(colors.isSandbox
                 ? String(localized: "Testnet credentials are separate from your real exchange account. Test funds are provided for free.")
                 : String(localized: "Security tip: Only enable trading permissions. Never enable withdrawal permissions for DCA bots."))
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(Spacing.lg)
        .background((colors.isSandbox ? colors.primary : colors.warning).opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Step 3: Credentials

    private func credentialsStep(_ exchange: Exchange) -> some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            // Exchange mini-header
            HStack(spacing: Spacing.md) {
                Image(exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 36, height: 36)
                    .clipShape(Circle())

                Text(exchange.displayName)
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.onSurface)

                Spacer()
            }

            // Scan All Credentials button
            Button {
                showMultiFieldScanner = true
            } label: {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "qrcode.viewfinder")
                    Text(String(localized: "Scan All Credentials"))
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
            .buttonStyle(.plain)

            // Credential fields
            VStack(alignment: .leading, spacing: Spacing.lg) {
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
                    scanTarget: .apiSecret
                )

                if exchange.requiresPassphrase {
                    credentialField(
                        label: String(localized: "Passphrase"),
                        text: $passphrase,
                        placeholder: String(localized: "Enter your passphrase"),
                        isSecure: true,
                        scanTarget: .passphrase
                    )
                }
            }
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))

            // Validation error
            if let error = validationError {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(colors.error)
                    Text(error)
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.error)
                }
                .padding(Spacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(colors.error.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            }

            // Connect button
            Button {
                Task { await validateAndSave() }
            } label: {
                HStack(spacing: Spacing.sm) {
                    if isValidating {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: colors.onPrimary))
                            .scaleEffect(0.8)
                    }
                    Text(isValidating
                         ? String(localized: "Validating...")
                         : String(localized: "Connect"))
                        .font(AccBotFonts.headline)
                }
                .foregroundStyle(canValidate ? colors.onPrimary : colors.disabledForeground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
                .background(canValidate ? colors.primary : colors.disabledBackground)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
            .disabled(!canValidate)
        }
    }

    private func credentialField(
        label: String,
        text: Binding<String>,
        placeholder: String,
        isSecure: Bool,
        scanTarget: CredentialScanTarget? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)

            HStack(spacing: Spacing.sm) {
                Group {
                    if isSecure {
                        SecureField(placeholder, text: text)
                    } else {
                        TextField(placeholder, text: text)
                    }
                }
                .font(AccBotFonts.mono)
                .foregroundStyle(colors.onSurface)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
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
                }
            }
        }
    }

    // MARK: - Step 4: Success

    private func successStep(_ exchange: Exchange) -> some View {
        VStack(spacing: Spacing.xxxl) {
            Spacer(minLength: Spacing.xxxl)

            // Success icon
            ZStack {
                Circle()
                    .fill(colors.success.opacity(0.15))
                    .frame(width: 96, height: 96)
                Image(systemName: "checkmark.circle.fill")
                    .font(AccBotFonts.iconLarge)
                    .foregroundStyle(colors.success)
            }

            VStack(spacing: Spacing.sm) {
                Text(String(localized: "Successfully Connected"))
                    .font(AccBotFonts.titleMedium)
                    .foregroundStyle(colors.onSurface)

                Text(String(localized: "You are now connected to \(exchange.displayName)"))
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: Spacing.md) {
                // Import via API button (for supported exchanges)
                if exchange.supportsApiImport {
                    Button {
                        Task { await importFromApi(exchange) }
                    } label: {
                        HStack(spacing: Spacing.sm) {
                            if isApiImporting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: colors.primary))
                                    .scaleEffect(0.8)
                                Text(apiImportProgress.isEmpty
                                     ? String(localized: "Importing...")
                                     : apiImportProgress)
                                    .font(AccBotFonts.headline)
                            } else {
                                Image(systemName: "square.and.arrow.down")
                                Text(String(localized: "Import Transaction History"))
                                    .font(AccBotFonts.headline)
                            }
                        }
                        .foregroundStyle(colors.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(colors.primary.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                        .overlay(
                            RoundedRectangle(cornerRadius: CornerRadius.md)
                                .stroke(colors.primary.opacity(0.3), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(isApiImporting)
                }

                // Done button
                Button {
                    router.pop()
                } label: {
                    Text(String(localized: "Done"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(colors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                }
            }

            Spacer(minLength: Spacing.xxxl)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Exchange Instructions Data

    private func instructionSteps(for exchange: Exchange) -> [String] {
        if colors.isSandbox {
            return sandboxInstructionSteps(for: exchange)
        }
        switch exchange {
        case .coinmate:
            return [
                String(localized: "Go to coinmate.io and log in"),
                String(localized: "Navigate to Settings > API"),
                String(localized: "Note your Client ID (shown at the top)"),
                String(localized: "Click 'Create new API key'"),
                String(localized: "Enable 'Trade' permission only"),
                String(localized: "Copy Client ID, Public Key, and Private Key"),
            ]
        case .binance:
            return [
                String(localized: "Go to binance.com and log in"),
                String(localized: "Navigate to API Management"),
                String(localized: "Create a new API key"),
                String(localized: "Enable 'Spot & Margin Trading' only"),
                String(localized: "Restrict to your IP if possible"),
                String(localized: "Copy the API Key and Secret"),
            ]
        case .kraken:
            return [
                String(localized: "Go to kraken.com and log in"),
                String(localized: "Navigate to Settings > API"),
                String(localized: "Create a new API key"),
                String(localized: "Enable 'Create & Modify Orders' only"),
                String(localized: "Copy the API Key and Private Key"),
            ]
        case .kucoin:
            return [
                String(localized: "Go to kucoin.com and log in"),
                String(localized: "Navigate to API Management"),
                String(localized: "Create a new API key with passphrase"),
                String(localized: "Enable 'Trade' permission only"),
                String(localized: "Copy the API Key, Secret, and Passphrase"),
            ]
        case .bitfinex:
            return [
                String(localized: "Go to bitfinex.com and log in"),
                String(localized: "Navigate to Account > API Keys"),
                String(localized: "Create a new API key"),
                String(localized: "Enable 'Orders' permission only"),
                String(localized: "Copy the API Key and Secret"),
            ]
        case .huobi:
            return [
                String(localized: "Go to huobi.com and log in"),
                String(localized: "Navigate to API Management"),
                String(localized: "Create a new API key"),
                String(localized: "Enable 'Trade' permission only"),
                String(localized: "Copy the Access Key and Secret Key"),
            ]
        case .coinbase:
            return [
                String(localized: "Go to coinbase.com and log in"),
                String(localized: "Navigate to Settings > API"),
                String(localized: "Create a new API key"),
                String(localized: "Enable 'Trade' permission only"),
                String(localized: "Copy the API Key and Secret"),
            ]
        }
    }

    private func sandboxInstructionSteps(for exchange: Exchange) -> [String] {
        switch exchange {
        case .binance:
            return [
                String(localized: "Open testnet.binance.vision"),
                String(localized: "Log in with your GitHub account"),
                String(localized: "Click 'Generate HMAC_SHA256 Key'"),
                String(localized: "Copy the API Key and Secret Key"),
                String(localized: "Test funds are provided automatically"),
            ]
        case .kucoin:
            return [
                String(localized: "Open sandbox.kucoin.com"),
                String(localized: "Register a new account (separate from production)"),
                String(localized: "Go to API Management"),
                String(localized: "Create a new API key with passphrase"),
                String(localized: "Enable 'Trade' permission"),
                String(localized: "Copy API Key, Secret, and Passphrase"),
            ]
        case .coinbase:
            return [
                String(localized: "Open the Coinbase Exchange Sandbox"),
                String(localized: "Register a sandbox account"),
                String(localized: "Go to Settings > API"),
                String(localized: "Create a new API key"),
                String(localized: "Select 'View' and 'Trade' permissions"),
                String(localized: "Copy the API Key and Secret"),
            ]
        default:
            return instructionSteps(for: exchange)
        }
    }

    private func apiPageUrl(for exchange: Exchange) -> URL? {
        if colors.isSandbox {
            switch exchange {
            case .binance: return URL(string: "https://testnet.binance.vision/")
            case .kucoin: return URL(string: "https://sandbox.kucoin.com/")
            case .coinbase: return URL(string: "https://public.sandbox.exchange.coinbase.com/")
            default: break
            }
        }
        switch exchange {
        case .coinmate: return URL(string: "https://coinmate.io/apikeys")
        case .binance: return URL(string: "https://www.binance.com/en/my/settings/api-management")
        case .kraken: return URL(string: "https://www.kraken.com/u/security/api")
        case .kucoin: return URL(string: "https://www.kucoin.com/account/api")
        case .bitfinex: return URL(string: "https://setting.bitfinex.com/api")
        case .huobi: return URL(string: "https://www.huobi.com/en-us/apikey/")
        case .coinbase: return URL(string: "https://www.coinbase.com/settings/api")
        }
    }

    // MARK: - Actions

    private func validateAndSave() async {
        guard let exchange = currentExchange else { return }

        isValidating = true
        validationError = nil

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
                goToStep(.success)
            } else {
                validationError = String(localized: "Invalid credentials. Please check your API key and secret.")
            }
        } catch {
            validationError = error.localizedDescription
        }

        isValidating = false
    }

    private func multiFieldScannerFields(for exchange: Exchange) -> [ScanTargetField] {
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

    private func importFromApi(_ exchange: Exchange) async {
        let isSandbox = dependencies.userPreferences.sandboxMode

        guard let credentials = dependencies.credentialsStore.get(
            for: exchange,
            isSandbox: isSandbox
        ) else {
            importResultMessage = String(localized: "Credentials not found")
            showImportResult = true
            return
        }

        // Get plans for this exchange
        let plans = (try? dependencies.activeDatabase.planDao.getPlansByExchange(exchange)) ?? []

        if plans.isEmpty {
            importResultMessage = String(localized: "No plans found for this exchange. Create a plan first, then import from the exchange detail screen.")
            showImportResult = true
            return
        }

        isApiImporting = true

        let api = dependencies.exchangeApiFactory.create(
            credentials: credentials,
            isSandbox: isSandbox
        )

        var totalImported = 0
        var totalSkipped = 0

        for plan in plans {
            let importUseCase = ImportTradeHistoryUseCase(
                transactionDao: dependencies.activeDatabase.transactionDao
            )

            let stream = importUseCase.importFromApi(
                api: api,
                planId: plan.id,
                crypto: plan.crypto,
                fiat: plan.fiat,
                exchange: exchange
            )

            for await progress in stream {
                switch progress {
                case .fetching(let page, let fetched):
                    apiImportProgress = "\(plan.pair) - p\(page) (\(fetched))"
                case .complete(let imported, let skipped):
                    totalImported += imported
                    totalSkipped += skipped
                case .error(let message):
                    importResultMessage = message
                    showImportResult = true
                    isApiImporting = false
                    return
                default:
                    break
                }
            }
        }

        isApiImporting = false

        if totalImported == 0 && totalSkipped == 0 {
            importResultMessage = String(localized: "No new transactions found")
        } else {
            importResultMessage = String(localized: "\(totalImported) new transactions imported, \(totalSkipped) skipped")
        }
        showImportResult = true
    }
}

// MARK: - Credential Scan Target

enum CredentialScanTarget {
    case apiKey
    case apiSecret
    case passphrase
    case clientId
    case scanAll
}

// MARK: - Preview

#Preview {
    NavigationStack {
        AddExchangeView(preselectedExchange: nil)
    }
    .preferredColorScheme(.dark)
}
