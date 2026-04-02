package io.gingerr6.stocktracker

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.gingerr6.stocktracker.extension.StockPreferences

@Composable
fun StocksScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Stock Tracker", fontSize = 18.sp)
        Text("Use Yahoo Finance symbols:", fontSize = 12.sp, color = Color.Gray)
        Text("Stocks: AAPL, TSLA | Crypto: ETH-USD, BTC-USD", fontSize = 11.sp, color = Color.Gray)
        Text("Indices: ^VIX, ^GSPC | Futures: GC=F", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))
        for (slot in 1..10) {
            var symbol by remember {
                mutableStateOf(StockPreferences.getSymbol(context, slot) ?: "")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$slot.", modifier = Modifier.padding(end = 8.dp))
                TextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Symbol") },
                )
                Button(
                    onClick = {
                        StockPreferences.setSymbol(context, slot, symbol.ifBlank { null })
                        Toast.makeText(
                            context,
                            if (symbol.isBlank()) "Slot $slot cleared" else "Slot $slot set to ${symbol.uppercase().trim()}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Save")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Prices update every 30s via Yahoo Finance.", fontSize = 11.sp, color = Color.Gray)
        Text("Add Stock 1-10 data fields to your ride screen.", fontSize = 11.sp, color = Color.Gray)
    }
}
