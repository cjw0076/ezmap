package com.example.ez_capstone.voice

/**
 * Live API 활성화 여부를 결정하는 정책.
 *
 * 현재 실험 모드(useLiveApi 기본값 false). 프로덕션 전환 전 해결 필요 사항:
 *  1. 에페메럴 토큰 릴레이 — 현재 x-goog-api-key 헤더 직접 전송 (캡스톤 한정 허용)
 *  2. Live API 공식 GA 여부 확인 (2025년 5월 기준 Preview 상태)
 *  3. tool declarations 스키마 최종 확정 (functionDeclarations 키 케이스)
 *  4. 실기기 PCM 스트리밍 레이턴시 측정 (목표 ≤300ms)
 *  5. 한국 리전 지원 여부 확인 (us-central1 fallback 포함)
 */
object VoicePipelinePolicy {
    fun shouldUseLiveApi(
        enabled: Boolean,
        networkAvailable: Boolean,
        apiKey: String?,
        liveSessionAvailable: Boolean
    ): Boolean = enabled && networkAvailable && apiKey?.isNotBlank() == true && liveSessionAvailable
}
