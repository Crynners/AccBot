import SwiftUI

/// Final onboarding screen confirming setup is complete.
struct CompletionView: View {
    @EnvironmentObject var dependencies: AppDependencies

    @State private var checkmarkScale: CGFloat = 0.0
    @State private var checkmarkOpacity: Double = 0.0
    @State private var contentOpacity: Double = 0.0
    @Environment(\.accBotColors) private var colors
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            VStack(spacing: Spacing.xxxl) {
                Spacer()

                // Success checkmark animation
                Image(systemName: "checkmark.circle.fill")
                    .font(AccBotFonts.iconXL)
                    .foregroundStyle(colors.primary)
                    .scaleEffect(checkmarkScale)
                    .opacity(checkmarkOpacity)

                // Title and subtitle
                VStack(spacing: Spacing.sm) {
                    Text(String(localized: "You're All Set!"))
                        .font(AccBotFonts.titleLarge)
                        .foregroundStyle(colors.onSurface)

                    Text(String(localized: "AccBot is ready to start stacking sats for you."))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                .opacity(contentOpacity)

                // Next steps card
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    Text(String(localized: "Next Steps"))
                        .font(AccBotFonts.titleSmall)
                        .foregroundStyle(colors.onSurface)

                    NextStepRow(
                        icon: "play.circle.fill",
                        text: "Start DCA — Run your first purchase"
                    )
                    NextStepRow(
                        icon: "slider.horizontal.3",
                        text: "Fine Tune Settings — Adjust your preferences"
                    )
                    NextStepRow(
                        icon: "bell.fill",
                        text: "Stay Informed — Get alerts on DCA events"
                    )
                }
                .padding(Spacing.lg)
                .background(colors.surface)
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                .opacity(contentOpacity)

                // Pro Tip card
                HStack(alignment: .top, spacing: Spacing.md) {
                    Image(systemName: "lightbulb.fill")
                        .font(AccBotFonts.iconSmall)
                        .foregroundStyle(colors.primary)
                        .frame(width: 24)
                        .accessibilityHidden(true)

                    Text(String(localized: "Pro tip: For reliable DCA execution on iOS, open AccBot at least once a day so background tasks can run on schedule."))
                        .font(AccBotFonts.bodySmall)
                        .foregroundStyle(colors.onSurfaceVariant)
                }
                .accessibilityElement(children: .combine)
                .padding(Spacing.lg)
                .background(colors.primary.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                .opacity(contentOpacity)

                Spacer()

                // Start Stacking button
                Button(action: completeOnboarding) {
                    Text(String(localized: "Start Stacking"))
                        .font(AccBotFonts.headline)
                        .foregroundStyle(colors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(colors.primary)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                }
                .opacity(contentOpacity)
                .padding(.bottom, Spacing.xxl)
            }
            .padding(.horizontal, Spacing.xxl)
        }
        .navigationBarBackButtonHidden(true)
        .onAppear {
            if reduceMotion {
                checkmarkScale = 1.0
                checkmarkOpacity = 1.0
                contentOpacity = 1.0
            } else {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.6).delay(0.2)) {
                    checkmarkScale = 1.0
                    checkmarkOpacity = 1.0
                }
                withAnimation(.easeIn(duration: 0.6).delay(0.6)) {
                    contentOpacity = 1.0
                }
            }
        }
    }

    private func completeOnboarding() {
        dependencies.onboardingPreferences.onboardingCompleted = true
    }
}

// MARK: - Next Step Row

private struct NextStepRow: View {
    let icon: String
    let text: LocalizedStringKey

    @Environment(\.accBotColors) private var colors

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: icon)
                .font(AccBotFonts.iconSmall)
                .foregroundStyle(colors.primary)
                .frame(width: 28)
                .accessibilityHidden(true)

            Text(text)
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)

            Spacer()
        }
        .accessibilityElement(children: .combine)
    }
}
