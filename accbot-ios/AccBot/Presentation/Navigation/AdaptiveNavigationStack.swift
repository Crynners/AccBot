import SwiftUI

// MARK: - Sheet Navigation Wrapper

/// Simple wrapper for sheets: NavigationStack on iOS 16+, NavigationView on iOS 15.
struct SheetNavigationWrapper<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        if #available(iOS 16, *) {
            NavigationStack {
                content()
            }
        } else {
            NavigationView {
                content()
            }
            .navigationViewStyle(.stack)
        }
    }
}

// MARK: - Adaptive Navigation Stack

/// Path-based navigation wrapper used in MainTabView tabs.
/// iOS 16+: uses NavigationStack(path:) with a typed [AppRoute] binding.
/// iOS 15: uses NavigationView + recursive NavigationLink(isActive:).
struct AdaptiveNavigationStack<Root: View>: View {
    @Binding var stack: [AppRoute]
    let routeDestination: (AppRoute) -> AnyView
    @ViewBuilder let root: () -> Root

    var body: some View {
        if #available(iOS 16, *) {
            NavigationStack(path: $stack) {
                root()
                    .navigationDestination(for: AppRoute.self) { route in
                        routeDestination(route)
                    }
            }
        } else {
            NavigationView {
                root()
                    .background(
                        LegacyNavigationLinks(
                            stack: $stack,
                            index: 0,
                            routeDestination: routeDestination
                        )
                    )
            }
            .navigationViewStyle(.stack)
        }
    }
}

// MARK: - Legacy Navigation Links (iOS 15)

/// Recursively creates NavigationLink(isActive:) for each route in the stack,
/// enabling proper back-navigation on iOS 15.
private struct LegacyNavigationLinks: View {
    @Binding var stack: [AppRoute]
    let index: Int
    let routeDestination: (AppRoute) -> AnyView

    private var isActive: Binding<Bool> {
        Binding(
            get: { stack.count > index },
            set: { isActive in
                if !isActive && stack.count > index {
                    stack.removeSubrange(index...)
                }
            }
        )
    }

    var body: some View {
        if index < stack.count {
            NavigationLink(
                destination: routeDestination(stack[index])
                    .background(
                        LegacyNavigationLinks(
                            stack: $stack,
                            index: index + 1,
                            routeDestination: routeDestination
                        )
                    ),
                isActive: isActive
            ) {
                EmptyView()
            }
            .isDetailLink(false)
            .hidden()
        }
    }
}

// MARK: - View Extensions for iOS 16+ API Compatibility

extension View {
    /// Replaces `.autocorrectionDisabled()` (iOS 16+)
    /// with `.disableAutocorrection(true)` on iOS 15.
    @ViewBuilder
    func noAutocorrection() -> some View {
        if #available(iOS 16, *) {
            self.autocorrectionDisabled()
        } else {
            self.disableAutocorrection(true)
        }
    }

    /// Replaces `.textInputAutocapitalization(.never)` (iOS 16+)
    /// with `.autocapitalization(.none)` on iOS 15.
    @ViewBuilder
    func noAutocapitalization() -> some View {
        if #available(iOS 16, *) {
            self.textInputAutocapitalization(.never)
        } else {
            self.autocapitalization(.none)
        }
    }

    /// Replaces `.scrollDismissesKeyboard(.interactively)` (iOS 16+).
    /// No-op on iOS 15.
    @ViewBuilder
    func scrollDismissesKeyboardIfAvailable() -> some View {
        if #available(iOS 16, *) {
            self.scrollDismissesKeyboard(.interactively)
        } else {
            self
        }
    }

    /// Replaces `.scrollContentBackground(.hidden)` (iOS 16+).
    /// No-op on iOS 15.
    @ViewBuilder
    func hideScrollContentBackground() -> some View {
        if #available(iOS 16, *) {
            self.scrollContentBackground(.hidden)
        } else {
            self
        }
    }

    /// Replaces `.presentationDetents([.medium])` (iOS 16+).
    /// Falls back to full-height sheets on iOS 15.
    @ViewBuilder
    func mediumDetent() -> some View {
        if #available(iOS 16, *) {
            self.presentationDetents([.medium])
        } else {
            self
        }
    }

    /// Replaces `.presentationDetents([.medium, .large])` (iOS 16+).
    /// Falls back to full-height sheets on iOS 15.
    @ViewBuilder
    func mediumLargeDetents() -> some View {
        if #available(iOS 16, *) {
            self.presentationDetents([.medium, .large])
        } else {
            self
        }
    }

    /// Replaces `.presentationDragIndicator(.visible)` (iOS 16+).
    /// No-op on iOS 15.
    @ViewBuilder
    func showDragIndicator() -> some View {
        if #available(iOS 16, *) {
            self.presentationDragIndicator(.visible)
        } else {
            self
        }
    }
}
