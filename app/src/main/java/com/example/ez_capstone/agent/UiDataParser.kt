package com.example.ez_capstone.agent

import com.example.ez_capstone.server.models.Coord
import com.example.ez_capstone.server.models.Guide
import com.example.ez_capstone.server.models.PlaceItem
import com.example.ez_capstone.server.models.RouteItem
import com.example.ez_capstone.server.models.ScheduleEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Agent 응답 uiData(생산자=ToolExecutor/normalizeResponse) → UI 모델(소비자) 변환.
 *
 * 순수 함수만 둔다(상태·Log·DI 없음) → JVM 단위테스트로 생산자키↔소비자키 계약을
 * 결정적으로 검증/회귀 방지. (정합성 붕괴 ①②의 재발 차단 게이트)
 */
object UiDataParser {

    /**
     * 장소 카드. normalizeResponse가 모든 보조 도구(stations/chargers/parking_lots/
     * pharmacys/hospitals)를 'places' 키로 정규화하므로 여기선 'places'만 읽으면 된다.
     * 좌표는 x/y(Kakao) 또는 lat/lng(공공데이터) 둘 다 수용.
     */
    fun parsePlaces(uiData: Map<String, Any?>?): List<PlaceItem> {
        val arr = jsonArrayFrom(uiData, jsonKey = "places_json", innerKey = "places", listKey = "places")
        val out = mutableListOf<PlaceItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (name.isBlank()) continue
            val lat = if (o.has("y")) o.optDouble("y", Double.NaN) else o.optDouble("lat", Double.NaN)
            val lng = if (o.has("x")) o.optDouble("x", Double.NaN) else o.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            out.add(
                PlaceItem(
                    name = name,
                    address = o.optString("address", ""),
                    lat = lat,
                    lng = lng,
                    category = o.optString("category", ""),
                    distance_m = o.optInt("distance_m", 0),
                    price = if (o.has("price")) o.optInt("price") else null,
                    brand = if (o.has("brand")) o.optString("brand") else null
                )
            )
        }
        return out
    }

    /**
     * 일정 카드. get_schedule 결과는 schedule_json(문자열) 안 "events" 배열.
     * 생산 필드 date/time → 소비측 start_time으로 합성. id는 Long/Int/String 모두 수용.
     */
    fun parseScheduleEvents(uiData: Map<String, Any?>?): List<ScheduleEvent> {
        val arr = jsonArrayFrom(uiData, jsonKey = "schedule_json", innerKey = "events", listKey = "events")
        val out = mutableListOf<ScheduleEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            if (title.isBlank()) continue
            val startTime = o.optString("start_time").ifBlank {
                listOf(o.optString("date"), o.optString("time")).filter { it.isNotBlank() }.joinToString(" ")
            }
            out.add(
                ScheduleEvent(
                    id = if (o.has("id")) o.opt("id")?.toString() ?: "" else "",
                    title = title,
                    start_time = startTime,
                    location = o.optString("location", "")
                )
            )
        }
        return out
    }

    /**
     * 경로. normalizeResponse가 uiData["routes"]에 List<Map>으로 주입.
     * 단위 자동 보정: km→m, min→s (LLM이 직접 생성한 routes 방어).
     */
    fun parseRoutes(uiData: Map<String, Any?>?): List<RouteItem> {
        val routesList = uiData?.get("routes") as? List<*> ?: return emptyList()
        return routesList.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val coordsList = (map["coords"] as? List<*>)?.mapNotNull { c ->
                val cm = c as? Map<*, *> ?: return@mapNotNull null
                val lat = (cm["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (cm["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                Coord(lat, lng)
            } ?: return@mapNotNull null
            if (coordsList.isEmpty()) return@mapNotNull null

            val rawDist = ((map["distance_m"] as? Number) ?: (map["distance"] as? Number))?.toDouble() ?: 0.0
            val distanceM = if (rawDist in 0.1..499.0) (rawDist * 1000).toInt() else rawDist.toInt()
            val rawDur = ((map["duration_s"] as? Number) ?: (map["duration"] as? Number))?.toDouble() ?: 0.0
            val durationS = if (rawDur in 1.0..299.0) (rawDur * 60).toInt() else rawDur.toInt()

            val guidesList = (map["guides"] as? List<*>)?.mapNotNull { g ->
                val gm = g as? Map<*, *> ?: return@mapNotNull null
                Guide(
                    lat = (gm["lat"] as? Number)?.toDouble() ?: 0.0,
                    lng = (gm["lng"] as? Number)?.toDouble() ?: 0.0,
                    name = (gm["name"] as? String) ?: "",
                    type = (gm["type"] as? Number)?.toInt() ?: 0,
                    guidance = (gm["guidance"] as? String) ?: "",
                    distance = (gm["distance"] as? Number)?.toInt() ?: 0
                )
            }

            RouteItem(
                coords = coordsList,
                distance_m = distanceM,
                duration_s = durationS,
                waypoints = null,
                guides = guidesList
            )
        }
    }

    /**
     * uiData에서 JSON 배열 추출.
     * 1순위: jsonKey(문자열 JSON) 안의 innerKey 배열. 2순위: listKey(이미 List<Map>) 호환.
     */
    private fun jsonArrayFrom(
        uiData: Map<String, Any?>?,
        jsonKey: String,
        innerKey: String,
        listKey: String
    ): JSONArray = when {
        uiData?.get(jsonKey) is String -> try {
            JSONObject(uiData[jsonKey] as String).optJSONArray(innerKey) ?: JSONArray()
        } catch (e: Exception) { JSONArray() }
        uiData?.get(listKey) is List<*> -> JSONArray().also { a ->
            (uiData[listKey] as List<*>).forEach { m -> if (m is Map<*, *>) a.put(JSONObject(m)) }
        }
        else -> JSONArray()
    }
}
