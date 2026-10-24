package com.example.kronosnse

object Config {
    // Android emulator -> your PC is 10.0.2.2. On a real phone use your PC's LAN IP
    // (e.g. http://192.168.1.20:8000) or your deployed https:// address.
    const val BASE_URL = "http://10.0.2.2:8000"

    // Must match the API_KEY environment variable on the server (empty = none).
    const val API_KEY = ""

    // Profit target used for the "target reached?" check, in percent.
    const val TARGET_PCT = 1.0
}
