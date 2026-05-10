package com.accbot.dca.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.accbot.dca.domain.usecase.ChartDataPoint
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.presentation.screens.portfolio.CryptoGroupLineInfo
import com.accbot.dca.presentation.screens.portfolio.CryptoGroupLineType
import com.accbot.dca.presentation.screens.portfolio.DenominationMode
import com.accbot.dca.presentation.screens.portfolio.PlanLineInfo
import com.accbot.dca.presentation.screens.portfolio.PlanLineType
import com.accbot.dca.presentation.ui.theme.Primary
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.accbot.dca.domain.model.TransactionSide
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberShowOnPress
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.DashedShape
import com.patrykandpatrick.vico.core.common.shape.Shape
import androidx.compose.ui.graphics.toArgb
import java.time.ZoneId

private val chartAccentColor = Primary
private val costBasisColor = Color(0xFF888888)
internal val btcPriceColor = Color(0xFFF7931A)
internal val accumulatedCryptoColor = Color(0xFF4CAF50)
internal val avgBuyPriceColor = Color(0xFF9C27B0)

/**
 * 16 visually distinct colors for per-plan-metric lines. One color per
 * (plan, metric) combination, cycling after 4 plans (4 plans * 4 metrics = 16).
 */
internal val distinctLineColors = listOf(
    Color(0xFFE53935), // 0  red
    Color(0xFF1E88E5), // 1  blue
    Color(0xFF43A047), // 2  green
    Color(0xFFFB8C00), // 3  orange
    Color(0xFF8E24AA), // 4  purple
    Color(0xFF00ACC1), // 5  cyan
    Color(0xFFFDD835), // 6  yellow
    Color(0xFF6D4C41), // 7  brown
    Color(0xFFEC407A), // 8  pink
    Color(0xFF00897B), // 9  teal
    Color(0xFF3949AB), // 10 indigo
    Color(0xFFF4511E), // 11 deep orange
    Color(0xFF7CB342), // 12 light green
    Color(0xFF5E35B1), // 13 deep purple
    Color(0xFF546E7A), // 14 blue grey
    Color(0xFFAFB42B), // 15 lime
)

/** Back-compat alias used by older code paths that only show one color per plan. */
internal val planLineColors = distinctLineColors

/**
 * Assigns a distinct color index for each (plan index, metric) combination.
 * Plan 0 gets indices 0..3, Plan 1 gets 4..7, etc. Cycles modulo 16.
 */
internal fun distinctColorIdx(planIdx: Int, metricOrdinal: Int): Int =
    ((planIdx * 4) + metricOrdinal) % 16

internal val cryptoGroupColors = mapOf(
    "BTC" to Color(0xFFF7931A),
    "ETH" to Color(0xFF627EEA),
    "LTC" to Color(0xFFA6A9AA),
    "BCH" to Color(0xFF8DC351),
    "XRP" to Color(0xFF00A5E0),
    "ADA" to Color(0xFF0033AD),
    "SOL" to Color(0xFF9945FF),
    "DOT" to Color(0xFFE6007A),
)
internal val defaultCryptoGroupColor = Color(0xFF888888)
internal fun colorForCrypto(crypto: String): Color =
    cryptoGroupColors[crypto] ?: defaultCryptoGroupColor

data class LegendEntry(
    val seriesIndex: Int,
    val label: String,
    val color: Color
)

/**
 * Interactive chart legend – tap a label to show/hide its series.
 * Renders entries in rows of 2.
 */
@Composable
fun InteractiveChartLegend(
    entries: List<LegendEntry>,
    visibleSeries: Set<Int> = setOf(0, 1),
    onToggleSeries: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.Center) {
                row.forEachIndexed { i, entry ->
                    if (i > 0) Spacer(Modifier.width(24.dp))
                    LegendItem(
                        color = entry.color,
                        label = entry.label,
                        enabled = entry.seriesIndex in visibleSeries,
                        onClick = { onToggleSeries(entry.seriesIndex) }
                    )
                }
            }
        }
    }
}

