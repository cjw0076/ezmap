package com.example.ez_capstone.skill

import android.content.Context
import android.content.SharedPreferences
import com.example.ez_capstone.config.ApiKeyProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SkillLifecycleManager 단위 테스트.
 *
 * 연속 실패 임계값(AUTO_DISABLE_THRESHOLD=5) 및 상태 전환 검증.
 * Android Context/SharedPreferences를 mockk으로 대체하여 JVM에서 실행.
 */
class SkillLifecycleManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val apiKeyProvider: ApiKeyProvider = mockk(relaxed = true)
    private val sharedPrefsEditor: SharedPreferences.Editor = mockk(relaxed = true)
    private val sharedPrefs: SharedPreferences = mockk(relaxed = true)

    // 인메모리 저장소 — SharedPreferences 시뮬레이션
    private val store = mutableMapOf<String, String?>()

    private lateinit var manager: SkillLifecycleManager

    @Before
    fun setUp() {
        // SharedPreferences.getString — store에서 읽기
        every { sharedPrefs.getString(any(), null) } answers {
            store[firstArg<String>()]
        }

        // SharedPreferences.Editor — store에 쓰기
        val keySlot = slot<String>()
        val valueSlot = slot<String>()
        every { sharedPrefsEditor.putString(capture(keySlot), capture(valueSlot)) } answers {
            store[keySlot.captured] = valueSlot.captured
            sharedPrefsEditor
        }
        every { sharedPrefsEditor.remove(any()) } answers {
            store.remove(firstArg<String>())
            sharedPrefsEditor
        }
        every { sharedPrefsEditor.apply() } returns Unit
        every { sharedPrefs.edit() } returns sharedPrefsEditor

        // Context.getSharedPreferences
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs

        manager = SkillLifecycleManager(context, apiKeyProvider)
    }

    // ── Test 1: 연속 5회 실패 → AUTO_DISABLED 반환 ──

    @Test
    fun `연속 5회 실패하면 AUTO_DISABLED 상태가 된다`() {
        val skillName = "get_weather"

        // 4회 실패 — 아직 AUTO_DISABLED 아님
        repeat(4) { i ->
            val autoDisabled = manager.recordExecution(skillName, success = false, latencyMs = 100)
            assertFalse("${i + 1}번째 실패는 아직 AUTO_DISABLED 아님", autoDisabled)
        }

        // 5번째 실패 → AUTO_DISABLED
        val autoDisabled = manager.recordExecution(skillName, success = false, latencyMs = 100)
        assertTrue("5회 연속 실패 후 AUTO_DISABLED 반환", autoDisabled)

        val health = manager.getHealth(skillName)
        assertEquals(SkillStatus.AUTO_DISABLED, health.status)
        assertEquals(5, health.consecutiveFailures)
    }

    // ── Test 2: 성공 후 consecutiveFailures 리셋 ──

    @Test
    fun `실패 후 성공하면 consecutiveFailures가 0으로 리셋된다`() {
        val skillName = "search_places"

        // 3회 실패 (FAILING 상태)
        repeat(3) {
            manager.recordExecution(skillName, success = false, latencyMs = 50)
        }

        var health = manager.getHealth(skillName)
        assertEquals(SkillStatus.FAILING, health.status)
        assertEquals(3, health.consecutiveFailures)

        // 1회 성공 → 리셋
        val autoDisabled = manager.recordExecution(skillName, success = true, latencyMs = 200)
        assertFalse("성공 후 AUTO_DISABLED 아님", autoDisabled)

        health = manager.getHealth(skillName)
        assertEquals(SkillStatus.ACTIVE, health.status)
        assertEquals(0, health.consecutiveFailures)
    }

    // ── Test 3: 신규 스킬 조회 → 기본값 ACTIVE ──

    @Test
    fun `기록 없는 스킬의 기본 상태는 ACTIVE`() {
        val health = manager.getHealth("nonexistent_skill")

        assertEquals(SkillStatus.ACTIVE, health.status)
        assertEquals(0, health.totalCalls)
        assertEquals(1f, health.successRate)
        assertEquals(0, health.consecutiveFailures)
    }

    // ── Test 4: totalCalls 및 successRate 계산 ──

    @Test
    fun `3번 호출(2성공 1실패) 후 successRate가 2분의1 이상이다`() {
        val skillName = "get_directions"

        manager.recordExecution(skillName, success = true, latencyMs = 300)
        manager.recordExecution(skillName, success = true, latencyMs = 400)
        manager.recordExecution(skillName, success = false, latencyMs = 100)

        val health = manager.getHealth(skillName)
        assertEquals(3, health.totalCalls)
        // successRate = 2/3 ≈ 0.666...
        assertTrue("successRate > 0.5 기대", health.successRate > 0.5f)
        assertTrue("successRate < 1.0 기대", health.successRate < 1.0f)
    }

    // ── Test 5: checkKeyHealth — 5회 이상 호출에 성공률 0%이면 탐지 ──

    @Test
    fun `5회 이상 모두 실패한 스킬은 checkKeyHealth에서 탐지된다`() {
        val skillName = "get_weather"

        // AUTO_DISABLE_THRESHOLD(5) 회 실패
        repeat(5) {
            manager.recordExecution(skillName, success = false, latencyMs = 50)
        }

        val problems = manager.checkKeyHealth()

        // 5회 이상 호출 + 성공률 0% → 목록에 포함
        val found = problems.any { it.skillName == skillName }
        assertTrue("checkKeyHealth가 '$skillName'을 탐지해야 함", found)
    }

    // ── Test 6: resetHealth 후 상태 초기화 ──

    @Test
    fun `resetHealth 후 상태가 기본값으로 초기화된다`() {
        val skillName = "get_gas_stations"

        // 5회 실패 → AUTO_DISABLED
        repeat(5) {
            manager.recordExecution(skillName, success = false, latencyMs = 100)
        }
        assertEquals(SkillStatus.AUTO_DISABLED, manager.getHealth(skillName).status)

        // 리셋
        manager.resetHealth(skillName)

        val health = manager.getHealth(skillName)
        assertEquals("리셋 후 기본 상태 ACTIVE", SkillStatus.ACTIVE, health.status)
        assertEquals(0, health.totalCalls)
    }
}
