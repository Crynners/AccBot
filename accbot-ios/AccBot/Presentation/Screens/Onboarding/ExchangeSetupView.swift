import SwiftUI
import UIKit

/// Exchange selection and credential input during onboarding.
struct ExchangeSetupView: View {
    let onNext: () -> Void
    var onSkip: (() -> Void)?

    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors
    @StateObject private var credentials = CredentialFormDelegate()
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
                    let availableExchanges = ExchangeFilter.getAvailableExchanges(
                        isSandboxMode: dependencies.userPreferences.sandboxMode,
                        showExperimental: dependencies.userPreferences.showExperimentalExchanges
                    )
                    LazyVGrid(columns: columns, spacing: Spacing.md) {
                        ForEach(availableExchanges) { exchange in
                            ExchangeGridItem(
                                exchange: exchange,
                                isSelected: credentials.selectedExchange == exchange,
                                onTap: {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        credentials.selectExchange(exchange)
                                    }
                                }
                            )
                        }
                    }
                    .padding(.horizontal, Spacing.sm)

                    // Credentials input card
                    if let exchange = credentials.selectedExchange {
                        OnboardingCredentialsCard(
                            exchange: exchange,
                            credentials: credentials,
                            onValidate: {
                                Task {
                                    await credentials.validateAndSave(
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
                        if credentials.isValid {
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
        .onChange(of: credentials.validationError) { newError in
            if let error = newError {
                UIAccessibility.post(notification: .announcement, argument: error)
            }
        }
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
    @ObservedObject var credentials: CredentialFormDelegate
    let onValidate: () -> Void
    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            HStack {
                Text(String(localized: "\(exchange.displayName) API Credentials"))
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.onSurface)

                Spacer()

                // Paste All from clipboard (hidden for Binance - uses QR scan instead)
                if exchange != .binance {
                    Button {
                        credentials.pasteAllCredentials()
                    } label: {
                        HStack(spacing: Spacing.xs) {
                            Image(systemName: "doc.on.clipboard")
                            Text(String(localized: "Paste All"))
                        }
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.primary)
                    }
                    .buttonStyle(.plain)
                }
            }

            CredentialField(label: String(localized: "API Key"), text: $credentials.apiKey, placeholder: String(localized: "Enter your API key"))
            CredentialField(label: String(localized: "API Secret"), text: $credentials.apiSecret, placeholder: String(localized: "Enter your API secret"), isSecure: true)

            if exchange.requiresPassphrase {
                CredentialField(label: String(localized: "Passphrase"), text: $credentials.passphrase, placeholder: String(localized: "Enter your passphrase"), isSecure: true)
            }

            if exchange.requiresClientId {
                CredentialField(label: String(localized: "Client ID"), text: $credentials.clientId, placeholder: String(localized: "Enter your client ID"))
            }

            // Validation status
            if let error = credentials.validationError {
                HStack(spacing: Spacing.sm) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(colors.error)
                        .accessibilityHidden(true)
                    Text(error)
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.error)
                }
            }

            if credentials.isValid {
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
                    if credentials.isValidating {
                        ProgressView()
                            .tint(colors.onPrimary)
                    }
                    Text(credentials.isValidating ? String(localized: "Validating...") : String(localized: "Validate & Connect"))
                }
                .font(AccBotFonts.headline)
                .foregroundStyle(credentials.canValidate ? colors.onPrimary : colors.disabledForeground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(credentials.canValidate ? colors.primary : colors.disabledBackground)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }
            .disabled(!credentials.canValidate)
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
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

            HStack(spacing: 0) {
                Group {
                    if isSecure {
                        SecureField(placeholder, text: $text)
                    } else {
                        TextField(placeholder, text: $text)
                    }
                }
                .font(AccBotFonts.mono)
                .foregroundStyle(colors.onSurface)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

                // Paste (empty) or Clear (non-empty) button
                if text.isEmpty {
                    Button {
                        if let clipboard = UIPasteboard.general.string {
                            text = clipboard.trimmingCharacters(in: .whitespacesAndNewlines)
                        }
                    } label: {
                        Image(systemName: "doc.on.clipboard")
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.primary)
                    }
                    .buttonStyle(.plain)
                    .padding(.leading, Spacing.sm)
                    .accessibilityLabel(String(localized: "Paste"))
                } else {
                    Button {
                        text = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                    .buttonStyle(.plain)
                    .padding(.leading, Spacing.sm)
                    .accessibilityLabel(String(localized: "Clear"))
                }
            }
            .padding(Spacing.md)
            .background(colors.background)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
        }
    }
}
