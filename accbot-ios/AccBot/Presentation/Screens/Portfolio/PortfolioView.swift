import SwiftUI
import Charts

struct PortfolioView: View {
    @EnvironmentObject var dependencies: AppDependencies
    @StateObject private var viewModel: PortfolioViewModel

    init() {
        _viewModel = StateObject(wrappedValue: PortfolioViewModel(
            dependencies: AppDependencies()
        ))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                if viewModel.isLoading {
                    LoadingStateView(message: "Loading portfolio...")
                } else if viewModel.pairs.isEmpty {
                    EmptyStateView(
                        icon: "chart.pie",
                        title: "No Portfolio Data",
                        subtitle: "Complete your first DCA purchase to see portfolio analytics"
                    )
                } else {
                    pairPager
                    kpiSection
                    chartSection
                    seriesSelector
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.xxl)
        }
        .background(Color.backgroundDark)
        .navigationTitle("Portfolio")
        .task {
            await viewModel.loadData()
        }
    }

    // MARK: - Pair Pager

    private var pairPager: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(Array(viewModel.pairs.enumerated()), id: \.offset) { index, pair in
                    Text("\(pair.crypto)/\(pair.fiat)")
                        .font(AccBotFonts.label)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, Spacing.sm)
                        .background(index == viewModel.selectedPairIndex
                                    ? Color.accentTeal : Color.surfaceDark)
                        .foregroundColor(index == viewModel.selectedPairIndex
                                         ? Color.surfaceDark : .white)
                        .cornerRadius(CornerRadius.xl)
                        .onTapGesture {
                            viewModel.selectPair(at: index)
                        }
                }
            }
        }
    }

    // MARK: - KPI Section

    private var kpiSection: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: Spacing.sm) {
            kpiCard(
                title: "Portfolio Value",
                value: viewModel.portfolioValue.map { formatFiat($0) } ?? "—",
                subtitle: viewModel.currentPair?.fiat
            )
            kpiCard(
                title: "ROI",
                value: viewModel.roiPercent.map { String(format: "%+.1f%%", NSDecimalNumber(decimal: $0).doubleValue) } ?? "—",
                subtitle: nil,
                valueColor: (viewModel.roiPercent ?? 0) >= 0 ? .accentTeal : .errorRed
            )
            kpiCard(
                title: "Avg Buy Price",
                value: formatFiat(viewModel.avgBuyPrice),
                subtitle: viewModel.currentPair?.fiat
            )
            kpiCard(
                title: "Transactions",
                value: "\(viewModel.transactionCount)",
                subtitle: nil
            )
        }
    }

    private func kpiCard(title: String, value: String, subtitle: String?, valueColor: Color = .white) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(AccBotFonts.caption)
                .foregroundColor(.onSurfaceVariantColor)
            Text(value)
                .font(AccBotFonts.titleSmall)
                .foregroundColor(valueColor)
            if let subtitle = subtitle {
                Text(subtitle)
                    .font(AccBotFonts.captionSmall)
                    .foregroundColor(.onSurfaceVariantColor)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Chart

    private var chartSection: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(viewModel.selectedChartSeries.rawValue)
                .font(AccBotFonts.headline)
                .foregroundColor(.white)

            if viewModel.chartData.isEmpty {
                Text("Not enough data for chart")
                    .font(AccBotFonts.bodySmall)
                    .foregroundColor(.onSurfaceVariantColor)
                    .frame(height: 200)
                    .frame(maxWidth: .infinity)
            } else {
                Chart(viewModel.chartData) { point in
                    LineMark(
                        x: .value("Date", point.date),
                        y: .value("Value", NSDecimalNumber(decimal: point.value).doubleValue)
                    )
                    .foregroundStyle(Color.accentTeal)

                    AreaMark(
                        x: .value("Date", point.date),
                        y: .value("Value", NSDecimalNumber(decimal: point.value).doubleValue)
                    )
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Color.accentTeal.opacity(0.3), Color.accentTeal.opacity(0.0)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                }
                .chartXAxis {
                    AxisMarks(values: .automatic) { _ in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(Color.onSurfaceVariantColor.opacity(0.3))
                        AxisValueLabel()
                            .foregroundStyle(Color.onSurfaceVariantColor)
                    }
                }
                .chartYAxis {
                    AxisMarks(values: .automatic) { _ in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(Color.onSurfaceVariantColor.opacity(0.3))
                        AxisValueLabel()
                            .foregroundStyle(Color.onSurfaceVariantColor)
                    }
                }
                .frame(height: 250)
            }
        }
        .padding(Spacing.lg)
        .background(Color.surfaceDark)
        .cornerRadius(CornerRadius.md)
    }

    // MARK: - Series Selector

    private var seriesSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(PortfolioViewModel.ChartSeries.allCases, id: \.self) { series in
                    Text(series.rawValue)
                        .font(AccBotFonts.caption)
                        .padding(.horizontal, Spacing.md)
                        .padding(.vertical, Spacing.xs)
                        .background(series == viewModel.selectedChartSeries
                                    ? Color.accentTeal : Color.surfaceDark)
                        .foregroundColor(series == viewModel.selectedChartSeries
                                         ? Color.surfaceDark : .onSurfaceVariantColor)
                        .cornerRadius(CornerRadius.xl)
                        .onTapGesture {
                            viewModel.selectedChartSeries = series
                        }
                }
            }
        }
    }

    private func formatFiat(_ value: Decimal) -> String {
        let formatter = NumberFormatter()
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.numberStyle = .decimal
        return formatter.string(from: NSDecimalNumber(decimal: value)) ?? "0"
    }
}
