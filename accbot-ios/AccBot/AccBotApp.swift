import SwiftUI

/// App entry point
@main
struct AccBotApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var dependencies = AppDependencies()
    @StateObject private var router = AppRouter()
    @Environment(\.scenePhase) var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(dependencies)
                .environmentObject(router)
                .environment(\.isSandboxMode, dependencies.userPreferences.sandboxMode)
                .preferredColorScheme(colorScheme)
                .onChange(of: scenePhase) { newPhase in
                    if newPhase == .active {
                        // Layer 4: Foreground catch-up - execute due plans on app open
                        Task {
                            await dependencies.dcaExecutionEngine.executeDuePlans()
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

    var body: some View {
        NavigationStack(path: $path) {
            WelcomeView(onNext: { path.append(OnboardingStep.security) })
                .navigationDestination(for: OnboardingStep.self) { step in
                    switch step {
                    case .security:
                        SecurityView(onNext: { path.append(OnboardingStep.exchangeSetup) })
                    case .exchangeSetup:
                        ExchangeSetupView(onNext: { path.append(OnboardingStep.firstPlan) })
                    case .firstPlan:
                        FirstPlanView(onNext: { path.append(OnboardingStep.complete) })
                    case .complete:
                        CompletionView()
                    }
                }
        }
    }
}

enum OnboardingStep: Hashable {
    case security
    case exchangeSetup
    case firstPlan
    case complete
}
