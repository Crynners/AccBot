import SwiftUI

/// Exchange selection and credential input during onboarding.
struct ExchangeSetupView: View {
    let onNext: () -> Void

    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel = ExchangeSetupViewModel()

    private let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.md), count: 3)

    var body: some View {
        ZStack {
            Color.backgroundDark
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: Spacing.xxl) {
                    // Header
                    VStack(spacing: Spacing.sm) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.system(size: 48))
                            .foregroundColor(.accentTeal)

                        Text("Connect Your Exchange")
                            .font(AccBotFonts.titleLarge)
                            .foregroundColor(.white)

                        Text("Select an exchange and enter your API credentials to get started.")
                            .font(AccBotFonts.body)
                            .foregroundColor(.white.opacity(0.7))
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, Spacing.xxl)

                    // Exchange grid
                    LazyVGrid(columns: columns, spacing: Spacing.md) {
                        ForEach(Exchange.allCases) { exchange in
                            ExchangeGridItem(
                                exchange: exchange,
                                isSelected: viewModel.selectedExchange == exchange,
                                onTap: {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        viewModel.selectExchange(exchange)
                                    }
                                }
                            )
                        }
                    }
                    .padding(.horizontal, Spacing.sm)

                    // Credentials input card
                    if let exchange = viewModel.selectedExchange {
                        OnboardingCredentialsCard(
                            exchange: exchange,
                            apiKey: $viewModel.apiKey,
                            apiSecret: $viewModel.apiSecret,
                            passphrase: $viewModel.passphrase,
                            clientId: $viewModel.clientId,
                            isValidating: viewModel.isValidating,
                            validationError: viewModel.validationError,
                            isValid: viewModel.isValid,
                            onValidate: {
                                Task {
                                    await viewModel.validateAndSave(
                                        credentialsStore: dependencies.credentialsStore,
                                        exchangeApiFactory: dependencies.exchangeApiFactory,
                                        isSandbox: dependencies.userPreferences.sandboxMode
                                    )
                                }
                            }
                        )
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                    }

                    Spacer(minLength: Spacing.xxl)

                    // Action buttons
                    VStack(spacing: Spacing.md) {
                        if viewModel.isValid {
                            Button(action: onNext) {
                                HStack(spacing: Spacing.sm) {
                                    Image(systemName: "checkmark.circle.fill")
                                    Text("Continue")
                                }
                                .font(AccBotFonts.headline)
                                .foregroundColor(.backgroundDark)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, Spacing.lg)
                                .background(Color.accentTeal)
                                .cornerRadius(CornerRadius.md)
                            }
                        }

                        Button(action: onNext) {
                            Text("Skip for Now")
                                .font(AccBotFonts.headline)
                                .foregroundColor(.accentTeal)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, Spacing.lg)
                                .background(Color.surfaceDark)
                                .cornerRadius(CornerRadius.md)
                                .overlay(
                                    RoundedRectangle(cornerRadius: CornerRadius.md)
                                        .stroke(Color.accentTeal.opacity(0.3), lineWidth: 1)
                                )
                        }
                    }
                    .padding(.bottom, Spacing.xxl)
                }
                .padding(.horizontal, Spacing.xxl)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Exchange Grid Item

private struct ExchangeGridItem: View {
    let exchange: Exchange
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.sm) {
                Image(exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 40, height: 40)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))

                Text(exchange.displayName)
                    .font(AccBotFonts.caption)
                    .foregroundColor(.white)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.xs)
            .background(isSelected ? Color.accentTeal.opacity(0.15) : Color.surfaceDark)
            .cornerRadius(CornerRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(isSelected ? Color.accentTeal : Color.clear, lineWidth: 2)
            )
        }
    }
}

// MARK: - Credentials Input Card

private struct OnboardingCredentialsCard: View {
    let exchange: Exchange
    @Binding var apiKey: String
    @Binding var apiSecret: String
    @Binding var passphrase: String
    @Binding var clientId: String
    let isValidating: Bool
    let validationError: String?
    let isValid: Bool
    let onValidate: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("\(exchange.displayName) API Credentials")
                .font(AccBotFonts.titleSmall)
                .foregroundColor(.white)

