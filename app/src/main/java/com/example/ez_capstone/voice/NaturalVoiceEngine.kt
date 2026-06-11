package com.example.ez_capstone.voice

/**
 * AI 자연어 음성 안내 엔진.
 * "300m 앞 우회전" → "맥도날드 보이면 우회전하세요"
 *
 * Rule-based 변환 (Gemini 호출 X — 속도 최우선).
 * ttsStyle에 따라 casual/polite/ez 분기.
 */
object NaturalVoiceEngine {

    enum class Direction(
        val casual: String,
        val polite: String,
        val ez: String
    ) {
        // 초보자 친화 표현(오른쪽/왼쪽/앞으로) — 주행안정성 목적.
        RIGHT("우회전", "오른쪽으로 돌아주세요", "오른쪽 방향, 우회전"),
        LEFT("좌회전", "왼쪽으로 돌아주세요", "왼쪽 방향, 좌회전"),
        STRAIGHT("직진", "그대로 직진해주세요", "앞으로 직진"),
        UTURN("유턴", "유턴해주세요", "유턴 기동"),
        MERGE("합류", "합류해주세요", "본선 합류"),
        EXIT("출구", "빠져나가주세요", "출구로 이동"),
        ARRIVE("도착", "목적지에 도착했습니다", "목적지 도착, 안내 완료");

        companion object {
            fun fromGuideType(type: Int): Direction = when (type) {
                0 -> STRAIGHT
                1 -> LEFT
                2 -> RIGHT
                3 -> UTURN
                5, 6 -> MERGE
                11 -> MERGE
                12 -> ARRIVE
                16 -> LEFT  // 좌측 방향
                17 -> RIGHT // 우측 방향
                else -> STRAIGHT
            }
        }
    }

    /**
     * 가이드 텍스트를 자연어로 변환.
     *
     * @param guideText 원본 가이드 ("300m 앞 우회전")
     * @param guideType 카카오 guide.type (0=직진, 1=좌회전, 2=우회전...)
     * @param roadName 도로명 (nullable)
     * @param nearbyPoi 근처 POI (nullable, 500m 이내)
     * @param distanceM 다음 가이드까지 거리(m)
     * @param ttsStyle 스타일 ("casual", "polite", "ez")
     */
    fun convert(
        guideText: String,
        guideType: Int,
        roadName: String? = null,
        nearbyPoi: String? = null,
        distanceM: Int = 0,
        ttsStyle: String = "casual"
    ): String {
        val direction = Direction.fromGuideType(guideType)

        // 도착
        if (direction == Direction.ARRIVE) {
            return when (ttsStyle) {
                "ez" -> "목적지 도착. 수고하셨습니다."
                "polite" -> "목적지에 도착했습니다. 수고하셨습니다."
                else -> "목적지에 도착했어요!"
            }
        }

        return when (ttsStyle) {
            "casual" -> buildCasual(direction, distanceM, roadName, nearbyPoi)
            "polite" -> buildPolite(direction, distanceM, roadName, nearbyPoi)
            "ez" -> buildEz(direction, distanceM, roadName, nearbyPoi)
            else -> guideText  // 원본 유지
        }
    }

    /** "500m 앞 " 형태의 거리 접두어. 거리 0/무효면 빈 문자열. */
    private fun distPrefix(dist: Int): String {
        val d = formatDistance(dist)
        return if (d.isNotBlank()) "$d 앞 " else ""
    }

    private fun buildCasual(dir: Direction, dist: Int, road: String?, poi: String?): String {
        return when {
            // POI가 가까이 있으면 거리+랜드마크 기반 ("500m 앞 GS25 보이면 좌회전하세요")
            poi != null && dist < 500 ->
                "${distPrefix(dist)}${poi} 보이면 ${dir.casual}하세요"

            // 도로명 있으면 포함
            road != null && road.isNotBlank() ->
                "${formatDistance(dist)} 뒤에 ${road}에서 ${dir.casual}"

            // 기본
            else ->
                "${formatDistance(dist)} 앞에서 ${dir.casual}하세요"
        }
    }

    private fun buildPolite(dir: Direction, dist: Int, road: String?, poi: String?): String {
        return when {
            // 거리+랜드마크 ("500m 앞 GS25 앞에서 좌측으로 돌아주세요")
            poi != null && dist < 500 ->
                "${distPrefix(dist)}${poi} 앞에서 ${dir.polite}"

            road != null && road.isNotBlank() ->
                "${formatDistance(dist)} 후 ${road}에서 ${dir.polite}"

            else ->
                "${formatDistance(dist)} 앞에서 ${dir.polite}"
        }
    }

    private fun buildEz(dir: Direction, dist: Int, road: String?, poi: String?): String {
        val distStr = formatDistance(dist)
        val landmark = poi ?: road ?: ""
        val landmarkStr = if (landmark.isNotBlank()) ", $landmark 부근" else ""
        return "$distStr 앞$landmarkStr. ${dir.ez}."
    }

    private fun formatDistance(meters: Int): String = when {
        meters >= 1000 -> "%.1fkm".format(meters / 1000.0)
        meters > 0 -> "${meters}m"
        else -> ""
    }
}
