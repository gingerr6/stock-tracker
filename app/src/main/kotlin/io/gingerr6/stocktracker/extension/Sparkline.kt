package io.gingerr6.stocktracker.extension

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

fun createSparklineBitmap(
    prices: List<Double>,
    isUp: Boolean,
    width: Int = 224,
    height: Int = 64,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (prices.size < 2) return bitmap

    val canvas = Canvas(bitmap)
    val lineColor = if (isUp) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
    val fillColor = if (isUp) 0x334CAF50 else 0x33F44336

    val min = prices.min()
    val max = prices.max()
    val range = (max - min).coerceAtLeast(0.001)
    val padding = 4f

    val drawWidth = width - padding * 2
    val drawHeight = height - padding * 2

    val linePath = Path()
    val fillPath = Path()
    prices.forEachIndexed { i, price ->
        val x = padding + (i.toFloat() / (prices.size - 1)) * drawWidth
        val y = padding + drawHeight - ((price - min) / range * drawHeight).toFloat()
        if (i == 0) {
            linePath.moveTo(x, y)
            fillPath.moveTo(x, y)
        } else {
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    fillPath.lineTo(padding + drawWidth, padding + drawHeight)
    fillPath.lineTo(padding, padding + drawHeight)
    fillPath.close()

    canvas.drawPath(fillPath, Paint().apply {
        color = fillColor
        style = Paint.Style.FILL
        isAntiAlias = true
    })
    canvas.drawPath(linePath, Paint().apply {
        color = lineColor
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    })

    return bitmap
}
