import SwiftUI
import UIKit

/// Opens the Shortcuts setup guide from anywhere (dashboard warning, settings).
/// Used from the main thread only (views and their actions).
final class ShortcutsSetupPresenter: ObservableObject {
    static let shared = ShortcutsSetupPresenter()
    @Published var isPresented = false
    private init() {}
}

/// Guides the user through the one Shortcuts automation that makes purchases run on time.
///
/// iOS does not let an app start itself at an exact time, and an app cannot create a
/// Shortcuts automation for the user. The "Run due purchases" action is already there
/// after install, so the user only adds a daily Time of Day automation that runs it.
struct ShortcutsSetupView: View {
    enum Mode {
        case onboarding
        case sheet
    }

    let mode: Mode
    var onFinish: () -> Void = {}

    @EnvironmentObject private var dependencies: AppDependencies
    @Environment(\.accBotColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        ZStack {
            colors.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    header
                    steps
                    tip
                    status
                }
                .padding(.horizontal, Spacing.xxl)
                .padding(.vertical, Spacing.xl)
                .maxFormWidth()
            }
            .safeAreaInset(edge: .bottom) { buttons }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Content

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Image(systemName: "clock.badge.checkmark.fill")
                .font(AccBotFonts.displayLarge)
                .foregroundStyle(colors.primary)
                .accessibilityHidden(true)
            Text(String(localized: "Purchases on time"))
                .font(AccBotFonts.titleLarge)
                .foregroundStyle(colors.onSurface)
            Text(String(localized: "iOS doesn't let apps start themselves at an exact time, but the Shortcuts app can. Set up one automation once and AccBot buys at the same time every day, even with the iPhone locked. It takes about a minute."))
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var steps: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            step(1, String(localized: "Open Shortcuts and tap Automation at the bottom."))
            step(2, String(localized: "Tap + to create a new automation and choose the time of day trigger."))
            step(3, String(localized: "Set the time to \(suggestedTimeText), repeat daily, and let it run immediately without asking."))
            step(4, String(localized: "Tap Next, search for AccBot and pick the Run due purchases action."))
            step(5, String(localized: "Tap Done. That's it."))
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    private func step(_ number: Int, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.md) {
            Text("\(number)")
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onPrimary)
                .frame(width: 28, height: 28)
                .background(Circle().fill(colors.primary))
                .accessibilityHidden(true)
            Text(text)
                .font(AccBotFonts.body)
                .foregroundStyle(colors.onSurface)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }

    private var tip: some View {
        Text(String(localized: "The action buys only what is due, so it never buys twice. You can add more automations with it, for example when you stop your morning alarm or plug in the charger. If iOS still doesn't run a purchase, AccBot reminds you with a notification."))
            .font(AccBotFonts.bodySmall)
            .foregroundStyle(colors.onSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
    }

    @ViewBuilder
    private var status: some View {
        if let lastRun = dependencies.userPreferences.lastShortcutRun {
            Label(
                String(localized: "The automation last ran \(AccBotFormatters.relativeDate(lastRun))."),
                systemImage: "checkmark.circle.fill"
            )
            .font(AccBotFonts.bodySmall)
            .foregroundStyle(colors.success)
        }
    }

    private var buttons: some View {
        VStack(spacing: Spacing.sm) {
            Button {
                if let url = URL(string: "shortcuts://") {
                    openURL(url)
                }
            } label: {
                Label(String(localized: "Open Shortcuts"), systemImage: "arrow.up.forward.app")
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.lg)
                    .background(colors.primary)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
            }

            Button {
                dependencies.userPreferences.shortcutSetupConfirmedAt = Date()
                finish()
            } label: {
                Text(String(localized: "Done, it's set up"))
                    .font(AccBotFonts.headline)
                    .foregroundStyle(colors.primary)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }

            Button {
                finish()
            } label: {
                Text(String(localized: "Not now"))
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .padding(.horizontal, Spacing.xxl)
        .padding(.top, Spacing.sm)
        .padding(.bottom, Spacing.lg)
        .background(colors.background)
    }

    private func finish() {
        switch mode {
        case .onboarding: onFinish()
        case .sheet: dismiss()
        }
    }

    /// The time of the nearest plan, so the automation and the plans meet. 9:00 without plans.
    private var suggestedTimeText: String {
        let plans = (try? dependencies.activeDatabase.planDao.getEnabledPlans()) ?? []
        let next = plans.compactMap(\.nextExecutionAt).min()
        var components = DateComponents()
        if let next {
            let parts = Calendar.current.dateComponents([.hour, .minute], from: next)
            components.hour = parts.hour
            components.minute = parts.minute
        } else {
            components.hour = 9
            components.minute = 0
        }
        let date = Calendar.current.date(from: components) ?? Date()
        return date.formatted(date: .omitted, time: .shortened)
    }
}

/// Sheet wrapper with its own navigation bar and close button.
struct ShortcutsSetupSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ShortcutsSetupView(mode: .sheet)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(String(localized: "Close")) { dismiss() }
                    }
                }
        }
    }
}
