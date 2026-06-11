package com.example.ez_capstone.agent

/**
 * 사용자 발화에서 감지된 의도 카테고리.
 * GeminiAgentEngine이 이 값에 따라 Gemini에게 전달할 Tool 집합과
 * 시스템 프롬프트 섹션을 동적으로 선택한다.
 *
 * AUTO: 감지 불가 또는 복합 의도 → 전체 Tool 포함 (현재 동작 유지)
 */
enum class IntentCategory {
    NAVIGATION,     // 경로·내비·대중교통·ETA
    PLACES,         // 장소검색·약국·병원·주유소·충전소·주차장
    SCHEDULE,       // 일정 CRUD + 알람
    COMMUNICATION,  // 문자·전화·알람
    PROFILE,        // 프로필·선호도·이동패턴·즐겨찾기
    INFO,           // 날씨·미세먼지·환율·지식
    TRAFFIC,        // 실시간 교통·사고·고속도로·카메라
    MUSIC,          // 음악 재생·정지·다음곡 (Spotify)
    AUTO            // 복합/불명확 → 전체 Tool
}
