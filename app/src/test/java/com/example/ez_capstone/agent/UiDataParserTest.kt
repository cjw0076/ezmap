package com.example.ez_capstone.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UiDataParser 키 계약 회귀 테스트.
 * 정합성 붕괴 ①(보조 장소 카드)·②(일정 카드)·route 파싱이 다시 깨지면 빌드 실패하도록 잠근다.
 * 네트워크/기기/DI 없음 — 순수 JSON in → 모델 out.
 */
class UiDataParserTest {

    // ── ① places: places_json 안 "places" 배열을 읽는다 ──

    @Test
    fun parsePlaces_readsSearchShape_withXY() {
        val ui = mapOf(
            "places_json" to """{"places":[{"name":"스타벅스 강남점","address":"서울 강남","x":127.02,"y":37.49,"category":"카페","distance_m":120}]}"""
        )
        val r = UiDataParser.parsePlaces(ui)
        assertEquals(1, r.size)
        assertEquals("스타벅스 강남점", r[0].name)
        assertEquals(37.49, r[0].lat, 1e-6)   // y → lat
        assertEquals(127.02, r[0].lng, 1e-6)  // x → lng
        assertEquals(120, r[0].distance_m)
    }

    @Test
    fun parsePlaces_readsAuxShape_latLngAndPrice() {
        // 주유소/약국 등 보조 도구는 normalizeResponse가 'places' 키로 정규화 + lat/lng 좌표.
        val ui = mapOf(
            "places_json" to """{"places":[{"name":"GS칼텍스 역삼","address":"서울","lat":37.50,"lng":127.03,"price":1659}]}"""
        )
        val r = UiDataParser.parsePlaces(ui)
        assertEquals(1, r.size)
        assertEquals(37.50, r[0].lat, 1e-6)
        assertEquals(1659, r[0].price)
    }

    @Test
    fun parsePlaces_skipsBlankNameAndMissingCoords() {
        val ui = mapOf(
            "places_json" to """{"places":[{"name":"","x":1.0,"y":2.0},{"name":"좌표없음"},{"name":"정상","x":127.0,"y":37.0}]}"""
        )
        val r = UiDataParser.parsePlaces(ui)
        assertEquals(1, r.size)
        assertEquals("정상", r[0].name)
    }

    @Test
    fun parsePlaces_emptyWhenNoData() {
        assertTrue(UiDataParser.parsePlaces(null).isEmpty())
        assertTrue(UiDataParser.parsePlaces(emptyMap()).isEmpty())
        assertTrue(UiDataParser.parsePlaces(mapOf("places_json" to "not json")).isEmpty())
    }

    // ── ② schedule: schedule_json 안 "events", date/time → start_time, id 타입 수용 ──

    @Test
    fun parseScheduleEvents_synthesizesStartTimeFromDateTime() {
        val ui = mapOf(
            "schedule_json" to """{"events":[{"id":42,"title":"회의","date":"2026-05-25","time":"15:00","location":"강남"}]}"""
        )
        val r = UiDataParser.parseScheduleEvents(ui)
        assertEquals(1, r.size)
        assertEquals("회의", r[0].title)
        assertEquals("2026-05-25 15:00", r[0].start_time)  // date+time 합성
        assertEquals("42", r[0].id)                        // Long → String 수용
        assertEquals("강남", r[0].location)
    }

    @Test
    fun parseScheduleEvents_prefersExplicitStartTime() {
        val ui = mapOf(
            "schedule_json" to """{"events":[{"id":"e1","title":"점심","start_time":"12:30"}]}"""
        )
        val r = UiDataParser.parseScheduleEvents(ui)
        assertEquals("12:30", r[0].start_time)
    }

    @Test
    fun parseScheduleEvents_emptyWhenNoData() {
        assertTrue(UiDataParser.parseScheduleEvents(null).isEmpty())
        assertTrue(UiDataParser.parseScheduleEvents(mapOf("events" to emptyList<Any>())).isEmpty())
    }

    // ── route: km→m, min→s 보정 + coords/guides ──

    @Test
    fun parseRoutes_convertsKmToMetersAndMinToSeconds() {
        val ui = mapOf(
            "routes" to listOf(
                mapOf(
                    "coords" to listOf(
                        mapOf("lat" to 37.5, "lng" to 127.0),
                        mapOf("lat" to 37.6, "lng" to 127.1)
                    ),
                    "distance_m" to 12,   // km로 의심되는 작은 값 → ×1000
                    "duration_s" to 20    // min으로 의심되는 작은 값 → ×60
                )
            )
        )
        val r = UiDataParser.parseRoutes(ui)
        assertEquals(1, r.size)
        assertEquals(12000, r[0].distance_m)
        assertEquals(1200, r[0].duration_s)
        assertEquals(2, r[0].coords.size)
    }

    @Test
    fun parseRoutes_keepsRealisticUnitsUnchanged() {
        val ui = mapOf(
            "routes" to listOf(
                mapOf(
                    "coords" to listOf(mapOf("lat" to 37.5, "lng" to 127.0), mapOf("lat" to 37.51, "lng" to 127.01)),
                    "distance_m" to 8500,
                    "duration_s" to 900
                )
            )
        )
        val r = UiDataParser.parseRoutes(ui)
        assertEquals(8500, r[0].distance_m)
        assertEquals(900, r[0].duration_s)
    }

    @Test
    fun parseRoutes_emptyWhenNoCoords() {
        val ui = mapOf("routes" to listOf(mapOf("distance_m" to 1000, "duration_s" to 100)))
        assertTrue(UiDataParser.parseRoutes(ui).isEmpty())
    }
}