            CredentialField(label: "API Key", text: $apiKey, placeholder: "Enter your API key")
            CredentialField(label: "API Secret", text: $apiSecret, placeholder: "Enter your API secret", isSecure: true)

            if exchange.requiresPassphrase {
                CredentialField(label: "Passphrase", text: $passphrase, placeholder: "Enter your passphrase", isSecure: true)
            }

            if exchange.requiresClientId {
                CredentialField(label: "Client ID", text: $clientId, placeholder: "Enter your client ID")
            }

            // Validation status
            if let error = validationError {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.errorRed)
                    Text(error)
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(.errorRed)
                }
            }

            if isValid {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.accentTeal)
                    Text("Credentials validated successfully")
                        .font(AccBotFonts.bodySmall)
                        .foregroundColor(.accentTeal)
                }
            }

            // Validate button
            Button(action: onValidate) {
                HStack(spacing: Spacing.sm) {
                    if isValidating {
                        ProgressView()
                            .tint(.backgroundDark)
                    }
                    Text(isValidating ? "Validating..." : "Validate & Connect")
                }
                .font(AccBotFonts.headline)
                .foregroundColor(.backgroundDark)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(canValidate ? Color.accentTeal : Color.accentTeal.opacity(0.4))
                .cornerRadius(CornerRadius.md)
            }
            .disabled(!canValidate)
        }
        .padding(Spacing.lg)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.md)
    }

    private var canValidate: Bool {
        !isValidating &&
        !apiKey.trimmingCharacters(in: .whitespaces).isEmpty &&
        !apiSecret.trimmingCharacters(in: .whitespaces).isEmpty &&
        (!exchange.requiresPassphrase || !passphrase.trimmingCharacters(in: .whitespaces).isEmpty) &&
        (!exchange.requiresClientId || !clientId.trimmingCharacters(in: .whitespaces).isEmpty)
    }
}

// MARK: - Credential Field

private struct CredentialField: View {
    let label: String
    @Binding var text: String
    let placeholder: String
    var isSecure: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.label)
                .foregroundColor(.white.opacity(0.7))

            if isSecure {
                SecureField(placeholder, text: $text)
                    .font(AccBotFonts.mono)
                    .foregroundColor(.white)
                    .padding(Spacing.md)
                    .background(Color.backgroundDark)
                    .cornerRadius(CornerRadius.sm)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } else {
                TextField(placeholder, text: $text)
                    .font(AccBotFonts.mono)
                    .foregroundColor(.white)
                    .padding(Spacing.md)
                    .background(Color.backgroundDark)
                    .cornerRadius(CornerRadius.sm)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
        }
    }
}

// MARK: - Exchange Setup ViewModel

@MainActor
private class ExchangeSetupViewModel: ObservableObject {
    @Published var selectedExchange: Exchange?
    @Published var apiKey = ""
    @Published var apiSecret = ""
    @Published var passphrase = ""
    @Published var clientId = ""
    @Published var isValidating = false
    @Published var validationError: String?
    @Published var isValid = false

    func selectExchange(_ exchange: Exchange) {
        if selectedExchange == exchange {
            return
        }
        selectedExchange = exchange
        apiKey = ""
        apiSecret = ""
        passphrase = ""
        clientId = ""
        validationError = nil
        isValid = false
    }

    func validateAndSave(
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        isSandbox: Bool
    ) async {
        guard let exchange = selectedExchange else { return }

        isValidating = true
        validationError = nil
        isValid = false

        let credentials = ExchangeCredentials(
            exchange: exchange,
            apiKey: apiKey.trimmingCharacters(in: .whitespaces),
            apiSecret: apiSecret.trimmingCharacters(in: .whitespaces),
            passphrase: exchange.requiresPassphrase ? passphrase.trimmingCharacters(in: .whitespaces) : nil,
            clientId: exchange.requiresClientId ? clientId.trimmingCharacters(in: .whitespaces) : nil
        )

        do {
            let api = exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
            let valid = try await api.validateCredentials()
            if valid {
                try credentialsStore.save(credentials, isSandbox: isSandbox)
                isValid = true
            } else {
                validationError = "Invalid credentials. Please check your API key and secret."
            }
        } catch {
            validationError = error.localizedDescription
        }

        isValidating = false
    }
}
