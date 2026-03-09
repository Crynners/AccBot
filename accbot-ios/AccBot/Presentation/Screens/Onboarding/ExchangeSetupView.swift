import SwiftUI
import UIKit

/// Exchange selection and credential input during onboarding.
struct ExchangeSetupView: View {
    let onNext: () -> Void
    var onSkip: (() -> Void)?

    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors
    @StateObject private var viewModel = ExchangeSetupViewModel()
    @State private var showSkipConfirmation = false

    private let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.md), count: 3)

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: Spacing.xxl) {
                    // Header
                    VStack(spacing: Spacing.sm) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(AccBotFonts.displayLarge)
                            .foregroundStyle(colors.primary)
                            .accessibilityHidden(true)

                        Text(String(localized: "Connect Your Exchange"))
                            .font(AccBotFonts.titleLarge)
                            .foregroundStyle(colors.onSurface)

                        Text(String(localized: "Select an exchange and enter your API credentials to get started."))
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.onSurfaceVariant)
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
                                    Text(String(localized: "Continue"))
                                }
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.onPrimary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, Spacing.lg)
                                .background(colors.primary)
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                            }
                        }

                        Button { showSkipConfirmation = true } label: {
                            Text(String(localized: "Skip for Now"))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.primary)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, Spacing.lg)
                                .background(colors.surface)
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                                .overlay(
                                    RoundedRectangle(cornerRadius: CornerRadius.md)
                                        .stroke(colors.primary.opacity(0.3), lineWidth: 1)
                                )
                        }
                        .alert(String(localized: "Skip Exchange Setup?"), isPresented: $showSkipConfirmation) {
                            Button(String(localized: "Cancel"), role: .cancel) {}
                            Button(String(localized: "Skip")) { (onSkip ?? onNext)() }
                        } message: {
                            Text(String(localized: "You can add exchanges later in Settings."))
                        }
                    }
                    .padding(.bottom, Spacing.xxl)
                }
                .padding(.horizontal, Spacing.xxl)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Exchange Grid Item

private struct ExchangeGridItem: View {
    let exchange: Exchange
    let isSelected: Bool
    let onTap: () -> Void
    @Environment(\.accBotColors) private var colors

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
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.md)
            .padding(.horizontal, Spacing.xs)
            .background(isSelected ? colors.primary.opacity(0.15) : colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.md)
                    .stroke(isSelected ? colors.primary : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(exchange.displayName)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
        .accessibilityValue(isSelected ? String(localized: "Selected") : String(localized: "Not selected"))
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
    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text(String(localized: "\(exchange.displayName) API Credentials"))
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.onSurface)

            CredentialField(label: String(localized: "API Key"), text: $apiKey, placeholder: String(localized: "Enter your API key"))
            CredentialField(label: String(localized: "API Secret"), text: $apiSecret, placeholder: String(localized: "Enter your API secret"), isSecure: true)

            if exchange.requiresPassphrase {
                CredentialField(label: String(localized: "Passphrase"), text: $passphrase, placeholder: String(localized: "Enter your passphrase"), isSecure: true)
            }

            if exchange.requiresClientId {
                CredentialField(label: String(localized: "Client ID"), text: $clientId, placeholder: String(localized: "Enter your client ID"))
            }

            // Validation status
            if let error = validationError {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(colors.error)
                        .accessibilityHidden(true)
                    Text(error)
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.error)
                }
            }

            if isValid {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(colors.primary)
                    Text(String(localized: "Credentials validated successfully"))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.primary)
                }
            }

            // Validate button
            Button(action: onValidate) {
                HStack(spacing: Spacing.sm) {
                    if isValidating {
                        ProgressView()
                            .tint(colors.onPrimary)
                    }
                    Text(isValidating ? String(localized: "Validating...") : String(localized: "Validate & Connect"))
                }
                .font(AccBotFonts.headline)
                .foregroundStyle(canValidate ? colors.onPrimary : colors.disabledForeground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(canValidate ? colors.primary : colors.disabledBackground)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
            .disabled(!canValidate)
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private var canValidate: Bool {
        !isValidating &&
        !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !apiSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (!exchange.requiresPassphrase || !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) &&
        (!exchange.requiresClientId || !clientId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }
}

// MARK: - Credential Field

private struct CredentialField: View {
    let label: String
    @Binding var text: String
    let placeholder: String
    var isSecure: Bool = false
    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(label)
                .font(AccBotFonts.label)
                .foregroundStyle(colors.onSurfaceVariant)

            if isSecure {
                SecureField(placeholder, text: $text)
                    .font(AccBotFonts.mono)
                    .foregroundStyle(colors.onSurface)
                    .padding(Spacing.md)
                    .background(colors.background)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } else {
                TextField(placeholder, text: $text)
                    .font(AccBotFonts.mono)
                    .foregroundStyle(colors.onSurface)
                    .padding(Spacing.md)
                    .background(colors.background)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
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
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            apiSecret: apiSecret.trimmingCharacters(in: .whitespacesAndNewlines),
            passphrase: exchange.requiresPassphrase ? passphrase.trimmingCharacters(in: .whitespacesAndNewlines) : nil,
            clientId: exchange.requiresClientId ? clientId.trimmingCharacters(in: .whitespacesAndNewlines) : nil
        )

        do {
            let api = exchangeApiFactory.create(credentials: credentials, isSandbox: isSandbox)
            let valid = try await api.validateCredentials()
            if valid {
                try credentialsStore.save(credentials, isSandbox: isSandbox)
                isValid = true
            } else {
                let errorMessage = String(localized: "Invalid credentials. Please verify your API key, secret, and required permissions (Read + Trade).")
                validationError = errorMessage
                UIAccessibility.post(notification: .announcement, argument: errorMessage)
            }
        } catch {
            let errorMessage = error.localizedDescription
            validationError = errorMessage
            UIAccessibility.post(notification: .announcement, argument: errorMessage)
        }

        isValidating = false
    }
}
