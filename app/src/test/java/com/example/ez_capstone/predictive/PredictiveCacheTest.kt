package com.example.ez_capstone.predictive

import android.content.SharedPreferences
import com.example.ez_capstone.agent.models.AgentResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class PredictiveCacheTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var cache: PredictiveCache

    @Before
    fun setUp() {
        editor = mockk(relaxed = true)
        prefs = mockk {
            every { edit() } returns editor
            every { getString(any(), null) } returns null
        }
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        cache = PredictiveCache(prefs)
    }

    @Test
    fun `getCachedPrediction returns null when nothing stored`() {
        val snap = ContextSnapshot(8, Calendar.MONDAY, false, false, 37.5, 127.0)
        assertNull(cache.getCachedPrediction(snap))
    }

    @Test
    fun `putPrediction does not throw`() {
        val snap = ContextSnapshot(8, Calendar.MONDAY, false, false, 37.5, 127.0)
        val response = AgentResponse("집 경로 30분", "집 경로 30분", "none", emptyMap())
        cache.putPrediction(snap, response)
    }

    @Test
    fun `weekday morning key is predict_weekday_morning`() {
        val snap = ContextSnapshot(8, Calendar.TUESDAY, false, true, 37.5, 127.0)
        assertEquals("predict_weekday_morning", cache.keyFor(snap))
    }

    @Test
    fun `weekday evening key is predict_weekday_evening`() {
        val snap = ContextSnapshot(18, Calendar.WEDNESDAY, false, true, 37.5, 127.0)
        assertEquals("predict_weekday_evening", cache.keyFor(snap))
    }

    @Test
    fun `weekend morning key is predict_weekend_morning`() {
        val snap = ContextSnapshot(10, Calendar.SATURDAY, true, true, 37.5, 127.0)
        assertEquals("predict_weekend_morning", cache.keyFor(snap))
    }
}
