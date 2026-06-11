package com.example.ez_capstone.resilience

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SkillCacheTest {

    private lateinit var cache: SkillCache

    @Before
    fun setup() { cache = SkillCache() }

    @Test
    fun `miss returns null`() {
        assertNull(cache.get("get_weather", "abc"))
    }

    @Test
    fun `put and get returns result`() {
        cache.put("search_places", "hash123", """{"places":[]}""")
        assertEquals("""{"places":[]}""", cache.get("search_places", "hash123"))
    }

    @Test
    fun `different tool name is different key`() {
        cache.put("get_weather", "hash1", "weather_result")
        assertNull(cache.get("get_directions", "hash1"))
    }

    @Test
    fun `argsHash is order-independent`() {
        val args1 = JSONObject().apply { put("lat", 37.5); put("lng", 127.0) }
        val args2 = JSONObject().apply { put("lng", 127.0); put("lat", 37.5) }
        assertEquals(SkillCache.argsHash(args1), SkillCache.argsHash(args2))
    }

    @Test
    fun `invalidate removes matching entries`() {
        cache.put("get_weather", "hash1", "result1")
        cache.put("get_weather", "hash2", "result2")
        cache.put("get_directions", "hash3", "result3")
        cache.invalidate("get_weather")
        assertNull(cache.get("get_weather", "hash1"))
        assertNull(cache.get("get_weather", "hash2"))
        assertNotNull(cache.get("get_directions", "hash3"))
    }
}
