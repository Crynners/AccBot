import SwiftUI

// MARK: - Schedule Builder State

/// Schedule type for the visual schedule builder.
enum ScheduleType: CaseIterable {
    case daily
    case daysOfWeek
    case daysOfMonth

    var displayName: String {
        switch self {
        case .daily: return String(localized: "Every Day")
        case .daysOfWeek: return String(localized: "Days of Week")
        case .daysOfMonth: return String(localized: "Days of Month")
        }
    }
}

/// Pure state model for the visual schedule builder.
/// Maps bidirectionally to/from 5-field UNIX CRON expressions.
struct ScheduleBuilderState {
    var scheduleType: ScheduleType = .daily
    var selectedMinute: Int = 0
    var selectedHours: Set<Int> = [9]
    var selectedDaysOfWeek: Set<Int> = []  // 0=Sun, 1=Mon..6=Sat (CRON convention)
    var selectedDaysOfMonth: Set<Int> = [] // 1-31
    var useAdvancedMode: Bool = false
    var rawCronExpression: String = ""

    /// Generate a 5-field UNIX CRON expression from the visual state.
    func toCronExpression() -> String? {
        if useAdvancedMode {
            return rawCronExpression.isEmpty ? nil : rawCronExpression
        }

        guard !selectedHours.isEmpty else { return nil }

        return CronUtils.buildCronAdvanced(
            minute: selectedMinute,
            hours: selectedHours,
            daysOfWeek: scheduleType == .daysOfWeek && !selectedDaysOfWeek.isEmpty ? selectedDaysOfWeek : nil,
            daysOfMonth: scheduleType == .daysOfMonth && !selectedDaysOfMonth.isEmpty ? selectedDaysOfMonth : nil
        )
    }

    /// Whether the current state produces a valid CRON expression.
    var isValid: Bool {
        guard let cron = toCronExpression() else { return false }
        return CronUtils.isValid(cron: cron)
    }

    /// Formatted times based on selected hours and minute.
    var selectedTimes: [String] {
        selectedHours.sorted().map { String(format: "%d:%02d", $0, selectedMinute) }
    }

    /// Parse a CRON expression back into visual builder state.
    /// Falls back to advanced mode for complex expressions.
    static func fromCronExpression(_ cron: String) -> ScheduleBuilderState {
        if cron.isEmpty { return ScheduleBuilderState() }

        let parts = cron.trimmingCharacters(in: .whitespaces).split(separator: " ")
        guard parts.count == 5 else {
            return ScheduleBuilderState(useAdvancedMode: true, rawCronExpression: cron)
        }

        let minutePart = String(parts[0])
        let hourPart = String(parts[1])
        let domPart = String(parts[2])
        let monthPart = String(parts[3])
        let dowPart = String(parts[4])

        // If month field isn't wildcard, fall back to advanced
        guard monthPart == "*" else {
            return ScheduleBuilderState(useAdvancedMode: true, rawCronExpression: cron)
        }

        // Parse minute — must be a single number
        guard let minute = Int(minutePart), (0...59).contains(minute) else {
            return ScheduleBuilderState(useAdvancedMode: true, rawCronExpression: cron)
        }

        // Parse hours — must be a comma-separated list of numbers
        guard let hours = parseNumberList(hourPart, range: 0...23) else {
            return ScheduleBuilderState(useAdvancedMode: true, rawCronExpression: cron)
        }

        let isDomWild = domPart == "*"
        let isDowWild = dowPart == "*"

        switch (isDomWild, isDowWild) {
        case (true, true):
            // "m h * * *" → daily
            return ScheduleBuilderState(
                scheduleType: .daily,
                selectedMinute: minute,
                selectedHours: hours
            )

        case (true, false):
            // "m h * * d,d,d" → days of week
            guard let dows = parseNumberList(dowPart, range: 0...7) else {
                return ScheduleBuilderState(useAdvancedMode: true, rawCronExpression: cron)
            }
            // Normalize DOW 7 (some crons use 7=Sun) to 0
            let normalized = Set(dows.map { $0 == 7 ? 0 : $0 })
            return ScheduleBuilderState(
                scheduleType: .daysOfWeek,
                selectedMinute: minute,
                selectedHours: hours,
                selectedDaysOfWeek: normalized
            )

        case (false, true):
            // "m h d,d * *" → days of month
            guard let doms = parseNumberList(domPart, range: 1...31) else {
                return ScheduleBuilderState(useAdvancedMode: true, rawCronExpression: cron)
            }
            return ScheduleBuilderState(
                scheduleType: .daysOfMonth,
                selectedMinute: minute,
                selectedHours: hours,
                selectedDaysOfMonth: doms
            )

        default:
            // Both DOM and DOW specified — too complex
            return ScheduleBuilderState(useAdvancedMode: true, rawCronExpression: cron)
        }
    }

