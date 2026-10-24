@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.kronosnse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    var symbol by mutableStateOf("RELIANCE")
    var interval by mutableStateOf("5m")
    var steps by mutableStateOf(12)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var result by mutableStateOf<Forecast?>(null)

    fun predict() {
        val s = symbol.trim()
        if (s.isEmpty() || loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                result = withContext(Dispatchers.IO) { Api.predict(s, interval, steps, Config.TARGET_PCT) }
            } catch (e: Exception) {
                result = null
                error = e.message ?: "Couldn’t reach the server"
            } finally {
                loading = false
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Ink, onPrimary = Color.White,
                    background = Paper, surface = Paper,
                    onBackground = Ink, onSurface = Ink,
                    secondaryContainer = Color(0xFFFBE3B8), onSecondaryContainer = Ink,
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Screen() }
            }
        }
    }
}

@Composable
fun Screen(vm: MainViewModel = viewModel()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Kronos NSE", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        OutlinedTextField(
            value = vm.symbol,
            onValueChange = { vm.symbol = it.uppercase() },
            label = { Text("NSE symbol") },
            placeholder = { Text("RELIANCE, SBIN, NIFTY") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { vm.predict() }),
        )

        ChipRow("Candle size", listOf("5m", "15m", "1h", "1d"), vm.interval, { it }) { vm.interval = it }
        ChipRow("Look ahead", listOf(6, 12, 24), vm.steps, { "$it candles" }) { vm.steps = it }

        Button(
            onClick = { vm.predict() },
            enabled = !vm.loading && vm.symbol.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.loading) {
                CircularProgressIndicator(
                    Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(10.dp))
                Text("Forecasting…")
            } else {
                Text("Get forecast")
            }
        }

        vm.error?.let { Text(it, color = Down) }
        vm.result?.let { ResultView(it) }
    }
}

@Composable
fun <T> ChipRow(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(text(o)) })
            }
        }
    }
}

@Composable
fun ResultView(f: Forecast) {
    val s = f.summary
    val moveColor = if (s.changePct >= 0) Up else Down
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${f.symbol}, ${f.interval} candles", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("₹" + fmt(s.forecastClose), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(signed(s.changePct) + "%", style = MaterialTheme.typography.titleMedium, color = moveColor)
        }
        Text("Forecast close after ${f.forecast.size} candles", style = MaterialTheme.typography.bodySmall)

        CandleChart(
            history = f.history,
            forecast = f.forecast,
            lastClose = s.lastClose,
            targetPrice = s.lastClose * (1 + s.targetPct / 100),
            modifier = Modifier.fillMaxWidth().height(300.dp),
        )
        Text(
            "Hollow candles on the saffron strip are the forecast. The bar behind each shows the range of " +
                "closes across ${s.paths} sampled paths. Dashed lines: last close and +${s.targetPct}% target.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        StatRow("Last close", "₹" + fmt(s.lastClose))
        StatRow("Forecast high", "₹${fmt(s.forecastHigh)} (${signed(s.maxUpPct)}%)")
        StatRow("Forecast low", "₹${fmt(s.forecastLow)} (${signed(s.maxDownPct)}%)")
        StatRow("Paths closing higher", "${s.pathsUp} of ${s.paths}")
        StatRow("+${s.targetPct}% target", if (s.targetHit) "Forecast high reaches it" else "Not reached")
        StatRow("Data as of", f.dataAsOf)
        HorizontalDivider()
        Text(
            "Model forecast from delayed free data. It can be wrong, so it isn’t investment advice.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun StatRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
