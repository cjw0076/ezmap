package com.example.ez_capstone.monitoring

import android.content.Context
import com.example.ez_capstone.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

/**
 * 옵트인 + 프라이버시 스크러빙을 강제하는 Sentry 게이트.
 *
 * EZmap의 온디바이스 원칙(CLAUDE.md 작업 원칙 #4)과 정면으로 충돌하는 기능이므로,
 * 다음 두 조건이 모두 만족될 때만 초기화된다:
 *   1. 사용자가 설정에서 명시적으로 동의 (기본값 false)
 *   2. SENTRY_DSN 빌드 설정이 비어있지 않음
 *
 * 위치 좌표 / 집·회사 주소 / API 키는 전송 전 [scrub]에서 제거한다.
 * Session Replay·스크린샷·뷰 계층·PII 전송은 모두 비활성이다.
 */
object SentryInitializer {

    private const val PREFS_NAME = "ezmap_settings"
    private const val KEY_CONSENT = "crash_reporting_enabled"

    // 위도/경도처럼 소수점 4자리 이상인 십진수 (버전 "1.0", 샘플레이트 "0.05"는 건드리지 않음)
    private val COORD = Regex("""-?\d{1,3}\.\d{4,}""")

    // URL 쿼리스트링 — 카카오/네이버 API가 lat,lng 를 여기에 실어 보낸다
    private val URL_QUERY = Regex("""(https?://[^\s?]+)\?\S*""")

    // key / token / secret / dsn / authorization = <값>
    private val SECRET = Regex("""(?i)\b(key|token|secret|dsn|password|auth(?:orization)?)\b\s*[=:]\s*\S+""")

    fun isConsentGranted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONSENT, false)

    /** 동의 상태를 저장하고 Sentry를 즉시 켜거나(close) 끈다. */
    fun setConsent(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONSENT, enabled).apply()
        if (enabled) init(context) else disable()
    }

    /** 동의 + DSN 이 모두 있을 때만 초기화. 그 외에는 조용히 no-op. */
    fun init(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) return
        if (!isConsentGranted(context)) return
        if (Sentry.isEnabled()) return

        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"

            // ── 프라이버시: 화면/PII/위치를 절대 전송하지 않음 ──
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0

            // 크래시/ANR/NDK는 수집 (Sentry의 본래 목적)
            options.isEnableNdk = true
            options.isAnrEnabled = true

            // 트레이싱은 저비율. 3rd-party API 요청에 trace 헤더를 주입하지 않음.
            options.tracesSampleRate = if (BuildConfig.DEBUG) 0.2 else 0.05
            options.setTracePropagationTargets(emptyList())

            options.isDebug = BuildConfig.DEBUG

            // 모든 이벤트/브레드크럼에서 좌표·주소·키 제거
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                scrubEvent(event)
                event
            }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { crumb, _ ->
                scrubBreadcrumb(crumb)
            }
        }
    }

    fun disable() {
        if (Sentry.isEnabled()) Sentry.close()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun scrubEvent(event: SentryEvent) {
        event.message?.let { m ->
            m.message = scrub(m.message)
            m.formatted = scrub(m.formatted)
        }
        event.exceptions?.forEach { ex ->
            ex.value = scrub(ex.value)
        }
    }

    private fun scrubBreadcrumb(crumb: Breadcrumb): Breadcrumb {
        crumb.message = scrub(crumb.message)
        (crumb.data["url"] as? String)?.let { crumb.setData("url", scrub(it) ?: "") }
        return crumb
    }

    /** 좌표·URL 쿼리·시크릿을 마스킹한다. 정규식 치환이라 입력이 무엇이든 예외를 던지지 않는다. */
    private fun scrub(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        var t = text
        t = URL_QUERY.replace(t) { "${it.groupValues[1]}?<redacted>" }
        t = COORD.replace(t, "<coord>")
        t = SECRET.replace(t) { "${it.groupValues[1]}=<redacted>" }
        return t
    }
}
