import SwiftUI

struct ExchangeManagementView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = ExchangeManagementViewModel()

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
                        colors: colors,
                        onTap: {
                            router.navigate(to: .addExchange(exchange))
                        }
                    )
                }
            }
        }
    }
}

// MARK: - Exchange Tile (Grid item)

private struct ExchangeTile: View {
    let exchange: Exchange
    let isConnected: Bool
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
