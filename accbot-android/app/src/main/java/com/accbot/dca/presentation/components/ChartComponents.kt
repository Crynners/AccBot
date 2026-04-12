package com.accbot.dca.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.accbot.dca.presentation.utils.NumberFormatters
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberShowOnPress
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

private val chartAccentColor = Primary
private val costBasisColor = Color(0xFF888888)
internal val btcPriceColor = Color(0xFFF7931A)
internal val accumulatedCryptoColor = Color(0xFF4CAF50)
internal val avgBuyPriceColor = Color(0xFF9C27B0)

internal val planLineColors = listOf(
    Color(0xFFFF6B6B), // red
    Color(0xFF4ECDC4), // teal
    Color(0xFFFFD93D), // yellow
    Color(0xFF6C63FF), // purple
    Color(0xFFFF8A65), // orange
    Color(0xFF81C784), // green
)

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

    // Pre-create plan line styles (max 6 plans) - value lines (solid, full color)
    val planValueStyle0 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[0])))
    val planValueStyle1 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[1])))
    val planValueStyle2 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[2])))
    val planValueStyle3 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[3])))
    val planValueStyle4 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[4])))
    val planValueStyle5 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[5])))
    val planValueStyles = listOf(planValueStyle0, planValueStyle1, planValueStyle2, planValueStyle3, planValueStyle4, planValueStyle5)

    // Invested lines (lighter/translucent, same base color)
    val planInvestedStyle0 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[0].copy(alpha = 0.4f))))
    val planInvestedStyle1 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[1].copy(alpha = 0.4f))))
    val planInvestedStyle2 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[2].copy(alpha = 0.4f))))
    val planInvestedStyle3 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[3].copy(alpha = 0.4f))))
    val planInvestedStyle4 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[4].copy(alpha = 0.4f))))
    val planInvestedStyle5 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[5].copy(alpha = 0.4f))))
    val planInvestedStyles = listOf(planInvestedStyle0, planInvestedStyle1, planInvestedStyle2, planInvestedStyle3, planInvestedStyle4, planInvestedStyle5)

    // Avg buy price lines (alpha 0.7)
    val planAvgBuyStyle0 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[0].copy(alpha = 0.7f))))
    val planAvgBuyStyle1 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[1].copy(alpha = 0.7f))))
    val planAvgBuyStyle2 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[2].copy(alpha = 0.7f))))
    val planAvgBuyStyle3 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[3].copy(alpha = 0.7f))))
    val planAvgBuyStyle4 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[4].copy(alpha = 0.7f))))
    val planAvgBuyStyle5 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[5].copy(alpha = 0.7f))))
    val planAvgBuyStyles = listOf(planAvgBuyStyle0, planAvgBuyStyle1, planAvgBuyStyle2, planAvgBuyStyle3, planAvgBuyStyle4, planAvgBuyStyle5)

    // Accumulated lines (alpha 0.85)
    val planAccumulatedStyle0 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[0].copy(alpha = 0.85f))))
    val planAccumulatedStyle1 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[1].copy(alpha = 0.85f))))
    val planAccumulatedStyle2 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[2].copy(alpha = 0.85f))))
    val planAccumulatedStyle3 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[3].copy(alpha = 0.85f))))
    val planAccumulatedStyle4 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[4].copy(alpha = 0.85f))))
    val planAccumulatedStyle5 = LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(planLineColors[5].copy(alpha = 0.85f))))
    val planAccumulatedStyles = listOf(planAccumulatedStyle0, planAccumulatedStyle1, planAccumulatedStyle2, planAccumulatedStyle3, planAccumulatedStyle4, planAccumulatedStyle5)

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
        // Per-plan line styles (value + invested share the same color index per plan)
        var planStyleIdx = 0
        planLines.forEach { planLine ->
            val valueKey = planLine.planId to PlanLineType.VALUE
            val investedKey = planLine.planId to PlanLineType.INVESTED
            if (valueKey in visiblePlanLines && planLine.valueSeries.size == chartData.size) {
                add(planValueStyles[planStyleIdx % planValueStyles.size])
            }
            if (investedKey in visiblePlanLines && planLine.investedSeries.size == chartData.size) {
                add(planInvestedStyles[planStyleIdx % planInvestedStyles.size])
            }
            planStyleIdx++  // increment per plan, not per line, so value and invested share the color
        }
        // Per-plan avg buy price styles
        var planStyleIdxAvg = 0
        planLines.forEach { planLine ->
            val key = planLine.planId to PlanLineType.AVG_BUY_PRICE
            if (key in visiblePlanLines && planLine.avgBuyPriceSeries.size == chartData.size) {
                add(planAvgBuyStyles[planStyleIdxAvg % planAvgBuyStyles.size])
            }
            planStyleIdxAvg++
        }
        // Per-crypto price styles
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
        // Per-plan accumulated styles
        var planStyleIdxAcc = 0
        planLines.forEach { planLine ->
            val key = planLine.planId to PlanLineType.ACCUMULATED
            if (key in visiblePlanLines && planLine.accumulatedSeries.size == chartData.size) {
                add(planAccumulatedStyles[planStyleIdxAcc % planAccumulatedStyles.size])
            }
            planStyleIdxAcc++
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
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(leftLines)
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
                markerController = CartesianMarkerController.rememberShowOnPress()
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = remember { Zoom.Content }),
            modifier = Modifier.fillMaxSize()
        )
    }
}
