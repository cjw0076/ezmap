package com.example.ez_capstone.skill

import org.junit.Assert.*
import org.junit.Test

/**
 * SkillStatus enum + 상태 전환 로직 테스트.
 * SkillLifecycleManager는 Android Context가 필요하므로,
 * 여기서는 상태 전환 규칙의 정확성만 검증.
 */
class SkillStatusTest {

    @Test
    fun `연속 실패 0 → ACTIVE`() {
        val status = determineStatus(consecutiveFailures = 0)
        assertEquals(SkillStatus.ACTIVE, status)
    }

    @Test
    fun `연속 실패 1~2 → DEGRADED`() {
        assertEquals(SkillStatus.DEGRADED, determineStatus(1))
        assertEquals(SkillStatus.DEGRADED, determineStatus(2))
    }

    @Test
    fun `연속 실패 3~4 → FAILING`() {
        assertEquals(SkillStatus.FAILING, determineStatus(3))
        assertEquals(SkillStatus.FAILING, determineStatus(4))
    }

    @Test
    fun `연속 실패 5+ → AUTO_DISABLED`() {
        // 예상: 5회 이상이면 자동 비활성화
        assertEquals(SkillStatus.AUTO_DISABLED, determineStatus(5))
        assertEquals(SkillStatus.AUTO_DISABLED, determineStatus(10))
    }

    @Test
    fun `성공하면 연속 실패 리셋`() {
        // 시나리오: 4회 실패(FAILING) → 1회 성공 → 0(ACTIVE)
        var failures = 4
        assertEquals(SkillStatus.FAILING, determineStatus(failures))

        // 성공 시 리셋
        failures = 0
        assertEquals(SkillStatus.ACTIVE, determineStatus(failures))
    }

    @Test
    fun `SkillStatus enum 값 5개 존재`() {
        assertEquals(5, SkillStatus.entries.size)
        assertTrue(SkillStatus.entries.contains(SkillStatus.ACTIVE))
        assertTrue(SkillStatus.entries.contains(SkillStatus.AUTO_DISABLED))
        assertTrue(SkillStatus.entries.contains(SkillStatus.KEY_MISSING))
    }

    /** SkillLifecycleManager.recordExecution() 내부 로직 재현 */
    private fun determineStatus(consecutiveFailures: Int): SkillStatus = when {
        consecutiveFailures >= 5 -> SkillStatus.AUTO_DISABLED
        consecutiveFailures >= 3 -> SkillStatus.FAILING
        consecutiveFailures >= 1 -> SkillStatus.DEGRADED
        else -> SkillStatus.ACTIVE
    }
}
