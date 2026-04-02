package io.gingerr6.stocktracker.extension

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.gingerr6.stocktracker.R

@Composable
fun StockPriceView(state: StockState) {
    val symbol = state.symbol.ifBlank { "--" }
    val priceText = state.price?.let { String.format("%.2f", it) } ?: "--"

    val isUp = if (state.price != null && state.previousClose != null) {
        state.price >= state.previousClose
    } else {
        null
    }

    val priceColor = when (isUp) {
        true -> ColorProvider(R.color.stock_green)
        false -> ColorProvider(R.color.stock_red)
        null -> ColorProvider(R.color.stock_neutral)
    }

    val changeText = if (state.price != null && state.previousClose != null && state.previousClose > 0) {
        val changePct = ((state.price - state.previousClose) / state.previousClose) * 100
        val sign = if (changePct >= 0) "+" else ""
        val arrow = if (changePct >= 0) "\u25B2" else "\u25BC"
        "$arrow ${sign}${String.format("%.2f", changePct)}%"
    } else {
        null
    }

    Column(
        modifier = GlanceModifier.fillMaxSize().padding(4.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        // Ticker name
        Text(
            text = symbol,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(2.dp))

        // Price + Change %
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = priceText,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = priceColor,
                ),
            )
            if (changeText != null) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = changeText,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = priceColor,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(2.dp))

        // Sparkline chart
        if (state.sparklineBitmap != null) {
            Image(
                provider = ImageProvider(state.sparklineBitmap),
                contentDescription = "$symbol chart",
                modifier = GlanceModifier.fillMaxWidth().height(40.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}
