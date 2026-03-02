import SwiftUI

/// Generic selectable chip with rounded capsule shape.
/// When selected, the chip is filled with the primary color.
/// When unselected, it has an outlined (stroked) appearance.
struct SelectableChip<Icon: View>: View {
    let title: String
    let isSelected: Bool
    let icon: Icon?
    let onTap: () -> Void

    @Environment(\.accBotColors) private var colors

    init(
        title: String,
        isSelected: Bool,
        @ViewBuilder icon: () -> Icon,
        onTap: @escaping () -> Void
    ) {
        self.title = title
        self.isSelected = isSelected
        self.icon = icon()
        self.onTap = onTap
    }

    var body: some View {
        Button(action: {
            if !UIAccessibility.isReduceMotionEnabled {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
            onTap()
        }) {
            HStack(spacing: Spacing.xs) {
                if let icon {
                    icon
                }
                Text(title)
                    .font(AccBotFonts.label)
            }
            .foregroundStyle(isSelected ? colors.onPrimary : colors.onSurface)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(minWidth: 44, minHeight: 44)
            .background(
                Capsule()
                    .fill(isSelected ? colors.primary : Color.clear)
            )
            .overlay(
                Capsule()
                    .strokeBorder(
                        isSelected ? colors.primary : colors.onSurfaceVariant,
                        lineWidth: 1
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
        .accessibilityValue(isSelected ? String(localized: "Selected") : String(localized: "Not selected"))
    }
}

// Convenience init without icon
extension SelectableChip where Icon == EmptyView {
    init(
        title: String,
        isSelected: Bool,
        onTap: @escaping () -> Void
    ) {
        self.title = title
        self.isSelected = isSelected
        self.icon = nil
        self.onTap = onTap
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
    let icon: ((Item) -> AnyView)?
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
        self.icon = nil
        self.onSelect = onSelect
    }

    init<V: View>(
        items: [Item],
        selection: Item,
        label: @escaping (Item) -> String,
        icon: @escaping (Item) -> V,
        onSelect: @escaping (Item) -> Void
    ) {
        self.items = items
        self.selection = selection
        self.label = label
        self.icon = { AnyView(icon($0)) }
        self.onSelect = onSelect
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(items, id: \.self) { item in
                    if let icon {
                        SelectableChip(
                            title: label(item),
                            isSelected: item == selection,
                            icon: { icon(item) },
                            onTap: { onSelect(item) }
                        )
                    } else {
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
}

// MARK: - Preview

#Preview {
    VStack(spacing: Spacing.lg) {
        SelectableChipGroup(
            items: ["BTC", "ETH", "SOL", "ADA"],
            selection: "BTC",
            label: { $0 },
            icon: { CryptoIcon(symbol: $0, size: 18) },
            onSelect: { _ in }
        )

        SelectableChipGroup(
            items: ["EUR", "USD", "CZK", "GBP"],
            selection: "EUR",
            label: { $0 },
            icon: { FiatIcon(symbol: $0, size: 18) },
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
