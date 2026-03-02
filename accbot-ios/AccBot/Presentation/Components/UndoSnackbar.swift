import SwiftUI

/// Snackbar with undo button and visible countdown timer.
/// Provides clear visual feedback on how long the undo option remains available (WCAG 2.2.1).
struct UndoSnackbar: View {
    let message: String
    let duration: TimeInterval
    let onUndo: () -> Void

    @Environment(\.accBotColors) private var colors
    @State private var remaining: Int

    init(message: String, duration: TimeInterval = 8, onUndo: @escaping () -> Void) {
        self.message = message
        self.duration = duration
        self.onUndo = onUndo
        _remaining = State(initialValue: Int(duration))
    }

    var body: some View {
        HStack(spacing: Spacing.md) {
            Text(message)
                .font(AccBotFonts.bodySmall)
                .foregroundStyle(colors.onSurface)

            Spacer()

            Text("\(remaining)s")
                .font(AccBotFonts.captionSmall)
                .foregroundStyle(colors.onSurfaceVariant)
                .monospacedDigit()

            Button(String(localized: "Undo")) {
                onUndo()
            }
            .font(AccBotFonts.label)
            .foregroundStyle(colors.primary)
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.md)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
        .shadow(color: .black.opacity(0.3), radius: 8, y: 4)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(String(localized: "\(message). \(remaining) seconds to undo."))
        .onAppear {
            startCountdown()
        }
    }

    private func startCountdown() {
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { timer in
            if remaining > 0 {
                remaining -= 1
            } else {
                timer.invalidate()
            }
        }
    }
}
