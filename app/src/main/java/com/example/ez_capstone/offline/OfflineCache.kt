package com.example.ez_capstone.offline

import android.content.SharedPreferences
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class WeatherCacheEntry(val temp: Double, val desc: String, val cachedAt: Long)
data class RouteCacheEntry(val destination: String, val durationMin: Int, val cachedAt: Long)

@Singleton
class OfflineCache @Inject constructor(
    @Named("offline_cache_prefs") private val prefs: SharedPreferences
) {
    private val gson = Gson()
    private val weatherTtlMs = 6 * 60 * 60 * 1000L  // 6시간

    // SAFETY: synchronous SharedPrefs read — safe to call from main thread in driving context
    fun getCachedWeather(lat: Double, lng: Double): WeatherCacheEntry? {
        val key = "weather_${lat.toInt()}_${lng.toInt()}"
        val json = prefs.getString(key, null) ?: return null
        val entry = runCatching { gson.fromJson(json, WeatherCacheEntry::class.java) }.getOrNull() ?: return null
        return if (System.currentTimeMillis() - entry.cachedAt < weatherTtlMs) entry else null
    }

    fun putWeather(lat: Double, lng: Double, entry: WeatherCacheEntry) {
        val key = "weather_${lat.toInt()}_${lng.toInt()}"
        prefs.edit().putString(key, gson.toJson(entry)).apply()
    }

    // SAFETY: synchronous SharedPrefs read — instant return for cached navigation routes
    fun getCachedRoute(destination: String): RouteCacheEntry? {
        val key = "route_${destination.lowercase().replace(" ", "_")}"
        val json = prefs.getString(key, null) ?: return null
        return runCatching { gson.fromJson(json, RouteCacheEntry::class.java) }.getOrNull()
    }

    fun putRoute(destination: String, entry: RouteCacheEntry) {
        val key = "route_${destination.lowercase().replace(" ", "_")}"
        prefs.edit().putString(key, gson.toJson(entry)).apply()
    }
}
