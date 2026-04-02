@file:OptIn(FlowPreview::class)

package io.gingerr6.stocktracker.extension

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

// --- Yahoo Finance API models ---

@Serializable
private data class YahooChartResponse(val chart: YahooChart)

@Serializable
private data class YahooChart(
    val result: List<YahooChartResult>? = null,
    val error: YahooError? = null,
)

@Serializable
private data class YahooChartResult(
    val meta: YahooMeta,
    val indicators: YahooIndicators? = null,
)

@Serializable
private data class YahooMeta(
    val regularMarketPrice: Double? = null,
    val previousClose: Double? = null,
    val symbol: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val currency: String? = null,
)

@Serializable
private data class YahooIndicators(val quote: List<YahooQuote>? = null)

@Serializable
private data class YahooQuote(val close: List<Double?>? = null)

@Serializable
private data class YahooError(val code: String? = null, val description: String? = null)

private val stockJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// --- Shared view state ---

data class StockState(
    val symbol: String = "",
    val companyName: String? = null,
    val price: Double? = null,
    val previousClose: Double? = null,
    val sparklineBitmap: android.graphics.Bitmap? = null,
)

// --- Data type ---

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class StockPriceDataType(
    private val karooSystem: KarooSystemService,
    private val context: Context,
    extension: String,
    private val slot: Int,
) : DataTypeImpl(extension, "$TYPE_ID_PREFIX$slot") {

    private val glance = GlanceRemoteViews()
    private val stockState = MutableStateFlow(StockState())

    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("start stock-price-$slot stream")
        val streaming = StreamState.Streaming(DataPoint(dataTypeId))
        val job = CoroutineScope(Dispatchers.IO).launch {
            // Always tell Karoo we're streaming so it never overlays its own UI
            emitter.onNext(streaming)
            val startTime = System.currentTimeMillis()

            while (true) {
                val symbol = StockPreferences.getSymbol(context, slot)
                if (symbol.isNullOrBlank()) {
                    stockState.value = StockState()
                    delay(FAST_POLL_MS)
                    continue
                }

                val quote = fetchStockQuote(symbol)
                if (quote != null) {
                    val isUp = quote.previousClose == null || quote.price >= quote.previousClose
                    val sparkline = if (quote.closePrices.size >= 2) {
                        createSparklineBitmap(quote.closePrices, isUp)
                    } else {
                        null
                    }
                    stockState.value = StockState(
                        symbol = symbol,
                        companyName = quote.companyName,
                        price = quote.price,
                        previousClose = quote.previousClose,
                        sparklineBitmap = sparkline,
                    )
                }
                // Always stay in streaming state, keep last good data on failure
                emitter.onNext(streaming)

                // Fast polling for first 2 minutes, then slow
                val elapsed = System.currentTimeMillis() - startTime
                delay(if (elapsed < FAST_PERIOD_MS) FAST_POLL_MS else SLOW_POLL_MS)
            }
        }
        emitter.setCancellable {
            Timber.d("stop stock-price-$slot stream")
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("Starting stock view $slot")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
        }
        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            stockState.collect { state ->
                val result = glance.compose(context, DpSize.Unspecified) {
                    StockPriceView(state)
                }
                emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Timber.d("Stopping stock view $slot")
            configJob.cancel()
            viewJob.cancel()
        }
    }

    private suspend fun fetchStockQuote(symbol: String): StockQuoteResult? {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=5m&range=1d"
            val result = callbackFlow {
                val listenerId = karooSystem.addConsumer(
                    OnHttpResponse.MakeHttpRequest(
                        method = "GET",
                        url = url,
                        waitForConnection = false,
                    ),
                ) { event: OnHttpResponse ->
                    when (val state = event.state) {
                        is HttpResponseState.Complete -> {
                            if (state.error != null) {
                                Timber.w("Stock HTTP error for $symbol: ${state.error}")
                                trySend(null)
                            } else {
                                trySend(state.body?.decodeToString())
                            }
                        }
                        is HttpResponseState.InProgress -> {}
                        is HttpResponseState.Queued -> {}
                    }
                }
                awaitClose { karooSystem.removeConsumer(listenerId) }
            }
                .timeout(15.seconds)
                .mapNotNull { it }
                .firstOrNull()

            if (result != null) {
                val response = stockJson.decodeFromString<YahooChartResponse>(result)
                val chartResult = response.chart.result?.firstOrNull()
                val meta = chartResult?.meta
                val price = meta?.regularMarketPrice
                if (price != null) {
                    val closePrices = chartResult.indicators?.quote?.firstOrNull()
                        ?.close?.filterNotNull() ?: emptyList()
                    val companyName = meta.shortName ?: meta.longName
                    Timber.d("Stock $symbol: $price prevClose=${meta.previousClose} name=$companyName points=${closePrices.size}")
                    StockQuoteResult(price, meta.previousClose, companyName, closePrices)
                } else {
                    null
                }
            } else {
                Timber.w("No response for stock $symbol")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch stock quote for $symbol")
            null
        }
    }

    companion object {
        const val TYPE_ID_PREFIX = "stock-price-"
        const val FAST_POLL_MS = 5_000L
        const val FAST_PERIOD_MS = 120_000L
        const val SLOW_POLL_MS = 300_000L
    }
}

private data class StockQuoteResult(
    val price: Double,
    val previousClose: Double?,
    val companyName: String?,
    val closePrices: List<Double>,
)
