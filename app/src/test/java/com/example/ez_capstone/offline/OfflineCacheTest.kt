package com.example.ez_capstone.offline

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OfflineCacheTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var cache: OfflineCache

    @Before
    fun setUp() {
        editor = mockk(relaxed = true)
        prefs = mockk {
            every { edit() } returns editor
            every { getString(any(), null) } returns null
        }
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        cache = OfflineCache(prefs)
    }

    @Test
    fun `getCachedWeather returns null when prefs has no entry`() {
        assertNull(cache.getCachedWeather(37.5, 127.0))
    }

    @Test
    fun `putWeather stores JSON and verify editor called`() {
        val result = WeatherCacheEntry(temp = 22.0, desc = "맑음", cachedAt = System.currentTimeMillis())
        val slot = slot<String>()
        every { editor.putString(any(), capture(slot)) } returns editor

        cache.putWeather(37.5, 127.0, result)

        verify { editor.putString(any(), any()) }
        verify { editor.apply() }
    }

    @Test
    fun `getCachedRoute returns null for unknown destination`() {
        assertNull(cache.getCachedRoute("판교"))
    }

    @Test
    fun `putRoute stores entry`() {
        val entry = RouteCacheEntry(destination = "판교", durationMin = 32, cachedAt = System.currentTimeMillis())
        cache.putRoute("판교", entry)
        verify { editor.putString(any(), any()) }
    }

    @Test
    fun `expired weather entry returns null`() {
        val expired = WeatherCacheEntry(
            temp = 15.0,
            desc = "흐림",
            cachedAt = System.currentTimeMillis() - 7 * 60 * 60 * 1000L  // 7시간 전 (TTL 초과)
        )
        val json = com.google.gson.Gson().toJson(expired)
        every { prefs.getString(match { it.contains("weather") }, null) } returns json

        assertNull(cache.getCachedWeather(37.5, 127.0))
    }
}
