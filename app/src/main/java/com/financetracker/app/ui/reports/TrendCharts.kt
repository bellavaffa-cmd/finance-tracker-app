package com.financetracker.app.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.Money
import com.financetracker.app.data.insight.MonthlyFlow
import com.financetracker.app.data.insight.NetWorthPoint
import com.financetracker.app.ui.theme.Accent
import com.financetracker.app.ui.theme.BorderColor
import com.financetracker.app.ui.theme.Negative
import com.financetracker.app.ui.theme.Positive
import com.financetracker.app.ui.theme.Surface2

/**
 * Income against spending, one pair of bars per month.
 *
 * Both series share a single scale taken from the largest value across both, because drawing them
 * on independent scales would make a month that spent twice what it earned look balanced.
 */
@Composable
fun IncomeExpenseChart(
    flows: List<MonthlyFlow>,
    baseCurrency: String,
    modifier: Modifier = Modifier
) {
    if (flows.isEmpty()) return
    val maxValue = flows.maxOf { maxOf(it.incomeMinorBase, it.expenseMinorBase) }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            flows.forEach { flow ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        modifier = Modifier.height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Bar(flow.incomeMinorBase, maxValue, Positive)
                        Bar(flow.expenseMinorBase, maxValue, Negative)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // Just the month initial: twelve three-letter labels do not fit, and the
                        // period is already named above the chart.
                        flow.period.shortLabel.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot("In", Positive)
            LegendDot("Out", Negative)
            Spacer(Modifier.weight(1f))
            Text(
                "peak ${Money.formatCompact(maxValue, baseCurrency)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Bar(valueMinor: Long, maxMinor: Long, color: Color) {
    val fraction = (valueMinor.toFloat() / maxMinor.toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(120.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // A zero month still gets a hairline, so an empty bar reads as "nothing" rather than as a
        // rendering failure.
        Box(
            modifier = Modifier
                .width(12.dp)
                .height((120f * fraction).coerceAtLeast(2f).dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Net worth as a line.
 *
 * The y-axis is scaled to the data's own range rather than anchored at zero, so a steady balance
 * with small movements still shows its shape. Zero is drawn as a reference line whenever the range
 * crosses it, which is what stops that choice from being misleading.
 */
@Composable
fun NetWorthChart(
    points: List<NetWorthPoint>,
    baseCurrency: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val values = points.map { it.netWorthMinorBase }
    val maxValue = values.max()
    val minValue = values.min()
    val span = (maxValue - minValue).coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
            fun yFor(value: Long): Float {
                val fraction = (value - minValue).toFloat() / span.toFloat()
                // Inset top and bottom so the line never sits flush against the edge.
                return size.height - (fraction * (size.height - 16f)) - 8f
            }

            if (minValue < 0 && maxValue > 0) {
                val zeroY = yFor(0)
                drawLine(
                    color = BorderColor,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5f
                )
            }

            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { index, point ->
                val x = index * stepX
                val y = yFor(point.netWorthMinorBase)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(Accent.copy(alpha = 0.28f), Accent.copy(alpha = 0.02f))
                )
            )
            drawPath(
                path = linePath,
                color = Accent,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            // Only the latest point is marked: dotting every month turns the line into noise.
            val lastX = (points.size - 1) * stepX
            val lastY = yFor(values.last())
            drawCircle(color = Surface2, radius = 7f, center = Offset(lastX, lastY))
            drawCircle(color = Accent, radius = 4.5f, center = Offset(lastX, lastY))
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                points.first().period.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                points.last().period.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
