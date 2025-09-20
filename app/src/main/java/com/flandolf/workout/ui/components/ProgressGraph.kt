package com.flandolf.workout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.flandolf.workout.data.formatWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun ProgressGraph(
    dataPoints: List<Triple<Long, Float, Int>>,
    modifier: Modifier = Modifier,
    dataType: String = "weight" // "weight", "volume", "reps", "1rm"
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (dataPoints.size < 2) {
            // Draw empty state
            val emptyText = "Not enough data to display chart"
            val textLayout = textMeasurer.measure(
                emptyText, style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp)
            )
            drawText(
                textLayout,
                topLeft = Offset(
                    (size.width - textLayout.size.width) / 2,
                    (size.height - textLayout.size.height) / 2
                )
            )
            return@Canvas
        }

        val padding = 90f
        val graphWidth = size.width - padding * 2
        val graphHeight = size.height - padding * 2

        val minValue = dataPoints.minOf { it.second }
        val maxValue = dataPoints.maxOf { it.second }
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

        // Vertical grid lines (based on data points)
        val vGridLines = minOf(6, dataPoints.size)
        val vStep = max(1, (dataPoints.size - 1) / (vGridLines - 1))
        for (i in 0 until dataPoints.size step vStep) {
            val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * i
            drawLine(
                color = minorGridColor,
                start = Offset(x, padding),
                end = Offset(x, xAxisY),
                strokeWidth = 0.5f
            )
        }

        // Draw axes with better styling
        drawLine(outline, Offset(yAxisX, padding), Offset(yAxisX, xAxisY), 2f)
        drawLine(outline, Offset(yAxisX, xAxisY), Offset(size.width - padding, xAxisY), 2f)

        // Y axis labels (4 steps for better granularity)
        val ySteps = 4
        for (i in 0..ySteps) {
            val y = padding + (graphHeight / ySteps) * i
            val value = maxValue - (valueRange / ySteps) * i

            val label = when (dataType) {
                "weight", "1rm" -> formatWeight(value, round = true)
                "volume" -> "${formatWeight(value, round = true)} kg"
                "reps" -> value.toInt().toString()
                else -> formatWeight(value, round = true)
            }
            val textLayout = textMeasurer.measure(
                label, style = TextStyle(color = onSurface, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            )
            drawText(
                textLayout, topLeft = Offset(
                    yAxisX - textLayout.size.width - 8f, y - textLayout.size.height / 2
                )
            )
        }

        // X axis labels (max 5 evenly spaced)
        val xLabels = minOf(5, dataPoints.size)
        val step = max(1, (dataPoints.size - 1) / (xLabels - 1))
        val dateFormatter = SimpleDateFormat("MM/dd", Locale.getDefault())
        dataPoints.forEachIndexed { index, (date, _, _) ->
            if (index % step == 0 || index == dataPoints.lastIndex) {
                val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * index
                val label = dateFormatter.format(Date(date))
                val textLayout = textMeasurer.measure(
                    label, style = TextStyle(color = onSurface, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                )
                drawText(
                    textLayout, topLeft = Offset(x - textLayout.size.width / 2, xAxisY + 8f)
                )
            }
        }

        // Draw the line chart with gradient fill
        val path = Path()
        val fillPath = Path()
        dataPoints.forEachIndexed { index, (_, value, _) ->
            val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * index
            val y = padding + graphHeight - ((value - minValue) / valueRange) * graphHeight

            // Draw data point with shadow effect
            drawCircle(
                color = primaryColor.copy(alpha = 0.3f),
                radius = 7f,
                center = Offset(x + 1, y + 1)
            )
            drawCircle(color = primaryColor, radius = 5f, center = Offset(x, y))

            // Point labels with better positioning
            val label = when (dataType) {
                "weight", -> formatWeight(value)
                "1rm" -> formatWeight(value, round = true)
                "volume" -> "${formatWeight(value)} kg"
                "reps" -> value.toInt().toString()
                else -> formatWeight(value)
            }
            val textLayout = textMeasurer.measure(
                label,
                style = TextStyle(color = onSurface, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            )

            // Smart label placement
            val labelOffset = 32f
            val textY = if (y - labelOffset - textLayout.size.height < padding) {
                y + labelOffset
            } else {
                y - labelOffset
            }

            // Draw label background for better readability
            drawRoundRect(
                color = surface.copy(alpha = 0.8f),
                topLeft = Offset(x - textLayout.size.width / 2 - 4, textY - 2),
                size = androidx.compose.ui.geometry.Size((textLayout.size.width + 8).toFloat(),
                    (textLayout.size.height + 4).toFloat()
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
            )

            drawText(
                textLayout,
                topLeft = Offset(x - textLayout.size.width / 2, textY)
            )

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, xAxisY)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (index == dataPoints.lastIndex) {
                fillPath.lineTo(x, xAxisY)
                fillPath.close()
            }
        }

        // Draw gradient fill under the line
        val gradientBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.2f),
                primaryColor.copy(alpha = 0.05f)
            ),
            startY = padding,
            endY = xAxisY
        )
        drawPath(fillPath, gradientBrush)

        // Draw the line on top
        drawPath(path, primaryColor, style = Stroke(width = 3f))
    }
}
