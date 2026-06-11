package com.example.ez_capstone.predictive

import android.content.SharedPreferences
import com.example.ez_capstone.agent.models.AgentResponse
import com.google.gson.Gson
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PredictiveCache @Inject constructor(
    @Named("predictive_cache_prefs") private val prefs: SharedPreferences
) {
    private val gson = Gson()
    private val ttlMs = 30 * 60 * 1000L  // 30분

    private data class CachedPrediction(val response: AgentResponse, val cachedAt: Long)

    fun keyFor(snapshot: ContextSnapshot): String {
        val isWeekend = snapshot.dayOfWeek == Calendar.SATURDAY || snapshot.dayOfWeek == Calendar.SUNDAY
        val slot = when (snapshot.hourOfDay) {
            in 6..10 -> "morning"
            in 11..14 -> "noon"
            in 15..19 -> "evening"
            else -> "night"
        }
        return if (isWeekend) "predict_weekend_$slot" else "predict_weekday_$slot"
    }

    // SAFETY: synchronous SharedPrefs read — instant return for pre-computed nav predictions
    fun getCachedPrediction(snapshot: ContextSnapshot): AgentResponse? {
        val json = prefs.getString(keyFor(snapshot), null) ?: return null
        val entry = runCatching { gson.fromJson(json, CachedPrediction::class.java) }.getOrNull() ?: return null
        return if (System.currentTimeMillis() - entry.cachedAt < ttlMs) entry.response else null
    }

    fun putPrediction(snapshot: ContextSnapshot, response: AgentResponse) {
        val entry = CachedPrediction(response, System.currentTimeMillis())
        prefs.edit().putString(keyFor(snapshot), gson.toJson(entry)).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
