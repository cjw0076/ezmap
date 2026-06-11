package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentResponse
import org.junit.Assert.*
import org.junit.Test

/**
 * AgentResponse.voiceSummary 테스트.
 * 결정론적: 첫 문장 추출은 구두점 기반으로 항상 동일.
 */
class AgentModelsTest {

    @Test
    fun `voiceSummary - 마침표로 끝나는 첫 문장 추출`() {
        // 예상: "자차로 30분이에요."
        val r = AgentResponse(
            replyText = "자차로 30분이에요. 대중교통은 45분이고 환승 1회예요."
        )
        assertEquals("자차로 30분이에요.", r.voiceSummary)
    }

    @Test
    fun `voiceSummary - 느낌표로 끝나는 첫 문장`() {
        // 예상: "목적지에 도착했습니다!"
        val r = AgentResponse(
            replyText = "목적지에 도착했습니다! 수고하셨어요."
        )
        assertEquals("목적지에 도착했습니다!", r.voiceSummary)
    }

    @Test
    fun `voiceSummary - 문장 하나만 있으면 전체 반환`() {
        // 예상: "네이버로 안내할게요"
        val r = AgentResponse(replyText = "네이버로 안내할게요")
        assertEquals("네이버로 안내할게요", r.voiceSummary)
    }

    @Test
    fun `voiceSummary - ttsText가 있으면 ttsText에서 추출`() {
        // 예상: ttsText 첫 문장 "비 와요."
        val r = AgentResponse(
            replyText = "상세한 날씨 정보입니다.",
            ttsText = "비 와요. 우산 챙기세요."
        )
        assertEquals("비 와요.", r.voiceSummary)
    }

    @Test
    fun `voiceSummary - 빈 텍스트`() {
        val r = AgentResponse(replyText = "")
        assertEquals("", r.voiceSummary)
    }
}
