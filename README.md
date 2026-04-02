# Stock Tracker for Karoo

A [Karoo Extension](https://github.com/hammerheadnav/karoo-ext) that brings **live stock prices** to your [Hammerhead Karoo](https://www.hammerhead.io/) cycling computer.

Monitor up to **10 stocks, indices, crypto, or futures** directly on your ride screen with real-time data from Yahoo Finance.

---

## Features

- **10 configurable stock slots** - add any ticker to any slot via the app
- **Rolling Stock Ticker** - a single data field that automatically cycles through all your stocks every 5 seconds
- **Custom graphical display** for each data field showing:
  - Ticker symbol (bold header)
  - Current price (colored green/red based on daily performance)
  - Daily change percentage with arrow indicator
  - Intraday sparkline chart with colored fill
- **Supports all Yahoo Finance symbols:**
  - Stocks: `AAPL`, `TSLA`, `MSFT`
  - Crypto: `BTC-USD`, `ETH-USD`
  - Indices: `^VIX`, `^GSPC`, `^DJI`
  - Futures: `GC=F`, `CL=F`
- **5-minute refresh interval** - keeps data fresh without draining battery
- **Resilient display** - if a refresh fails, the last known price stays on screen

---

## Installation

### Sideload the APK

1. Download the latest `stock-tracker.apk` from the [Releases](https://github.com/gingerr6/stock-tracker/releases) page
2. Right-click (or long press) the APK and share it to the **Hammerhead Companion** app to install


---

## Setup

### 1. Configure your stocks

Open the **Stock Tracker** app on your Karoo. You'll see 10 numbered slots. Enter a Yahoo Finance symbol in each slot and tap **Save**.

### 2. Add data fields to your ride screen

Go to your ride profile and add data fields from the **Stock Tracker** extension:

| Data Field | Description |
|---|---|
| **Stock 1 - 10** | Dedicated field for the stock configured in that slot |
| **Stock Ticker** | Automatically cycles through all configured stocks every 5 seconds |

### 3. Ride and monitor

Prices update every 5 minutes via the Karoo's network connection (WiFi or Bluetooth companion).

---

## Data Field Display

Each stock data field shows a custom graphical view:

```
AAPL
182.52    +1.24%
~~~~~~~~~~~~~~~~~~~~~~~~  (sparkline chart)
```

- **Ticker symbol** at the top in bold
- **Price** colored green (up) or red (down) for the day
- **Change %** with arrow indicator (e.g. `+1.24%` or `-2.10%`)
- **Sparkline** showing the intraday price movement with a colored fill

---

## Project Structure

```
app/
  src/main/kotlin/io/gingerr6/stocktracker/
    MainActivity.kt              # App entry point
    TabLayout.kt                 # Stock configuration UI (StocksScreen)
    extension/
      SampleExtension.kt         # Karoo extension service
      StockPriceDataType.kt      # Per-slot stock data type (x10)
      StockRollingDataType.kt    # Rolling ticker that cycles all stocks
      StockPriceView.kt          # Glance composable for the display
      StockPreferences.kt        # SharedPreferences for saved symbols
      Sparkline.kt               # Bitmap sparkline chart generator
lib/
  src/main/kotlin/io/hammerhead/karooext/
    ...                           # Karoo Extensions library (do not modify)
```

---

## How It Works

1. **Stock symbols** are saved to SharedPreferences via the app UI
2. Each **StockPriceDataType** (slots 1-10) runs a coroutine that:
   - Reads its configured symbol from preferences
   - Fetches price data from Yahoo Finance's chart API via the Karoo HTTP system
   - Parses the response for current price, previous close, and intraday price points
   - Generates a sparkline bitmap from the intraday data
   - Updates the custom Glance view with ticker, price, change %, and chart
3. The **StockRollingDataType** fetches data for all configured symbols and rotates the display every 5 seconds
4. All network requests go through `KarooSystemService` / `OnHttpResponse.MakeHttpRequest` which handles connectivity via WiFi or Bluetooth companion

---

## Building from Source

### Requirements

- Android Studio
- JDK 17+
- GitHub Packages credentials for karoo-ext (see below)

### GitHub Packages Authentication

The karoo-ext library is hosted on GitHub Packages. Add your credentials to `local.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

Or set environment variables `USERNAME` and `TOKEN`.

### Build

```bash
./gradlew app:assembleDebug
```

### Install

```bash
./gradlew app:installDebug
```

---

## Technical Notes

- The karoo-ext library (`lib/`) uses the `io.hammerhead.karooext` package and **must not be renamed** - these are Karoo system IPC constants
- The app package is `io.gingerr6.stocktracker`
- Custom views use [Jetpack Glance](https://developer.android.com/jetpack/compose/glance) for RemoteViews rendering
- Yahoo Finance API endpoint: `query1.finance.yahoo.com/v8/finance/chart/{SYMBOL}?interval=5m&range=1d`
- Karoo HTTP requests have a 100KB limit - the 5-minute interval chart data fits comfortably within this

---

## License

Based on [karoo-ext](https://github.com/hammerheadnav/karoo-ext) by SRAM LLC, licensed under the Apache License 2.0.
