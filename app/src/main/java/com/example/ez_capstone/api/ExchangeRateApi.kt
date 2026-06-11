package com.example.ez_capstone.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ExchangeRate-API — 키 불필요.
 * Tool: get_exchange_rate
 * 실시간 환율 조회.
 */
@Singleton
class ExchangeRateApi @Inject constructor() {

    companion object {
        private const val TAG = "ExchangeRateApi"
        private const val BASE = "https://open.er-api.com/v6/latest"
        private val POPULAR = listOf("USD", "EUR", "JPY", "CNY", "GBP", "AUD", "CAD", "CHF", "HKD", "THB")
    }

    private val client = OkHttpClient()

    // 1시간 캐시
    private var cache: Pair<String, JSONObject>? = null
    private var cacheTime = 0L

    data class ExchangeResult(
        val base: String,
        val rates: Map<String, Double>,
        val lastUpdate: String
    )

    suspend fun getRates(
        baseCurrency: String = "KRW",
        targetCurrency: String? = null
    ): ExchangeResult = withContext(Dispatchers.IO) {
        try {
            val base = baseCurrency.uppercase()
            val now = System.currentTimeMillis()

            // 캐시 확인 (1시간)
            val cached = cache
            if (cached != null && cached.first == base && now - cacheTime < 3_600_000) {
                return@withContext parseResult(base, cached.second, targetCurrency)
            }

            val url = "$BASE/$base"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: return@withContext ExchangeResult(base, emptyMap(), "")

            val json = JSONObject(body)
            if (json.optString("result") != "success") {
                return@withContext ExchangeResult(base, emptyMap(), "API 오류")
            }

            cache = base to json
            cacheTime = now

            parseResult(base, json, targetCurrency)
        } catch (e: Exception) {
            Log.e(TAG, "Exchange rate fetch failed", e)
            ExchangeResult(baseCurrency, emptyMap(), "오류: ${e.message}")
        }
    }

    private fun parseResult(base: String, json: JSONObject, target: String?): ExchangeResult {
        val rates = json.optJSONObject("rates") ?: return ExchangeResult(base, emptyMap(), "")
        val lastUpdate = json.optString("time_last_update_utc", "")

        val resultMap = if (target != null) {
            val t = target.uppercase()
            val rate = rates.optDouble(t, -1.0)
            if (rate > 0) mapOf(t to rate) else emptyMap()
        } else {
            POPULAR.mapNotNull { code ->
                val rate = rates.optDouble(code, -1.0)
                if (rate > 0) code to rate else null
            }.toMap()
        }

        return ExchangeResult(base, resultMap, lastUpdate)
    }
}
