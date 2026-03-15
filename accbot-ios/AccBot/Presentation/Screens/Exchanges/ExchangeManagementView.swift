import SwiftUI

struct ExchangeManagementView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = ExchangeManagementViewModel()
    @State private var showExperimentalDisclaimer = false

    @Environment(\.accBotColors) private var colors

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xxl) {
                // Connected exchanges
                connectedSection

                // Available exchanges
                if !viewModel.availableExchanges.isEmpty {
                    availableSection
                }

                // Experimental toggle
                experimentalToggle
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
            .maxFormWidth()
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Exchanges"))
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            viewModel.setup(dependencies)
            viewModel.loadExchanges()
        }
    }

    // MARK: - Connected Section

    private var connectedSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text(String(localized: "Connected"))
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.onSurface)

                Spacer()

                Text("\(viewModel.connectedExchanges.count)")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            if viewModel.connectedExchanges.isEmpty {
                EmptyStateView(
                    systemImage: "link.badge.plus",
                    title: String(localized: "No Exchanges Connected"),
                    subtitle: String(localized: "Connect an exchange to start your DCA journey.")
                )
                .frame(height: 150)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: Spacing.sm)], spacing: Spacing.sm) {
                    ForEach(viewModel.connectedExchanges) { exchange in
                        ExchangeTile(
                            exchange: exchange,
                            isConnected: true,
                            colors: colors,
                            onTap: {
                                router.navigate(to: .exchangeDetail(exchange))
                            }
                        )
                    }
                }
            }
        }
    }

    // MARK: - Available Section

    private var availableSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(String(localized: "Available"))
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.onSurface)

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: Spacing.sm)], spacing: Spacing.sm) {
                ForEach(viewModel.availableExchanges) { exchange in
                    ExchangeTile(
                        exchange: exchange,
                        isConnected: false,
                        showExperimentalBadge: !exchange.isStable,
                        colors: colors,
                        onTap: {
                            router.navigate(to: .addExchange(exchange))
                        }
                    )
                }
            }
        }
    }

    // MARK: - Experimental Toggle

    private var experimentalToggle: some View {
        Button {
            if viewModel.showExperimental {
                viewModel.setExperimentalEnabled(false)
            } else {
                showExperimentalDisclaimer = true
            }
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: "flask")
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.warning)

                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(String(localized: "Experimental Exchanges"))
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.onSurface)
                    Text(String(localized: "Show additional exchanges that haven't been fully tested"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }

                Spacer()

                Image(systemName: viewModel.showExperimental ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(viewModel.showExperimental ? colors.primary : colors.onSurfaceVariant)
            }
            .padding(Spacing.md)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .buttonStyle(.plain)
        .alert(String(localized: "Enable Experimental Exchanges?"), isPresented: $showExperimentalDisclaimer) {
            Button(String(localized: "Cancel"), role: .cancel) {}
            Button(String(localized: "Enable")) {
                viewModel.setExperimentalEnabled(true)
            }
        } message: {
            Text(String(localized: "These exchanges haven't been fully tested with AccBot. Use at your own risk. Please report any issues on GitHub."))
        }
    }
}

// MARK: - Exchange Tile (Grid item)

private struct ExchangeTile: View {
    let exchange: Exchange
    let isConnected: Bool
    var showExperimentalBadge: Bool = false
    let colors: AccBotColors
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
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

                if showExperimentalBadge {
                    Text(String(localized: "EXPERIMENTAL"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.warning)
                        .lineLimit(1)
                } else {
                    HStack(spacing: 4) {
                        Image(systemName: isConnected ? "checkmark.circle.fill" : "circle")
                            .font(AccBotFonts.captionSmall)
                            .foregroundStyle(isConnected ? colors.success : colors.onSurfaceVariant)
                            .accessibilityHidden(true)
                        Text(isConnected
                             ? String(localized: "Connected")
                             : String(localized: "\(exchange.supportedCryptos.count) cryptos"))
                            .font(AccBotFonts.captionSmall)
                            .foregroundStyle(isConnected ? colors.success : colors.onSurfaceVariant)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(Spacing.lg)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ExchangeManagementView()
    }
}
