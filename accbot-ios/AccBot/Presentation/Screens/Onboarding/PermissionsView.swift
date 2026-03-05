import SwiftUI
import UserNotifications

/// Onboarding step that requests notification permissions.
struct PermissionsView: View {
    let onNext: () -> Void

    @Environment(\.accBotColors) private var colors
    @State private var notificationStatus: UNAuthorizationStatus?

    var body: some View {
        ZStack {
            colors.background
                .ignoresSafeArea()

            VStack(spacing: Spacing.xxxl) {
                Spacer()

                // Icon
                ZStack {
                    Circle()
                        .fill(colors.primary.opacity(0.15))
                        .frame(width: 96, height: 96)
                    Image(systemName: "bell.badge.fill")
                        .font(AccBotFonts.displayLarge)
                        .foregroundStyle(colors.primary)
                }
                .accessibilityHidden(true)

                // Text
                VStack(spacing: Spacing.sm) {
                    Text(String(localized: "Stay Informed"))
                        .font(AccBotFonts.titleLarge)
                        .foregroundStyle(colors.onSurface)

                    Text(String(localized: "Get notified about completed purchases, errors, and weekly summaries. You can customize notifications later in Settings."))
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, Spacing.lg)
                }

                Spacer()

                // Buttons
                VStack(spacing: Spacing.md) {
                    if notificationStatus == .authorized {
                        // Already granted
                        HStack(spacing: Spacing.sm) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(colors.success)
                            Text(String(localized: "Notifications Enabled"))
                                .font(AccBotFonts.headline)
                                .foregroundStyle(colors.success)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.lg)
                        .background(colors.success.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                    } else {
                        Button {
                            Task { await requestNotifications() }
                        } label: {
                            HStack(spacing: Spacing.sm) {
                                Image(systemName: "bell.fill")
                                Text(String(localized: "Allow Notifications"))
                            }
                            .font(AccBotFonts.headline)
                            .foregroundStyle(colors.onPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, Spacing.lg)
                            .background(colors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
                        }
                    }

                    Button {
                        onNext()
                    } label: {
                        Text(notificationStatus == .authorized
                             ? String(localized: "Continue")
                             : String(localized: "Skip"))
                            .font(AccBotFonts.body)
                            .foregroundStyle(colors.primary)
                            .padding(.vertical, Spacing.sm)
                            .frame(minHeight: 44)
                    }
                }
                .padding(.bottom, Spacing.xxl)
            }
            .padding(.horizontal, Spacing.xxl)
        }
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await checkNotificationStatus()
        }
    }

    private func requestNotifications() async {
        let center = UNUserNotificationCenter.current()
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            await MainActor.run {
                notificationStatus = granted ? .authorized : .denied
            }
        } catch {
            await MainActor.run {
                notificationStatus = .denied
            }
        }
    }

    private func checkNotificationStatus() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        await MainActor.run {
            notificationStatus = settings.authorizationStatus
        }
    }
}