/**
 * Static (non-interactive) legend entry describing the dashed red horizontal lines
 * that mark open sell-order limit prices on the per-plan chart. Shown only when
 * the chart actually renders such lines (i.e. plan allows sells, FIAT mode, and
 * at least one open sell exists). Mirrors the dashed look used on the chart with
 * a tiny dashed mini-line as the colour swatch.
 */
@Composable
fun LimitOrderLegendItem(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val baseColor = MaterialTheme.colorScheme.error
    val swatchColor = if (enabled) baseColor else baseColor.copy(alpha = 0.3f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp)
    ) {
        // Mini dashed line: 3 short segments to evoke the chart's dash pattern.
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                if (i > 0) Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(width = 4.dp, height = 2.dp)
                        .background(swatchColor)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textDecoration = if (enabled) null else TextDecoration.LineThrough
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (enabled) color else color.copy(alpha = 0.3f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textDecoration = if (enabled) null else TextDecoration.LineThrough
        )
    }
}

/**
 * BUY/SELL transaction marker for the portfolio chart timeline.
 *
 * Rendered by [TradeMarkersDecoration] as a small triangle at the bottom (BUY,
 * up-triangle, success color) or top (SELL, down-triangle, error color) of the
 * plot area, x-aligned with the chart bucket that contains the trade.
 */
data class ChartTradeMarker(
    val time: Instant,
    val side: TransactionSide
)

/**
 * Custom Vico [Decoration] that renders BUY/SELL trade markers as triangles
 * pinned to the plot area edges, aligned with chart-bucket indices.
 *
 * Coordinate math: each [ChartDataPoint] is at integer model x = its array
 * index, so a marker for chart index i lives at model x = i.toDouble(). The
 * pixel x is derived from layerBounds + layerDimensions.startPadding plus the
 * scaled offset relative to ranges.minX, minus the current scroll.
 */
private class TradeMarkersDecoration(
    private val markers: List<Pair<Double, TransactionSide>>,
    private val buyColorArgb: Int,
    private val sellColorArgb: Int,
    private val sizeDp: Float = 8f
) : Decoration {

    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }

    override fun drawOverLayers(context: com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext) {
        if (markers.isEmpty()) return
        val bounds = context.layerBounds
        val dims = context.layerDimensions
        val ranges = context.ranges
        val sizePx = context.dpToPx(sizeDp)
        val halfSize = sizePx / 2f
        val scroll = context.scroll

        for ((x, side) in markers) {
            val modelDelta = x - ranges.minX
            val pxX = bounds.left + dims.startPadding + (modelDelta * dims.xSpacing).toFloat() - scroll
            if (pxX < bounds.left - halfSize || pxX > bounds.right + halfSize) continue
            val (color, apexY, baseY) = when (side) {
                TransactionSide.BUY -> Triple(buyColorArgb, bounds.bottom - sizePx, bounds.bottom)
                TransactionSide.SELL -> Triple(sellColorArgb, bounds.top + sizePx, bounds.top)
            }
            paint.color = color
            val path = android.graphics.Path().apply {
                moveTo(pxX, apexY)
                lineTo(pxX - halfSize, baseY)
                lineTo(pxX + halfSize, baseY)
                close()
            }
            context.canvas.drawPath(path, paint)
        }
    }
}

/**
 * Portfolio line chart with dual Y-axis support.
 * Left axis (start): portfolio value, cost basis, crypto price (all in fiat).
 * Right axis (end): accumulated crypto (in crypto units, e.g. BTC).
 * Scrubbing fires onScrub to update KPI cards above the chart.
 */
