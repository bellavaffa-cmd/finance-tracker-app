package com.financetracker.app.ui.reports

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.financetracker.app.ui.theme.Surface2

/** One arc of the donut: a category, its share, and the colour it is drawn in. */
data class DonutSlice(
    val label: String,
    val valueMinor: Long,
    val color: Color
)

/**
 * Spending by category for one month.
 *
 * Slices below [MIN_VISIBLE_FRACTION] are still drawn at a floor width rather than being dropped,
 * because a slice you cannot see reads as money that was never spent. The arcs are separated by a
 * small gap so adjacent categories with similar colours stay countable.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerPrimary: String,
    centerSecondary: String,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 58f
) {
    val total = slices.sumOf { it.valueMinor }.coerceAtLeast(1L)
    val sweepProgress by animateFloatAsState(
        targetValue = if (slices.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 550),
        label = "donut-sweep"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val diameter = minOf(size.width, size.height) - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = Surface2,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )

            if (slices.isEmpty()) return@Canvas

            // Reserve the gaps up front so the visible arcs still add up to a full circle.
            val gapDegrees = if (slices.size > 1) GAP_DEGREES else 0f
            val available = 360f - gapDegrees * slices.size

            // Floor each share so tiny categories stay visible, then renormalise - otherwise the
            // floors add up to more than a whole circle and the last slice overruns the first.
            val floored = slices.map {
                (it.valueMinor.toFloat() / total.toFloat()).coerceAtLeast(MIN_VISIBLE_FRACTION)
            }
            val flooredTotal = floored.sum().coerceAtLeast(0.0001f)

            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = available * (floored[index] / flooredTotal) * sweepProgress
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweep + gapDegrees
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerPrimary,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                centerSecondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val GAP_DEGREES = 1.6f
private const val MIN_VISIBLE_FRACTION = 0.012f
