import SwiftUI

/// First onboarding screen with app branding and feature overview.
struct WelcomeView: View {
    let onNext: () -> Void

    var body: some View {
        ZStack {
            Color.backgroundDark
                .ignoresSafeArea()

            VStack(spacing: Spacing.xxxl) {
                Spacer()

                // App branding
                VStack(spacing: Spacing.sm) {
                    Text("AccBot")
                        .font(.system(size: 40, weight: .bold))
                        .foregroundColor(.accentTeal)

                    Text("Your Self-Custody DCA Companion")
                        .font(AccBotFonts.body)
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }

                // Feature cards
                VStack(spacing: Spacing.lg) {
                    FeatureCard(
                        icon: "timer",
                        title: "Auto DCA",
                        description: "Automatically buy crypto at regular intervals with dollar cost averaging."
                    )
                    FeatureCard(
                        icon: "lock.fill",
                        title: "Self-Custody",
                        description: "Your keys, your crypto. All credentials stored locally on your device."
                    )
                    FeatureCard(
                        icon: "bitcoinsign.circle",
                        title: "Stack Sats",
                        description: "Accumulate Bitcoin and other cryptos across multiple exchanges."
                    )
                }

                Spacer()

                // Get Started button
                Button(action: onNext) {
                    Text("Get Started")
                        .font(AccBotFonts.headline)
                        .foregroundColor(.backgroundDark)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(Color.accentTeal)
                        .cornerRadius(CornerRadius.md)
                }
                .padding(.bottom, Spacing.xxl)
            }
            .padding(.horizontal, Spacing.xxl)
        }
        .navigationBarHidden(true)
    }
}

// MARK: - Feature Card Component

private struct FeatureCard: View {
    let icon: String
    let title: String
    let description: String

    var body: some View {
        HStack(spacing: Spacing.lg) {
            Image(systemName: icon)
                .font(.system(size: 28))
                .foregroundColor(.accentTeal)
                .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .font(AccBotFonts.headline)
                    .foregroundColor(.white)

                Text(description)
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(.white.opacity(0.6))
                    .lineLimit(2)
            }

            Spacer()
        }
        .padding(Spacing.lg)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.md)
    }
}
