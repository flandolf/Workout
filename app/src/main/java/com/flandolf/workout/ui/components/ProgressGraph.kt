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

        val padding = 60f
        val graphWidth = size.width - padding * 2
        val graphHeight = size.height - padding * 2

        val minWeight = dataPoints.minOf { it.second }
        val maxWeight = dataPoints.maxOf { it.second }
        val weightRange = max(maxWeight - minWeight, 1f)

        // Draw axes
        val xAxisY = size.height - padding
        val yAxisX = padding
        drawLine(
            color = onSurface,
            start = Offset(yAxisX, padding),
            end = Offset(yAxisX, xAxisY),
            strokeWidth = 2f
        )
        drawLine(
            color = onSurface,
            start = Offset(yAxisX, xAxisY),
            end = Offset(size.width - padding, xAxisY),
            strokeWidth = 2f
        )

        // Grid + Y labels
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = padding + (graphHeight / gridLines) * i
            val weightValue = maxWeight - (weightRange / gridLines) * i

            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.2f),
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = 1f
            )

            val textLayoutResult = textMeasurer.measure(
                text = String.format("%.1f", weightValue),
                style = TextStyle(color = onSurface, fontSize = 12.sp)
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(padding - textLayoutResult.size.width - 8f, y - textLayoutResult.size.height / 2)
            )
        }

        // X labels
        dataPoints.forEachIndexed { index, (date, _, _) ->
            val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * index
            if (index % max(1, dataPoints.size / 4) == 0) { // avoid clutter
                val textLayoutResult = textMeasurer.measure(
                    text = date.toString(), // replace with formatted date if needed
                    style = TextStyle(color = onSurface, fontSize = 12.sp)
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(x - textLayoutResult.size.width / 2, xAxisY + 8f)
                )
            }
        }

        // Data path
        val path = Path()
        dataPoints.forEachIndexed { index, (_, weight, _) ->
            val x = padding + (graphWidth / max(dataPoints.size - 1, 1)) * index
            val y = padding + graphHeight - ((weight - minWeight) / weightRange) * graphHeight

            drawCircle(color = primaryColor, radius = 5f, center = Offset(x, y))

            // Weight labels above point
            val weightText = String.format("%.1f", weight)
            val textLayoutResult = textMeasurer.measure(
                text = weightText,
                style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp)
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(x - textLayoutResult.size.width / 2, y - 20f)
            )

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path = path, color = primaryColor, style = Stroke(width = 2f))
    }
}
