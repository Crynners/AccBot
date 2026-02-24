import SwiftUI

/// Final onboarding screen confirming setup is complete.
struct CompletionView: View {
    @EnvironmentObject var dependencies: AppDependencies

    @State private var checkmarkScale: CGFloat = 0.0
    @State private var checkmarkOpacity: Double = 0.0
    @State private var contentOpacity: Double = 0.0

    var body: some View {
        ZStack {
            Color.backgroundDark
                .ignoresSafeArea()

            VStack(spacing: Spacing.xxxl) {
                Spacer()

                // Success checkmark animation
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 80))
                    .foregroundColor(.accentTeal)
                    .scaleEffect(checkmarkScale)
                    .opacity(checkmarkOpacity)

                // Title and subtitle
                VStack(spacing: Spacing.sm) {
                    Text("You're All Set!")
                        .font(AccBotFonts.titleLarge)
                        .foregroundColor(.white)

                    Text("AccBot is ready to start stacking sats for you.")
                        .font(AccBotFonts.body)
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                }
                .opacity(contentOpacity)

                // Next steps card
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    Text("Next Steps")
                        .font(AccBotFonts.titleSmall)
                        .foregroundColor(.white)

                    NextStepRow(
                        icon: "play.circle.fill",
                        text: "Your DCA plan will execute automatically"
                    )
                    NextStepRow(
                        icon: "iphone",
                        text: "Open the app daily for reliable iOS execution"
                    )
                    NextStepRow(
                        icon: "plus.circle.fill",
                        text: "Add more plans in Dashboard"
                    )
                }
                .padding(Spacing.lg)
                .background(Color.surfaceDark)
                .cornerRadius(CornerRadius.md)
                .opacity(contentOpacity)

                Spacer()

                // Go to Dashboard button
                Button(action: completeOnboarding) {
                    Text("Go to Dashboard")
                        .font(AccBotFonts.headline)
                        .foregroundColor(.backgroundDark)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(Color.accentTeal)
                        .cornerRadius(CornerRadius.md)
                }
                .opacity(contentOpacity)
                .padding(.bottom, Spacing.xxl)
            }
            .padding(.horizontal, Spacing.xxl)
        }
        .navigationBarBackButtonHidden(true)
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.6).delay(0.2)) {
                checkmarkScale = 1.0
                checkmarkOpacity = 1.0
            }
            withAnimation(.easeIn(duration: 0.6).delay(0.6)) {
                contentOpacity = 1.0
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
    let text: String

    var body: some View {
        HStack(spacing: Spacing.md) {
            Image(systemName: icon)
                .font(.system(size: 20))
                .foregroundColor(.accentTeal)
                .frame(width: 28)

            Text(text)
                .font(AccBotFonts.body)
                .foregroundColor(.white.opacity(0.8))

            Spacer()
        }
    }
}
