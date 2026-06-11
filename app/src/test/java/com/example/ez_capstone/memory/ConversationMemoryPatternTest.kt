package com.example.ez_capstone.memory

import org.junit.Assert.*
import org.junit.Test

/**
 * ConversationMemory 패턴 매칭 테스트.
 * 선호도 추출 패턴(FOOD_PATTERNS, TRANSPORT_PATTERNS)은 결정론적.
 */
class ConversationMemoryPatternTest {

    // ═══════════════════════════════════════════
    // 음식 선호 패턴
    // ═══════════════════════════════════════════

    @Test
    fun `매운 거 빼줘 → spicy_avoid 감지`() {
        val text = "매운 거 빼고 추천해줘"
        val matched = ConversationMemory.FOOD_PATTERNS.filter { (pattern, _, _) ->
            text.lowercase().contains(pattern)
        }
        assertTrue("매운 패턴 매칭", matched.isNotEmpty())
        assertEquals("spicy_avoid", matched.first().second)
        assertEquals(0f, matched.first().third)
    }

    @Test
    fun `채식 추천 → vegetarian 감지`() {
        val text = "채식 식당 있어?"
        val matched = ConversationMemory.FOOD_PATTERNS.filter { (pattern, _, _) ->
            text.lowercase().contains(pattern)
        }
        assertTrue(matched.isNotEmpty())
        assertEquals("vegetarian", matched.first().second)
        assertEquals(1f, matched.first().third)
    }

    @Test
    fun `패턴 없는 텍스트 → 매칭 없음`() {
        val text = "오늘 날씨 어때?"
        val matched = ConversationMemory.FOOD_PATTERNS.filter { (pattern, _, _) ->
            text.lowercase().contains(pattern)
        }
        assertTrue("음식 패턴 없음", matched.isEmpty())
    }

    // ═══════════════════════════════════════════
    // 이동수단 선호 패턴
    // ═══════════════════════════════════════════

    @Test
    fun `대중교통으로 가줘 → public_transit_prefer 감지`() {
        val text = "대중교통으로 가줘"
        val matched = ConversationMemory.TRANSPORT_PATTERNS.filter { (pattern, _, _) ->
            text.lowercase().contains(pattern)
        }
        assertTrue(matched.isNotEmpty())
        assertEquals("public_transit_prefer", matched.first().second)
    }

    @Test
    fun `택시 불러줘 → taxi_prefer 감지`() {
        val text = "택시 불러줘"
        val matched = ConversationMemory.TRANSPORT_PATTERNS.filter { (pattern, _, _) ->
            text.lowercase().contains(pattern)
        }
        assertTrue(matched.isNotEmpty())
        assertEquals("taxi_prefer", matched.first().second)
    }

    @Test
    fun `걸어서 갈게 → walk_prefer 감지`() {
        val text = "걸어서 갈게"
        val matched = ConversationMemory.TRANSPORT_PATTERNS.filter { (pattern, _, _) ->
            text.lowercase().contains(pattern)
        }
        assertTrue(matched.isNotEmpty())
        assertEquals("walk_prefer", matched.first().second)
    }

    @Test
    fun `자차로 갈게 → car_prefer 감지`() {
        val text = "자차로 갈게"
        val matched = ConversationMemory.TRANSPORT_PATTERNS.filter { (pattern, _, _) ->
            text.lowercase().contains(pattern)
        }
        assertTrue(matched.isNotEmpty())
        assertEquals("car_prefer", matched.first().second)
    }

    // ═══════════════════════════════════════════
    // 패턴 커버리지
    // ═══════════════════════════════════════════

    @Test
    fun `음식 패턴 5개 존재`() {
        assertEquals(5, ConversationMemory.FOOD_PATTERNS.size)
    }

    @Test
    fun `이동수단 패턴 5개 존재`() {
        assertEquals(5, ConversationMemory.TRANSPORT_PATTERNS.size)
    }

    @Test
    fun `모든 값은 0f 또는 1f`() {
        // 예상: avoid=0f, prefer=1f
        val allPatterns = ConversationMemory.FOOD_PATTERNS + ConversationMemory.TRANSPORT_PATTERNS
        for ((_, _, value) in allPatterns) {
            assertTrue("값은 0f 또는 1f: $value", value == 0f || value == 1f)
        }
    }
}
