package com.example.ez_capstone.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NaturalVoiceEngine 회귀 게이트 — 랜드마크 기반 음성 안내.
 *
 * 사용자 요청: "500m 앞 좌회전" 외에 "500m 앞 ○○(랜드마크) 앞에서 좌회전"처럼
 * 거리 + 랜드마크를 함께 안내해 UX를 높인다. POI가 있으면 거리와 랜드마크가 모두 들어가야 한다.
 */
class NaturalVoiceEngineTest {

    // guideType 1 = 좌회전(LEFT)
    private fun left(poi: String?, dist: Int, style: String) =
        NaturalVoiceEngine.convert(
            guideText = "${dist}m 앞 좌회전",
            guideType = 1,
            roadName = "테헤란로",
            nearbyPoi = poi,
            distanceM = dist,
            ttsStyle = style
        )

    // 실제 안내는 FAR(≤300m)/NEAR(≤100m)/IMMINENT(≤50m)에서 발화 → POI 분기 조건 dist<500을 항상 만족.
    @Test fun `POI가 있으면 거리와 랜드마크가 모두 포함된다 (polite)`() {
        val s = left(poi = "강남역", dist = 300, style = "polite")
        assertTrue("거리 포함: $s", s.contains("300m"))
        assertTrue("랜드마크 포함: $s", s.contains("강남역"))
        assertTrue("회전 방향 포함: $s", s.contains("왼쪽"))
    }

    @Test fun `POI가 있으면 거리와 랜드마크가 모두 포함된다 (casual)`() {
        val s = left(poi = "GS25", dist = 300, style = "casual")
        assertTrue("거리 포함: $s", s.contains("300m"))
        assertTrue("랜드마크 포함: $s", s.contains("GS25"))
        assertTrue("보이면 단서: $s", s.contains("보이면"))
    }

    @Test fun `POI가 없으면 거리 기반 기본 안내 (랜드마크 없음)`() {
        val s = left(poi = null, dist = 500, style = "polite")
        assertTrue("거리 포함: $s", s.contains("500m"))
        assertFalse("랜드마크 없음: $s", s.contains("앞에서 좌측") && s.contains("역"))
    }

    @Test fun `도착은 거리 무관 도착 안내`() {
        val s = NaturalVoiceEngine.convert("도착", guideType = 12, distanceM = 0, ttsStyle = "polite")
        assertTrue("도착 안내: $s", s.contains("도착"))
    }
}
