package com.accbot.dca.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.accbot.dca.domain.usecase.ChartDataPoint
import com.accbot.dca.presentation.ui.theme.Primary
import com.accbot.dca.presentation.ui.theme.Success

/**
 * Simplified Canvas-based line chart for @Preview rendering.
 * Vico's CartesianChartHost relies on LaunchedEffect which doesn't run in previews.
 */
@Composable
fun PreviewLineChart(
    chartData: List<ChartDataPoint>,
    modifier: Modifier = Modifier
) {
    if (chartData.isEmpty()) return

    val portfolioColor = Success
    val investedColor = Primary.copy(alpha = 0.5f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingH = 24.dp.toPx()
            val paddingV = 20.dp.toPx()
            val chartWidth = size.width - paddingH * 2
            val chartHeight = size.height - paddingV * 2

            val allValues = chartData.flatMap { listOf(it.portfolioValue, it.totalInvested) }
            val minVal = allValues.minOf { it.toFloat() }
            val maxVal = allValues.maxOf { it.toFloat() }
            val range = (maxVal - minVal).coerceAtLeast(1f)

            fun valueToY(value: Float): Float {
                return paddingV + chartHeight - ((value - minVal) / range * chartHeight)
            }

            fun indexToX(index: Int): Float {
                return paddingH + (index.toFloat() / (chartData.size - 1).coerceAtLeast(1)) * chartWidth
            }

            // Draw invested line (cost basis)
            val investedPath = Path()
            chartData.forEachIndexed { i, point ->
                val x = indexToX(i)
                val y = valueToY(point.totalInvested.toFloat())
                if (i == 0) investedPath.moveTo(x, y) else investedPath.lineTo(x, y)
            }
            drawPath(investedPath, investedColor, style = Stroke(width = 2.dp.toPx()))

            // Draw portfolio value line
            val portfolioPath = Path()
            chartData.forEachIndexed { i, point ->
                val x = indexToX(i)
                val y = valueToY(point.portfolioValue.toFloat())
                if (i == 0) portfolioPath.moveTo(x, y) else portfolioPath.lineTo(x, y)
            }
            drawPath(portfolioPath, portfolioColor, style = Stroke(width = 3.dp.toPx()))

            // Draw dots on last point
            val lastX = indexToX(chartData.size - 1)
            val lastY = valueToY(chartData.last().portfolioValue.toFloat())
            drawCircle(portfolioColor, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
