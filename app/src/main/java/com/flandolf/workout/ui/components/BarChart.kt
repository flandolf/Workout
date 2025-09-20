package com.flandolf.workout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
fun BarChart(
    dataPoints: List<Pair<String, Float>>, // label to value
    modifier: Modifier = Modifier,
    showValues: Boolean = true,
    barColor: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Float) -> String = { it.toInt().toString() }
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (dataPoints.isEmpty()) {
            val emptyText = "No data to display"
            val textLayout = textMeasurer.measure(
                emptyText, style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp)
            )
            drawText(
                textLayout, topLeft = Offset(
                    (size.width - textLayout.size.width) / 2,
                    (size.height - textLayout.size.height) / 2
                )
            )
            return@Canvas
        }

        val padding = 60f
        val graphWidth = size.width - padding * 2
        val graphHeight = size.height - padding * 2

        val maxValue = dataPoints.maxOf { it.second }
        val minValue = 0f // Bars start from 0
        val valueRange = max(maxValue - minValue, 1f)

        val xAxisY = size.height - padding
        val yAxisX = padding

        // Draw background grid
        val gridColor = surfaceVariant.copy(alpha = 0.3f)
        val minorGridColor = surfaceVariant.copy(alpha = 0.1f)

        // Horizontal grid lines (5 lines)
        val hGridLines = 5
        for (i in 0..hGridLines) {
            val y = padding + (graphHeight / hGridLines) * i
            val strokeWidth = if (i == 0 || i == hGridLines) 1.5f else 0.5f
            val color = if (i == 0 || i == hGridLines) gridColor else minorGridColor
            drawLine(
                color = color,
                start = Offset(yAxisX, y),
                end = Offset(size.width - padding, y),
                strokeWidth = strokeWidth
            )
        }

        // Draw axes
        drawLine(outline, Offset(yAxisX, padding), Offset(yAxisX, xAxisY), 2f)
        drawLine(outline, Offset(yAxisX, xAxisY), Offset(size.width - padding, xAxisY), 2f)

        // Y axis labels (5 steps)
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = padding + (graphHeight / ySteps) * i
            val value = maxValue - (valueRange / ySteps) * i

            val label = valueFormatter(value)
            val textLayout = textMeasurer.measure(
                label, style = TextStyle(
                    color = onSurface,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
            drawText(
                textLayout, topLeft = Offset(
                    yAxisX - textLayout.size.width - 8f, y - textLayout.size.height / 2
                )
            )
        }

        // Calculate bar dimensions
        val barSpacing = 8f
        val availableWidth = graphWidth - (barSpacing * (dataPoints.size - 1))
        val barWidth = availableWidth / dataPoints.size

        // Draw bars
        dataPoints.forEachIndexed { index, (label, value) ->
            val barHeight = (value / valueRange) * graphHeight
            val barX = padding + (barWidth + barSpacing) * index
            val barY = xAxisY - barHeight

            // Draw bar with gradient
            val barBrush = Brush.verticalGradient(
                colors = listOf(
                    barColor.copy(alpha = 0.8f), barColor.copy(alpha = 0.6f)
                )
            )

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(barX, barY),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )

            // Draw bar outline
            drawRoundRect(
                color = barColor,
                topLeft = Offset(barX, barY),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )

            // Draw value label on top of bar
            if (showValues && value > 0) {
                val valueLabel = valueFormatter(value)
                val valueTextLayout = textMeasurer.measure(
                    valueLabel, style = TextStyle(
                        color = onSurface,
                        fontSize = 10.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                )

                val valueX = barX + barWidth / 2 - valueTextLayout.size.width / 2
                val valueY = barY - valueTextLayout.size.height - 4f

                // Only show value if it fits above the bar
                if (valueY >= padding) {
                    // Draw background for value
                    drawRoundRect(
                        color = surface.copy(alpha = 0.8f),
                        topLeft = Offset(valueX - 2, valueY - 2),
                        size = Size(
                            (valueTextLayout.size.width + 4).toFloat(),
                            (valueTextLayout.size.height + 4).toFloat()
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )

                    drawText(
                        valueTextLayout, topLeft = Offset(valueX, valueY)
                    )
                }
            }

            // Draw X axis label
            val labelTextLayout = textMeasurer.measure(
                label, style = TextStyle(
                    color = onSurface,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
            drawText(
                labelTextLayout, topLeft = Offset(
                    barX + barWidth / 2 - labelTextLayout.size.width / 2, xAxisY + 8f
                )
            )
        }
    }
}