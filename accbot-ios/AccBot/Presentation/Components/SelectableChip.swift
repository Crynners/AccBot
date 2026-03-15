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

/// Row of selectable chips. By default uses a horizontal ScrollView.
/// When `wrapping` is true, chips flow into multiple lines to show all items at once.
struct SelectableChipGroup<Item: Hashable>: View {
    let items: [Item]
    let selection: Item
    let label: (Item) -> String
    let icon: ((Item) -> AnyView)?
    let wrapping: Bool
    let onSelect: (Item) -> Void

    init(
        items: [Item],
        selection: Item,
        label: @escaping (Item) -> String,
        wrapping: Bool = false,
        onSelect: @escaping (Item) -> Void
    ) {
        self.items = items
        self.selection = selection
        self.label = label
        self.icon = nil
        self.wrapping = wrapping
        self.onSelect = onSelect
    }

    init<V: View>(
        items: [Item],
        selection: Item,
        label: @escaping (Item) -> String,
        icon: @escaping (Item) -> V,
        wrapping: Bool = false,
        onSelect: @escaping (Item) -> Void
    ) {
        self.items = items
        self.selection = selection
        self.label = label
        self.icon = { AnyView(icon($0)) }
        self.wrapping = wrapping
        self.onSelect = onSelect
    }

    var body: some View {
        if wrapping {
            FlowLayout(spacing: Spacing.sm) {
                chipViews
            }
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    chipViews
                }
            }
        }
    }

    @ViewBuilder
    private var chipViews: some View {
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

// MARK: - Flow Layout

/// A wrapping layout that places items left-to-right and flows into new rows when needed.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = computeRows(proposal: proposal, subviews: subviews)
        var height: CGFloat = 0
        for (i, row) in rows.enumerated() {
            let rowHeight = row.map { subviews[$0].sizeThatFits(.unspecified).height }.max() ?? 0
            height += rowHeight
            if i > 0 { height += spacing }
        }
        return CGSize(width: proposal.width ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = computeRows(proposal: proposal, subviews: subviews)
        var y = bounds.minY
        for (i, row) in rows.enumerated() {
            let rowHeight = row.map { subviews[$0].sizeThatFits(.unspecified).height }.max() ?? 0
            if i > 0 { y += spacing }
            var x = bounds.minX
            for idx in row {
                let size = subviews[idx].sizeThatFits(.unspecified)
                subviews[idx].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += rowHeight
        }
    }

    private func computeRows(proposal: ProposedViewSize, subviews: Subviews) -> [[Int]] {
        let maxWidth = proposal.width ?? .infinity
        var rows: [[Int]] = [[]]
        var currentWidth: CGFloat = 0

        for (i, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            if !rows[rows.count - 1].isEmpty && currentWidth + spacing + size.width > maxWidth {
                rows.append([i])
                currentWidth = size.width
            } else {
                if !rows[rows.count - 1].isEmpty { currentWidth += spacing }
                rows[rows.count - 1].append(i)
                currentWidth += size.width
            }
        }
        return rows
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
