import SwiftUI

/// Splash screen shown on app launch with animated AccBot branding.
struct SplashView: View {
    let onFinished: () -> Void

    @State private var logoScale: CGFloat = 0.5
    @State private var subtitleOpacity: Double = 0.0

    var body: some View {
        ZStack {
            Color.backgroundDark
                .ignoresSafeArea()

            VStack(spacing: Spacing.lg) {
                Text("AccBot")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(.accentTeal)
                    .scaleEffect(logoScale)

                Text("Self-Custody DCA")
                    .font(AccBotFonts.titleSmall)
                    .foregroundColor(.white.opacity(0.7))
                    .opacity(subtitleOpacity)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.6)) {
                logoScale = 1.0
            }
            withAnimation(.easeIn(duration: 0.8).delay(0.4)) {
                subtitleOpacity = 1.0
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                onFinished()
            }
        }
    }
}
