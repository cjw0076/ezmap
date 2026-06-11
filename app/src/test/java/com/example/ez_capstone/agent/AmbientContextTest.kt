package com.example.ez_capstone.agent

import com.example.ez_capstone.context.AmbientContextEngine
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * AmbientContextEngine.classifyTime() 테스트.
 * 결정론적: 시간 → 분류 매핑은 항상 동일.
 */
class AmbientContextTest {

    private val engine = AmbientContextEngine(
        scheduleDao = mockk(relaxed = true),
        routeHistoryDao = mockk(relaxed = true)
    )

    @Test
    fun `0~5시 → 새벽`() {
        assertEquals("새벽", engine.classifyTime(0))
        assertEquals("새벽", engine.classifyTime(3))
        assertEquals("새벽", engine.classifyTime(5))
    }

    @Test
    fun `6~9시 → 아침출근`() {
        assertEquals("아침출근", engine.classifyTime(6))
        assertEquals("아침출근", engine.classifyTime(8))
        assertEquals("아침출근", engine.classifyTime(9))
    }

    @Test
    fun `10~11시 → 오전`() {
        assertEquals("오전", engine.classifyTime(10))
        assertEquals("오전", engine.classifyTime(11))
    }

    @Test
    fun `12~13시 → 점심`() {
        assertEquals("점심", engine.classifyTime(12))
        assertEquals("점심", engine.classifyTime(13))
    }

    @Test
    fun `14~16시 → 오후`() {
        assertEquals("오후", engine.classifyTime(14))
        assertEquals("오후", engine.classifyTime(16))
    }

    @Test
    fun `17~19시 → 저녁퇴근`() {
        assertEquals("저녁퇴근", engine.classifyTime(17))
        assertEquals("저녁퇴근", engine.classifyTime(19))
    }

    @Test
    fun `20~23시 → 야간`() {
        assertEquals("야간", engine.classifyTime(20))
        assertEquals("야간", engine.classifyTime(23))
    }

    @Test
    fun `경계값 6시 정확히 아침출근`() {
        assertEquals("아침출근", engine.classifyTime(6))
    }

    @Test
    fun `경계값 20시 정각 야간`() {
        assertEquals("야간", engine.classifyTime(20))
    }
}
