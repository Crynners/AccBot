import SwiftUI
import WidgetKit

/// Home and Lock Screen widget: when the next purchase is due and whether the last one ran.
/// Tapping it opens AccBot, which runs any due purchase right away.
struct PurchaseStatusEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct PurchaseStatusProvider: TimelineProvider {
    func placeholder(in context: Context) -> PurchaseStatusEntry {
        PurchaseStatusEntry(date: Date(), snapshot: .preview)
    }

    func getSnapshot(in context: Context, completion: @escaping (PurchaseStatusEntry) -> Void) {
        let snapshot: WidgetSnapshot = context.isPreview ? WidgetSnapshot.preview : (WidgetSnapshotStore.read() ?? WidgetSnapshot.empty)
        completion(PurchaseStatusEntry(date: Date(), snapshot: snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PurchaseStatusEntry>) -> Void) {
        let now = Date()
        let snapshot = WidgetSnapshotStore.read() ?? .empty
        var entries = [PurchaseStatusEntry(date: now, snapshot: snapshot)]
        // Switch to "waiting" on its own when the purchase did not run; the app reloads the
        // widget after every run, so no other refresh is needed.
        if let next = snapshot.nextPurchaseAt {
            let lateAt = next.addingTimeInterval(WidgetSnapshot.lateAfter)
            if lateAt > now {
                entries.append(PurchaseStatusEntry(date: lateAt, snapshot: snapshot))
            }
        }
        completion(Timeline(entries: entries, policy: .never))
    }
}

struct PurchaseStatusView: View {
    let entry: PurchaseStatusEntry
    @Environment(\.widgetFamily) private var family

    private var snapshot: WidgetSnapshot { entry.snapshot }
    private var isLate: Bool { snapshot.isLate(at: entry.date) }

    var body: some View {
        switch family {
        case .accessoryInline:
            inline
        case .accessoryRectangular:
            rectangular
        default:
            small
        }
    }

    private var inline: some View {
        Group {
            if isLate {
                Label(String(localized: "Purchase waiting"), systemImage: "exclamationmark.circle")
            } else if let next = snapshot.nextPurchaseAt {
                Label(next.formatted(date: .abbreviated, time: .shortened), systemImage: "bitcoinsign.circle")
            } else {
                Label(String(localized: "No active plan"), systemImage: "bitcoinsign.circle")
            }
        }
    }

    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("AccBot").font(.headline)
            statusLines
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: isLate ? "exclamationmark.circle.fill" : "bitcoinsign.circle.fill")
                    .foregroundStyle(isLate ? Color.orange : Color.accentColor)
                Text("AccBot").font(.headline)
                if snapshot.isSandbox {
                    Text("Sandbox").font(.caption2).foregroundStyle(.orange)
                }
            }
            statusLines
            Spacer(minLength: 0)
            if let last = snapshot.lastPurchaseAt {
                Text(String(localized: "Last purchase \(last.formatted(.relative(presentation: .named)))"))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private var statusLines: some View {
        if isLate {
            Text(String(localized: "Purchase waiting"))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.orange)
            Text(String(localized: "Tap to buy now"))
                .font(.caption)
        } else if let next = snapshot.nextPurchaseAt {
            Text(String(localized: "Next purchase"))
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(next.formatted(date: .abbreviated, time: .shortened))
                .font(.subheadline.weight(.semibold))
            if let amount = snapshot.nextAmountText {
                Text(amount).font(.caption)
            }
        } else {
            Text(String(localized: "No active plan"))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

struct PurchaseStatusWidget: Widget {
    let kind = "PurchaseStatusWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PurchaseStatusProvider()) { entry in
            PurchaseStatusView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName(String(localized: "Next purchase"))
        .description(String(localized: "When AccBot buys next, and a reminder when a purchase is waiting."))
        .supportedFamilies([.systemSmall, .accessoryRectangular, .accessoryInline])
    }
}

@main
struct AccBotWidgetBundle: WidgetBundle {
    var body: some Widget {
        PurchaseStatusWidget()
    }
}

extension WidgetSnapshot {
    static let preview = WidgetSnapshot(
        nextPurchaseAt: Date().addingTimeInterval(3 * 3600),
        nextPlanName: "BTC",
        nextAmountText: "500 CZK",
        lastPurchaseAt: Date().addingTimeInterval(-21 * 3600),
        activePlanCount: 1,
        isSandbox: false,
        updatedAt: Date()
    )
}
