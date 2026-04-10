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
                // Connected connections
                connectedSection

                // Available exchanges (always shown)
                availableSection

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

                Text("\(viewModel.connectedConnections.count)")
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
            }

            if viewModel.connectedConnections.isEmpty {
                EmptyStateView(
                    systemImage: "link.badge.plus",
                    title: String(localized: "No Exchanges Connected"),
                    subtitle: String(localized: "Connect an exchange to start your DCA journey.")
                )
                .frame(height: 150)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: Spacing.sm)], spacing: Spacing.sm) {
                    ForEach(viewModel.connectedConnections) { connection in
                        ConnectionTile(
                            connection: connection,
                            colors: colors,
                            onTap: {
                                router.navigate(to: .exchangeDetail(connection.exchange, connection.id))
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
            Text(String(localized: "Add Connection"))
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(colors.onSurface)

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: Spacing.sm)], spacing: Spacing.sm) {
                ForEach(viewModel.availableExchanges) { exchange in
                    ExchangeTile(
                        exchange: exchange,
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

// MARK: - Connection Tile (Grid item for connected connections)

private struct ConnectionTile: View {
    let connection: ExchangeConnection
    let colors: AccBotColors
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.sm) {
                Image(connection.exchange.logoName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 48, height: 48)
                    .clipShape(Circle())

                Text(connection.displayLabel)
                    .font(AccBotFonts.label)
                    .foregroundStyle(colors.onSurface)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.success)
                        .accessibilityHidden(true)
                    Text(String(localized: "Connected"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.success)
                        .lineLimit(1)
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

// MARK: - Exchange Tile (Grid item for available exchanges)

private struct ExchangeTile: View {
    let exchange: Exchange
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
                    Text(String(localized: "\(exchange.supportedCryptos.count) cryptos"))
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .lineLimit(1)
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
