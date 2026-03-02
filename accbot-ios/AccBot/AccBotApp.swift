import SwiftUI

/// App entry point
@main
struct AccBotApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var dependencies = {
        let deps = AppDependencies()
        AppDependencies.shared = deps
        return deps
    }()
    @StateObject private var router = AppRouter()
    @Environment(\.scenePhase) var scenePhase

    var body: some Scene {
        WindowGroup {
            ColorSchemeResolver {
                RootView()
            }
            .environmentObject(dependencies)
            .environmentObject(router)
            .environment(\.isSandboxMode, dependencies.userPreferences.sandboxMode)
            .preferredColorScheme(colorScheme)
            .onOpenURL { url in
                router.handleDeepLink(url)
            }
            .onChange(of: scenePhase) { newPhase in
                if newPhase == .active {
                    // Layer 4: Foreground catch-up - execute due plans on app open
                    // Throttle: skip if last execution was less than 5 minutes ago
                    let now = Date()
                    if let last = dependencies.userPreferences.lastBackgroundRun,
                       now.timeIntervalSince(last) < 300 {
                        return
                    }
                    Task {
                        await dependencies.dcaExecutionEngine.executeDuePlans()
                        await MainActor.run {
                            dependencies.userPreferences.lastBackgroundRun = now
                        }
                    }
                }
            }
        }
    }

    private var colorScheme: ColorScheme? {
        switch dependencies.userPreferences.appTheme {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }
}

/// Resolves colorScheme from environment and injects AccBotColors
struct ColorSchemeResolver<Content: View>: View {
    @EnvironmentObject var dependencies: AppDependencies
    @Environment(\.colorScheme) private var colorScheme
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .environment(\.accBotColors, AccBotColors(
                isSandbox: dependencies.userPreferences.sandboxMode,
                isDark: colorScheme == .dark
            ))
    }
}

/// Root view that decides between onboarding and main app
struct RootView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @State private var showSplash = true

    var body: some View {
        ZStack {
            if showSplash {
                SplashView(onFinished: {
                    withAnimation(.easeInOut(duration: 0.5)) {
                        showSplash = false
                    }
                })
            } else if dependencies.onboardingPreferences.onboardingCompleted {
                if dependencies.userPreferences.biometricLockEnabled {
                    BiometricLockView {
                        MainTabView()
                    }
                } else {
                    MainTabView()
                }
            } else {
                OnboardingFlowView()
            }
        }
    }
}

/// Onboarding navigation flow
struct OnboardingFlowView: View {
    @State private var path = NavigationPath()
    @Environment(\.accBotColors) private var colors

    /// Current step index (0-based) derived from the navigation path depth
    private var currentStep: Int { path.count }
    private let totalSteps = 5

    var body: some View {
        NavigationStack(path: $path) {
            WelcomeView(onNext: { path.append(OnboardingStep.security) })
                .navigationDestination(for: OnboardingStep.self) { step in
                    switch step {
                    case .security:
                        SecurityView(onNext: { path.append(OnboardingStep.exchangeSetup) })
                    case .exchangeSetup:
                        ExchangeSetupView(
                            onNext: { path.append(OnboardingStep.firstPlan) },
                            onSkip: { path.append(OnboardingStep.complete) }
                        )
                    case .firstPlan:
                        FirstPlanView(onNext: { path.append(OnboardingStep.complete) })
                    case .complete:
                        CompletionView()
                    }
                }
                .safeAreaInset(edge: .top) {
                    onboardingProgressBar
                }
        }
    }

    private var onboardingProgressBar: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(0..<totalSteps, id: \.self) { index in
                Capsule()
                    .fill(index <= currentStep ? colors.primary : colors.onSurfaceVariant.opacity(0.3))
                    .frame(height: 4)
                    .animation(.easeInOut(duration: 0.3), value: currentStep)
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.xs)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(String(localized: "Onboarding step \(currentStep + 1) of \(totalSteps)"))
    }
}

enum OnboardingStep: Hashable {
    case security
    case exchangeSetup
    case firstPlan
    case complete
}
