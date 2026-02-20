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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.accbot.dca.domain.usecase.ChartDataPoint
import com.accbot.dca.domain.usecase.ChartZoomLevel
import com.accbot.dca.presentation.screens.portfolio.DenominationMode
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
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

private val chartAccentColor = Primary
private val costBasisColor = Color(0xFF888888)
internal val btcPriceColor = Color(0xFFF7931A)
internal val accumulatedCryptoColor = Color(0xFF4CAF50)

data class LegendEntry(
    val seriesIndex: Int,
    val label: String,
    val color: Color
)

/**
 * Interactive chart legend — tap a label to show/hide its series.
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
            .clickable(onClick = onClick)
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
 * Tooltip shows only values for currently visible series.
 */
@Composable
fun PortfolioLineChart(
    chartData: List<ChartDataPoint>,
    denominationMode: DenominationMode = DenominationMode.FIAT,
    unitSuffix: String = "",
    fiatSymbol: String = "",
    cryptoSymbol: String = "",
    visibleSeries: Set<Int> = setOf(0, 1),
    zoomLevel: ChartZoomLevel = ChartZoomLevel.Overview,
    onScrub: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (chartData.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }
    val hasRightAxis = cryptoSymbol.isNotEmpty() && 3 in visibleSeries

    // Update model when data, denomination, or visibility changes
    LaunchedEffect(chartData, denominationMode, visibleSeries) {
        try {
            modelProducer.runTransaction {
                // Layer 1: left axis (portfolio value, cost basis, crypto price — all fiat)
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
                    if (setOf(0, 1, 2).none { it in visibleSeries }) {
                        series(List(chartData.size) { 0f })
                    }
                }
                // Layer 2: right axis (accumulated crypto — BTC units)
                lineSeries {
                    if (3 in visibleSeries) series(chartData.map { it.cumulativeCrypto.toFloat() })
                    else series(List(chartData.size) { 0f })
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e("PortfolioChart", "OOM building chart model, skipping render", e)
        }
    }

    val xLabels = remember(chartData, zoomLevel) {
        val formatter = when (zoomLevel) {
            is ChartZoomLevel.Overview -> DateTimeFormatter.ofPattern("MMM yyyy")
            is ChartZoomLevel.Year -> DateTimeFormatter.ofPattern("MMM")
            is ChartZoomLevel.Month -> DateTimeFormatter.ofPattern("d")
        }
        chartData.map { LocalDate.ofEpochDay(it.epochDay).format(formatter) }
    }

    val xAxisSpacing = remember(chartData.size, zoomLevel) {
        when (zoomLevel) {
            is ChartZoomLevel.Overview -> maxOf(1, chartData.size / 6)
            is ChartZoomLevel.Year -> 1
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
    val hiddenLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(Color.Transparent))
    )

    // Build visible line lists for each layer
    val leftLines = buildList<LineCartesianLayer.Line> {
        if (0 in visibleSeries) add(valueLine)
        if (1 in visibleSeries) add(costBasisLine)
        if (2 in visibleSeries) add(priceLine)
        if (isEmpty()) add(hiddenLine)
    }
    val rightLines = buildList<LineCartesianLayer.Line> {
        if (3 in visibleSeries) add(accumulatedLine)
        if (isEmpty()) add(hiddenLine)
    }

    // Tap-to-inspect marker with conditional tooltip content
    val labelColor = MaterialTheme.colorScheme.onSurface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val indicatorComponent = rememberShapeComponent(
        fill = fill(chartAccentColor),
        shape = CorneredShape.Pill
    )
    val tooltipCurrency = fiatSymbol.ifEmpty { unitSuffix }

    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            color = labelColor,
            textSize = 11.sp,
            lineCount = 8,
            background = rememberShapeComponent(
                fill = fill(surfaceContainerColor),
                shape = CorneredShape.rounded(allPercent = 8)
            ),
            padding = insets(10.dp, 6.dp)
        ),
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val points = targets.filterIsInstance<LineCartesianLayerMarkerTarget>()
                .flatMap { it.points }
            val xIndex = points.firstOrNull()?.entry?.x?.toInt()
                ?.coerceIn(0, chartData.size - 1)
            val dataPoint = xIndex?.let { chartData[it] }

            if (xIndex != null) onScrub(xIndex)

            if (dataPoint != null) {
                val date = LocalDate.ofEpochDay(dataPoint.epochDay)
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy"))

                val text = buildString {
                    append(date)
                    append("\n")
                    if (0 in visibleSeries) {
                        val v = when (denominationMode) {
                            DenominationMode.FIAT -> "${NumberFormatters.fiat(dataPoint.portfolioValue)} $tooltipCurrency"
                            DenominationMode.CRYPTO -> "${NumberFormatters.crypto(dataPoint.cumulativeCrypto)} $cryptoSymbol"
                        }
                        append("\n$v")
                    }
                    if (1 in visibleSeries) {
                        val v = when (denominationMode) {
                            DenominationMode.FIAT -> "${NumberFormatters.fiat(dataPoint.totalInvested)} $tooltipCurrency"
                            DenominationMode.CRYPTO -> "${NumberFormatters.crypto(dataPoint.investedEquivCrypto)} $cryptoSymbol"
                        }
                        append("\n$v")
                    }
                    if (0 in visibleSeries) {
                        val isPositive = dataPoint.roiAbsolute >= BigDecimal.ZERO
                        val sign = if (isPositive) "+" else ""
                        append("\nROI: $sign${NumberFormatters.percent(dataPoint.roiPercent)}% ($sign${NumberFormatters.fiat(dataPoint.roiAbsolute)} $tooltipCurrency)")
                    }
                    if (2 in visibleSeries && dataPoint.price > BigDecimal.ZERO) {
                        append("\n$cryptoSymbol: ${NumberFormatters.fiat(dataPoint.price)} $tooltipCurrency")
                    }
                    if (3 in visibleSeries && dataPoint.cumulativeCrypto > BigDecimal.ZERO) {
                        append("\n${NumberFormatters.crypto(dataPoint.cumulativeCrypto)} $cryptoSymbol")
                    }
                }

                android.text.SpannableStringBuilder(text).apply {
                    setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0, date.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else ""
        },
        indicator = { indicatorComponent },
        indicatorSize = 8.dp,
        guideline = rememberAxisGuidelineComponent()
    )

    // Axis title styling — unit label shown once above axis instead of on every tick
    val axisTitleComponent = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textSize = 10.sp
    )

    // End (right) axis — accumulated crypto in BTC units
    val endAxisComponent = VerticalAxis.rememberEnd(
        title = cryptoSymbol,
        titleComponent = axisTitleComponent,
        itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 5 }) },
        valueFormatter = { _, value, _ ->
            NumberFormatters.crypto(BigDecimal.valueOf(value))
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
                    title = unitSuffix,
                    titleComponent = axisTitleComponent,
                    itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 5 }) },
                    valueFormatter = { _, value, _ ->
                        val bd = BigDecimal.valueOf(value)
                        when {
                            value >= 1 -> NumberFormatters.compactFiat(bd)
                            else -> NumberFormatters.crypto(bd)
                        }
                    }
                ),
                endAxis = if (hasRightAxis) endAxisComponent else null,
                bottomAxis = HorizontalAxis.rememberBottom(
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
