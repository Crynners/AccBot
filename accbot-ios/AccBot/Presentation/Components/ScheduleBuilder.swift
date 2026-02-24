import SwiftUI

/// Schedule builder with frequency chip picker and custom CRON inputs.
/// When `DcaFrequency.custom` is selected, displays hour and day-of-week
/// pickers so the user can build a CRON expression visually.
struct ScheduleBuilder: View {
    @Binding var selectedFrequency: DcaFrequency
    @Binding var cronExpression: String

    @State private var selectedHour: Int = 9
    @State private var selectedDayOfWeek: Int? = nil // nil = every day

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    private static let dayNames: [(Int, String)] = [
        (0, String(localized: "Sun")),
        (1, String(localized: "Mon")),
        (2, String(localized: "Tue")),
        (3, String(localized: "Wed")),
        (4, String(localized: "Thu")),
        (5, String(localized: "Fri")),
        (6, String(localized: "Sat")),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            sectionHeader(String(localized: "Frequency"))

            frequencyChips

            if let warning = selectedFrequency.backgroundWarning {
                backgroundWarningView(warning)
            }

            if selectedFrequency == .custom {
                customCronSection
            }
        }
    }

    // MARK: - Frequency Chips

    private var frequencyChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(DcaFrequency.allCases, id: \.self) { frequency in
                    SelectableChip(
                        title: frequency.displayName,
                        isSelected: selectedFrequency == frequency,
                        onTap: {
                            selectedFrequency = frequency
                            if frequency != .custom {
                                cronExpression = ""
                            } else {
                                rebuildCron()
                            }
                        }
                    )
                }
            }
        }
    }

    // MARK: - Background Warning

    private func backgroundWarningView(_ warning: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(colors.warning)
                .font(AccBotFonts.bodySmall)

            Text(warning)
                .font(AccBotFonts.caption)
                .foregroundColor(colors.warning)
        }
        .padding(Spacing.md)
        .background(colors.warning.opacity(0.1))
        .cornerRadius(CornerRadius.sm)
    }

    // MARK: - Custom CRON

    private var customCronSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Custom Schedule"))

            // Hour picker
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Hour"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                Picker("", selection: $selectedHour) {
                    ForEach(0..<24, id: \.self) { hour in
                        Text(String(format: "%02d:00", hour))
                            .tag(hour)
                    }
                }
                .pickerStyle(.wheel)
                .frame(height: 100)
                .clipped()
                .onChange(of: selectedHour) { _ in rebuildCron() }
            }

            // Day of week picker
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Day of Week"))
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        SelectableChip(
                            title: String(localized: "Every Day"),
                            isSelected: selectedDayOfWeek == nil,
                            onTap: {
                                selectedDayOfWeek = nil
                                rebuildCron()
                            }
                        )

                        ForEach(Self.dayNames, id: \.0) { day in
                            SelectableChip(
                                title: day.1,
                                isSelected: selectedDayOfWeek == day.0,
                                onTap: {
                                    selectedDayOfWeek = day.0
                                    rebuildCron()
                                }
                            )
                        }
                    }
                }
            }

            // Show generated CRON
            HStack(spacing: Spacing.sm) {
                Image(systemName: "terminal")
                    .font(AccBotFonts.caption)
                    .foregroundColor(colors.onSurfaceVariant)

                Text(cronExpression.isEmpty ? "--" : cronExpression)
                    .font(AccBotFonts.mono)
                    .foregroundColor(colors.primary)
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.surfaceVariant.opacity(0.3))
            .cornerRadius(CornerRadius.sm)
        }
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AccBotFonts.headline)
            .foregroundColor(colors.onSurface)
    }

    private func rebuildCron() {
        cronExpression = CronUtils.buildCron(
            minute: 0,
            hour: selectedHour,
            dayOfWeek: selectedDayOfWeek
        )
    }
}

// MARK: - Preview

#Preview {
    ScrollView {
        ScheduleBuilder(
            selectedFrequency: .constant(.daily),
            cronExpression: .constant("")
        )
        .padding()
    }
    .background(Color.backgroundDark)
}
