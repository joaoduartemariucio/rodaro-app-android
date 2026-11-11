package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.domain.fueling.calculations.ConsumptionInterval
import br.com.joaoduartemariucio.rodaro.presentation.common.Formats
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedDay
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedShortMonth
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.VehicleTimeline
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val axisWidth = 40.dp
private val labelHeight = 18.dp

@Composable
fun ConsumptionChart(
    intervals: List<ConsumptionInterval>,
    average: Double?,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (intervals.isEmpty()) return@Canvas
        val values = intervals.map { it.kilometersPerLiter } + listOfNotNull(average)
        val ticks = niceTicks(values.min(), values.max(), includeZero = false)
        val plot = Size(size.width - axisWidth.toPx(), size.height - labelHeight.toPx())

        drawGrid(ticks, plot, measurer, labelStyle, gridColor) { Formats.number(it, if (ticks.step < 1) 1 else 0) }

        val times = intervals.map { it.endDate.toEpochMilli().toDouble() }
        val minTime = times.min()
        val span = (times.max() - minTime).takeIf { it > 0 } ?: 1.0
        val x = { time: Double -> if (times.size == 1) plot.width / 2 else ((time - minTime) / span * plot.width).toFloat() }
        val y = { value: Double -> ((ticks.max - value) / (ticks.max - ticks.min) * plot.height).toFloat() }

        val points = intervals.mapIndexed { index, interval -> Offset(x(times[index]), y(interval.kilometersPerLiter)) }
        val path =
            Path().apply {
                moveTo(points.first().x, points.first().y)
                points.zipWithNext { start, end ->
                    val middle = (start.x + end.x) / 2
                    cubicTo(middle, start.y, middle, end.y, end.x, end.y)
                }
            }
        drawPath(path, Palette.fuel, style = Stroke(width = 2.5.dp.toPx()))
        points.forEach { drawCircle(Palette.fuel, radius = 3.5.dp.toPx(), center = it) }

        if (average != null) {
            drawLine(
                averageColor,
                Offset(0f, y(average)),
                Offset(plot.width, y(average)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }

        val labelIndices = evenlySpaced(intervals.size, 4)
        for (index in labelIndices) {
            val text = intervals[index].endDate.formattedDay()
            val layout = measurer.measure(text, labelStyle)
            val left = (points[index].x - layout.size.width / 2).coerceIn(0f, plot.width - layout.size.width)
            drawText(layout, topLeft = Offset(left, plot.height + 4.dp.toPx()))
        }
    }
}

@Composable
fun SpendingChart(
    months: List<VehicleTimeline.MonthSpending>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        if (months.isEmpty()) return@Canvas
        val ticks = niceTicks(0.0, months.maxOf { maxOf(0.0, it.total.toDouble()) }, includeZero = true)
        val plot = Size(size.width - axisWidth.toPx(), size.height - labelHeight.toPx())

        drawGrid(ticks, plot, measurer, labelStyle, gridColor) { Formats.number(it, 0) }

        val slot = plot.width / months.size
        val barWidth = slot * 0.55f
        val scale = { value: Double -> (value / ticks.max * plot.height).toFloat() }
        months.forEachIndexed { index, month ->
            val left = index * slot + (slot - barWidth) / 2
            val fuel = scale(month.fuel.toDouble())
            val service = scale(month.service.toDouble())
            val expenses = scale(month.expenses.toDouble())
            val radius = CornerRadius(3.dp.toPx())
            if (fuel > 0) drawRoundRect(Palette.fuel, Offset(left, plot.height - fuel), Size(barWidth, fuel), radius)
            if (service > 0) drawRoundRect(Palette.service, Offset(left, plot.height - fuel - service), Size(barWidth, service), radius)
            if (expenses > 0) {
                val below = maxOf(0f, fuel) + maxOf(0f, service)
                drawRoundRect(Palette.expense, Offset(left, plot.height - below - expenses), Size(barWidth, expenses), radius)
            }

            val layout = measurer.measure(month.month.formattedShortMonth(), labelStyle)
            drawText(layout, topLeft = Offset(index * slot + (slot - layout.size.width) / 2, plot.height + 4.dp.toPx()))
        }
    }
}

private data class Ticks(
    val min: Double,
    val max: Double,
    val step: Double,
) {
    val values: List<Double> get() = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
}

private fun niceTicks(
    low: Double,
    high: Double,
    includeZero: Boolean,
): Ticks {
    val bottom = if (includeZero) 0.0 else low
    val range = (high - bottom).takeIf { it > 0 } ?: maxOf(1.0, high * 0.1)
    val rough = range / 3
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    val min = if (includeZero) 0.0 else floor(low / step) * step
    val max = ceil(high / step) * step
    return Ticks(min, if (max > min) max else min + step, step)
}

private fun DrawScope.drawGrid(
    ticks: Ticks,
    plot: Size,
    measurer: TextMeasurer,
    style: TextStyle,
    color: Color,
    label: (Double) -> String,
) {
    for (value in ticks.values) {
        val y = ((ticks.max - value) / (ticks.max - ticks.min) * plot.height).toFloat()
        drawLine(color, Offset(0f, y), Offset(plot.width, y), strokeWidth = 1f)
        val layout = measurer.measure(label(value), style)
        drawText(
            layout,
            topLeft =
                Offset(
                    plot.width + 6.dp.toPx(),
                    (y - layout.size.height / 2).coerceIn(
                        0f,
                        size.height - layout.size.height,
                    ),
                ),
        )
    }
}

private fun evenlySpaced(
    count: Int,
    desired: Int,
): List<Int> {
    if (count <= desired) return (0 until count).toList()
    return (0 until desired).map { it * (count - 1) / (desired - 1) }.distinct()
}
