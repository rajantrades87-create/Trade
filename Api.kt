package com.example.kronosnse

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Candle(val t: String, val o: Double, val h: Double, val l: Double, val c: Double)
data class FCandle(val candle: Candle, val bandLow: Double, val bandHigh: Double)

data class Summary(
    val lastClose: Double,
    val forecastClose: Double,
    val changePct: Double,
    val forecastHigh: Double,
    val forecastLow: Double,
    val maxUpPct: Double,
    val maxDownPct: Double,
    val targetPct: Double,
    val targetHit: Boolean,
    val pathsUp: Int,
    val paths: Int,
)

data class Forecast(
    val symbol: String,
    val interval: String,
    val dataAsOf: String,
    val history: List<Candle>,
    val forecast: List<FCandle>,
    val summary: Summary,
)

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // first request loads the model and can be slow
        .build()

    fun predict(symbol: String, interval: String, steps: Int, targetPct: Double): Forecast {
        val url = (Config.BASE_URL + "/predict").toHttpUrl().newBuilder()
            .addQueryParameter("symbol", symbol)
            .addQueryParameter("interval", interval)
            .addQueryParameter("steps", steps.toString())
            .addQueryParameter("target", targetPct.toString())
            .build()
        val req = Request.Builder().url(url).header("X-API-Key", Config.API_KEY).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).getString("detail") }
                    .getOrDefault("Server error ${resp.code}")
                throw IOException(msg)
            }
            return parse(JSONObject(body))
        }
    }

    private fun JSONObject.toCandle() =
        Candle(getString("t"), getDouble("o"), getDouble("h"), getDouble("l"), getDouble("c"))

    private fun parse(j: JSONObject): Forecast {
        val hist = j.getJSONArray("history").let { a -> List(a.length()) { a.getJSONObject(it).toCandle() } }
        val fc = j.getJSONArray("forecast").let { a ->
            List(a.length()) {
                val o = a.getJSONObject(it)
                FCandle(o.toCandle(), o.getDouble("band_low"), o.getDouble("band_high"))
            }
        }
        val s = j.getJSONObject("summary")
        return Forecast(
            symbol = j.getString("symbol"),
            interval = j.getString("interval"),
            dataAsOf = j.getString("data_as_of"),
            history = hist,
            forecast = fc,
            summary = Summary(
                lastClose = s.getDouble("last_close"),
                forecastClose = s.getDouble("forecast_close"),
                changePct = s.getDouble("change_pct"),
                forecastHigh = s.getDouble("forecast_high"),
                forecastLow = s.getDouble("forecast_low"),
                maxUpPct = s.getDouble("max_up_pct"),
                maxDownPct = s.getDouble("max_down_pct"),
                targetPct = s.getDouble("target_pct"),
                targetHit = s.getBoolean("target_hit"),
                pathsUp = s.getInt("paths_up"),
                paths = s.getInt("paths"),
            ),
        )
    }
}