@Composable
fun PortfolioLineChart(
    chartData: List<ChartDataPoint>,
    denominationMode: DenominationMode = DenominationMode.FIAT,
    unitSuffix: String = "",
    fiatSymbol: String = "",
    cryptoSymbol: String = "",
    visibleSeries: Set<Int> = setOf(0, 1),
    planLines: List<PlanLineInfo> = emptyList(),
    visiblePlanLines: Set<Pair<Long, PlanLineType>> = emptySet(),
    cryptoGroupLines: List<CryptoGroupLineInfo> = emptyList(),
    visibleCryptoGroupLines: Set<Pair<String, CryptoGroupLineType>> = emptySet(),
    zoomLevel: ChartZoomLevel = ChartZoomLevel.Overview,
    onScrub: (Int?) -> Unit = {},
    /**
     * Optional BUY (green up-triangle) / SELL (red down-triangle) markers to render
     * on the chart timeline. Markers are bucketed to the chart point whose epochDay
     * is the floor of the trade's epochDay.
     */
    tradeMarkers: List<ChartTradeMarker> = emptyList(),
    /**
     * Limit prices of currently open (PENDING / PARTIAL) sell orders for the
     * displayed plan. Each value yields a horizontal line on the left (fiat) axis
     * to give the user a visual reference for where their orders will fill.
     * Empty for aggregate pages or plans with sells disabled.
     */
    openSellLimitPrices: List<BigDecimal> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (chartData.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }
    val hasRightAxis = (cryptoSymbol.isNotEmpty() && 3 in visibleSeries) ||
        planLines.any { planLine ->
            (planLine.planId to PlanLineType.ACCUMULATED) in visiblePlanLines &&
            planLine.accumulatedSeries.size == chartData.size
        } ||
        cryptoGroupLines.any { cgLine ->
            (cgLine.crypto to CryptoGroupLineType.TOTAL_ACCUMULATED) in visibleCryptoGroupLines &&
            cgLine.totalAccumulatedSeries.size == chartData.size
        }

    // Update model when data, denomination, or visibility changes
    LaunchedEffect(
        chartData,
        denominationMode,
        visibleSeries,
        planLines,
        visiblePlanLines,
        cryptoGroupLines,
        visibleCryptoGroupLines
    ) {
        try {
            modelProducer.runTransaction {
                // Layer 1: left axis (portfolio value, cost basis, crypto price – all fiat)
                lineSeries {
                    val (series0, series1) = when (denominationMode) {
                        DenominationMode.FIAT ->
                            chartData.map { it.portfolioValue.toFloat() } to
                                    chartData.map { it.totalInvested.toFloat() }
                        DenominationMode.CRYPTO ->
                            chartData.map { it.cumulativeCrypto.toFloat() } to
                                    chartData.map { it.investedEquivCrypto.toFloat() }
                    }
                    if (0 in visibleSeries) series(series0)
                    if (1 in visibleSeries) series(series1)
                    if (2 in visibleSeries) series(chartData.map { it.price.toFloat() })
                    if (4 in visibleSeries) series(chartData.map { it.avgBuyPrice.toFloat() })
                    // Per-plan lines (value + invested per plan, only when visible)
                    var anyLeftSeriesAdded = false
                    for (planLine in planLines) {
                        val valueKey = planLine.planId to PlanLineType.VALUE
                        if (valueKey in visiblePlanLines && planLine.valueSeries.size == chartData.size) {
                            series(planLine.valueSeries)
                            anyLeftSeriesAdded = true
                        }
                        val investedKey = planLine.planId to PlanLineType.INVESTED
                        if (investedKey in visiblePlanLines && planLine.investedSeries.size == chartData.size) {
                            series(planLine.investedSeries)
                            anyLeftSeriesAdded = true
                        }
                    }
                    // Per-plan avg buy price (left axis, fiat)
                    for (planLine in planLines) {
                        val key = planLine.planId to PlanLineType.AVG_BUY_PRICE
                        if (key in visiblePlanLines && planLine.avgBuyPriceSeries.size == chartData.size) {
                            series(planLine.avgBuyPriceSeries)
                            anyLeftSeriesAdded = true
                        }
                    }
                    // Per-crypto price (left axis, fiat)
                    for (cgLine in cryptoGroupLines) {
                        val key = cgLine.crypto to CryptoGroupLineType.PRICE
                        if (key in visibleCryptoGroupLines && cgLine.priceSeries.size == chartData.size) {
                            series(cgLine.priceSeries)
                            anyLeftSeriesAdded = true
                        }
                    }
                    if (setOf(0, 1, 2, 4).none { it in visibleSeries } && !anyLeftSeriesAdded) {
                        series(List(chartData.size) { 0f })
                    }
                }
                // Layer 2: right axis (accumulated crypto – BTC units)
                lineSeries {
                    var anyRightSeriesAdded = false
                    if (3 in visibleSeries) {
                        series(chartData.map { it.cumulativeCrypto.toFloat() })
                        anyRightSeriesAdded = true
                    }
                    // Per-plan accumulated (right axis, crypto amount)
                    for (planLine in planLines) {
                        val key = planLine.planId to PlanLineType.ACCUMULATED
                        if (key in visiblePlanLines && planLine.accumulatedSeries.size == chartData.size) {
                            series(planLine.accumulatedSeries)
                            anyRightSeriesAdded = true
                        }
                    }
                    // Per-crypto total accumulated (right axis, crypto amount)
                    for (cgLine in cryptoGroupLines) {
                        val key = cgLine.crypto to CryptoGroupLineType.TOTAL_ACCUMULATED
                        if (key in visibleCryptoGroupLines && cgLine.totalAccumulatedSeries.size == chartData.size) {
                            series(cgLine.totalAccumulatedSeries)
                            anyRightSeriesAdded = true
                        }
                    }
                    if (!anyRightSeriesAdded) {
                        series(List(chartData.size) { 0f })
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e("PortfolioChart", "OOM building chart model, skipping render", e)
        }
    }

    val xLabels = remember(chartData, zoomLevel) {
        val formatter = when (zoomLevel) {
            is ChartZoomLevel.Overview -> {
                val spanDays = if (chartData.size >= 2)
                    chartData.last().epochDay - chartData.first().epochDay else 0L
                if (spanDays > 365) DateTimeFormatter.ofPattern("MMM yyyy")
                else DateTimeFormatter.ofPattern("d MMM")
            }
            is ChartZoomLevel.Year -> DateTimeFormatter.ofPattern("d MMM")
            is ChartZoomLevel.Month -> DateTimeFormatter.ofPattern("d")
        }
        chartData.map { LocalDate.ofEpochDay(it.epochDay).format(formatter) }
    }

    val xAxisSpacing = remember(chartData.size, zoomLevel) {
        when (zoomLevel) {
            is ChartZoomLevel.Overview -> maxOf(1, chartData.size / 6)
            is ChartZoomLevel.Year -> maxOf(1, chartData.size / 8)
            is ChartZoomLevel.Month -> maxOf(1, chartData.size / 7)
        }
    }

    // Always remember all line styles (composable calls can't be conditional)
    val valueLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(chartAccentColor)),
        areaFill = LineCartesianLayer.AreaFill.single(fill(chartAccentColor.copy(alpha = 0.4f)))
    )
    val costBasisLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(costBasisColor))
    )
    val priceLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(btcPriceColor))
    )
    val accumulatedLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(accumulatedCryptoColor))
    )
    val avgBuyPriceLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(avgBuyPriceColor))
    )
    val hiddenLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(Color.Transparent))
    )

    // Pre-create 16 distinct line styles (one per plan-metric combination, cycles after 4 plans).
    // See distinctLineColors for the palette. Styles are used via distinctColorIdx(planIdx, metricOrdinal).
    val distinctLine0 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[0])))
    val distinctLine1 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[1])))
    val distinctLine2 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[2])))
    val distinctLine3 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[3])))
    val distinctLine4 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[4])))
    val distinctLine5 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[5])))
    val distinctLine6 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[6])))
    val distinctLine7 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[7])))
    val distinctLine8 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[8])))
    val distinctLine9 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[9])))
    val distinctLine10 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[10])))
    val distinctLine11 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[11])))
    val distinctLine12 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[12])))
    val distinctLine13 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[13])))
    val distinctLine14 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[14])))
    val distinctLine15 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(distinctLineColors[15])))
    val distinctLineStyles = listOf(
        distinctLine0, distinctLine1, distinctLine2, distinctLine3,
        distinctLine4, distinctLine5, distinctLine6, distinctLine7,
        distinctLine8, distinctLine9, distinctLine10, distinctLine11,
        distinctLine12, distinctLine13, distinctLine14, distinctLine15
    )

    // Crypto group price line styles (full alpha) - pre-allocated for known cryptos
    val btcPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFFF7931A))))
    val ethPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF627EEA))))
    val ltcPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFFA6A9AA))))
    val bchPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF8DC351))))
    val xrpPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF00A5E0))))
    val adaPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF0033AD))))
    val solPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF9945FF))))
    val dotPriceStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFFE6007A))))
    val defaultCryptoPriceStyle = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(defaultCryptoGroupColor)))

    val cryptoPriceStylesMap = mapOf(
        "BTC" to btcPriceStyleLine,
        "ETH" to ethPriceStyleLine,
        "LTC" to ltcPriceStyleLine,
        "BCH" to bchPriceStyleLine,
        "XRP" to xrpPriceStyleLine,
        "ADA" to adaPriceStyleLine,
        "SOL" to solPriceStyleLine,
        "DOT" to dotPriceStyleLine,
    )

    // Crypto group accumulated line styles (alpha 0.6)
    val btcAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFFF7931A).copy(alpha = 0.6f))))
    val ethAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF627EEA).copy(alpha = 0.6f))))
    val ltcAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFFA6A9AA).copy(alpha = 0.6f))))
    val bchAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF8DC351).copy(alpha = 0.6f))))
    val xrpAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF00A5E0).copy(alpha = 0.6f))))
    val adaAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF0033AD).copy(alpha = 0.6f))))
    val solAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFF9945FF).copy(alpha = 0.6f))))
    val dotAccStyleLine = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(Color(0xFFE6007A).copy(alpha = 0.6f))))
    val defaultCryptoAccStyle = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(defaultCryptoGroupColor.copy(alpha = 0.6f))))

    val cryptoAccStylesMap = mapOf(
        "BTC" to btcAccStyleLine,
        "ETH" to ethAccStyleLine,
        "LTC" to ltcAccStyleLine,
        "BCH" to bchAccStyleLine,
        "XRP" to xrpAccStyleLine,
        "ADA" to adaAccStyleLine,
        "SOL" to solAccStyleLine,
        "DOT" to dotAccStyleLine,
    )

    // Build visible line lists for each layer
    val leftLines = buildList<LineCartesianLayer.Line> {
        if (0 in visibleSeries) add(valueLine)
        if (1 in visibleSeries) add(costBasisLine)
        if (2 in visibleSeries) add(priceLine)
        if (4 in visibleSeries) add(avgBuyPriceLine)
        // Per-plan lines (each plan+metric combo gets its own distinct color, cycles after 4 plans)
        planLines.forEachIndexed { planIdx, planLine ->
            val valueKey = planLine.planId to PlanLineType.VALUE
            if (valueKey in visiblePlanLines && planLine.valueSeries.size == chartData.size) {
                add(distinctLineStyles[distinctColorIdx(planIdx, PlanLineType.VALUE.ordinal)])
            }
            val investedKey = planLine.planId to PlanLineType.INVESTED
            if (investedKey in visiblePlanLines && planLine.investedSeries.size == chartData.size) {
                add(distinctLineStyles[distinctColorIdx(planIdx, PlanLineType.INVESTED.ordinal)])
            }
            val avgKey = planLine.planId to PlanLineType.AVG_BUY_PRICE
            if (avgKey in visiblePlanLines && planLine.avgBuyPriceSeries.size == chartData.size) {
                add(distinctLineStyles[distinctColorIdx(planIdx, PlanLineType.AVG_BUY_PRICE.ordinal)])
            }
        }
        // Per-crypto price styles (uses crypto brand colors, not distinct palette)
        cryptoGroupLines.forEach { cgLine ->
            val key = cgLine.crypto to CryptoGroupLineType.PRICE
            if (key in visibleCryptoGroupLines && cgLine.priceSeries.size == chartData.size) {
                add(cryptoPriceStylesMap[cgLine.crypto] ?: defaultCryptoPriceStyle)
            }
        }
        if (isEmpty()) add(hiddenLine)
    }
    val rightLines = buildList<LineCartesianLayer.Line> {
        if (3 in visibleSeries) add(accumulatedLine)
        // Per-plan accumulated (one distinct color per plan-metric combo)
        planLines.forEachIndexed { planIdx, planLine ->
            val key = planLine.planId to PlanLineType.ACCUMULATED
            if (key in visiblePlanLines && planLine.accumulatedSeries.size == chartData.size) {
                add(distinctLineStyles[distinctColorIdx(planIdx, PlanLineType.ACCUMULATED.ordinal)])
            }
        }
        // Per-crypto accumulated styles
        cryptoGroupLines.forEach { cgLine ->
            val key = cgLine.crypto to CryptoGroupLineType.TOTAL_ACCUMULATED
            if (key in visibleCryptoGroupLines && cgLine.totalAccumulatedSeries.size == chartData.size) {
                add(cryptoAccStylesMap[cgLine.crypto] ?: defaultCryptoAccStyle)
            }
        }
        if (isEmpty()) add(hiddenLine)
    }

    // Open-sell limit-price horizontal lines (per plan, on the left/fiat axis).
    // Each value renders a thin dashed red line so the user can visually compare
    // their pending sell targets against the current portfolio value / crypto price.
    val sellLineColor = MaterialTheme.colorScheme.error
    val dashedSellShape = remember {
        DashedShape(
            shape = Shape.Rectangle,
            dashLengthDp = 6f,
            gapLengthDp = 4f,
            fitStrategy = DashedShape.FitStrategy.Resize
        )
    }
    val sellLineComponent = rememberLineComponent(
        fill = fill(sellLineColor),
        thickness = 1.5.dp,
        shape = dashedSellShape
    )
    val sellDecorations = remember(openSellLimitPrices, sellLineComponent) {
        openSellLimitPrices.map { price ->
            val v = price.toDouble()
            HorizontalLine(
                y = { v },
                line = sellLineComponent
            )
        }
    }

    // Trade markers: convert each ChartTradeMarker into a chart-x model index by
    // finding the chart bucket whose epochDay floors the trade's epochDay.
    val buyMarkerColor = accumulatedCryptoColor.toArgb()
    val sellMarkerColor = MaterialTheme.colorScheme.error.toArgb()
    val tradeMarkerPoints = remember(tradeMarkers, chartData) {
        if (tradeMarkers.isEmpty() || chartData.isEmpty()) emptyList()
        else {
            val zone = ZoneId.systemDefault()
            val firstDay = chartData.first().epochDay
            val lastDay = chartData.last().epochDay
            tradeMarkers.mapNotNull { m ->
                val txDay = m.time.atZone(zone).toLocalDate().toEpochDay()
                if (txDay < firstDay || txDay > lastDay) return@mapNotNull null
                // Floor: largest index whose epochDay <= txDay.
                val idx = chartData.indexOfLast { it.epochDay <= txDay }
                if (idx < 0) null else idx.toDouble() to m.side
            }
        }
    }
    val tradeMarkerDecoration = remember(tradeMarkerPoints, buyMarkerColor, sellMarkerColor) {
        if (tradeMarkerPoints.isEmpty()) null
        else TradeMarkersDecoration(tradeMarkerPoints, buyMarkerColor, sellMarkerColor)
    }
    val allDecorations = remember(sellDecorations, tradeMarkerDecoration) {
        sellDecorations + listOfNotNull(tradeMarkerDecoration)
    }

    // Y-axis range provider: when there are open-sell limit lines, expand the
    // auto-calculated Y range (left/fiat axis) so all limit prices remain visible
    // even when they sit far above/below the actual portfolio/price series.
    val leftRangeProvider = remember(openSellLimitPrices) {
        if (openSellLimitPrices.isEmpty()) {
            CartesianLayerRangeProvider.auto()
        } else {
            object : CartesianLayerRangeProvider {
                private val limitMax = openSellLimitPrices.maxOf { it.toDouble() }
                private val limitMin = openSellLimitPrices.minOf { it.toDouble() }
                override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
                    maxOf(maxY, limitMax * 1.05)
                override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
                    minOf(minY, limitMin * 0.95)
            }
        }
    }

    // Tap-to-inspect marker – scrub fires onScrub to update KPI cards, no tooltip text
    val indicatorComponent = rememberShapeComponent(
        fill = fill(chartAccentColor),
        shape = CorneredShape.Pill
    )

    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(),
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val points = targets.filterIsInstance<LineCartesianLayerMarkerTarget>()
                .flatMap { it.points }
            val xIndex = points.firstOrNull()?.entry?.x?.toInt()
                ?.coerceIn(0, chartData.size - 1)
            if (xIndex != null) onScrub(xIndex)
            ""
        },
        indicator = { indicatorComponent },
        indicatorSize = 8.dp,
        guideline = rememberAxisGuidelineComponent()
    )

    // Axis title styling – unit label shown once above axis instead of on every tick
    val axisTitleComponent = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textSize = 10.sp
    )
    // Axis tick label styling – must be explicit for light theme support
    val axisLabelComponent = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textSize = 10.sp
    )

    // End (right) axis – accumulated crypto in BTC units
    val endAxisComponent = VerticalAxis.rememberEnd(
        label = axisLabelComponent,
        title = cryptoSymbol,
        titleComponent = axisTitleComponent,
        itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 5 }) },
        valueFormatter = { _, value, _ ->
            NumberFormatters.cryptoCompact(BigDecimal.valueOf(value))
        }
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Release) {
                            onScrub(null)
                        }
                    }
                }
            }
    ) {
        key(openSellLimitPrices, tradeMarkerPoints) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(leftLines),
                        rangeProvider = leftRangeProvider
                    ),
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(rightLines),
                        verticalAxisPosition = Axis.Position.Vertical.End
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabelComponent,
                        title = unitSuffix,
                        titleComponent = axisTitleComponent,
                        itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 5 }) },
                        valueFormatter = { _, value, _ ->
                            val bd = BigDecimal.valueOf(value)
                            when {
                                value >= 1 -> NumberFormatters.compactFiat(bd)
                                else -> NumberFormatters.cryptoCompact(bd)
                            }
                        }
                    ),
                    endAxis = if (hasRightAxis) endAxisComponent else null,
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = axisLabelComponent,
                        valueFormatter = { _, value, _ ->
                            val index = value.toInt().coerceIn(0, xLabels.size - 1)
                            xLabels.getOrElse(index) { "" }
                        },
                        itemPlacer = remember(chartData.size, xAxisSpacing) {
                            HorizontalAxis.ItemPlacer.aligned(
                                spacing = { xAxisSpacing }
                            )
                        }
                    ),
                    marker = marker,
                    markerController = CartesianMarkerController.rememberShowOnPress(),
                    decorations = allDecorations
                ),
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = remember { Zoom.Content }),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
