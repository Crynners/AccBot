import SwiftUI

struct ChangelogView: View {
    let entries: [ChangelogEntry]
    @Environment(\.accBotColors) private var colors
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    ForEach(entries) { entry in
                        entryCard(entry)
                    }
                }
                .padding(Spacing.lg)
            }
            .background(colors.background)
            .navigationTitle(String(localized: "What's New"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(String(localized: "Done")) {
                        dismiss()
                    }
                }
            }
        }
    }

    private func entryCard(_ entry: ChangelogEntry) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            HStack {
                Text("v\(entry.version)")
                    .font(AccBotFonts.titleSmall)
                    .foregroundStyle(colors.primary)
                Spacer()
            }

            Text(entry.title)
                .font(AccBotFonts.headline)
                .foregroundStyle(colors.onSurface)

            VStack(alignment: .leading, spacing: Spacing.sm) {
                ForEach(entry.features, id: \.self) { feature in
                    HStack(alignment: .top, spacing: Spacing.sm) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(AccBotFonts.caption)
                            .foregroundStyle(colors.primary)
                            .padding(.top, 2)
                        Text(feature)
                            .font(AccBotFonts.bodySmall)
                            .foregroundStyle(colors.onSurface)
                    }
                }
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }
}
