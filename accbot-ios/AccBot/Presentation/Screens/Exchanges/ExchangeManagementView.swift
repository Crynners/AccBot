import SwiftUI

struct ExchangeManagementView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel: ExchangeManagementViewModel

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    init() {
        _viewModel = StateObject(wrappedValue: ExchangeManagementViewModel(
            dependencies: AppDependencies()
        ))
    }

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
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Exchanges"))
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            viewModel.loadExchanges()
        }
    }

    // MARK: - Connected Section

    private var connectedSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text(String(localized: "Connected"))
                    .font(AccBotFonts.titleSmall)
                    .foregroundColor(colors.onSurface)

                Spacer()

                Text("\(viewModel.connectedExchanges.count)")
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)
            }

            if viewModel.connectedExchanges.isEmpty {
                EmptyStateView(
                    systemImage: "link.badge.plus",
                    title: String(localized: "No Exchanges Connected"),
                    subtitle: String(localized: "Connect an exchange to start your DCA journey.")
                )
                .frame(height: 150)
            } else {
                LazyVStack(spacing: Spacing.sm) {
                    ForEach(viewModel.connectedExchanges) { exchange in
                        ExchangeCard(
                            exchange: exchange,
                            isConnected: true,
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
                .foregroundColor(colors.onSurface)

            LazyVStack(spacing: Spacing.sm) {
                ForEach(viewModel.availableExchanges) { exchange in
                    ExchangeCard(
                        exchange: exchange,
                        isConnected: false,
                        onTap: {
                            router.navigate(to: .addExchange(exchange))
                        }
                    )
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ExchangeManagementView()
    }
    .preferredColorScheme(.dark)
}