    private static func parseNumberList(_ field: String, range: ClosedRange<Int>) -> Set<Int>? {
        if field.contains("-") || field.contains("/") || field.contains("*") { return nil }
        let numbers = field.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        var result: Set<Int> = []
        for num in numbers {
            guard let n = Int(num), range.contains(n) else { return nil }
            result.insert(n)
        }
        return result.isEmpty ? nil : result
    }
}

// MARK: - Schedule Builder View

/// Visual schedule builder — drop-in replacement matching Android's full CRON builder.
/// Supports: schedule type (daily/days-of-week/days-of-month), minute picker,
/// multi-hour selection, day-of-week chips, day-of-month chips, CRON preview,
/// and advanced raw CRON input mode.
struct ScheduleBuilder: View {
    @Binding var selectedFrequency: DcaFrequency
    @Binding var cronExpression: String

    @State private var state = ScheduleBuilderState()

    @Environment(\.accBotColors) private var colors
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
        .task(id: cronExpression) {
            // Sync state when cronExpression changes externally (e.g. loading saved plan)
            // Also handles initial load (replaces onAppear)
            if selectedFrequency == .custom && !cronExpression.isEmpty {
                let parsed = ScheduleBuilderState.fromCronExpression(cronExpression)
                if parsed.toCronExpression() != state.toCronExpression() {
                    state = parsed
                }
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
                                emitCron()
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
                .foregroundStyle(colors.warning)
                .font(AccBotFonts.bodySmall)
                .accessibilityHidden(true)

            Text(warning)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.warning)
        }
        .padding(Spacing.md)
        .background(colors.warning.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
        .accessibilityElement(children: .combine)
    }

    // MARK: - Custom CRON Section

    private var customCronSection: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            if !state.useAdvancedMode {
                scheduleTypeSelector
                timeSection
                daySection
                cronPreviewCard
            }

