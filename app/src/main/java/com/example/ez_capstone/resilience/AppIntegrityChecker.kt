package com.example.ez_capstone.resilience

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Play Integrity API — 앱 무결성 검증.
 *
 * EZmap은 서버리스이므로 토큰을 백엔드로 전송하는 대신
 * 로컬에서 요청 성공 여부만 확인한다.
 * 실제 상용 앱이라면 토큰을 서버로 전송하여 MEETS_DEVICE_INTEGRITY 등을 검증해야 함.
 *
 * 사용처:
 * - 앱 시작 시 1회 호출 (EZApplication 또는 SplashScreen)
 * - API 키 설정 화면에서 재확인
 *
 * NOTE: Play Integrity는 Play Store 배포 앱에서만 동작.
 *       debug 빌드 / sideload 시 INTEGRITY_ERROR가 발생하며 이는 정상.
 */
@Singleton
class AppIntegrityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AppIntegrity"
        private const val PREFS_NAME = "ezmap_integrity"
        private const val KEY_LAST_CHECK = "last_check_ts"
        private const val KEY_LAST_RESULT = "last_result"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24시간마다 재확인
    }

    /**
     * 무결성 검사 결과.
     * - PASSED: Play Integrity 토큰 발급 성공
     * - FAILED: 토큰 발급 실패 (에뮬레이터/루팅/비공식 배포)
     * - SKIPPED: 최근 24시간 내 이미 검사함
     * - ERROR: 네트워크/서비스 오류
     */
    enum class IntegrityResult { PASSED, FAILED, SKIPPED, ERROR }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 무결성 토큰 요청.
     * nonce는 현재 시간 기반 — 실제 서버 연동 시 서버에서 발급받아야 함.
     */
    suspend fun check(cloudProjectNumber: Long = 0L): IntegrityResult {
        // 최근 24시간 내 통과한 경우 스킵
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val lastResult = prefs.getString(KEY_LAST_RESULT, "")
        if (lastResult == "PASSED" &&
            System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
            return IntegrityResult.SKIPPED
        }

        return try {
            val manager = IntegrityManagerFactory.create(context)
            val nonce = "EZmap-${System.currentTimeMillis()}"
            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .apply { if (cloudProjectNumber > 0) setCloudProjectNumber(cloudProjectNumber) }
                .build()

            suspendCancellableCoroutine { cont ->
                manager.requestIntegrityToken(request)
                    .addOnSuccessListener {
                        prefs.edit()
                            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                            .putString(KEY_LAST_RESULT, "PASSED")
                            .apply()
                        Log.i(TAG, "Integrity check passed")
                        cont.resume(IntegrityResult.PASSED)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Integrity check failed: ${e.message}")
                        cont.resume(IntegrityResult.ERROR)
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Integrity check error: ${e.message}")
            // debug/sideload 환경에서는 예외 발생이 정상 — 앱 기능을 차단하지 않음
            IntegrityResult.ERROR
        }
    }

    /** 캐시된 마지막 결과 조회 */
    fun getCachedResult(): String = prefs.getString(KEY_LAST_RESULT, "UNKNOWN") ?: "UNKNOWN"
}
