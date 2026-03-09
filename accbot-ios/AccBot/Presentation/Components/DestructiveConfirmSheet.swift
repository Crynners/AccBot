import SwiftUI

/// A sheet-based confirmation dialog for destructive actions.
/// Replaces the TextField-in-Alert anti-pattern with a proper modal
/// that provides adequate touch targets and text visibility (WCAG 2.5.5).
struct DestructiveConfirmSheet: View {
    let title: String
    let message: String
    let confirmWord: String
    let confirmButtonLabel: String
    let onConfirm: () -> Void

    @State private var inputText = ""
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accBotColors) private var colors
    @FocusState private var isFocused: Bool

    private var isConfirmEnabled: Bool {
        inputText.lowercased() == confirmWord.lowercased()
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: Spacing.lg) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(AccBotFonts.iconLarge)
                    .foregroundStyle(colors.error)
                    .accessibilityHidden(true)

                Text(message)
                    .font(AccBotFonts.body)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .multilineTextAlignment(.center)

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(String(localized: "Type \(confirmWord) to confirm"))
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.onSurfaceVariant)

                    TextField(confirmWord, text: $inputText)
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurface)
                        .padding(Spacing.md)
                        .background(colors.surfaceVariant.opacity(0.3))
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                        .overlay(
                            RoundedRectangle(cornerRadius: CornerRadius.sm)
                                .strokeBorder(
                                    isConfirmEnabled ? colors.success : colors.onSurfaceVariant.opacity(0.3),
                                    lineWidth: 1
                                )
                        )
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .focused($isFocused)
                        .accessibilityLabel(String(localized: "Confirmation text field"))
                        .accessibilityHint(String(localized: "Type the confirmation word to enable the button"))
                }

                Button {
                    dismiss()
                    onConfirm()
                } label: {
                    Text(confirmButtonLabel)
                        .font(AccBotFonts.headline)
                        .foregroundStyle(isConfirmEnabled ? colors.onPrimary : colors.disabledForeground)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.md)
                        .background(isConfirmEnabled ? colors.error : colors.disabledBackground)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
                }
                .disabled(!isConfirmEnabled)

                Spacer()
            }
            .padding(Spacing.lg)
            .background(colors.background)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "Cancel")) {
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium])
        .onAppear { isFocused = true }
    }
}
