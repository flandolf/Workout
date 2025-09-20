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
import kotlin.math.max

@Composable
fun ProgressGraph(
    dataPoints: List<Triple<Long, Float, Int>>, modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        if (dataPoints.size < 2) return@Canvas

        val padding = 48f
        val graphWidth = size.width - padding * 2
        val graphHeight = size.height - padding * 2

        val minWeight = dataPoints.minOf { it.second }
        val maxWeight = dataPoints.maxOf { it.second }
        val weightRange = max(maxWeight - minWeight, 1f)

        val xAxisY = size.height - padding
        val yAxisX = padding

        // Axes
        drawLine(onSurface, Offset(yAxisX, padding), Offset(yAxisX, xAxisY), 2f)
        drawLine(onSurface, Offset(yAxisX, xAxisY), Offset(size.width - padding, xAxisY), 2f)

        // Y axis labels (3 steps)
        val ySteps = 3
        for (i in 0..ySteps) {
            val y = padding + (graphHeight / ySteps) * i
            val value = maxWeight - (weightRange / ySteps) * i

            val label = formatWeight(value, round = true)
            val textLayout = textMeasurer.measure(
                label, style = TextStyle(color = onSurface, fontSize = 10.sp)
            )
            drawText(
                textLayout, topLeft = Offset(
                    yAxisX - textLayout.size.width - 6f, y - textLayout.size.height / 2
                )
            )

            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.15f),
                start = Offset(yAxisX, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 1f
            )
        }

        // X axis labels (max 4 evenly spaced)
        val xLabels = minOf(4, dataPoints.size)
        val step = max(1, (dataPoints.size - 1) / (xLabels - 1))
        dataPoints.forEachIndexed { index, (date, _, _) ->
            if (index % step == 0 || index == dataPoints.lastIndex) {
                val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * index
                val label = date.toString().take(6) // keep short, e.g. "12/09"
                val textLayout = textMeasurer.measure(
                    label, style = TextStyle(color = onSurface, fontSize = 10.sp)
                )
                drawText(
                    textLayout, topLeft = Offset(x - textLayout.size.width / 2, xAxisY + 6f)
                )
            }
        }

        val path = Path()
        dataPoints.forEachIndexed { index, (_, weight, _) ->
            val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * index
            val y = padding + graphHeight - ((weight - minWeight) / weightRange) * graphHeight

            // Draw point
            drawCircle(color = primaryColor, radius = 5f, center = Offset(x, y))

            // Prepare weight label
            val label = formatWeight(weight)
            val textLayout = textMeasurer.measure(
                label,
                style = TextStyle(color = onSurfaceVariant, fontSize = 10.sp)
            )

            // Smart placement: above unless too close to top
            val verticalGap = 28f
            val textY = if (y - verticalGap - textLayout.size.height < padding) {
                // not enough space above, put below
                y + verticalGap / 2
            } else {
                // default: above point
                y - verticalGap
            }

            drawText(
                textLayout,
                topLeft = Offset(
                    x - textLayout.size.width / 2,
                    textY
                )
            )

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primaryColor, style = Stroke(width = 2f))


    }
}
