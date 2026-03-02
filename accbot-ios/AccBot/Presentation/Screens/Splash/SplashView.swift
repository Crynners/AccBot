import SwiftUI

/// Splash screen shown on app launch with animated AccBot branding.
struct SplashView: View {
    let onFinished: () -> Void

    @State private var logoScale: CGFloat = 0.5
    @State private var subtitleOpacity: Double = 0.0
    @Environment(\.accBotColors) private var colors
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            VStack(spacing: Spacing.lg) {
                Text(String(localized: "AccBot DCA"))
                    .font(AccBotFonts.displayLarge)
                    .foregroundStyle(colors.primary)
                    .scaleEffect(logoScale)

                Text(String(localized: "Stack Sats. Stay Humble."))
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .opacity(subtitleOpacity)
            }
        }
        .onAppear {
            if reduceMotion {
                logoScale = 1.0
                subtitleOpacity = 1.0
            } else {
                withAnimation(.spring(response: 0.6, dampingFraction: 0.6)) {
                    logoScale = 1.0
                }
                withAnimation(.easeIn(duration: 0.8).delay(0.4)) {
                    subtitleOpacity = 1.0
                }
            }
        }
        .task {
            let delay: Double = reduceMotion ? 0.5 : 1.5
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            onFinished()
        }
    }
}
