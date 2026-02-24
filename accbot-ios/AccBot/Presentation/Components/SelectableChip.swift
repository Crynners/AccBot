import SwiftUI

/// Generic selectable chip with rounded capsule shape.
/// When selected, the chip is filled with the primary color.
/// When unselected, it has an outlined (stroked) appearance.
struct SelectableChip: View {
    let title: String
    let isSelected: Bool
    let onTap: () -> Void

    @Environment(\.isSandboxMode) private var isSandboxMode
    @Environment(\.colorScheme) private var colorScheme

    private var colors: AccBotColors {
        AccBotColors(isSandbox: isSandboxMode, isDark: colorScheme == .dark)
    }

    var body: some View {
        Button(action: onTap) {
            Text(title)
                .font(AccBotFonts.label)
                .foregroundColor(isSelected ? .white : colors.onSurfaceVariant)
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
                .background(
                    Capsule()
                        .fill(isSelected ? colors.primary : Color.clear)
                )
                .overlay(
                    Capsule()
                        .strokeBorder(
                            isSelected ? colors.primary : colors.onSurfaceVariant.opacity(0.4),
                            lineWidth: 1
                        )
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Convenience: Selectable Chip Group

/// Horizontal scrolling row of selectable chips.
/// Works generically with any Hashable + CustomStringConvertible items,
/// or pass explicit labels via the `label` closure.
struct SelectableChipGroup<Item: Hashable>: View {
    let items: [Item]
    let selection: Item
    let label: (Item) -> String
    let onSelect: (Item) -> Void

    init(
        items: [Item],
        selection: Item,
        label: @escaping (Item) -> String,
        onSelect: @escaping (Item) -> Void
    ) {
        self.items = items
        self.selection = selection
        self.label = label
        self.onSelect = onSelect
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(items, id: \.self) { item in
                    SelectableChip(
                        title: label(item),
                        isSelected: item == selection,
                        onTap: { onSelect(item) }
                    )
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: Spacing.lg) {
        SelectableChipGroup(
            items: ["BTC", "ETH", "SOL", "ADA"],
            selection: "BTC",
            label: { $0 },
            onSelect: { _ in }
        )

        SelectableChipGroup(
            items: DcaFrequency.allCases,
            selection: .daily,
            label: { $0.displayName },
            onSelect: { _ in }
        )
    }
    .padding()
    .background(Color.backgroundDark)
}