            advancedModeSection
        }
    }

    // MARK: - Schedule Type Selector

    private var scheduleTypeSelector: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            sectionHeader(String(localized: "Schedule Type"))

            HStack(spacing: 0) {
                ForEach(ScheduleType.allCases, id: \.self) { type in
                    let isActive = state.scheduleType == type
                    Button {
                        state.scheduleType = type
                        emitCron()
                    } label: {
                        Text(type.displayName)
                            .font(AccBotFonts.label)
                            .foregroundStyle(isActive ? colors.onPrimary : colors.onSurfaceVariant)
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 44)
                            .background(isActive ? colors.primary : Color.clear)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(isActive ? .isSelected : [])
                }
            }
            .background(colors.surfaceVariant.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
            .overlay(
                RoundedRectangle(cornerRadius: CornerRadius.sm)
                    .strokeBorder(colors.onSurfaceVariant.opacity(0.3), lineWidth: 1)
            )
        }
    }

    // MARK: - Time Section

    private var timeSection: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            sectionHeader(String(localized: "Time"))

            // Minute selector
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Minute"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)

                HStack(spacing: Spacing.sm) {
                    ForEach([0, 15, 30, 45], id: \.self) { minute in
                        SelectableChip(
                            title: String(format: ":%02d", minute),
                            isSelected: state.selectedMinute == minute,
                            onTap: {
                                state.selectedMinute = minute
                                emitCron()
                            }
                        )
                    }
                }
            }

            // Hour selector (multi-select)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Hour (tap to select multiple)"))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .accessibilityHint(String(localized: "Multiple hours can be selected"))

                hourGrid
            }

            // Time summary
            if !state.selectedTimes.isEmpty {
                Text(state.selectedTimes.joined(separator: ", "))
                    .font(AccBotFonts.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(colors.primary)
            }
        }
    }

    private var hourGrid: some View {
        let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.xs), count: 6)
        return LazyVGrid(columns: columns, spacing: Spacing.xs) {
            ForEach(0..<24, id: \.self) { hour in
                SelectableChip(
                    title: String(format: "%d:00", hour),
                    isSelected: state.selectedHours.contains(hour),
                    onTap: {
                        if state.selectedHours.contains(hour) {
                            state.selectedHours.remove(hour)
                        } else {
                            state.selectedHours.insert(hour)
                        }
                        emitCron()
                    }
                )
            }
        }
    }

    // MARK: - Day Section

    @ViewBuilder
    private var daySection: some View {
        if state.scheduleType == .daysOfWeek {
            dayOfWeekSelector
        } else if state.scheduleType == .daysOfMonth {
            dayOfMonthSelector
        }
    }

    private var dayOfWeekSelector: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            sectionHeader(String(localized: "Select Days"))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.xs) {
                    // Mon..Sat, Sun (matching Android order: 1,2,3,4,5,6,0)
                    let dayOrder: [(Int, String)] = [
                        (1, String(localized: "Mon")),
                        (2, String(localized: "Tue")),
                        (3, String(localized: "Wed")),
                        (4, String(localized: "Thu")),
                        (5, String(localized: "Fri")),
                        (6, String(localized: "Sat")),
                        (0, String(localized: "Sun")),
                    ]

                    ForEach(dayOrder, id: \.0) { cronDow, name in
                        SelectableChip(
                            title: name,
                            isSelected: state.selectedDaysOfWeek.contains(cronDow),
                            onTap: {
                                if state.selectedDaysOfWeek.contains(cronDow) {
                                    state.selectedDaysOfWeek.remove(cronDow)
                                } else {
                                    state.selectedDaysOfWeek.insert(cronDow)
                                }
                                emitCron()
                            }
                        )
                    }
                }
            }
        }
    }

    private var dayOfMonthSelector: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            sectionHeader(String(localized: "Select Days"))

            let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.xs), count: 7)
            LazyVGrid(columns: columns, spacing: Spacing.xs) {
                ForEach(1...31, id: \.self) { day in
                    SelectableChip(
                        title: "\(day)",
                        isSelected: state.selectedDaysOfMonth.contains(day),
                        onTap: {
                            if state.selectedDaysOfMonth.contains(day) {
                                state.selectedDaysOfMonth.remove(day)
                            } else {
                                state.selectedDaysOfMonth.insert(day)
                            }
                            emitCron()
                        }
                    )
                }
            }
        }
    }

    // MARK: - CRON Preview Card

    @ViewBuilder
    private var cronPreviewCard: some View {
        if let cron = state.toCronExpression() {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(String(localized: "Schedule Preview"))
                    .font(AccBotFonts.label)
                    .fontWeight(.semibold)
                    .foregroundStyle(colors.onSurface)

                HStack(spacing: Spacing.sm) {
                    Image(systemName: "terminal")
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)
                        .accessibilityHidden(true)

                    Text(cron)
                        .font(AccBotFonts.mono)
                        .foregroundStyle(colors.primary)
                }

                if let description = CronUtils.describeCron(cron) {
                    Text(description)
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.primary)
                }
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.surfaceVariant.opacity(0.3))
            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
        }
    }

    // MARK: - Advanced Mode Section

    private var advancedModeSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Button {
                withAnimation(reduceMotion ? nil : .default) {
                    toggleAdvancedMode()
                }
            } label: {
                HStack {
                    Text(String(localized: "Advanced: Edit CRON directly"))
                        .font(AccBotFonts.label)
                        .foregroundStyle(colors.onSurfaceVariant)
                    Spacer()
                    Image(systemName: state.useAdvancedMode ? "chevron.up" : "chevron.down")
                        .foregroundStyle(colors.onSurfaceVariant)
                        .font(AccBotFonts.captionSmall)
                }
            }
            .buttonStyle(.plain)

            if state.useAdvancedMode {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    TextField(
                        String(localized: "e.g. 0 9 * * 1 (Mon 9:00)"),
                        text: Binding(
                            get: { state.rawCronExpression },
                            set: { newValue in
                                state.rawCronExpression = newValue
                                emitCron()
                            }
                        )
                    )
                    .font(AccBotFonts.mono)
                    .foregroundStyle(colors.onSurface)
                    .padding(Spacing.md)
                    .background(colors.surfaceVariant.opacity(0.3))
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                    .overlay(
                        RoundedRectangle(cornerRadius: CornerRadius.sm)
                            .strokeBorder(
                                !state.rawCronExpression.isEmpty && !CronUtils.isValid(cron: state.rawCronExpression)
                                    ? colors.error : colors.onSurfaceVariant.opacity(0.3),
                                lineWidth: 1
                            )
                    )
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)

                    if !state.rawCronExpression.isEmpty {
                        if CronUtils.isValid(cron: state.rawCronExpression) {
                            if let description = CronUtils.describeCron(state.rawCronExpression) {
                                Text(description)
                                    .font(AccBotFonts.caption)
                                    .foregroundStyle(colors.primary)
                            }
                        } else {
                            Text(String(localized: "Invalid CRON expression"))
                                .font(AccBotFonts.caption)
                                .foregroundStyle(colors.error)
                        }
                    }

                    // CRON examples card
                    cronExamplesCard
                }
            }
        }
    }

    private var cronExamplesCard: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(String(localized: "Examples:"))
                .font(AccBotFonts.label)
                .fontWeight(.semibold)
                .foregroundStyle(colors.onSurface)

            Group {
                Text("0 9 * * *  =  ") + Text(String(localized: "Every day at 9:00"))
                Text("0 9 * * 1  =  ") + Text(String(localized: "Every Monday at 9:00"))
                Text("0 9 1 * *  =  ") + Text(String(localized: "1st of every month at 9:00"))
                Text("0 9,21 * * *  =  ") + Text(String(localized: "Every day at 9:00 and 21:00"))
            }
            .font(AccBotFonts.caption)
            .foregroundStyle(colors.onSurfaceVariant)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.surfaceVariant.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
    }

    // MARK: - Helpers

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AccBotFonts.headline)
            .foregroundStyle(colors.onSurface)
            .accessibilityAddTraits(.isHeader)
    }

    private func emitCron() {
        if let cron = state.toCronExpression() {
            cronExpression = cron
            if let description = CronUtils.describeCron(cron) {
                UIAccessibility.post(notification: .announcement, argument: description)
            }
        }
    }

    private func toggleAdvancedMode() {
        if state.useAdvancedMode {
            // Switching from advanced → visual: try to parse current raw CRON
            let parsed = ScheduleBuilderState.fromCronExpression(state.rawCronExpression)
            if !parsed.useAdvancedMode {
                state = parsed
            }
            // If can't parse back to visual, stay in advanced
        } else {
            // Switching from visual → advanced: keep current CRON in raw field
            let cron = state.toCronExpression() ?? ""
            state.useAdvancedMode = true
            state.rawCronExpression = cron
        }
        emitCron()
    }
}

// MARK: - Preview

#Preview {
    ScrollView {
        ScheduleBuilder(
            selectedFrequency: .constant(.custom),
            cronExpression: .constant("0 9 * * *")
        )
        .padding()
    }
    .background(Color.backgroundDark)
}
