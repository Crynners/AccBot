import SwiftUI
import UIKit

/// App entry point
@main
struct AccBotApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var dependencies = AppDependencies.shared
    @StateObject private var router = AppRouter()

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
    @Environment(\.scenePhase) private var scenePhase
    @State private var showSplash = true
    @State private var isUnlocked = false

    private var foregroundExecutionMode: Bool? {
        guard scenePhase == .active, !showSplash, dependencies.onboardingPreferences.onboardingCompleted,
              !dependencies.userPreferences.biometricLockEnabled || isUnlocked else { return nil }
        return dependencies.userPreferences.sandboxMode
    }

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
                    BiometricLockView(isUnlocked: $isUnlocked) {
                        MainTabView()
                            .id(dependencies.userPreferences.sandboxMode)
                    }
                } else {
                    MainTabView()
                        .id(dependencies.userPreferences.sandboxMode)
                }
            } else {
                OnboardingFlowView()
            }
        }
        .background { AppSnapshotCover() }
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                isUnlocked = false
                DcaBackgroundService.shared.scheduleNext(using: dependencies)
            }
        }
        .onChange(of: dependencies.userPreferences.biometricLockEnabled) { _ in
            isUnlocked = false
        }
        .task(id: foregroundExecutionMode) {
            if foregroundExecutionMode != nil {
                await dependencies.dcaExecutionEngine.executeDuePlans()
            }
        }
    }
}

/// Hides the window, including any presented sheets, in the app switcher snapshot.
private struct AppSnapshotCover: UIViewRepresentable {
    func makeUIView(context: Context) -> WindowObserver { WindowObserver() }
    func updateUIView(_ view: WindowObserver, context: Context) {}

    final class WindowObserver: UIView {
        private let cover = UIView()

        override func didMoveToWindow() {
            super.didMoveToWindow()
            NotificationCenter.default.removeObserver(self)
            cover.removeFromSuperview()
            guard let scene = window?.windowScene else { return }

            NotificationCenter.default.addObserver(self, selector: #selector(showCover(_:)),
                                                   name: UIScene.willDeactivateNotification, object: scene)
            NotificationCenter.default.addObserver(self, selector: #selector(hideCover(_:)),
                                                   name: UIScene.didActivateNotification, object: scene)
            if scene.activationState != .foregroundActive { showCover() }
        }

        @objc private func showCover(_ notification: Notification? = nil) {
            guard let window else { return }
            cover.frame = window.bounds
            cover.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            cover.backgroundColor = .systemBackground
            cover.accessibilityViewIsModal = true
            window.addSubview(cover)
        }

        @objc private func hideCover(_ notification: Notification) { cover.removeFromSuperview() }
    }
}

/// Onboarding navigation flow
struct OnboardingFlowView: View {
    @State private var path = NavigationPath()
    @Environment(\.accBotColors) private var colors

    /// Current step index (0-based) derived from the navigation path depth
    private var currentStep: Int { path.count }
    private let totalSteps = 6

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
                        FirstPlanView(onNext: { path.append(OnboardingStep.permissions) })
                    case .permissions:
                        PermissionsView(onNext: { path.append(OnboardingStep.complete) })
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
    case permissions
    case complete
}
