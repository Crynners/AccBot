import WidgetKit
import SwiftUI

// MARK: - Shared Data

struct WidgetData: Codable {
    let lastExecutedAt: Date?
    let nextExecutionAt: Date?
    let totalCrypto: String
    let cryptoSymbol: String
    let totalInvested: String
    let fiatSymbol: String
    let portfolioValue: String?
    let roiPercent: String?
    let planCount: Int
    let activePlanCount: Int
    let updatedAt: Date

    static let empty = WidgetData(
        lastExecutedAt: nil, nextExecutionAt: nil,
        totalCrypto: "---", cryptoSymbol: "BTC",
        totalInvested: "---", fiatSymbol: "CZK",
        portfolioValue: nil, roiPercent: nil,
        planCount: 0, activePlanCount: 0,
        updatedAt: Date()
    )
}

// MARK: - Timeline

struct AccBotEntry: TimelineEntry {
    let date: Date
    let data: WidgetData
}

struct AccBotProvider: TimelineProvider {
    func placeholder(in context: Context) -> AccBotEntry {
        AccBotEntry(date: Date(), data: .empty)
    }

    func getSnapshot(in context: Context, completion: @escaping (AccBotEntry) -> Void) {
        completion(AccBotEntry(date: Date(), data: loadData()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AccBotEntry>) -> Void) {
        let entry = AccBotEntry(date: Date(), data: loadData())
        let next = Calendar.current.date(byAdding: .minute, value: 30, to: Date())!
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    private func loadData() -> WidgetData {
        guard let defaults = UserDefaults(suiteName: "group.com.accbot.dca"),
              let data = defaults.data(forKey: "widgetData"),
              let decoded = try? JSONDecoder().decode(WidgetData.self, from: data)
        else { return .empty }
        return decoded
    }
}

// MARK: - Widget Entry View

struct AccBotWidgetEntryView: View {
    var entry: AccBotEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        switch family {
        case .systemMedium:
            mediumView
        default:
            smallView
        }
    }

    // MARK: Small

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 6) {
            logo
            Spacer()

            Text(entry.data.totalCrypto)
                .font(.system(.title3, weight: .semibold))
                .foregroundStyle(.green)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(entry.data.cryptoSymbol)
                .font(.caption2)
                .foregroundStyle(.secondary)

            Spacer()

            if let next = entry.data.nextExecutionAt {
                HStack(spacing: 4) {
                    Image(systemName: "clock")
                        .font(.caption2)
                    Text(next, style: .relative)
                        .font(.caption2)
                        .lineLimit(1)
                }
                .foregroundStyle(.secondary)
            }

            HStack(spacing: 4) {
                Circle()
                    .fill(entry.data.activePlanCount > 0 ? .green : .gray)
                    .frame(width: 6, height: 6)
                Text("\(entry.data.activePlanCount)/\(entry.data.planCount)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: Medium

    private var mediumView: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                logo
                Spacer()
                Text(entry.data.totalCrypto)
                    .font(.system(.title2, weight: .semibold))
                    .foregroundStyle(.green)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text(entry.data.cryptoSymbol)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                if let next = entry.data.nextExecutionAt {
                    HStack(spacing: 4) {
                        Image(systemName: "clock")
                            .font(.caption2)
                        Text(next, style: .relative)
                            .font(.caption2)
                            .lineLimit(1)
                    }
                    .foregroundStyle(.secondary)
                }
            }

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                stat(label: "Invested", value: "\(entry.data.totalInvested) \(entry.data.fiatSymbol)")
                if let pv = entry.data.portfolioValue {
                    stat(label: "Value", value: "\(pv) \(entry.data.fiatSymbol)")
                }
                if let roi = entry.data.roiPercent {
                    stat(label: "ROI", value: roi,
                         color: roi.hasPrefix("+") ? .green : roi.hasPrefix("-") ? .red : .primary)
                }
                Spacer()
                HStack(spacing: 4) {
                    Circle()
                        .fill(entry.data.activePlanCount > 0 ? .green : .gray)
                        .frame(width: 6, height: 6)
                    Text("\(entry.data.activePlanCount) active")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    // MARK: Helpers

    private var logo: some View {
        HStack(spacing: 0) {
            Text("Acc").font(.system(.headline, weight: .bold))
            Text("\u{20BF}").font(.system(.headline, weight: .bold)).foregroundStyle(.orange)
            Text("ot").font(.system(.headline, weight: .bold))
        }
    }

    private func stat(label: String, value: String, color: Color = .primary) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Text(value).font(.system(.caption, weight: .semibold)).foregroundStyle(color)
                .lineLimit(1).minimumScaleFactor(0.7)
        }
    }
}

// MARK: - Widget

struct AccBotWidget: Widget {
    let kind = "AccBotWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: AccBotProvider()) { entry in
            if #available(iOSApplicationExtension 17.0, *) {
                AccBotWidgetEntryView(entry: entry)
                    .containerBackground(.fill.tertiary, for: .widget)
            } else {
                AccBotWidgetEntryView(entry: entry)
                    .padding()
                    .background()
            }
        }
        .configurationDisplayName("AccBot DCA")
        .description("Portfolio overview and DCA plan status")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct AccBotWidgetBundle: WidgetBundle {
    var body: some Widget {
        AccBotWidget()
    }
}
