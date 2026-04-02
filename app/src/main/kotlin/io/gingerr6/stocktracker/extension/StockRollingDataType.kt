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

@Serializable
private data class RollingYahooChartResponse(val chart: RollingYahooChart)

@Serializable
private data class RollingYahooChart(
    val result: List<RollingYahooChartResult>? = null,
)

@Serializable
private data class RollingYahooChartResult(
    val meta: RollingYahooMeta,
    val indicators: RollingYahooIndicators? = null,
)

@Serializable
private data class RollingYahooMeta(
    val regularMarketPrice: Double? = null,
    val previousClose: Double? = null,
    val shortName: String? = null,
    val longName: String? = null,
)

@Serializable
private data class RollingYahooIndicators(val quote: List<RollingYahooQuote>? = null)

@Serializable
private data class RollingYahooQuote(val close: List<Double?>? = null)

private val rollingJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class StockRollingDataType(
    private val karooSystem: KarooSystemService,
    private val context: Context,
    extension: String,
) : DataTypeImpl(extension, TYPE_ID) {

    private val glance = GlanceRemoteViews()
    private val viewState = MutableStateFlow(StockState())

    // Cache of fetched stock states keyed by symbol
    private val stockCache = mutableMapOf<String, StockState>()

    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("start stock-rolling stream")
        val streaming = StreamState.Streaming(DataPoint(dataTypeId))
        val job = CoroutineScope(Dispatchers.IO).launch {
            // Always tell Karoo we're streaming
            emitter.onNext(streaming)

            // Fetch loop: fast for first 2 mins then every 5 mins
            launch {
                val startTime = System.currentTimeMillis()
                while (true) {
                    val symbols = (1..10).mapNotNull { StockPreferences.getSymbol(context, it) }
                    for (symbol in symbols) {
                        val quote = fetchQuote(symbol)
                        if (quote != null) {
                            val isUp = quote.previousClose == null || quote.price >= quote.previousClose
                            val sparkline = if (quote.closePrices.size >= 2) {
                                createSparklineBitmap(quote.closePrices, isUp)
                            } else {
                                null
                            }
                            stockCache[symbol] = StockState(
                                symbol = symbol,
                                price = quote.price,
                                previousClose = quote.previousClose,
                                sparklineBitmap = sparkline,
                            )
                        }
                    }
                    stockCache.keys.retainAll(symbols.toSet())
                    val elapsed = System.currentTimeMillis() - startTime
                    delay(if (elapsed < FAST_PERIOD_MS) FAST_FETCH_MS else SLOW_FETCH_MS)
                }
            }

            // Cycle loop: rotate displayed stock every 5s
            var index = 0
            while (true) {
                val symbols = (1..10).mapNotNull { StockPreferences.getSymbol(context, it) }
                if (symbols.isNotEmpty()) {
                    index = index % symbols.size
                    val state = stockCache[symbols[index]]
                    viewState.value = state ?: StockState(symbol = symbols[index])
                    index++
                }
                emitter.onNext(streaming)
                delay(CYCLE_INTERVAL_MS)
            }
        }
        emitter.setCancellable {
            Timber.d("stop stock-rolling stream")
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("Starting stock rolling view")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
        }
        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            viewState.collect { state ->
                val result = glance.compose(context, DpSize.Unspecified) {
                    StockPriceView(state)
                }
                emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Timber.d("Stopping stock rolling view")
            configJob.cancel()
            viewJob.cancel()
        }
    }

    private suspend fun fetchQuote(symbol: String): RollingQuoteResult? {
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
                                Timber.w("Rolling HTTP error for $symbol: ${state.error}")
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
                val response = rollingJson.decodeFromString<RollingYahooChartResponse>(result)
                val chartResult = response.chart.result?.firstOrNull()
                val meta = chartResult?.meta
                val price = meta?.regularMarketPrice
                if (price != null) {
                    val closePrices = chartResult.indicators?.quote?.firstOrNull()
                        ?.close?.filterNotNull() ?: emptyList()
                    RollingQuoteResult(price, meta.previousClose, closePrices)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch rolling quote for $symbol")
            null
        }
    }

    companion object {
        const val TYPE_ID = "stock-rolling"
        const val CYCLE_INTERVAL_MS = 5_000L
        const val FAST_FETCH_MS = 5_000L
        const val FAST_PERIOD_MS = 120_000L
        const val SLOW_FETCH_MS = 300_000L
    }
}

private data class RollingQuoteResult(
    val price: Double,
    val previousClose: Double?,
    val closePrices: List<Double>,
)
