import SwiftUI

/// Visual step progress indicator for backup wizards.
struct BackupStepIndicator: View {
    let steps: [String]
    let currentStep: Int
    @Environment(\.accBotColors) private var colors

    var body: some View {
        VStack(spacing: Spacing.xs) {
            HStack(spacing: Spacing.xs) {
                ForEach(0..<steps.count, id: \.self) { index in
                    RoundedRectangle(cornerRadius: CornerRadius.xxs)
                        .fill(index <= currentStep ? colors.primary : colors.onSurfaceVariant.opacity(Opacity.divider))
                        .frame(height: 4)
                }
            }

            HStack {
                ForEach(0..<steps.count, id: \.self) { index in
                    Text(steps[index])
                        .font(AccBotFonts.caption)
                        .foregroundStyle(index <= currentStep ? colors.primary : colors.onSurfaceVariant)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(String(localized: "Step \(currentStep + 1) of \(steps.count): \(currentStep < steps.count ? steps[currentStep] : "")"))
    }
}
