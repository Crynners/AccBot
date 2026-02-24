import SwiftUI

struct AddExchangeView: View {
    let preselectedExchange: Exchange?

    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter

    @State private var selectedExchange: Exchange?
    @State private var apiKey = ""
    @State private var apiSecret = ""
    @State private var passphrase = ""
    @State private var clientId = ""
    @State private var isValidating = false
    @State private var validationError: String?
    @State private var isValid = false

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    private let exchangeColumns = Array(
        repeating: GridItem(.flexible(), spacing: Spacing.md),
        count: 3
    )

    private var currentExchange: Exchange? {
        selectedExchange ?? preselectedExchange
    }

    private var canValidate: Bool {
        guard let exchange = currentExchange else { return false }
        let hasKey = !apiKey.trimmingCharacters(in: .whitespaces).isEmpty
        let hasSecret = !apiSecret.trimmingCharacters(in: .whitespaces).isEmpty
        let hasPassphrase = !exchange.requiresPassphrase
            || !passphrase.trimmingCharacters(in: .whitespaces).isEmpty
        let hasClientId = !exchange.requiresClientId
            || !clientId.trimmingCharacters(in: .whitespaces).isEmpty
        return !isValidating && hasKey && hasSecret && hasPassphrase && hasClientId
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                // Exchange selection (if not preselected)
                if preselectedExchange == nil {
                    exchangePickerSection
                }

                // Exchange info header
                if let exchange = currentExchange {
                    exchangeInfoHeader(exchange)
                }

                // Credentials form
                if let exchange = currentExchange {
                    credentialsSection(exchange)
                }

                // Validation status
                if let error = validationError {
                    errorBanner(error)
                }

                if isValid {
                    successBanner
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Add Exchange"))
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            if let exchange = preselectedExchange {
                selectedExchange = exchange
            }
        }
    }

    // MARK: - Exchange Picker

    private var exchangePickerSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Select Exchange"))
                .font(AccBotFonts.headline)
                .foregroundColor(colors.onSurface)

            let isSandbox = dependencies.userPreferences.sandboxMode
            let available = ExchangeFilter.getAvailableExchanges(isSandboxMode: isSandbox)
            let alreadyConfigured = dependencies.credentialsStore.getConfiguredExchanges(isSandbox: isSandbox)
            let unconfigured = available.filter { !alreadyConfigured.contains($0) }

            LazyVGrid(columns: exchangeColumns, spacing: Spacing.md) {
                ForEach(unconfigured) { exchange in
                    exchangeGridItem(exchange)
                }
            }
        }
    }

    private func exchangeGridItem(_ exchange: Exchange) -> some View {
        let isSelected = selectedExchange == exchange
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectExchange(exchange)
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
                    .foregroundColor(colors.onSurface)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.xs)
            .background(isSelected ? colors.primary.opacity(0.15) : colors.surface)
            .cornerRadius(CornerRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(isSelected ? colors.primary : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Exchange Info Header

    private func exchangeInfoHeader(_ exchange: Exchange) -> some View {
        HStack(spacing: Spacing.md) {
            Image(exchange.logoName)
                .resizable()
                .scaledToFit()
                .frame(width: 48, height: 48)
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(exchange.displayName)
                    .font(AccBotFonts.titleSmall)
                    .foregroundColor(colors.onSurface)

                HStack(spacing: Spacing.xs) {
                    Text(String(localized: "Pairs:"))
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.onSurfaceVariant)
                    Text(exchange.supportedCryptos.joined(separator: ", "))
                        .font(AccBotFonts.caption)
                        .foregroundColor(colors.primary)
                }
            }

            Spacer()
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Credentials Section

    private func credentialsSection(_ exchange: Exchange) -> some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(String(localized: "API Credentials"))
                .font(AccBotFonts.headline)
                .foregroundColor(colors.onSurface)

            credentialField(
                label: String(localized: "API Key"),
                text: $apiKey,
                placeholder: String(localized: "Enter your API key"),
                isSecure: false
            )

            credentialField(
                label: String(localized: "API Secret"),
                text: $apiSecret,
                placeholder: String(localized: "Enter your API secret"),
                isSecure: true
            )

            if exchange.requiresPassphrase {
                credentialField(
                    label: String(localized: "Passphrase"),
                    text: $passphrase,
                    placeholder: String(localized: "Enter your passphrase"),
                    isSecure: true
                )
            }

            if exchange.requiresClientId {
                credentialField(
                    label: String(localized: "Client ID"),
                    text: $clientId,
                    placeholder: String(localized: "Enter your client ID"),
                    isSecure: false
                )
            }

            // Validate & Save button
            Button {
                Task { await validateAndSave() }
            } label: {
                HStack(spacing: Spacing.sm) {
                    if isValidating {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: colors.background))
                            .scaleEffect(0.8)
                    }
                    Text(isValidating
                         ? String(localized: "Validating...")
                         : String(localized: "Validate & Save"))
                        .font(AccBotFonts.headline)
                }
                .foregroundColor(colors.background)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.lg)
                .background(canValidate ? colors.primary : colors.primary.opacity(0.4))
                .cornerRadius(CornerRadius.md)
            }
            .disabled(!canValidate)
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .cornerRadius(CornerRadius.md)
    }

    private func credentialField(
        label: String,
        text: Binding<String>,
        placeholder: String,
        isSecure: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.caption)
                .foregroundColor(colors.onSurfaceVariant)

            Group {
                if isSecure {
                    SecureField(placeholder, text: text)
                } else {
                    TextField(placeholder, text: text)
                }
            }
            .font(AccBotFonts.mono)
            .foregroundColor(colors.onSurface)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .padding(Spacing.md)
            .background(colors.surfaceVariant.opacity(0.3))
            .cornerRadius(CornerRadius.sm)
        }
    }

    // MARK: - Status Banners

    private func errorBanner(_ message: String) -> some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(colors.error)
            Text(message)
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.error)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.error.opacity(0.1))
        .cornerRadius(CornerRadius.sm)
    }

    private var successBanner: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(colors.success)
            Text(String(localized: "Credentials validated and saved successfully!"))
                .font(AccBotFonts.bodySmall)
                .foregroundColor(colors.success)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.success.opacity(0.1))
        .cornerRadius(CornerRadius.sm)
    }

    // MARK: - Actions

    private func selectExchange(_ exchange: Exchange) {
        if selectedExchange == exchange { return }
        selectedExchange = exchange
        apiKey = ""
        apiSecret = ""
        passphrase = ""
        clientId = ""
        validationError = nil
        isValid = false
    }

    private func validateAndSave() async {
        guard let exchange = currentExchange else { return }

        isValidating = true
        validationError = nil
        isValid = false

        let credentials = ExchangeCredentials(
            exchange: exchange,
            apiKey: apiKey.trimmingCharacters(in: .whitespaces),
            apiSecret: apiSecret.trimmingCharacters(in: .whitespaces),
            passphrase: exchange.requiresPassphrase
                ? passphrase.trimmingCharacters(in: .whitespaces)
                : nil,
            clientId: exchange.requiresClientId
                ? clientId.trimmingCharacters(in: .whitespaces)
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
                isValid = true

                // Dismiss after a brief delay to show success message
                try? await Task.sleep(nanoseconds: 800_000_000)
                router.pop()
            } else {
                validationError = String(localized: "Invalid credentials. Please check your API key and secret.")
            }
        } catch {
            validationError = error.localizedDescription
        }

        isValidating = false
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        AddExchangeView(preselectedExchange: .binance)
    }
    .preferredColorScheme(.dark)
}
