package com.example.ez_capstone.agent

import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.context.AmbientContextEngine
import com.example.ez_capstone.db.dao.AgentNoteDao
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.db.entity.ProfileEntity
import com.example.ez_capstone.memory.ConversationMemory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * SystemPrompt.build() 출력 내용 검증.
 * 네트워크/DB는 MockK로 대체.
 */
class SystemPromptTest {

    private lateinit var systemPrompt: SystemPrompt
    private val profileDao: ProfileDao = mockk(relaxed = true)
    private val conversationMemory: ConversationMemory = mockk(relaxed = true)
    private val ambientContextEngine: AmbientContextEngine = mockk(relaxed = true)
    private val agentNoteDao: AgentNoteDao = mockk(relaxed = true)

    private val demoContext = AgentContext(locationX = 127.0, locationY = 37.5)

    @Before
    fun setUp() {
        coEvery { ambientContextEngine.toPromptString(any()) } returns "GPS:37.5,127.0"
        coEvery { conversationMemory.getPreferenceSummary() } returns ""
        coEvery { conversationMemory.getRecentContext(any()) } returns ""
        systemPrompt = SystemPrompt(profileDao, conversationMemory, ambientContextEngine, agentNoteDao)
    }

    @Test
    fun `프로필 없을 때 기본 시스템 프롬프트 포함`() = runTest {
        coEvery { profileDao.getProfile() } returns null

        val prompt = systemPrompt.build(demoContext)

        assertTrue("이지(EZ) 멘션 포함", prompt.contains("이지(EZ)"))
        assertTrue("JSON 포맷 명시", prompt.contains("reply_text"))
        assertTrue("안전 규칙 포함", prompt.contains("안전 규칙"))
    }

    @Test
    fun `프로필 있을 때 집 주소 프롬프트에 주입`() = runTest {
        val profile = ProfileEntity(
            homeAddress = "서울 강남구 테헤란로 123",
            homeLat = 37.497, homeLng = 127.027,
            workAddress = "서울 중구 세종대로 110",
            vehicleType = "sedan",
            fuelType = "electric",
            ttsStyle = "casual"
        )
        coEvery { profileDao.getProfile() } returns profile

        val prompt = systemPrompt.build(demoContext)

        assertTrue("집 주소 포함", prompt.contains("서울 강남구 테헤란로 123"))
        assertTrue("연료 타입 포함", prompt.contains("electric"))
        assertTrue("TTS 스타일 포함", prompt.contains("casual"))
    }

    @Test
    fun `sessionId 있으면 최근 대화 섹션 포함`() = runTest {
        coEvery { profileDao.getProfile() } returns null
        coEvery { conversationMemory.getRecentContext("session-abc") } returns "이전: 카페 추천해줘"

        val prompt = systemPrompt.build(demoContext, sessionId = "session-abc")

        assertTrue("최근 대화 섹션 포함", prompt.contains("최근 대화"))
        assertTrue("이전 대화 내용 포함", prompt.contains("카페 추천해줘"))
    }

    @Test
    fun `선호도 요약 있으면 컨텍스트에 포함`() = runTest {
        coEvery { profileDao.getProfile() } returns null
        coEvery { conversationMemory.getPreferenceSummary() } returns "고속도로 선호"

        val prompt = systemPrompt.build(demoContext)

        assertTrue("선호도 정보 포함", prompt.contains("고속도로 선호"))
    }

    @Test
    fun `80000자 초과 시 잘라냄`() = runTest {
        // 매우 긴 선호도 요약으로 프롬프트를 강제로 80K 초과시킴
        val longPrefs = "A".repeat(85_000)
        coEvery { conversationMemory.getPreferenceSummary() } returns longPrefs
        coEvery { profileDao.getProfile() } returns null

        val prompt = systemPrompt.build(demoContext)

        assertTrue("80K 이하로 잘림", prompt.length <= 80_000)
    }
}
