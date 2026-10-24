package com.example.kronosnse

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val Ink = Color(0xFF14213D)
val Paper = Color(0xFFEEF2F6)
val Saffron = Color(0xFFE08A00)
val Up = Color(0xFF1B8A5A)
val Down = Color(0xFFC8372D)
private val Grid = Color(0xFFCBD3DE)

fun fmt(d: Double): String = String.format(Locale.US, "%.2f", d)
fun signed(d: Double): String = (if (d >= 0) "+" else "−") + fmt(abs(d))

/** Solid candles = history. Hollow candles on the saffron strip = forecast.
 *  The translucent bar behind each forecast candle spans the lowest to highest
 *  close among the sampled paths. Dashed lines: last close and profit target. */
@Composable
fun CandleChart(
    history: List<Candle>,
    forecast: List<FCandle>,
    lastClose: Double,
    targetPrice: Double,
    modifier: Modifier = Modifier,
) {
    val label = remember {
        android.graphics.Paint().apply { color = 0xFF5B6475.toInt(); isAntiAlias = true }
    }
    Canvas(modifier) {
        if (history.isEmpty()) return@Canvas
        label.textSize = 11.sp.toPx()

        var lo = min(history.minOf { it.l }, forecast.minOfOrNull { min(it.candle.l, it.bandLow) } ?: Double.MAX_VALUE)
        var hi = max(history.maxOf { it.h }, forecast.maxOfOrNull { max(it.candle.h, it.bandHigh) } ?: -Double.MAX_VALUE)
        hi = max(hi, targetPrice)
        if (hi - lo < 1e-9) return@Canvas
        val pad = (hi - lo) * 0.04
        lo -= pad
        hi += pad

        val n = history.size + forecast.size
        val padL = 4.dp.toPx()
        val axisW = 56.dp.toPx()
        val plotW = size.width - axisW - padL
        val h = size.height
        val step = plotW / n
        val bodyW = step * 0.62f
        fun x(i: Int) = padL + step * (i + 0.5f)
        fun y(p: Double) = ((hi - p) / (hi - lo) * h).toFloat()

        // forecast strip
        drawRect(
            Saffron.copy(alpha = 0.10f),
            topLeft = Offset(padL + step * history.size, 0f),
            size = Size(step * forecast.size, h),
        )

        // grid + price labels
        for (k in 0..3) {
            val p = lo + (hi - lo) * k / 3
            val yy = y(p)
            drawLine(Grid, Offset(padL, yy), Offset(padL + plotW, yy), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", p), padL + plotW + 6.dp.toPx(), yy + 4.dp.toPx(), label,
            )
        }

        // dashed reference lines
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        drawLine(Ink.copy(alpha = 0.55f), Offset(padL, y(lastClose)), Offset(padL + plotW, y(lastClose)),
            strokeWidth = 1.5f, pathEffect = dash)
        drawLine(Saffron, Offset(padL, y(targetPrice)), Offset(padL + plotW, y(targetPrice)),
            strokeWidth = 1.5f, pathEffect = dash)

        // close-range bars across sampled paths
        forecast.forEachIndexed { i, f ->
            val idx = history.size + i
            drawRect(
                Saffron.copy(alpha = 0.22f),
                topLeft = Offset(x(idx) - step * 0.5f, y(f.bandHigh)),
                size = Size(step, max(y(f.bandLow) - y(f.bandHigh), 2f)),
            )
        }

        fun candle(i: Int, c: Candle, hollow: Boolean) {
            val col = if (c.c >= c.o) Up else Down
            drawLine(col, Offset(x(i), y(c.h)), Offset(x(i), y(c.l)), strokeWidth = 1.5.dp.toPx())
            val top = y(max(c.o, c.c))
            val bodyH = max(y(min(c.o, c.c)) - top, 1.5f)
            val tl = Offset(x(i) - bodyW / 2, top)
            if (hollow) drawRect(col, tl, Size(bodyW, bodyH), style = Stroke(1.5.dp.toPx()))
            else drawRect(col, tl, Size(bodyW, bodyH))
        }
        history.forEachIndexed { i, c -> candle(i, c, hollow = false) }
        forecast.forEachIndexed { i, f -> candle(history.size + i, f.candle, hollow = true) }
    }
}
