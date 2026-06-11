package com.example.ez_capstone.config

import org.junit.Assert.*
import org.junit.Test

/**
 * ApiKeyProvider 상수 및 isTrialLimitReached 순수 로직 테스트.
 *
 * ApiKeyProvider 자체는 EncryptedSharedPreferences (Android 프레임워크) 의존성이 있어
 * JVM 단위 테스트에서 직접 인스턴스화 불가. 여기서는 companion object 상수와
 * isTrialLimitReached 임계값 로직을 순수 함수 형태로 검증한다.
 */
class ApiKeyProviderTest {

    // isTrialLimitReached() 로직을 동일하게 복제한 순수 함수
    private fun isTrialLimitReached(count: Int, limit: Int = ApiKeyProvider.TRIAL_DAILY_LIMIT): Boolean =
        count >= limit

    // isReady 로직 (isConfigured || (isTrialMode && !isTrialLimitReached()))
    private fun isReady(isConfigured: Boolean, isTrialMode: Boolean, trialCount: Int): Boolean =
        isConfigured || (isTrialMode && !isTrialLimitReached(trialCount))

    @Test
    fun `TRIAL_DAILY_LIMIT 는 30`() {
        assertEquals(30, ApiKeyProvider.TRIAL_DAILY_LIMIT)
    }

    @Test
    fun `29회 사용 시 한도 미도달`() {
        assertFalse(isTrialLimitReached(29))
    }

    @Test
    fun `30회 사용 시 한도 도달`() {
        assertTrue(isTrialLimitReached(30))
    }

    @Test
    fun `31회 사용 시도 한도 도달`() {
        assertTrue(isTrialLimitReached(31))
    }

    @Test
    fun `0회는 한도 미도달`() {
        assertFalse(isTrialLimitReached(0))
    }

    @Test
    fun `isReady — 실제 키 있으면 trial 여부 무관하게 true`() {
        assertTrue("키 있음, 트라이얼 아님", isReady(isConfigured = true, isTrialMode = false, trialCount = 0))
        assertTrue("키 있음, 트라이얼 한도 초과", isReady(isConfigured = true, isTrialMode = true, trialCount = 30))
    }

    @Test
    fun `isReady — 키 없고 트라이얼 모드, 한도 미초과 시 true`() {
        assertTrue(isReady(isConfigured = false, isTrialMode = true, trialCount = 10))
    }

    @Test
    fun `isReady — 키 없고 트라이얼 한도 초과 시 false`() {
        assertFalse(isReady(isConfigured = false, isTrialMode = true, trialCount = 30))
    }

    @Test
    fun `isReady — 키 없고 트라이얼 모드도 아니면 false`() {
        assertFalse(isReady(isConfigured = false, isTrialMode = false, trialCount = 0))
    }
}
