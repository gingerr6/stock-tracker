package io.gingerr6.stocktracker.extension

import android.content.Context
import android.content.SharedPreferences

object StockPreferences {
    private const val PREFS_NAME = "stock_preferences"
    private const val KEY_PREFIX = "stock_symbol_"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSymbol(context: Context, slot: Int): String? {
        return prefs(context).getString("$KEY_PREFIX$slot", null)
    }

    fun setSymbol(context: Context, slot: Int, symbol: String?) {
        prefs(context).edit().putString("$KEY_PREFIX$slot", symbol?.uppercase()?.trim()).apply()
    }
}
