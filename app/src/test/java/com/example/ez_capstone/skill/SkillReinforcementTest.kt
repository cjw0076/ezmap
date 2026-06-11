package com.example.ez_capstone.skill

import org.junit.Assert.*
import org.junit.Test

/**
 * 자기학습 신뢰도 강화(reinforcement) 잠금 테스트 — 순수 JVM, Android 의존 0.
 *
 * 핵심 갭 수정 검증: 정적 confidence(0.65 고정) → 사용 결과로 적응 갱신되어
 * 검증된 스킬만 자동 활성화 임계(SAFE 0.75)를 넘는다.
 */
class SkillReinforcementTest {

    private val AUTO_ACTIVATE_THRESHOLD = 0.75f  // SafetyPolicyEngine SAFE 계층 임계

    @Test fun `검증 0회는 보수적 출발점(임계 미달)`() {
        val c = SkillExecutor.reinforcedConfidence(successCount = 0, usageCount = 0)
        assertTrue("0회는 자동 활성화 임계 미만이어야 함: $c", c < AUTO_ACTIVATE_THRESHOLD)
    }

    @Test fun `5회 연속 성공이면 자동 활성화 임계를 넘는다`() {
        val c = SkillExecutor.reinforcedConfidence(successCount = 5, usageCount = 5)
        assertTrue("5회 100% 성공 → 임계 초과해야 자동 활성화: $c", c >= AUTO_ACTIVATE_THRESHOLD)
    }

    @Test fun `성공률이 떨어지면 신뢰도도 떨어진다`() {
        val high = SkillExecutor.reinforcedConfidence(successCount = 5, usageCount = 5)
        val low = SkillExecutor.reinforcedConfidence(successCount = 2, usageCount = 5)
        assertTrue("성공률↓ → confidence↓ ($low < $high)", low < high)
    }

    @Test fun `실패가 누적되면 임계 아래로 떨어져 자동 활성화 철회`() {
        // 10회 중 4회만 성공(40%)
        val c = SkillExecutor.reinforcedConfidence(successCount = 4, usageCount = 10)
        assertTrue("저성공률은 임계 미만이어야 함: $c", c < AUTO_ACTIVATE_THRESHOLD)
    }

    @Test fun `사용량이 적으면 100퍼센트라도 천천히 오른다(2회)`() {
        val twoUses = SkillExecutor.reinforcedConfidence(successCount = 2, usageCount = 2)
        val fiveUses = SkillExecutor.reinforcedConfidence(successCount = 5, usageCount = 5)
        assertTrue("사용량 가중: 2회 < 5회 ($twoUses < $fiveUses)", twoUses < fiveUses)
    }

    @Test fun `신뢰도는 상한 0_95를 넘지 않는다`() {
        val c = SkillExecutor.reinforcedConfidence(successCount = 100, usageCount = 100)
        assertTrue("상한 0.95 이하: $c", c <= 0.95f)
    }
}
