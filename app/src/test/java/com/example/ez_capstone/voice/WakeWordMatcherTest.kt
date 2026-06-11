package com.example.ez_capstone.voice

import org.junit.Assert.*
import org.junit.Test

class WakeWordMatcherTest {

    @Test
    fun `containsWakeWord detects standard wake word`() {
        assertTrue(WakeWordMatcher.containsWakeWord("이지야"))
        assertTrue(WakeWordMatcher.containsWakeWord("이지야 울산역 가자"))
    }

    @Test
    fun `containsWakeWord detects pronunciation variants`() {
        assertTrue(WakeWordMatcher.containsWakeWord("이지아"))
        assertTrue(WakeWordMatcher.containsWakeWord("이지얌"))
        assertTrue(WakeWordMatcher.containsWakeWord("이지여"))
    }

    @Test
    fun `containsWakeWord returns false for non-wake word`() {
        assertFalse(WakeWordMatcher.containsWakeWord("울산역 가자"))
        assertFalse(WakeWordMatcher.containsWakeWord("안녕하세요"))
        assertFalse(WakeWordMatcher.containsWakeWord(""))
    }

    @Test
    fun `extractCommand returns text after wake word`() {
        assertEquals("울산역 가자", WakeWordMatcher.extractCommand("이지야 울산역 가자"))
        assertEquals("날씨 알려줘", WakeWordMatcher.extractCommand("이지아 날씨 알려줘"))
    }

    @Test
    fun `extractCommand returns null when only wake word`() {
        assertNull(WakeWordMatcher.extractCommand("이지야"))
        assertNull(WakeWordMatcher.extractCommand("이지야 "))
    }

    @Test
    fun `extractCommand returns null when no wake word`() {
        assertNull(WakeWordMatcher.extractCommand("울산역 가자"))
    }

    @Test
    fun `extractCommand preserves original spacing`() {
        assertEquals("서울역까지 얼마나 걸려", WakeWordMatcher.extractCommand("이지야 서울역까지 얼마나 걸려"))
    }
}
