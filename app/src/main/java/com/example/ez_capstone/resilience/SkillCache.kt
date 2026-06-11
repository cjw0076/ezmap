package com.example.ez_capstone.resilience

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillCache @Inject constructor() {

    companion object {
        private const val TAG = "SkillCache"
        private const val MAX_ENTRIES = 50
        private const val TTL_SHORT = 3 * 60 * 1000L   // 3분 (날씨·교통)
        private const val TTL_NORMAL = 5 * 60 * 1000L  // 5분 (그 외)
        private val SHORT_TTL_TOOLS = setOf(
            "get_weather", "get_weather_kma", "get_gas_stations",
            "get_traffic_speed", "get_parking"
        )

        fun argsHash(args: JSONObject): String {
            val sortedKeys = args.keys().asSequence().sorted().toList()
            val normalized = sortedKeys.joinToString(",") { "$it=${args.opt(it)}" }
            return normalized.hashCode().toString()
        }
    }

    private data class CacheEntry(val result: String, val expiresAt: Long)

    /**
     * LRU cache backed by LinkedHashMap — works in both Android runtime and JVM unit tests.
     * android.util.LruCache is not available in the JVM test environment.
     */
    private val cache: LinkedHashMap<String, CacheEntry> =
        object : LinkedHashMap<String, CacheEntry>(MAX_ENTRIES + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>): Boolean =
                size > MAX_ENTRIES
        }

    fun get(toolName: String, argsHash: String): String? {
        val key = "$toolName:$argsHash"
        val entry = synchronized(cache) { cache[key] } ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            synchronized(cache) { cache.remove(key) }
            return null
        }
        android.util.Log.d(TAG, "Cache HIT: $key")
        return entry.result
    }

    fun put(toolName: String, argsHash: String, result: String) {
        val ttl = if (toolName in SHORT_TTL_TOOLS) TTL_SHORT else TTL_NORMAL
        val key = "$toolName:$argsHash"
        synchronized(cache) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + ttl)
        }
        android.util.Log.d(TAG, "Cache SET: $key (ttl=${ttl / 1000}s)")
    }

    fun invalidate(toolName: String) {
        synchronized(cache) {
            cache.keys.filter { it.startsWith("$toolName:") }.forEach { cache.remove(it) }
        }
    }

    fun clear() = synchronized(cache) { cache.clear() }
}
