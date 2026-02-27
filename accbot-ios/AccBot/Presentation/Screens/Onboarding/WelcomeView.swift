import SwiftUI

/// First onboarding screen with app branding and feature overview.
struct WelcomeView: View {
    let onNext: () -> Void

    @State private var titleOpacity: Double = 0
    @State private var card1Opacity: Double = 0
    @State private var card2Opacity: Double = 0
    @State private var card3Opacity: Double = 0
    @State private var buttonOpacity: Double = 0
    @Environment(\.accBotColors) private var colors
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            VStack(spacing: Spacing.xxxl) {
                Spacer()

                // App branding
                VStack(spacing: Spacing.sm) {
                    Text("AccBot")
                        .font(AccBotFonts.titleLarge)
                        .foregroundStyle(colors.primary)

                    Text(String(localized: "Your Self-Custody DCA Companion"))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                .opacity(titleOpacity)

                // Feature cards
                VStack(spacing: Spacing.lg) {
                    FeatureCard(
                        icon: "arrow.triangle.2.circlepath",
                        title: String(localized: "Auto DCA"),
                        description: String(localized: "Automatically buy crypto at regular intervals with dollar cost averaging.")
                    )
                    .opacity(card1Opacity)

                    FeatureCard(
                        icon: "shield.lefthalf.filled",
                        title: String(localized: "Self-Custody"),
                        description: String(localized: "Your keys, your crypto. All credentials stored locally on your device.")
                    )
                    .opacity(card2Opacity)

                    FeatureCard(
                        icon: "bitcoinsign.circle",
                        title: String(localized: "Stack Sats"),
                        description: String(localized: "Accumulate Bitcoin and other cryptos across multiple exchanges.")
                    )
                    .opacity(card3Opacity)
                }

                Spacer()

                // Get Started button
                Button(action: onNext) {
                    Text(String(localized: "Get Started"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(colors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                }
                .opacity(buttonOpacity)
                .padding(.bottom, Spacing.xxl)
            }
            .padding(.horizontal, Spacing.xxl)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            if reduceMotion {
                titleOpacity = 1.0
                card1Opacity = 1.0
                card2Opacity = 1.0
                card3Opacity = 1.0
                buttonOpacity = 1.0
            } else {
                withAnimation(.easeIn(duration: 0.4)) {
                    titleOpacity = 1.0
                }
                withAnimation(.easeIn(duration: 0.4).delay(0.2)) {
                    card1Opacity = 1.0
                }
                withAnimation(.easeIn(duration: 0.4).delay(0.4)) {
                    card2Opacity = 1.0
                }
                withAnimation(.easeIn(duration: 0.4).delay(0.6)) {
                    card3Opacity = 1.0
                }
                withAnimation(.easeIn(duration: 0.4).delay(0.8)) {
                    buttonOpacity = 1.0
                }
            }
        }
    }
}

// MARK: - Feature Card Component

private struct FeatureCard: View {
    let icon: String
    let title: String
    let description: String

    @Environment(\.accBotColors) private var colors

    var body: some View {
        HStack(spacing: Spacing.lg) {
            ZStack {
                Circle()
                    .fill(colors.primary.opacity(0.15))
                    .frame(width: 48, height: 48)
                Image(systemName: icon)
                    .font(AccBotFonts.titleMedium)
                    .foregroundStyle(colors.primary)
                    .accessibilityHidden(true)
            }
            .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onSurface)

                Text(description)
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        .accessibilityElement(children: .combine)
    }
}
