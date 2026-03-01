import SwiftUI
import Charts

struct PortfolioView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @EnvironmentObject var router: AppRouter
    @StateObject private var viewModel = PortfolioViewModel()
    @Environment(\.accBotColors) private var colors
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @State private var selectedDate: Date?
    @State private var lastHapticTime: Date = .distantPast
    @State private var sortedPointsBySeries: [PortfolioViewModel.ChartSeries: [PortfolioViewModel.ChartPoint]] = [:]

    private var isLandscape: Bool {
        verticalSizeClass == .compact
    }

    var body: some View {
        ScrollView {
            Group {
                if viewModel.isLoading {
                    LoadingStateView(message: "Loading portfolio...")
                        .padding(.horizontal, Spacing.lg)
                } else if viewModel.pages.isEmpty {
                    EmptyStateView(
                        systemImage: "chart.pie",
                        title: String(localized: "No Portfolio Data"),
                        subtitle: String(localized: "Complete your first DCA purchase to see portfolio analytics")
                    )
                    .padding(.horizontal, Spacing.lg)
                } else if isLandscape {
                    landscapeLayout
                } else {
                    portraitLayout
                }
            }
            .maxFormWidth()
        }
        .background(colors.background)
        .navigationTitle(String(localized: "Portfolio"))
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if let pair = viewModel.currentPair, pair.crypto != "ALL" {
                    Button {
                        router.navigate(to: .history(crypto: pair.crypto, fiat: pair.fiat))
                    } label: {
                        Image(systemName: "clock.arrow.circlepath")
                    }
                    .accessibilityLabel(String(localized: "View transaction history"))
                }
            }
        }
        .refreshable {
            await viewModel.refresh()
        }
        .task {
            viewModel.setup(dependencies)
            await viewModel.loadData()
            // Auto-select pair if navigated from Dashboard holdings
            if let crypto = router.portfolioSelectedCrypto,
               let fiat = router.portfolioSelectedFiat {
                viewModel.selectPair(crypto: crypto, fiat: fiat)
                router.portfolioSelectedCrypto = nil
                router.portfolioSelectedFiat = nil
            }
        }
    }

    // MARK: - Portrait Layout

    private var portraitLayout: some View {
        VStack(spacing: Spacing.lg) {
            pairPager
            controlsRow
            kpiSection
            chartSection
            zoomHeader
            legendSection
            drillDownChips
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.xxl)
    }

    // MARK: - Landscape Layout (two-pane: chart left, controls right)

    private var landscapeLayout: some View {
        VStack(spacing: Spacing.sm) {
            pairPager
                .padding(.horizontal, Spacing.lg)

            HStack(alignment: .top, spacing: Spacing.lg) {
                // Left pane: chart
                VStack(spacing: Spacing.sm) {
                    zoomHeader
                    chartSection
                    legendSection
                }
                .frame(maxWidth: .infinity)

                // Right pane: controls + KPIs
                VStack(spacing: Spacing.sm) {
                    controlsRow
                    kpiSection
                    drillDownChips
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.xxl)
        }
    }

    // MARK: - Pair Pager

    private var pairPager: some View {
        VStack(spacing: Spacing.xs) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(Array(viewModel.pages.enumerated()), id: \.offset) { index, page in
                        Button {
                            viewModel.selectPage(at: index)
                        } label: {
                            Text(page.label)
                                .font(AccBotFonts.label)
                                .padding(.horizontal, Spacing.lg)
                                .padding(.vertical, Spacing.sm)
                                .frame(minHeight: 44)
                                .background(index == viewModel.selectedPageIndex
                                            ? colors.primary : colors.surface)
                                .foregroundStyle(index == viewModel.selectedPageIndex
                                                 ? colors.onPrimary : colors.onSurface)
                                .clipShape(RoundedRectangle(cornerRadius: CornerRadius.xl))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(page.label)
                        .accessibilityAddTraits(index == viewModel.selectedPageIndex ? .isSelected : [])
                        .accessibilityValue(index == viewModel.selectedPageIndex ? String(localized: "Selected") : String(localized: "Not selected"))
                    }
                }
            }

        }
    }

    // MARK: - Controls Row (Denomination + Exchange Filter)

    private var controlsRow: some View {
        HStack(spacing: Spacing.md) {
            // Denomination toggle
            Picker(String(localized: "Denomination"), selection: Binding(
                get: { viewModel.denomination },
                set: { viewModel.setDenomination($0) }
            )) {
                Text(String(localized: "FIAT")).tag(PortfolioViewModel.Denomination.fiat)
                Text(String(localized: "CRYPTO")).tag(PortfolioViewModel.Denomination.crypto)
            }
            .pickerStyle(.segmented)
            .labelsHidden()
            .frame(maxWidth: 150)

            Spacer()

            // Exchange filter
            if viewModel.availableExchanges.count > 1 {
                Menu {
                    Button(String(localized: "All Exchanges")) {
                        viewModel.setExchangeFilter(nil)
                    }
                    ForEach(viewModel.availableExchanges, id: \.self) { exchange in
                        Button(exchange.displayName) {
                            viewModel.setExchangeFilter(exchange)
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "line.3.horizontal.decrease.circle")
                        Text(viewModel.exchangeFilter?.displayName ?? String(localized: "All"))
                            .font(AccBotFonts.caption)
                    }
                    .padding(.horizontal, Spacing.md)
                    .padding(.vertical, Spacing.xs)
                    .frame(minHeight: 44)
                    .background(colors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: CornerRadius.xl))
                    .foregroundStyle(viewModel.exchangeFilter != nil ? colors.primary : colors.onSurfaceVariant)
                }
                .accessibilityLabel(String(localized: "Filter by exchange: \(viewModel.exchangeFilter?.displayName ?? String(localized: "All"))"))
            }
        }
    }

    // MARK: - Scrubbed KPI

    private var scrubbedKpi: PortfolioViewModel.KpiSnapshot? {
        guard let date = selectedDate, !viewModel.kpiSnapshots.isEmpty else { return nil }
        let snapshots = viewModel.kpiSnapshots
        let target = date.timeIntervalSince1970
        // Binary search for closest date (kpiSnapshots are sorted chronologically)
        var lo = 0, hi = snapshots.count - 1
        while lo < hi {
            let mid = (lo + hi) / 2
            if snapshots[mid].date.timeIntervalSince1970 < target { lo = mid + 1 }
            else { hi = mid }
        }
        let candidates = [max(0, lo - 1), min(lo, snapshots.count - 1)]
        return candidates.map { snapshots[$0] }
            .min(by: { abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date)) })
    }

    private var isScrubbing: Bool { selectedDate != nil }

    // MARK: - KPI Section

    private var kpiSection: some View {
        VStack(spacing: Spacing.sm) {
            if isScrubbing, let snap = scrubbedKpi {
                // Scrub date indicator
                Text(snap.date.formatted(date: .abbreviated, time: .omitted))
                    .font(AccBotFonts.caption)
                    .foregroundStyle(colors.primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: Spacing.sm) {
                if let snap = scrubbedKpi, isScrubbing {
                    kpiCard(
                        title: viewModel.denomination == .fiat ? String(localized: "Portfolio Value") : String(localized: "Crypto Held"),
                        value: viewModel.denomination == .fiat
                            ? formatFiat(snap.portfolioValue)
                            : formatCrypto(snap.cumulativeCrypto),
                        subtitle: viewModel.denomination == .fiat ? viewModel.currentPair?.fiat : viewModel.currentPair?.crypto
                    )
                    kpiCard(
                        title: String(localized: "ROI"),
                        value: snap.roiPercent.map { AccBotFormatters.formatSignedPercent($0) } ?? "---",
                        subtitle: (snap.roiPercent ?? 0) >= 0 ? String(localized: "Gain") : String(localized: "Loss"),
                        valueColor: (snap.roiPercent ?? 0) >= 0 ? colors.primary : colors.error
                    )
                    kpiCard(
                        title: String(localized: "Invested"),
                        value: formatFiat(snap.totalInvested),
                        subtitle: viewModel.currentPair?.fiat
                    )
                    kpiCard(
                        title: String(localized: "Avg Buy Price"),
                        value: formatFiat(snap.avgBuyPrice),
                        subtitle: viewModel.currentPair?.fiat
                    )
                } else {
                    kpiCard(
                        title: viewModel.denomination == .fiat ? String(localized: "Portfolio Value") : String(localized: "Crypto Held"),
                        value: viewModel.denomination == .fiat
                            ? (viewModel.portfolioValue.map { formatFiat($0) } ?? "---")
                            : formatCrypto(viewModel.totalCrypto),
                        subtitle: viewModel.denomination == .fiat ? viewModel.currentPair?.fiat : viewModel.currentPair?.crypto
                    )
                    kpiCard(
                        title: String(localized: "ROI"),
                        value: viewModel.roiPercent.map { AccBotFormatters.formatSignedPercent($0) } ?? "---",
                        subtitle: (viewModel.roiPercent ?? 0) >= 0 ? String(localized: "Gain") : String(localized: "Loss"),
                        valueColor: (viewModel.roiPercent ?? 0) >= 0 ? colors.primary : colors.error
                    )
                    kpiCard(
                        title: String(localized: "Invested"),
                        value: formatFiat(viewModel.totalInvested),
                        subtitle: viewModel.currentPair?.fiat
                    )
                    kpiCard(
                        title: String(localized: "Avg Buy Price"),
                        value: formatFiat(viewModel.avgBuyPrice),
                        subtitle: viewModel.currentPair?.fiat
                    )
                }
            }

            // Single-pair extra KPIs: Crypto Price + Accumulated Crypto
            if case .singlePair = viewModel.currentPage {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: Spacing.sm) {
                    if let snap = scrubbedKpi, isScrubbing {
                        kpiCard(
                            title: String(localized: "Crypto Price"),
                            value: viewModel.currentPrice.map { formatFiat($0) } ?? "---",
                            subtitle: viewModel.currentPair?.fiat
                        )
                        kpiCard(
                            title: String(localized: "Accumulated"),
                            value: formatCrypto(snap.cumulativeCrypto),
                            subtitle: viewModel.currentPair?.crypto
                        )
                    } else {
                        kpiCard(
                            title: String(localized: "Crypto Price"),
                            value: viewModel.currentPrice.map { formatFiat($0) } ?? "---",
                            subtitle: viewModel.currentPair?.fiat
                        )
                        kpiCard(
                            title: String(localized: "Accumulated"),
                            value: formatCrypto(viewModel.totalCrypto),
                            subtitle: viewModel.currentPair?.crypto
                        )
                    }
                }
            }

            // Period ROI display when zoomed and not scrubbing
            if !isScrubbing,
               let periodRoi = viewModel.periodRoiPercent,
               let label = viewModel.periodRoiLabel {
                HStack(spacing: Spacing.xs) {
                    Image(systemName: periodRoi >= 0 ? "arrow.up.right" : "arrow.down.right")
                        .font(AccBotFonts.caption)
                    Text(String(localized: "\(AccBotFormatters.formatSignedPercent(periodRoi)) in \(label)"))
                        .font(AccBotFonts.bodySmall)
                        .lineLimit(1)
                }
                .foregroundStyle(periodRoi >= 0 ? colors.primary : colors.error)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.sm)
            }
        }
    }

    private func kpiCard(title: String, value: String, subtitle: String?, valueColor: Color? = nil) -> some View {
        let resolvedColor = valueColor ?? colors.onSurface
        return VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(AccBotFonts.caption)
                .foregroundStyle(colors.onSurfaceVariant)
            Text(value)
                .font(AccBotFonts.titleSmall)
                .foregroundStyle(resolvedColor)
            if let subtitle = subtitle {
                Text(subtitle)
                    .font(AccBotFonts.captionSmall)
                    .foregroundStyle(colors.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Zoom Header

    private var zoomHeader: some View {
        Group {
            if viewModel.zoomLevel != .overview {
                HStack {
                    Button {
                        viewModel.zoomOut()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text(String(localized: "Zoom Out"))
                        }
                        .font(AccBotFonts.caption)
                        .foregroundStyle(colors.primary)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .accessibilityLabel(String(localized: "Zoom out to wider time range"))

                    Spacer()

                    Button { viewModel.navigatePrev() } label: {
                        Image(systemName: "chevron.left")
                            .foregroundStyle(colors.primary)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel(String(localized: "Previous period"))

                    Text(viewModel.zoomLevel.title)
                        .font(AccBotFonts.body)
                        .foregroundStyle(colors.onSurface)

                    Button { viewModel.navigateNext() } label: {
                        Image(systemName: "chevron.right")
                            .foregroundStyle(colors.primary)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel(String(localized: "Next period"))
                }
                .padding(.horizontal, Spacing.sm)
            }
        }
    }

    // MARK: - Chart

    private var chartSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            // Scrub tooltip
            if let date = selectedDate, let nearest = nearestChartPoints(to: date) {
                scrubTooltip(date: date, points: nearest)
            }

            if viewModel.chartData.isEmpty {
                Text(String(localized: "Not enough data for chart"))
                    .font(AccBotFonts.bodySmall)
                    .foregroundStyle(colors.onSurfaceVariant)
                    .frame(height: 200)
                    .frame(maxWidth: .infinity)
            } else {
                Chart {
                    ForEach(viewModel.chartData) { point in
                        LineMark(
                            x: .value("Date", point.date),
                            y: .value("Value", point.value)
                        )
                        .foregroundStyle(by: .value("Series", point.series.localizedName))

                        AreaMark(
                            x: .value("Date", point.date),
                            y: .value("Value", point.value)
                        )
                        .foregroundStyle(by: .value("Series", point.series.localizedName))
                        .opacity(0.1)
                    }

                    if let date = selectedDate {
                        RuleMark(x: .value("Selected", date))
                            .foregroundStyle(colors.onSurfaceVariant)
                            .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                    }
                }
                .chartForegroundStyleScale([
                    PortfolioViewModel.ChartSeries.portfolioValue.localizedName: colors.primary,
                    PortfolioViewModel.ChartSeries.costBasis.localizedName: costBasisColor,
                    PortfolioViewModel.ChartSeries.cryptoPrice.localizedName: colors.warning,
                    PortfolioViewModel.ChartSeries.avgBuyPrice.localizedName: avgBuyPriceColor,
                    PortfolioViewModel.ChartSeries.accumulatedCrypto.localizedName: colors.success,
                ])
                .chartLegend(.hidden)
                .chartXSelectionIfAvailable(value: $selectedDate)
                .onChange(of: selectedDate) { _ in
                    let now = Date()
                    if now.timeIntervalSince(lastHapticTime) > 0.1 {
                        lastHapticTime = now
                        let generator = UIImpactFeedbackGenerator(style: .light)
                        generator.impactOccurred()
                    }
                }
                .onChange(of: viewModel.chartData.count) { _ in
                    sortedPointsBySeries = Dictionary(grouping: viewModel.chartData) { $0.series }
                }
                .chartXAxis {
                    AxisMarks(values: .automatic) { _ in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(colors.onSurfaceVariant.opacity(0.5))
                        AxisValueLabel()
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .chartYAxis {
                    AxisMarks(values: .automatic) { _ in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(colors.onSurfaceVariant.opacity(0.5))
                        AxisValueLabel()
                            .foregroundStyle(colors.onSurfaceVariant)
                    }
                }
                .frame(height: 220)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(chartAccessibilitySummary)
                .accessibilityHint(String(localized: "Swipe left or right to scrub through chart data points"))
            }
        }
        .padding(Spacing.lg)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.md))
    }

    // MARK: - Scrub Tooltip

    private func nearestChartPoints(to date: Date) -> [PortfolioViewModel.ChartPoint]? {
        var result = [PortfolioViewModel.ChartPoint]()
        let target = date.timeIntervalSince1970
        for (_, points) in sortedPointsBySeries {
            guard !points.isEmpty else { continue }
            // Binary search for closest date
            var lo = 0, hi = points.count - 1
            while lo < hi {
                let mid = (lo + hi) / 2
                if points[mid].date.timeIntervalSince1970 < target { lo = mid + 1 }
                else { hi = mid }
            }
            // Check lo and lo-1 for closest
            let candidates = [max(0, lo - 1), lo].map { points[min($0, points.count - 1)] }
            if let nearest = candidates.min(by: { abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date)) }) {
                result.append(nearest)
            }
        }
        return result.isEmpty ? nil : result
    }

    private func scrubTooltip(date: Date, points: [PortfolioViewModel.ChartPoint]) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(date.formatted(date: .abbreviated, time: .omitted))
                .font(AccBotFonts.captionSmall)
                .foregroundStyle(colors.onSurfaceVariant)

            ForEach(points, id: \.series) { point in
                HStack(spacing: Spacing.xs) {
                    Circle()
                        .fill(seriesColor(point.series))
                        .frame(width: 6, height: 6)
                    Text("\(point.series.localizedName): \(formatTooltipValue(point.value))")
                        .font(AccBotFonts.captionSmall)
                        .foregroundStyle(colors.onSurface)
                }
            }
        }
        .padding(Spacing.sm)
        .background(colors.surface.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.sm))
        .overlay(
            RoundedRectangle(cornerRadius: CornerRadius.sm)
                .strokeBorder(colors.onSurfaceVariant.opacity(0.5), lineWidth: 1)
        )
    }

    private func formatTooltipValue(_ value: Double) -> String {
        AccBotFormatters.formatTooltip(Decimal(value))
    }

    // MARK: - Interactive Legend

    private var legendSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(applicableSeries, id: \.self) { series in
                    let isVisible = viewModel.visibleSeries.contains(series)
                    Button {
                        viewModel.toggleSeries(series)
                    } label: {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(seriesColor(series))
                                .frame(width: 8, height: 8)
                            Text(series.localizedName)
                                .font(AccBotFonts.caption)
                        }
                        .padding(.horizontal, Spacing.md)
                        .padding(.vertical, Spacing.xs)
                        .frame(minHeight: 44)
                        .background(isVisible ? seriesColor(series).opacity(0.2) : colors.surface)
                        .foregroundStyle(isVisible ? colors.onSurface : colors.onSurfaceVariant)
                        .clipShape(RoundedRectangle(cornerRadius: CornerRadius.xl))
                        .overlay(
                            RoundedRectangle(cornerRadius: CornerRadius.xl)
                                .strokeBorder(isVisible ? seriesColor(series) : Color.clear, lineWidth: 1)
                        )
                    }
                    .accessibilityLabel(series.localizedName)
                    .accessibilityValue(isVisible ? String(localized: "Visible") : String(localized: "Hidden"))
                }
            }
        }
    }

    private var applicableSeries: [PortfolioViewModel.ChartSeries] {
        if case .aggregate = viewModel.currentPage {
            return [.portfolioValue, .costBasis]
        }
        if viewModel.denomination == .fiat {
            return [.portfolioValue, .costBasis, .cryptoPrice, .avgBuyPrice, .accumulatedCrypto]
        }
        return [.portfolioValue, .costBasis, .cryptoPrice, .accumulatedCrypto]
    }

    /// Distinct cost basis color that won't blend with chart grid lines.
    private var costBasisColor: Color {
        Color(hex: 0x8E99A4)
    }

    /// Purple color matching Android's avg buy price line (#9C27B0).
    private var avgBuyPriceColor: Color {
        Color(red: 0.61, green: 0.15, blue: 0.69)
    }

    private func seriesColor(_ series: PortfolioViewModel.ChartSeries) -> Color {
        switch series {
        case .portfolioValue: return colors.primary
        case .costBasis: return costBasisColor
        case .cryptoPrice: return colors.warning
        case .avgBuyPrice: return avgBuyPriceColor
        case .accumulatedCrypto: return colors.success
        }
    }

    // MARK: - Drill-Down Chips

    private var drillDownChips: some View {
        Group {
            if viewModel.zoomLevel == .overview && !viewModel.availableYears.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(viewModel.availableYears, id: \.self) { year in
                            Button("\(year)") {
                                viewModel.drillDown(year: year)
                            }
                            .font(AccBotFonts.caption)
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.xs)
                            .frame(minHeight: 44)
                            .background(colors.surface)
                            .foregroundStyle(colors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.xl))
                        }
                    }
                }
            } else if case .year = viewModel.zoomLevel, !viewModel.availableMonths.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: Spacing.sm) {
                        ForEach(viewModel.availableMonths, id: \.self) { month in
                            Button(AccBotFormatters.monthNameFromComponents(month: month)) {
                                viewModel.drillDown(month: month)
                            }
                            .font(AccBotFonts.caption)
                            .padding(.horizontal, Spacing.md)
                            .padding(.vertical, Spacing.xs)
                            .frame(minHeight: 44)
                            .background(colors.surface)
                            .foregroundStyle(colors.primary)
                            .clipShape(RoundedRectangle(cornerRadius: CornerRadius.xl))
                        }
                    }
                }
            }
        }
    }

    // MARK: - Chart Accessibility

    private var chartAccessibilitySummary: String {
        let value = viewModel.portfolioValue.map { formatFiat($0) } ?? "---"
        let roi = viewModel.roiPercent.map { AccBotFormatters.formatSignedPercent($0) } ?? "---"
        let count = viewModel.transactionCount
        return String(localized: "Portfolio chart. Value: \(value), ROI: \(roi), \(count) transactions. Use legend to toggle series.")
    }

    // MARK: - Formatters

    private func formatFiat(_ value: Decimal) -> String {
        AccBotFormatters.formatFiatPlain(value)
    }

    private func formatCrypto(_ value: Decimal) -> String {
        AccBotFormatters.formatCryptoPlain(value)
    }
}

// MARK: - iOS 17 chart selection compatibility

private extension View {
    @ViewBuilder
    func chartXSelectionIfAvailable(value: Binding<Date?>) -> some View {
        if #available(iOS 17.0, *) {
            self.chartXSelection(value: value)
        } else {
            self
        }
    }
}
