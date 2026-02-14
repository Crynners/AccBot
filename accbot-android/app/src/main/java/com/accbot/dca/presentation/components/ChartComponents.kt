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
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
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

/**
 * Interactive chart legend — tap a label to show/hide its series.
 */
@Composable
fun InteractiveChartLegend(
    line1Label: String,
    line2Label: String,
    visibleSeries: Set<Int> = setOf(0, 1),
    onToggleSeries: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        LegendItem(
            color = chartAccentColor,
            label = line1Label,
            enabled = 0 in visibleSeries,
            onClick = { onToggleSeries(0) }
        )
        Spacer(Modifier.width(24.dp))
        LegendItem(
            color = costBasisColor,
            label = line2Label,
            enabled = 1 in visibleSeries,
            onClick = { onToggleSeries(1) }
        )
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
 * Portfolio line chart with value line (solid with gradient fill) and cost basis line.
 * Supports FIAT and CRYPTO denomination modes, Y-axis units, series visibility, and tap-to-inspect markers.
 * X-axis formatting adapts to the current zoom level.
 */
@Composable
fun PortfolioLineChart(
    chartData: List<ChartDataPoint>,
    denominationMode: DenominationMode = DenominationMode.FIAT,
    unitSuffix: String = "",
    visibleSeries: Set<Int> = setOf(0, 1),
    zoomLevel: ChartZoomLevel = ChartZoomLevel.Overview,
    onScrub: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (chartData.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }

    // Update model when data, denomination, or visibility changes
    LaunchedEffect(chartData, denominationMode, visibleSeries) {
        try {
            modelProducer.runTransaction {
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
            is ChartZoomLevel.Year -> 1  // show all 12 months
            is ChartZoomLevel.Month -> maxOf(1, chartData.size / 7)  // ~weekly labels
        }
    }

    // Always remember both lines (composable calls can't be conditional)
    val valueLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(chartAccentColor)),
        areaFill = LineCartesianLayer.AreaFill.single(fill(chartAccentColor.copy(alpha = 0.4f)))
    )
    val costBasisLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(costBasisColor))
    )

    // Build visible line list
    val lines = buildList<LineCartesianLayer.Line> {
        if (0 in visibleSeries) add(valueLine)
        if (1 in visibleSeries) add(costBasisLine)
    }

    // Tap-to-inspect marker — simplified to date-only tooltip; KPI header shows live values
    val labelColor = MaterialTheme.colorScheme.onSurface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val indicatorComponent = rememberShapeComponent(
        fill = fill(chartAccentColor),
        shape = CorneredShape.Pill
    )
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            color = labelColor,
            textSize = 11.sp,
            lineCount = 1,
            background = rememberShapeComponent(
                fill = fill(surfaceContainerColor),
                shape = CorneredShape.rounded(allPercent = 8)
            ),
            padding = insets(8.dp, 4.dp)
        ),
        valueFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
            val points = targets.filterIsInstance<LineCartesianLayerMarkerTarget>()
                .flatMap { it.points }
            val xIndex = points.firstOrNull()?.entry?.x?.toInt()
                ?.coerceIn(0, chartData.size - 1)
            val dataPoint = xIndex?.let { chartData[it] }

            // Notify scrub listener
            if (xIndex != null) onScrub(xIndex)

            // Tooltip shows only date
            if (dataPoint != null) {
                LocalDate.ofEpochDay(dataPoint.epochDay)
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            } else ""
        },
        indicator = { indicatorComponent },
        indicatorSize = 8.dp,
        guideline = rememberAxisGuidelineComponent()
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
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
                    lineProvider = LineCartesianLayer.LineProvider.series(lines)
                ),
                startAxis = VerticalAxis.rememberStart(
                    itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 5 }) },
                    valueFormatter = { _, value, _ ->
                        val bd = BigDecimal.valueOf(value)
                        when {
                            value >= 1 -> "${NumberFormatters.compactFiat(bd)} $unitSuffix"
                            else -> "${NumberFormatters.crypto(bd)} $unitSuffix"
                        }
                    }
                ),
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
