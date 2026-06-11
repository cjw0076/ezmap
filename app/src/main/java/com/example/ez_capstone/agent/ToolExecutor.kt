package com.example.ez_capstone.agent

import android.util.Log
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.agent.models.WaypointParam
import com.example.ez_capstone.api.EvChargerApi
import com.example.ez_capstone.api.KakaoLocalApi
import com.example.ez_capstone.api.KakaoMobilityApi
import com.example.ez_capstone.api.KmaWeatherApi
import com.example.ez_capstone.api.NaverDirectionsApi
import com.example.ez_capstone.api.OdsayApi
import com.example.ez_capstone.api.OpinetApi
import com.example.ez_capstone.api.ParkingApi
import com.example.ez_capstone.api.WeatherApi
import com.example.ez_capstone.api.AirKoreaApi
import com.example.ez_capstone.api.WikipediaApi
import com.example.ez_capstone.api.WebFetchApi
import com.example.ez_capstone.api.ExchangeRateApi
import com.example.ez_capstone.api.MedicalApi
import com.example.ez_capstone.api.TrafficInfoApi
import com.example.ez_capstone.api.HighwayApi
import com.example.ez_capstone.api.RealtimeParkingApi
import com.example.ez_capstone.api.RoadRiskApi
import com.example.ez_capstone.api.SpeedCameraApi
import com.example.ez_capstone.api.IncidentApi
import com.example.ez_capstone.api.RestAreaApi
import com.example.ez_capstone.api.SpotifyApi
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.*
import com.example.ez_capstone.profile.MultiProfileManager
import com.example.ez_capstone.db.entity.ContactEntity
import com.example.ez_capstone.db.dao.AgentNoteDao
import com.example.ez_capstone.db.entity.AgentNoteEntity
import com.example.ez_capstone.db.entity.FrequentPlaceEntity
import com.example.ez_capstone.db.entity.PreferenceEntity
import com.example.ez_capstone.db.entity.ScheduleEntity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool 이름 → 실제 API/DB 실행 디스패처.
 * 서버 geminiAgent.js executeTool() 포팅.
 */
@Singleton
class ToolExecutor @Inject constructor(
    private val kakaoLocalApi: KakaoLocalApi,
    private val kakaoMobilityApi: KakaoMobilityApi,
    private val weatherApi: WeatherApi,
    private val opinetApi: OpinetApi,
    private val evChargerApi: EvChargerApi,
    private val kmaWeatherApi: KmaWeatherApi,
    private val parkingApi: ParkingApi,
    private val naverDirectionsApi: NaverDirectionsApi,
    private val odsayApi: OdsayApi,
    private val airKoreaApi: AirKoreaApi,
    private val wikipediaApi: WikipediaApi,
    private val webFetchApi: WebFetchApi,
    private val exchangeRateApi: ExchangeRateApi,
    private val medicalApi: MedicalApi,
    private val trafficInfoApi: TrafficInfoApi,
    private val highwayApi: HighwayApi,
    private val realtimeParkingApi: RealtimeParkingApi,
    private val roadRiskApi: RoadRiskApi,
    private val speedCameraApi: SpeedCameraApi,
    private val incidentApi: IncidentApi,
    private val restAreaApi: RestAreaApi,
    private val spotifyApi: SpotifyApi,
    private val apiKeyProvider: ApiKeyProvider,
    private val profileDao: ProfileDao,
    private val preferenceDao: PreferenceDao,
    private val routeHistoryDao: RouteHistoryDao,
    private val contactDao: ContactDao,
    private val scheduleDao: ScheduleDao,
    private val frequentPlaceDao: FrequentPlaceDao,
    private val agentNoteDao: AgentNoteDao,
    private val gson: Gson,
    private val multiProfileManager: MultiProfileManager,
    @ApplicationContext private val appContext: Context,
    private val mcpGateway: com.example.ez_capstone.mcp.client.McpToolGateway
) {
    companion object {
        private const val TAG = "ToolExecutor"
    }

    /** Spotify 결과 → 표준 JSON. ok/message/곡정보를 ui_action=music_control 카드에 전달. */
    private suspend fun executeSpotify(block: suspend () -> SpotifyApi.SpotifyResult): JSONObject {
        val r = block()
        return JSONObject().apply {
            put("ok", r.ok)
            put("message", r.message)
            r.trackName?.let { put("track", it) }
            r.artist?.let { put("artist", it) }
            if (!r.ok) put("error", r.message)
        }
    }

    /** API 키 미등록 시 suggest_skill 응답 생성 */
    private fun suggestSkillResponse(skill: String, message: String, setupPack: String): JSONObject =
        JSONObject().apply {
            put("suggest_skill", skill)
            put("message", message)
            put("setup_pack", setupPack)
        }

    /**
     * Tool 실행. 결과를 JSONObject로 반환.
     * toolResultStore에 전체 결과(coords 포함)를 저장하고,
     * Gemini에 보낼 때는 stripForGemini()로 축소.
     */
    suspend fun execute(
        name: String,
        args: Map<String, Any?>,
        context: AgentContext
    ): JSONObject {
        Log.d(TAG, "Tool call: $name(${args.keys.joinToString()})")

        val profile = multiProfileManager.getActiveProfile()
        if (name in profile.blockedSkills) {
            return JSONObject().apply {
                put("error", "현재 모드에서는 이 기능을 사용할 수 없습니다.")
                put("tool", name)
            }
        }

        return try {
            when (name) {
                "search_places" -> executeSearchPlaces(args, context)
                "geocode" -> executeGeocode(args)
                "reverse_geocode" -> executeReverseGeocode(args)
                "get_directions" -> executeGetDirections(args)
                "get_future_eta" -> executeGetFutureEta(args)
                "get_schedule" -> executeGetSchedule()
                "save_note" -> executeSaveNote(args)
                "get_notes" -> executeGetNotes(args)
                "delete_note" -> executeDeleteNote(args)
                "send_message" -> executeSendMessage(args)
                "get_user_preferences" -> executeGetPreferences(args)
                "update_user_preferences" -> executeUpdatePreferences(args)
                "get_user_profile" -> executeGetProfile()
                "update_user_profile" -> executeUpdateProfile(args)
                "lookup_contact" -> executeLookupContact(args)
                "manage_contacts" -> executeManageContacts(args)
                "get_weather" -> executeGetWeather(args, context)
                "analyze_route_patterns" -> executeAnalyzePatterns()
                // Phase N3: 신규 4종
                "get_gas_stations" -> executeGetGasStations(args, context)
                "get_ev_chargers" -> executeGetEvChargers(args, context)
                "get_weather_kma" -> executeGetWeatherKma(args, context)
                "get_parking" -> executeGetParking(args, context)
                // Phase N6-N7
                "get_directions_naver" -> executeGetDirectionsNaver(args)
                "get_transit_route" -> executeGetTransitRoute(args)
                // Tier 1: 생활 편의
                "get_air_quality" -> executeGetAirQuality(args, context)
                "search_knowledge" -> executeSearchKnowledge(args)
                "fetch_url" -> executeFetchUrl(args)
                "get_exchange_rate" -> executeGetExchangeRate(args)
                "search_pharmacies" -> executeSearchMedical(args, context, "pharmacy")
                "search_hospitals" -> executeSearchMedical(args, context, "hospital")
                // 실시간 교통 6종
                "get_traffic_speed" -> executeGetTrafficSpeed(args, context)
                "get_traffic_incidents" -> executeGetTrafficIncidents(args, context)
                "get_traffic_cctv" -> executeGetTrafficCctv(args, context)
                "get_highway_alerts" -> executeGetHighwayAlerts(args)
                "get_realtime_parking" -> executeGetRealtimeParking(args, context)
                "get_road_risk" -> executeGetRoadRisk(args, context)
                // Phase 7 내비게이션
                "get_speed_cameras" -> executeGetSpeedCameras(args, context)
                "get_road_incidents" -> executeGetRoadIncidents(args, context)
                "get_rest_areas" -> executeGetRestAreas(args)
                "suggest_parking" -> executeSuggestParking(args)
                // 일정·전화·알람·즐겨찾기
                "create_schedule" -> executeCreateSchedule(args)
                "update_schedule" -> executeUpdateSchedule(args)
                "delete_schedule" -> executeDeleteSchedule(args)
                "make_call" -> executeMakeCall(args)
                "set_alarm" -> executeSetAlarm(args)
                "manage_favorites" -> executeManageFavorites(args)
                // Spotify 음악 제어
                "play_music" -> executeSpotify { spotifyApi.play(args["query"]?.toString()?.takeIf { it.isNotBlank() }) }
                "pause_music" -> executeSpotify { spotifyApi.pause() }
                "next_track" -> executeSpotify { spotifyApi.next() }
                "previous_track" -> executeSpotify { spotifyApi.previous() }
                "current_track" -> executeSpotify { spotifyApi.current() }
                // 내장 도구가 아니면 MCP 플러그인 도구인지 확인 → 해당 외부 서버로 라우팅.
                // 실패는 위 catch가 ToolFailureException으로 일관 처리(silent fail 방지).
                else -> if (mcpGateway.isMcpTool(name)) mcpGateway.call(name, args)
                    else JSONObject().put("error", "알 수 없는 tool: $name")
            }
        } catch (e: ToolFailureException) {
            // API가 명시적으로 던진 분류된 실패 — 중앙에서 일관 처리.
            Log.e(TAG, "Tool [$name] FAILED kind=${e.kind.code}: ${e.message}")
            failureJson(e.kind, "${e.kind.userMessage}")
        } catch (e: retrofit2.HttpException) {
            val kind = FailureKind.fromHttp(e.code())
            Log.e(TAG, "Tool [$name] HTTP ${e.code()} → ${kind.code}")
            failureJson(kind)
        } catch (e: Exception) {
            // 미분류 예외도 silent로 흘리지 않고 분류해 surface (네트워크 vs 그 외).
            val kind = FailureKind.fromThrowable(e)
            Log.e(TAG, "Tool error [$name] → ${kind.code}: ${e.message}")
            failureJson(kind, e.message)
        }
    }

    /**
     * Gemini에 보내기 전 토큰 절약용 축소.
     * get_directions: coords, guides 제거 (UI에서는 전체 데이터 필요하므로 별도 저장).
     */
    fun stripForGemini(name: String, result: JSONObject): JSONObject = when (name) {
        "get_directions", "get_directions_naver" -> {
            // coords/guides 제거 → distance/duration/summary만
            val routes = result.optJSONArray("routes") ?: return result
            val stripped = JSONArray()
            for (i in 0 until routes.length()) {
                val r = routes.getJSONObject(i)
                stripped.put(JSONObject().apply {
                    put("distance_m", r.optInt("distance_m"))
                    put("duration_s", r.optInt("duration_s"))
                    if (r.has("summary")) put("summary", r.opt("summary"))
                })
            }
            JSONObject().put("routes", stripped)
        }
        "get_transit_route" -> {
            // steps 상세 제거 → 요약만
            val routes = result.optJSONArray("routes") ?: return result
            val stripped = JSONArray()
            for (i in 0 until routes.length()) {
                val r = routes.getJSONObject(i)
                stripped.put(JSONObject().apply {
                    put("total_time_min", r.optInt("total_time_min"))
                    put("transfer_count", r.optInt("transfer_count"))
                    put("fare", r.optInt("fare"))
                    put("type", r.optInt("type"))
                })
            }
            JSONObject().put("routes", stripped).put("count", stripped.length())
        }
        "search_knowledge" -> {
            // 위키 요약 100자 제한
            JSONObject().apply {
                put("title", result.optString("title"))
                put("summary", result.optString("summary").take(100))
            }
        }
        "get_gas_stations", "get_parking", "get_ev_chargers",
        "search_pharmacies", "search_hospitals",
        "get_traffic_speed", "get_traffic_incidents", "get_traffic_cctv",
        "get_highway_alerts", "get_realtime_parking", "get_road_risk" -> {
            // 목록 상위 3~5개만 Gemini에 전달
            val keys = listOf("stations", "lots", "chargers", "pharmacys", "hospitals",
                "segments", "incidents", "cctvs", "alerts", "parking_lots", "risk_zones")
            val key = keys.firstOrNull { result.has(it) } ?: return result
            val items = result.optJSONArray(key) ?: return result
            val stripped = JSONArray()
            for (i in 0 until minOf(items.length(), 3)) {
                stripped.put(items.getJSONObject(i))
            }
            JSONObject().put(key, stripped).put("count", items.length())
        }
        else -> result
    }

    // ── Tool 구현 ──

    private suspend fun executeSearchPlaces(args: Map<String, Any?>, context: AgentContext): JSONObject {
        val categoryCode = args["category_group_code"]?.toString()?.ifBlank { null }
        // "근처 카페"처럼 카테고리만 주고 query를 안 주는 경우, 카테고리 코드에서 키워드 유도
        val query = args["query"]?.toString()?.ifBlank { null }
            ?: categoryCode?.let { categoryKeyword(it) }
            ?: return JSONObject().put("error", "query 필요")
        val x = (args["x"] as? Number)?.toDouble() ?: context.locationX
        val y = (args["y"] as? Number)?.toDouble() ?: context.locationY
        val radius = (args["radius"] as? Number)?.toInt() ?: 20000
        // 위치가 있으면 거리순 기본 정렬 → places[0]이 항상 가장 가까운 곳(엉뚱한 먼 지점 라우팅 방지)
        val sort = args["sort"]?.toString()?.ifBlank { null }
            ?: if (x != null && y != null) "distance" else null

        val places = kakaoLocalApi.searchKeyword(query, x, y, radius, categoryCode, sort, 5)
        val arr = JSONArray()
        places.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("address", p.address)
                put("category", p.category)
                put("x", p.lng)
                put("y", p.lat)
                put("distance_m", p.distanceM ?: 0)
            })
        }
        return JSONObject().put("places", arr)
    }

    // Kakao category_group_code → 한국어 키워드 (query 없이 카테고리만 올 때 사용)
    private fun categoryKeyword(code: String): String? = when (code.uppercase()) {
        "MT1" -> "대형마트"
        "CS2" -> "편의점"
        "PK6" -> "주차장"
        "OL7" -> "주유소"
        "SW8" -> "지하철역"
        "BK9" -> "은행"
        "CT1" -> "문화시설"
        "AT4" -> "관광명소"
        "AD5" -> "숙박"
        "FD6" -> "음식점"
        "CE7" -> "카페"
        "HP8" -> "병원"
        "PM9" -> "약국"
        else -> null
    }

    private suspend fun executeGeocode(args: Map<String, Any?>): JSONObject {
        val address = args["address"]?.toString() ?: return JSONObject().put("error", "address 필요")
        val result = kakaoLocalApi.geocodeAddress(address)
        return JSONObject().apply {
            put("address", result.address)
            put("x", result.lng)
            put("y", result.lat)
        }
    }

    private suspend fun executeReverseGeocode(args: Map<String, Any?>): JSONObject {
        val x = (args["x"] as? Number)?.toDouble() ?: return JSONObject().put("error", "x 필요")
        val y = (args["y"] as? Number)?.toDouble() ?: return JSONObject().put("error", "y 필요")
        val result = kakaoLocalApi.reverseGeocode(x, y)
        return JSONObject().apply {
            put("address", result.address ?: "")
            put("road_address", result.roadAddress ?: "")
        }
    }

    private suspend fun executeGetDirections(args: Map<String, Any?>): JSONObject {
        val originX = (args["origin_x"] as? Number)?.toDouble() ?: return JSONObject().put("error", "origin_x 필요")
        val originY = (args["origin_y"] as? Number)?.toDouble() ?: return JSONObject().put("error", "origin_y 필요")
        val destX = (args["dest_x"] as? Number)?.toDouble() ?: return JSONObject().put("error", "dest_x 필요")
        val destY = (args["dest_y"] as? Number)?.toDouble() ?: return JSONObject().put("error", "dest_y 필요")
        val priority = args["priority"]?.toString() ?: "RECOMMEND"

        // 경유지 파싱
        val waypoints = mutableListOf<WaypointParam>()
        val wpList = args["waypoints"]
        if (wpList is List<*>) {
            wpList.filterIsInstance<Map<*, *>>().forEach { wp ->
                val name = wp["name"]?.toString() ?: ""
                val wx = (wp["x"] as? Number)?.toDouble() ?: return@forEach
                val wy = (wp["y"] as? Number)?.toDouble() ?: return@forEach
                waypoints.add(WaypointParam(name, wx, wy))
            }
        }

        val routes = kakaoMobilityApi.getDirections(originX, originY, destX, destY, waypoints, priority)

        val arr = JSONArray()
        routes.forEach { r ->
            arr.put(JSONObject().apply {
                put("distance_m", r.distanceM)
                put("duration_s", r.durationS)
                put("summary", JSONObject().apply {
                    put("fare", r.fare ?: 0)
                    put("taxi_fare", r.taxiFare ?: 0)
                })
                // 전체 데이터도 포함 (stripForGemini에서 제거됨)
                val coordsArr = JSONArray()
                r.coords.forEach { c -> coordsArr.put(JSONObject().put("lat", c.lat).put("lng", c.lng)) }
                put("coords", coordsArr)

                val guidesArr = JSONArray()
                r.guides.forEach { g ->
                    guidesArr.put(JSONObject().apply {
                        put("name", g.name)
                        put("guidance", g.guidance)
                        put("type", g.type)
                        put("lat", g.lat)
                        put("lng", g.lng)
                        put("distance", g.distance)
                        put("duration", g.duration)
                    })
                }
                put("guides", guidesArr)
            })
        }
        return JSONObject().put("routes", arr)
    }

    private suspend fun executeGetFutureEta(args: Map<String, Any?>): JSONObject {
        val originX = (args["origin_x"] as? Number)?.toDouble()
            ?: return JSONObject().put("error", "origin_x 필요")
        val originY = (args["origin_y"] as? Number)?.toDouble()
            ?: return JSONObject().put("error", "origin_y 필요")
        val destX = (args["dest_x"] as? Number)?.toDouble()
            ?: return JSONObject().put("error", "dest_x 필요")
        val destY = (args["dest_y"] as? Number)?.toDouble()
            ?: return JSONObject().put("error", "dest_y 필요")
        val departureTime = args["departure_time"]?.toString()
            ?: return JSONObject().put("error", "departure_time 필요")
        val result = kakaoMobilityApi.getFutureETA(originX, originY, destX, destY, departureTime)
        return JSONObject().apply {
            put("duration_s", result.durationS)
            put("distance_m", result.distanceM)
            put("departure_time", result.departureTime)
        }
    }

    private suspend fun executeGetSchedule(): JSONObject {
        val schedules = scheduleDao.getActive()
        val arr = JSONArray()
        schedules.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("date", s.date ?: "")
                put("time", s.time ?: "")
                put("location", s.destinationName ?: "")
                if (s.destinationLat != null) put("lat", s.destinationLat)
                if (s.destinationLng != null) put("lng", s.destinationLng)
                put("is_active", s.isActive)
            })
        }
        return JSONObject().put("events", arr)
    }

    private suspend fun executeSaveNote(args: Map<String, Any?>): JSONObject {
        val category = args["category"]?.toString() ?: return JSONObject().put("error", "category 필요")
        val content = args["content"]?.toString() ?: return JSONObject().put("error", "content 필요")
        val source = args["source"]?.toString()

        // 중복 확인: 비슷한 내용이 있으면 confidence 올리고 업데이트
        val existing = agentNoteDao.search(content.take(20)).firstOrNull()
        if (existing != null) {
            val updated = existing.copy(
                content = content,
                confidence = (existing.confidence + 0.1f).coerceAtMost(1.0f),
                updatedAt = System.currentTimeMillis()
            )
            agentNoteDao.update(updated)
            return JSONObject().apply {
                put("success", true)
                put("action", "updated")
                put("id", existing.id)
            }
        }

        val id = agentNoteDao.upsert(AgentNoteEntity(
            category = category,
            content = content,
            source = source,
            confidence = 0.8f
        ))
        return JSONObject().apply {
            put("success", true)
            put("action", "created")
            put("id", id)
        }
    }

    private suspend fun executeGetNotes(args: Map<String, Any?>): JSONObject {
        val category = args["category"]?.toString()
        val query = args["query"]?.toString()

        val notes = when {
            !query.isNullOrBlank() -> agentNoteDao.search(query)
            !category.isNullOrBlank() -> agentNoteDao.getByCategory(category)
            else -> agentNoteDao.getRecent(20)
        }

        val arr = JSONArray()
        notes.forEach { n ->
            agentNoteDao.incrementUseCount(n.id)
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("category", n.category)
                put("content", n.content)
                put("confidence", n.confidence)
            })
        }
        return JSONObject().put("notes", arr).put("count", notes.size)
    }

    private suspend fun executeDeleteNote(args: Map<String, Any?>): JSONObject {
        val id = ((args["id"] as? Number)?.toLong() ?: (args["id"] as? String)?.toLongOrNull())
            ?: return JSONObject().put("error", "id 필요")
        agentNoteDao.deleteById(id)
        return JSONObject().put("success", true)
    }

    // 연락처 이름 정규화: STT 노이즈 토큰(숫자·"2일"·"3시" 등)을 제거하고 한글 이름만 남긴다.
    // ("21 황동헌"·"2일 황동헌" → "황동헌")
    private fun normalizeContactName(raw: String): String {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val kept = tokens.filterNot { it.matches(Regex("\\d+[일시분초개명번째호동층]?")) }
        return (if (kept.isNotEmpty()) kept else tokens).joinToString("").replace(" ", "")
    }

    /** 편집거리(Levenshtein) — STT 1자 오인식 판별용. */
    private fun editDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n; if (n == 0) return m
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..n) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = tmp
            }
        }
        return dp[n]
    }

    /**
     * 연락처 매칭(공유): exact → 부분포함 → 퍼지(편집거리≤1).
     * STT 1자 오인식("황동헌"↔"황동원")·노이즈 접두("21 황동헌")를 흡수한다.
     */
    private fun matchContacts(
        query: String,
        all: List<Triple<String, String, String>>
    ): List<Triple<String, String, String>> {
        val q = normalizeContactName(query)
        if (q.isBlank()) return emptyList()
        all.filter { it.first.trim() == q }.let { if (it.isNotEmpty()) return it }
        all.filter { val n = it.first.trim(); n.contains(q) || q.contains(n) }
            .let { if (it.isNotEmpty()) return it }
        return all.filter {
            val n = it.first.trim()
            n.length >= 2 && q.length >= 2 && editDistance(n, q) <= 1
        }
    }

    /** 기기+Room 병합 연락처에서 이름→(매칭이름, 번호) 해석. lookup_contact와 동일 매처라 일관됨. */
    private suspend fun resolveContactPhone(name: String): Pair<String, String>? {
        if (name.isBlank()) return null
        val all = deviceAndCachedContacts()
        val hit = matchContacts(name, all).firstOrNull { it.second.isNotBlank() }
        return hit?.let { it.first to it.second }
    }

    private suspend fun executeSendMessage(args: Map<String, Any?>): JSONObject {
        val recipient = args["recipient"]?.toString() ?: return JSONObject().put("error", "recipient 필요")
        val message = args["message"]?.toString() ?: return JSONObject().put("error", "message 필요")
        val method = args["method"]?.toString() ?: "sms"
        val directPhone = args["phone"]?.toString()

        // 기기+Room 실 연락처를 우선 사용(lookup_contact와 동일 소스) — LLM 환각 번호 오발송 방지.
        // 연락처에 없을 때만 phone 파라미터(사용자가 직접 불러준 번호)를 사용한다.
        val resolved = resolveContactPhone(recipient)
        val phone = when {
            resolved != null -> resolved.second
            !directPhone.isNullOrBlank() -> directPhone
            else -> ""
        }

        if (phone.isBlank()) {
            return JSONObject().apply {
                put("status", "missing_phone")
                put("recipient", recipient)
                put("message", message)
                put("instruction", "'$recipient'의 전화번호를 모릅니다. 사용자에게 전화번호를 물어보세요. 번호를 받으면 phone 파라미터에 직접 넣어서 다시 호출하세요.")
            }
        }

        return JSONObject().apply {
            put("status", "draft_pending_confirmation")
            put("recipient", resolved?.first ?: recipient)
            put("phone", phone)
            put("method", method)
            put("message", message)
            put("requires_ui", true)
            put("instruction", "메시지가 아직 전송되지 않았습니다. 반드시 ui_action=show_message_draft, ui_data에 recipient/phone/message/method를 넣어주세요.")
        }
    }

    private suspend fun executeGetPreferences(args: Map<String, Any?>): JSONObject {
        val category = args["category"]?.toString()
        val prefs = if (category != null) {
            preferenceDao.getByCategory(category)
        } else {
            preferenceDao.getAll()
        }
        val result = JSONObject()
        prefs.forEach { p ->
            if (!result.has(p.category)) result.put(p.category, JSONObject())
            result.getJSONObject(p.category).put(p.key, p.value)
        }
        return result
    }

    private suspend fun executeUpdatePreferences(args: Map<String, Any?>): JSONObject {
        val category = args["category"]?.toString() ?: return JSONObject().put("error", "category 필요")
        val key = args["key"]?.toString() ?: return JSONObject().put("error", "key 필요")
        val value = (args["value"] as? Number)?.toFloat() ?: return JSONObject().put("error", "value 필요")

        preferenceDao.upsert(PreferenceEntity(
            category = category,
            key = key,
            value = value,
            updatedAt = System.currentTimeMillis()
        ))
        return JSONObject().put("success", true)
    }

    private suspend fun executeGetProfile(): JSONObject {
        val profile = profileDao.getProfile()
            ?: return JSONObject().put("message", "프로필이 아직 등록되지 않았습니다.")
        return JSONObject().apply {
            if (profile.homeAddress != null) {
                put("home", JSONObject().apply {
                    put("address", profile.homeAddress)
                    put("lat", profile.homeLat ?: 0.0)
                    put("lng", profile.homeLng ?: 0.0)
                })
            }
            if (profile.workAddress != null) {
                put("work", JSONObject().apply {
                    put("address", profile.workAddress)
                    put("lat", profile.workLat ?: 0.0)
                    put("lng", profile.workLng ?: 0.0)
                })
            }
            put("vehicle", JSONObject().apply {
                put("type", profile.vehicleType ?: "sedan")
                put("fuel", profile.fuelType ?: "gasoline")
                put("hipass", profile.hasHipass ?: false)
            })
            put("tts_style", profile.ttsStyle ?: "polite")
            put("detail_level", profile.detailLevel ?: "normal")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun executeUpdateProfile(args: Map<String, Any?>): JSONObject {
        val field = args["field"]?.toString() ?: return JSONObject().put("error", "field 필요")
        val value = args["value"]
        val profile = profileDao.getProfile() ?: return JSONObject().put("error", "프로필 없음")

        when (field) {
            "tts_style" -> profileDao.upsertProfile(profile.copy(ttsStyle = value?.toString() ?: "polite"))
            "detail_level" -> profileDao.upsertProfile(profile.copy(detailLevel = value?.toString() ?: "normal"))
            "home" -> {
                val map = value as? Map<String, Any?> ?: return JSONObject().put("error", "value는 {address, lat, lng} 형태여야 합니다")
                profileDao.upsertProfile(profile.copy(
                    homeAddress = map["address"]?.toString(),
                    homeLat = (map["lat"] as? Number)?.toDouble(),
                    homeLng = (map["lng"] as? Number)?.toDouble()
                ))
            }
            "work" -> {
                val map = value as? Map<String, Any?> ?: return JSONObject().put("error", "value는 {address, lat, lng} 형태여야 합니다")
                profileDao.upsertProfile(profile.copy(
                    workAddress = map["address"]?.toString(),
                    workLat = (map["lat"] as? Number)?.toDouble(),
                    workLng = (map["lng"] as? Number)?.toDouble()
                ))
            }
            "vehicle" -> {
                val map = value as? Map<String, Any?> ?: return JSONObject().put("error", "value는 {type, fuel, hipass} 형태여야 합니다")
                profileDao.upsertProfile(profile.copy(
                    vehicleType = map["type"]?.toString() ?: profile.vehicleType,
                    fuelType = map["fuel"]?.toString() ?: profile.fuelType,
                    hasHipass = (map["hipass"] as? Boolean) ?: profile.hasHipass
                ))
            }
        }
        return JSONObject().put("success", true)
    }

    /**
     * 기기 주소록(ContactsContract) 온디맨드 조회 + Room 수동입력 병합.
     * 에이전트가 "실제" 연락처를 보게 한다(이전엔 Room 캐시만 봐서 사실상 눈이 멀었음).
     * @return (name, phone, relationship)
     */
    private suspend fun deviceAndCachedContacts(): List<Triple<String, String, String>> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Triple<String, String, String>>()
        try {
            contactDao.getAll().forEach {
                out["${it.name}|${it.phone}"] = Triple(it.name, it.phone ?: "", it.relationship ?: "")
            }
        } catch (_: Exception) {}
        if (appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val proj = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                appContext.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null, null
                )?.use { c ->
                    val ni = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val pi = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (ni >= 0 && pi >= 0) while (c.moveToNext()) {
                        val n = c.getString(ni)?.trim().orEmpty()
                        val p = c.getString(pi)?.trim().orEmpty()
                        if (n.isNotBlank()) out.putIfAbsent("$n|$p", Triple(n, p, ""))
                    }
                }
            } catch (_: Exception) {}
        }
        out.values.toList()
    }

    /**
     * READ 전용 연락처 조회 — 절대 변경하지 않는다.
     * "연락처에 X 있어?" 같은 존재 확인이 add(쓰기)로 둔갑하던 hallucination을 구조적으로 차단.
     */
    private suspend fun executeLookupContact(args: Map<String, Any?>): JSONObject {
        val q = (args["name"] as? String)?.trim().orEmpty()
        if (q.isBlank()) return JSONObject().put("error", "name 필요")
        val all = deviceAndCachedContacts()
        val matches = matchContacts(q, all)
        val arr = JSONArray()
        matches.take(5).forEach { (n, p, rel) ->
            arr.put(JSONObject().put("name", n).put("phone", p).apply { if (rel.isNotBlank()) put("relationship", rel) })
        }
        // 권한 OFF인데 매칭 0 → '없는 사람'이 아니라 단말 주소록을 못 읽은 것. 모델이 오해하지 않게 구분.
        val contactsGranted =
            appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return JSONObject()
            .put("found", matches.isNotEmpty())
            .put("query", q)
            .put("matches", arr)
            .put("count", matches.size)
            .apply {
                if (matches.isEmpty() && !contactsGranted) {
                    put("error", "연락처 권한이 꺼져 있어 단말 주소록을 읽지 못했습니다.")
                    put("error_kind", "MISSING_PERMISSION")
                    put("hint", "연락처를 못 찾은 게 아니라 권한 때문입니다. '연락처 권한을 켜 주세요'라고 1문장 안내하고, 없는 사람이라고 단정하지 마세요.")
                }
            }
    }

    private suspend fun executeManageContacts(args: Map<String, Any?>): JSONObject {
        val action = args["action"]?.toString() ?: return JSONObject().put("error", "action 필요")
        val contactMap = args["contact"] as? Map<*, *>

        return when (action) {
            "list" -> {
                // 실 기기 연락처 + Room 병합 (이전엔 Room만 → 비어있으면 에이전트가 못 봄)
                val arr = JSONArray()
                deviceAndCachedContacts().forEach { (n, p, rel) ->
                    arr.put(JSONObject().apply {
                        put("name", n)
                        put("phone", p)
                        if (rel.isNotBlank()) put("relationship", rel)
                    })
                }
                JSONObject().put("contacts", arr).put("count", arr.length())
            }
            "add" -> {
                val name = contactMap?.get("name")?.toString() ?: return JSONObject().put("error", "이름 필요")
                contactDao.insert(ContactEntity(
                    name = name,
                    phone = contactMap["phone"]?.toString() ?: "",
                    relationship = contactMap["relationship"]?.toString() ?: "friend",
                    defaultMethod = contactMap["default_method"]?.toString() ?: "sms",
                    messageTemplates = null
                ))
                JSONObject().put("success", true)
            }
            "update" -> {
                val name = contactMap?.get("name")?.toString() ?: return JSONObject().put("error", "이름 필요")
                val found = contactDao.findByName(name).firstOrNull()
                    ?: return JSONObject().put("error", "해당 연락처를 찾을 수 없습니다.")
                val updated = found.copy(
                    phone = contactMap["phone"]?.toString() ?: found.phone,
                    relationship = contactMap["relationship"]?.toString() ?: found.relationship,
                    defaultMethod = contactMap["default_method"]?.toString() ?: found.defaultMethod
                )
                contactDao.insert(updated)
                JSONObject().put("success", true)
            }
            "delete" -> {
                val name = contactMap?.get("name")?.toString() ?: return JSONObject().put("error", "이름 필요")
                val found = contactDao.findByName(name).firstOrNull()
                if (found != null) {
                    contactDao.delete(found)
                    JSONObject().put("success", true)
                } else {
                    JSONObject().put("error", "해당 연락처를 찾을 수 없습니다.")
                }
            }
            else -> JSONObject().put("error", "알 수 없는 action: $action")
        }
    }

    private suspend fun executeGetWeather(args: Map<String, Any?>, context: AgentContext): JSONObject {
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val result = weatherApi.getWeather(lat, lng)
        return JSONObject().apply {
            put("temp", result.temp)
            put("condition", result.condition)
            put("rain_probability", result.rainProbability)
            put("wind_speed", result.windSpeed)
            put("description", result.description)
            put("road_condition", result.roadCondition)
        }
    }

    private suspend fun executeAnalyzePatterns(): JSONObject {
        val recent = routeHistoryDao.getRecent(30)
        // 간단한 패턴 분석: 목적지별 빈도
        val destFrequency = recent.groupBy { it.destName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(5)

        val arr = JSONArray()
        destFrequency.forEach { (dest, count) ->
            arr.put(JSONObject().apply {
                put("dest_name", dest)
                put("frequency", count)
            })
        }
        return JSONObject().apply {
            put("patterns", arr)
            put("count", arr.length())
        }
    }

    // ── Phase N3: 신규 Tool 4종 ──

    private suspend fun executeGetGasStations(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.opinetKey.isBlank()) {
            return suggestSkillResponse(
                "opinet",
                "주유소 정보를 사용하려면 설정에서 오피넷 API 키를 등록해주세요.",
                "opinet"
            )
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 5000
        val fuelType = args["fuel_type"]?.toString() ?: "B034"

        val stations = opinetApi.getGasStations(lat, lng, radius, fuelType)
        val arr = JSONArray()

        if (stations.isNotEmpty()) {
            stations.forEach { s ->
                arr.put(JSONObject().apply {
                    put("name", s.name)
                    put("brand", s.brand)
                    put("price", s.price)
                    put("lat", s.lat)
                    put("lng", s.lng)
                    put("self_service", s.hasSelfService)
                })
            }
            return JSONObject().put("stations", arr).put("count", arr.length())
        }

        // aroundAll.do 결과 없음(데모 키 제한) → Kakao OL4 + lowTop10 브랜드 시세로 폴백
        val brandPrices = opinetApi.getBrandPrices(fuelType)
        val kakaoStations = kakaoLocalApi.searchKeyword(
            query = "주유소", x = lng, y = lat, radius = radius,
            categoryGroupCode = "OL4", sort = "distance", size = 10
        )
        kakaoStations.forEach { place ->
            val brandCode = opinetApi.brandCode(place.name)
            val refPrice = brandPrices[brandCode]
            arr.put(JSONObject().apply {
                put("name", place.name)
                put("brand", brandCode)
                put("distance_m", place.distanceM ?: 0)
                put("address", place.address)
                put("lat", place.lat)
                put("lng", place.lng)
                if (refPrice != null) put("ref_price", refPrice) // 전국 최저가 참조
            })
        }
        val note = if (brandPrices.isNotEmpty()) "가격은 오피넷 전국 최저가 기준 참조값입니다" else "실시간 가격 정보를 가져올 수 없습니다"
        return JSONObject().put("stations", arr).put("count", arr.length()).put("price_note", note)
    }

    private suspend fun executeGetEvChargers(args: Map<String, Any?>, context: AgentContext): JSONObject {
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 5000
        val statusFilter = args["status_filter"] as? Boolean ?: false

        val chargers = try { evChargerApi.getChargers(lat, lng, radius, statusFilter) } catch (_: Exception) { emptyList() }
        // data.go.kr 미신청/빈 결과 → Kakao 폴백(실시간 상태는 없지만 위치는 안내)
        if (chargers.isEmpty()) {
            val k = kakaoPlacesFallback("전기차충전소", lng, lat)
            return JSONObject().put("chargers", k).put("count", k.length()).put("source", "kakao")
        }
        val arr = JSONArray()
        chargers.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("address", c.address)
                put("lat", c.lat)
                put("lng", c.lng)
                put("charger_type", c.chargerType)
                put("status", c.status)
                put("status_text", c.statusText)
            })
        }
        return JSONObject().put("chargers", arr).put("count", arr.length())
    }

    private suspend fun executeGetWeatherKma(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse(
                "weather_kma",
                "기상청 날씨 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.",
                "public_data"
            )
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: 0.0
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: 0.0
        // 위치(GPS)를 못 잡으면 날씨 조회 불가 — 사용자가 원인을 알 수 있게 명확히 사유 반환
        if (lat == 0.0 && lng == 0.0) {
            return JSONObject()
                .put("error", "no_location")
                .put("message", "현재 위치(GPS)를 확인할 수 없어 날씨를 가져올 수 없어요. 위치 권한과 GPS가 켜져 있는지 확인해주세요.")
        }

        val result = kmaWeatherApi.getWeather(lat, lng)
        // 키는 있으나 실황 데이터가 비어있는 경우(temp 없음) — 사유를 명확히 전달
        if (result.temp == null) {
            return JSONObject()
                .put("error", "no_data")
                .put("message", "해당 위치의 기상청 실황 데이터를 아직 받지 못했어요. 잠시 후 다시 시도해주세요.")
        }
        return JSONObject().apply {
            put("temp", result.temp)
            put("condition", result.condition)
            put("rain_probability", result.rainProbability)
            put("wind_speed", result.windSpeed)
            put("description", result.description)
            put("road_condition", result.roadCondition)
        }
    }

    /**
     * data.go.kr 미신청/빈 결과/예외 시 Kakao 키워드 검색 폴백(POI 위치).
     * 주차장/약국/병원/충전소는 Kakao에 있으므로 data.go.kr 없이도 위치는 안내 가능.
     * (x=경도, y=위도)
     */
    private suspend fun kakaoPlacesFallback(keyword: String, x: Double?, y: Double?): JSONArray {
        if (x == null || y == null) return JSONArray()
        val arr = JSONArray()
        try {
            kakaoLocalApi.searchKeyword(query = keyword, x = x, y = y, radius = 5000, sort = "distance", size = 10)
                .forEach { p ->
                    arr.put(JSONObject().apply {
                        put("name", p.name); put("address", p.address); put("lat", p.lat); put("lng", p.lng)
                        if (!p.phone.isNullOrBlank()) put("phone", p.phone)
                        p.distanceM?.let { put("distance_m", it) }
                    })
                }
        } catch (_: Exception) {}
        return arr
    }

    private suspend fun executeGetParking(args: Map<String, Any?>, context: AgentContext): JSONObject {
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 3000

        val lots = try { parkingApi.getParkingLots(lat, lng, radius) } catch (_: Exception) { emptyList() }
        // data.go.kr 미신청/빈 결과 → Kakao 폴백(위치만이라도 안내)
        if (lots.isEmpty()) {
            val k = kakaoPlacesFallback("주차장", lng, lat)
            return JSONObject().put("parking_lots", k).put("count", k.length()).put("source", "kakao")
        }
        val arr = JSONArray()
        lots.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("address", p.address)
                put("lat", p.lat)
                put("lng", p.lng)
                put("total_spaces", p.totalSpaces)
                put("fee_info", p.feeInfo)
                put("type", p.type)
            })
        }
        return JSONObject().put("parking_lots", arr).put("count", arr.length())
    }

    // ── Phase N6-N7 ──

    private suspend fun executeGetDirectionsNaver(args: Map<String, Any?>): JSONObject {
        if (apiKeyProvider.naverClientId.isBlank() || apiKeyProvider.naverClientSecret.isBlank()) {
            return suggestSkillResponse(
                "naver_directions",
                "네이버 경로를 사용하려면 설정에서 네이버 API 키를 등록해주세요.",
                "premium"
            )
        }
        val originX = (args["origin_x"] as? Number)?.toDouble() ?: return JSONObject().put("error", "origin_x 필요")
        val originY = (args["origin_y"] as? Number)?.toDouble() ?: return JSONObject().put("error", "origin_y 필요")
        val destX = (args["dest_x"] as? Number)?.toDouble() ?: return JSONObject().put("error", "dest_x 필요")
        val destY = (args["dest_y"] as? Number)?.toDouble() ?: return JSONObject().put("error", "dest_y 필요")
        val option = args["option"]?.toString() ?: "traoptimal"

        val result = naverDirectionsApi.getDirections(originX, originY, destX, destY, option)
            ?: return JSONObject().put("error", "네이버 API 키가 없거나 경로를 찾을 수 없습니다")

        // get_directions와 동일한 routes[].coords/guides 형식으로 wrap → 지도 렌더 + normalizeResponse 병합.
        // (이전엔 flat 필드만 반환해 네이버 경로가 지도에 안 그려졌음)
        val coordsArr = JSONArray()
        result.coords.forEach { c -> coordsArr.put(JSONObject().put("lat", c.lat).put("lng", c.lng)) }
        val guidesArr = JSONArray()
        result.guides.forEach { g ->
            guidesArr.put(JSONObject().apply {
                put("name", g.name); put("guidance", g.guidance); put("type", g.type)
                put("lat", g.lat); put("lng", g.lng); put("distance", g.distance); put("duration", g.duration)
            })
        }
        val route = JSONObject().apply {
            put("distance_m", result.distanceM)
            put("duration_s", result.durationMs / 1000)  // naver는 ms → get_directions와 동일하게 초로
            put("summary", JSONObject().put("toll_fare", result.tollFare).put("taxi_fare", result.taxiFare).put("fuel_price", result.fuelPrice))
            put("coords", coordsArr)
            put("guides", guidesArr)
        }
        return JSONObject().put("routes", JSONArray().put(route)).put("provider", "naver")
    }

    private suspend fun executeGetTransitRoute(args: Map<String, Any?>): JSONObject {
        if (apiKeyProvider.odsayKey.isBlank()) {
            return suggestSkillResponse(
                "odsay",
                "대중교통 경로를 사용하려면 설정에서 ODsay API 키를 등록해주세요.",
                "premium"
            )
        }
        // 이름 파라미터 → 좌표 자동 변환 (Gemini가 역 이름으로 호출할 때)
        suspend fun resolveCoord(nameKey: String, xKey: String, yKey: String): Pair<Double, Double>? {
            val name = args[nameKey]?.toString()
            val x = (args[xKey] as? Number)?.toDouble()
            val y = (args[yKey] as? Number)?.toDouble()
            if (x != null && y != null) return x to y
            if (!name.isNullOrBlank()) {
                val places = kakaoLocalApi.searchKeyword(name, size = 1)
                val first = places.firstOrNull() ?: return null
                return first.lng to first.lat
            }
            return null
        }
        val (startX, startY) = resolveCoord("origin_name", "start_x", "start_y")
            ?: return JSONObject().put("error", "출발지 좌표 또는 이름 필요")
        val (endX, endY) = resolveCoord("dest_name", "end_x", "end_y")
            ?: return JSONObject().put("error", "도착지 좌표 또는 이름 필요")
        val searchType = (args["search_type"] as? Number)?.toInt() ?: 0

        val routes = odsayApi.searchTransitPath(startX, startY, endX, endY, searchType)
        if (routes.isEmpty()) return JSONObject().put("error", "ODsay 키가 없거나 대중교통 경로를 찾을 수 없습니다")

        val arr = JSONArray()
        routes.forEach { r ->
            val stepsArr = JSONArray()
            r.steps.forEach { s ->
                stepsArr.put(JSONObject().apply {
                    put("mode", s.mode)
                    put("line", s.lineName)
                    put("start", s.startName)
                    put("end", s.endName)
                    put("stations", s.stationCount)
                    put("time_min", s.sectionTime)
                })
            }
            arr.put(JSONObject().apply {
                put("total_time_min", r.totalTime)
                put("transfer_count", r.transferCount)
                put("fare", r.fare)
                put("type", r.pathType)
                put("steps", stepsArr)
            })
        }
        return JSONObject().put("routes", arr).put("count", arr.length())
    }

    // ── Tier 1: 생활 편의 Tool 실행 ──

    private suspend fun executeGetAirQuality(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse(
                "air_quality",
                "미세먼지 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.",
                "public_data"
            )
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: 0.0
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: 0.0
        val result = airKoreaApi.getAirQuality(lat, lng)
        if (result.khaiGrade == "서비스 미신청") {
            // "error" 키를 쓰면 FallbackStrategy가 가로채 일반 폴백으로 바뀜 → status로 전달해 사유를 살림
            return JSONObject()
                .put("status", "not_subscribed")
                .put("message", "현재 공공데이터포털 키가 '대기오염정보(에어코리아)' 서비스에 미신청 상태예요. data.go.kr에서 해당 API를 추가 신청하면 미세먼지를 조회할 수 있어요.")
                .put("guidance", "위 message 사유를 사용자에게 그대로 안내할 것")
        }
        return JSONObject().apply {
            put("station_name", result.stationName)
            put("pm10", result.pm10 ?: JSONObject.NULL)
            put("pm25", result.pm25 ?: JSONObject.NULL)
            put("khai_value", result.khaiValue ?: JSONObject.NULL)
            put("khai_grade", result.khaiGrade)
            put("co", result.coValue ?: JSONObject.NULL)
            put("o3", result.o3Value ?: JSONObject.NULL)
            put("data_time", result.dataTime)
        }
    }

    private suspend fun executeSearchKnowledge(args: Map<String, Any?>): JSONObject {
        val query = args["query"] as? String ?: return JSONObject().put("error", "query 필수")
        val result = wikipediaApi.search(query)
        // 위키가 비면(예: "에펠탑 높이" 구문 검색 실패) 모델이 '찾을 수 없습니다'로 막다른 답을
        // 하던 문제 → 일반 상식은 자기 지식으로 폴백하도록 힌트. (세션 초반 '330m'는 됐듯 모델은 안다.)
        // miss 판별: hit이면 title=article 제목(non-blank), miss/실패면 title="" (summary엔
        // "찾지 못했습니다" 메시지가 차 있어 title로만 판별해야 한다).
        val empty = result.title.isBlank()
        return JSONObject().apply {
            if (empty) {
                put("found", false)
                put("query", query)
                put("hint", "위키 검색 결과가 없습니다. '$query'가 일반 상식(수도·높이·인물·정의 등)이면 " +
                    "도구 없이 당신의 지식으로 바로 답하세요 — '찾을 수 없습니다'로 끝내지 마세요. " +
                    "최신·세부 수치가 꼭 필요하면 더 짧은 핵심어(예: '에펠탑 높이'→'에펠탑')로 한 번만 재검색하세요.")
            } else {
                put("found", true)
                put("title", result.title)
                put("summary", result.summary)
                put("thumbnail_url", result.thumbnailUrl ?: JSONObject.NULL)
                put("wiki_url", result.wikiUrl)
            }
        }
    }

    private suspend fun executeFetchUrl(args: Map<String, Any?>): JSONObject {
        val url = args["url"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return JSONObject().put("error", "url 필수")
        val maxChars = (args["max_chars"] as? Number)?.toInt() ?: 3000
        val r = webFetchApi.fetch(url, maxChars)
        return JSONObject().apply {
            put("url", r.url)
            put("title", r.title)
            put("content", r.content)
            put("truncated", r.truncated)
            put("length", r.length)
            put("source", "web")  // 외부 미신뢰 텍스트 표식(인젝션 방어 신호)
        }
    }

    private suspend fun executeGetExchangeRate(args: Map<String, Any?>): JSONObject {
        val base = args["base_currency"] as? String ?: "KRW"
        val target = args["target_currency"] as? String
        val result = exchangeRateApi.getRates(base, target)
        val ratesObj = JSONObject()
        // 사람이 읽는 방향을 명시 — LLM이 환율 방향을 뒤집어 "1달러당 0.0006원"처럼
        // 잘못 말하는 것 방지. base=KRW면 "1 {외화} = N원"으로 역산해서 제공.
        val readable = JSONObject()
        result.rates.forEach { (k, v) ->
            ratesObj.put(k, v)
            if (v > 0) {
                if (result.base.equals("KRW", ignoreCase = true)) {
                    readable.put("1 $k", "${Math.round(1.0 / v)} KRW")
                } else {
                    readable.put("1 ${result.base}", "${"%.2f".format(v)} $k")
                }
            }
        }
        return JSONObject().apply {
            put("base", result.base)
            put("rates", ratesObj)
            put("readable", readable)
            put("note", "readable 필드의 방향(1 외화 = N원)으로 안내할 것")
            put("last_update", result.lastUpdate)
        }
    }

    private suspend fun executeSearchMedical(
        args: Map<String, Any?>,
        context: AgentContext,
        type: String
    ): JSONObject {
        val label = if (type == "pharmacy") "약국" else "병원"
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: 0.0
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: 0.0
        val results = try { medicalApi.search(lat, lng, type) } catch (_: Exception) { emptyList() }
        // data.go.kr 미신청/빈 결과 → Kakao 폴백(약국 PM9/병원 HP8 = Kakao에 있음)
        if (results.isEmpty()) {
            val k = kakaoPlacesFallback(label, lng, lat)
            return JSONObject().put("${type}s", k).put("count", k.length())
                .put("summary", "${k.length()}개 $label 발견").put("source", "kakao")
        }
        val arr = JSONArray()
        results.forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("address", f.address)
                put("phone", f.phone)
                put("lat", f.lat)
                put("lng", f.lng)
                put("distance_m", f.distanceM)
            })
        }
        return JSONObject().put("${type}s", arr).put("count", arr.length())
            .put("summary", "${arr.length()}개 $label 발견")
    }

    // ── 실시간 교통 Tool 구현 6종 ──

    private suspend fun executeGetTrafficSpeed(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("traffic_speed", "교통 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 5000

        val segments = trafficInfoApi.getTrafficSpeed(lat, lng, radius)
        val arr = JSONArray()
        segments.forEach { s ->
            arr.put(JSONObject().apply {
                put("road_name", s.roadName)
                put("speed_kmh", s.speed)
                put("travel_time_s", s.travelTime)
                put("congestion", s.congestionLevel)
            })
        }
        return JSONObject().put("segments", arr).put("count", arr.length())
    }

    private suspend fun executeGetTrafficIncidents(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("traffic_incidents", "돌발상황 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 10000

        val incidents = trafficInfoApi.getIncidents(lat, lng, radius)
        val arr = JSONArray()
        incidents.forEach { inc ->
            arr.put(JSONObject().apply {
                put("type", inc.type)
                put("description", inc.description)
                put("road_name", inc.roadName)
                put("lat", inc.lat)
                put("lng", inc.lng)
                put("start_time", inc.startTime)
                if (inc.detourInfo != null) put("detour_info", inc.detourInfo)
            })
        }
        return JSONObject().put("incidents", arr).put("count", arr.length())
    }

    private suspend fun executeGetTrafficCctv(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("traffic_cctv", "CCTV 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 5000

        val cctvs = trafficInfoApi.getCctvInfo(lat, lng, radius)
        val arr = JSONArray()
        cctvs.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.cctvName)
                put("lat", c.lat)
                put("lng", c.lng)
                if (c.imageUrl != null) put("image_url", c.imageUrl)
                if (c.videoUrl != null) put("video_url", c.videoUrl)
            })
        }
        return JSONObject().put("cctvs", arr).put("count", arr.length())
    }

    private suspend fun executeGetHighwayAlerts(args: Map<String, Any?>): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("highway_alerts", "고속도로 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val routeName = args["route_name"]?.toString()

        val alerts = highwayApi.getHighwayAlerts(routeName)
        val arr = JSONArray()
        alerts.forEach { a ->
            arr.put(JSONObject().apply {
                put("route_name", a.routeName)
                put("direction", a.direction)
                put("message", a.message)
                put("congestion", a.congestionLevel)
                put("event_type", a.eventType)
            })
        }
        return JSONObject().put("alerts", arr).put("count", arr.length())
    }

    private suspend fun executeGetRealtimeParking(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("realtime_parking", "실시간 주차 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 3000

        val lots = realtimeParkingApi.getRealtimeParking(lat, lng, radius)
        val arr = JSONArray()
        lots.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("address", p.address)
                put("lat", p.lat)
                put("lng", p.lng)
                put("capacity", p.capacity)
                put("available", p.availableSpaces)
                put("fee_info", p.feeInfo)
                put("update_time", p.updateTime)
            })
        }
        return JSONObject().put("parking_lots", arr).put("count", arr.length())
    }

    private suspend fun executeGetRoadRisk(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("road_risk", "도로 위험도 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 5000

        val risks = roadRiskApi.getRoadRisk(lat, lng, radius)
        val arr = JSONArray()
        risks.forEach { r ->
            arr.put(JSONObject().apply {
                put("segment_name", r.segmentName)
                put("risk_level", r.riskLevel)
                put("accident_count", r.accidentCount)
                put("death_count", r.deathCount)
                put("lat", r.lat)
                put("lng", r.lng)
                put("description", r.description)
            })
        }
        return JSONObject().put("risk_zones", arr).put("count", arr.length())
    }

    // ── Phase 7 내비게이션 Tool 구현 4종 ──

    private suspend fun executeGetSpeedCameras(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("speed_cameras", "단속 카메라 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 5000

        val cameras = speedCameraApi.getCamerasNear(lat, lng)
        val arr = JSONArray()
        cameras.forEach { c ->
            arr.put(JSONObject().apply {
                put("lat", c.lat)
                put("lng", c.lng)
                put("limit_speed", c.limitSpeed)
                put("type", c.type)
                if (c.sectionLengthM != null) put("section_length_m", c.sectionLengthM)
            })
        }
        return JSONObject().put("cameras", arr).put("count", arr.length())
    }

    private suspend fun executeGetRoadIncidents(args: Map<String, Any?>, context: AgentContext): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("road_incidents", "돌발상황 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val lat = (args["lat"] as? Number)?.toDouble() ?: context.locationY ?: return JSONObject().put("error", "위치 필요")
        val lng = (args["lng"] as? Number)?.toDouble() ?: context.locationX ?: return JSONObject().put("error", "위치 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 10000

        val incidents = incidentApi.getIncidents(lat, lng, radius)
        val arr = JSONArray()
        incidents.forEach { inc ->
            arr.put(JSONObject().apply {
                put("type", inc.type)
                put("description", inc.description)
                put("lat", inc.lat)
                put("lng", inc.lng)
                put("route_name", inc.routeName)
                put("start_time", inc.startTime)
                if (inc.expectedEndTime != null) put("expected_end_time", inc.expectedEndTime)
            })
        }
        return JSONObject().put("incidents", arr).put("count", arr.length())
    }

    private suspend fun executeGetRestAreas(args: Map<String, Any?>): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("rest_areas", "휴게소 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val routeName = args["route_name"]?.toString()

        val areas = restAreaApi.getRestAreas(routeName)
        val arr = JSONArray()
        areas.forEach { ra ->
            arr.put(JSONObject().apply {
                put("name", ra.name)
                put("lat", ra.lat)
                put("lng", ra.lng)
                put("route_name", ra.routeName)
                put("direction", ra.direction)
                put("has_gas_station", ra.hasGasStation)
                put("has_ev_charger", ra.hasEvCharger)
                put("has_restaurant", ra.hasRestaurant)
                put("has_convenience_store", ra.hasConvenienceStore)
            })
        }
        return JSONObject().put("rest_areas", arr).put("count", arr.length())
    }

    private suspend fun executeSuggestParking(args: Map<String, Any?>): JSONObject {
        if (apiKeyProvider.dataGoKrKey.isBlank()) {
            return suggestSkillResponse("suggest_parking", "주차장 정보를 사용하려면 설정에서 공공데이터포털 API 키를 등록해주세요.", "public_data")
        }
        val destLat = (args["dest_lat"] as? Number)?.toDouble() ?: return JSONObject().put("error", "dest_lat 필요")
        val destLng = (args["dest_lng"] as? Number)?.toDouble() ?: return JSONObject().put("error", "dest_lng 필요")
        val radius = (args["radius"] as? Number)?.toInt() ?: 500

        val lots = parkingApi.getParkingLots(destLat, destLng, radius)
        if (lots.isEmpty()) return JSONObject().put("message", "주변 500m 내 주차장이 없습니다.")

        val arr = JSONArray()
        lots.take(3).forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("address", p.address)
                put("lat", p.lat)
                put("lng", p.lng)
                put("fee_info", p.feeInfo)
                put("type", p.type)
            })
        }
        return JSONObject().put("recommended_lots", arr).put("count", arr.length())
            .put("summary", "${arr.length()}개 주차장 추천 (목적지 ${radius}m 내)")
    }

    // ── 일정·전화·알람·즐겨찾기 Tool 구현 ──

    private suspend fun executeCreateSchedule(args: Map<String, Any?>): JSONObject {
        // title만 진짜 필수(의미의 핵심). 못 채우면 그것만 되묻는다(needs_user_input).
        val title = args["title"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return JSONObject().put("error", "어떤 일정인가요?").put("needs_user_input", true)
        // 날짜·시간은 하드 실패 금지(엔티티상 둘 다 nullable) — 맥락으로 채운다.
        // 날짜 미지정/상대표현("오늘/내일/모레/금요일") → 오늘 기준 절대날짜로 결정론 해석.
        val resolved = resolveScheduleDate(args["date"]?.toString())
        val date = resolved.date
        val time = args["time"]?.toString()?.takeIf { it.isNotBlank() }  // 미지정 = 종일(null 허용)
        val location = args["location"]?.toString()
        val lat = (args["lat"] as? Number)?.toDouble()
        val lng = (args["lng"] as? Number)?.toDouble()

        val entity = ScheduleEntity(
            title = title,
            date = date,
            time = time,
            destinationName = location,
            destinationLat = lat,
            destinationLng = lng,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        val newId = scheduleDao.insert(entity)
        // 가정한 값(날짜·시간)을 투명하게 알려, 에이전트가 응답에 명시하게 한다(되묻기 대신).
        val assumed = JSONArray()
        resolved.assumedNote?.let { assumed.put("date=$date ($it)") }
        if (time == null) assumed.put("time=종일(시간 미지정)")
        return JSONObject().apply {
            put("success", true)
            put("id", newId)
            put("title", title)
            put("date", date)
            put("time", time ?: "")
            if (location != null) put("location", location)
            if (assumed.length() > 0) put("assumed", assumed)
        }
    }

    /** create_schedule 날짜 해석 결과. assumedNote != null이면 가정한 값(응답에 명시 대상). */
    private data class ResolvedDate(val date: String, val assumedNote: String?)

    /**
     * 일정 날짜를 오늘 기준 절대날짜(yyyy-MM-dd)로 결정론 해석.
     * 미지정→오늘, "오늘/내일/모레/글피", "(이번주/다음주) X요일", "M월 D일", 절대날짜 처리.
     * LLM의 날짜·요일 환각을 막기 위해 핸들러에서 계산한다.
     */
    private fun resolveScheduleDate(raw: String?): ResolvedDate {
        val today = java.time.LocalDate.now()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        if (raw.isNullOrBlank()) return ResolvedDate(today.format(fmt), "오늘")
        val s = raw.trim()
        runCatching { return ResolvedDate(java.time.LocalDate.parse(s).format(fmt), null) }
        val rel = when {
            s.contains("모레") || s.contains("내일모레") -> today.plusDays(2)
            s.contains("글피") -> today.plusDays(3)
            s.contains("내일") -> today.plusDays(1)
            s.contains("오늘") -> today
            else -> null
        }
        if (rel != null) return ResolvedDate(rel.format(fmt), null)
        koreanDayOfWeek(s)?.let { dow ->
            var d = today.plusDays(1)
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            if (s.contains("다음주") || s.contains("담주")) d = d.plusWeeks(1)
            return ResolvedDate(d.format(fmt), null)
        }
        Regex("""(\d{1,2})\s*월\s*(\d{1,2})\s*일""").find(s)?.let { m ->
            runCatching {
                val cand = java.time.LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                val fixed = if (cand.isBefore(today)) cand.plusYears(1) else cand
                return ResolvedDate(fixed.format(fmt), null)
            }
        }
        // 해석 실패: 오늘로 두되 추측임을 명시(명시했으나 못 알아들은 케이스 — 투명 고지).
        return ResolvedDate(today.format(fmt), "오늘 — 날짜를 정확히 못 알아들었어요")
    }

    private fun koreanDayOfWeek(s: String): java.time.DayOfWeek? = when {
        s.contains("월요") -> java.time.DayOfWeek.MONDAY
        s.contains("화요") -> java.time.DayOfWeek.TUESDAY
        s.contains("수요") -> java.time.DayOfWeek.WEDNESDAY
        s.contains("목요") -> java.time.DayOfWeek.THURSDAY
        s.contains("금요") -> java.time.DayOfWeek.FRIDAY
        s.contains("토요") -> java.time.DayOfWeek.SATURDAY
        s.contains("일요") -> java.time.DayOfWeek.SUNDAY
        else -> null
    }

    private suspend fun executeUpdateSchedule(args: Map<String, Any?>): JSONObject {
        val id = ((args["id"] as? Number)?.toLong() ?: (args["id"] as? String)?.toLongOrNull())
            ?: return JSONObject().put("error", "id 필요")
        val schedules = scheduleDao.getActive()
        val target = schedules.find { it.id == id }
            ?: return JSONObject().put("error", "해당 일정을 찾을 수 없습니다 (id=$id)")

        val updated = target.copy(
            title = args["title"]?.toString() ?: target.title,
            date = args["date"]?.toString() ?: target.date,
            time = args["time"]?.toString() ?: target.time,
            destinationName = args["location"]?.toString() ?: target.destinationName
        )
        scheduleDao.insert(updated)
        return JSONObject().put("success", true).put("updated_id", id)
    }

    private suspend fun executeDeleteSchedule(args: Map<String, Any?>): JSONObject {
        val id = ((args["id"] as? Number)?.toLong() ?: (args["id"] as? String)?.toLongOrNull())
            ?: return JSONObject().put("error", "id 필요")
        val schedules = scheduleDao.getActive()
        val target = schedules.find { it.id == id }
            ?: return JSONObject().put("error", "해당 일정을 찾을 수 없습니다 (id=$id)")

        scheduleDao.delete(target)
        return JSONObject().put("success", true).put("deleted_id", id)
    }

    private suspend fun executeMakeCall(args: Map<String, Any?>): JSONObject {
        val name = args["name"]?.toString() ?: return JSONObject().put("error", "name 필요")
        val directPhone = args["phone"]?.toString()

        // 기기+Room 실 연락처 우선(lookup_contact와 동일 소스) → 기기에만 있는 연락처도 전화 가능.
        val phone = if (!directPhone.isNullOrBlank()) directPhone
            else resolveContactPhone(name)?.second ?: ""

        return JSONObject().apply {
            put("name", name)
            put("phone", phone)
            if (phone.isBlank()) {
                put("status", "missing_phone")
                put("note", "전화번호가 등록되어 있지 않습니다. 연락처를 먼저 등록해주세요.")
            } else {
                put("status", "ready")
                put("note", "사용자 확인 후 전화를 겁니다.")
            }
        }
    }

    private fun executeSetAlarm(args: Map<String, Any?>): JSONObject {
        val hour = (args["hour"] as? Number)?.toInt() ?: return JSONObject().put("error", "hour 필요")
        val minute = (args["minute"] as? Number)?.toInt() ?: return JSONObject().put("error", "minute 필요")
        val message = args["message"]?.toString() ?: "EZmap 알람"

        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            return JSONObject().put("error", "알람 설정 실패: ${e.message}")
        }

        return JSONObject().apply {
            put("success", true)
            put("hour", hour)
            put("minute", minute)
            put("message", message)
        }
    }

    private suspend fun executeManageFavorites(args: Map<String, Any?>): JSONObject {
        val action = args["action"]?.toString() ?: return JSONObject().put("error", "action 필요")

        return when (action) {
            "list" -> {
                val places = frequentPlaceDao.getTopPlaces(20)
                val arr = JSONArray()
                places.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("address", p.address)
                        put("lat", p.lat)
                        put("lng", p.lng)
                        put("visit_count", p.visitCount)
                    })
                }
                JSONObject().put("favorites", arr).put("count", arr.length())
            }
            "add" -> {
                val name = args["name"]?.toString() ?: return JSONObject().put("error", "name 필요")
                val lat = (args["lat"] as? Number)?.toDouble() ?: 0.0
                val lng = (args["lng"] as? Number)?.toDouble() ?: 0.0
                val address = args["address"]?.toString() ?: ""

                frequentPlaceDao.upsert(FrequentPlaceEntity(
                    name = name,
                    address = address,
                    lat = lat,
                    lng = lng,
                    visitCount = 1,
                    lastVisited = System.currentTimeMillis()
                ))
                JSONObject().put("success", true).put("name", name)
            }
            "delete" -> {
                val name = args["name"]?.toString() ?: return JSONObject().put("error", "name 필요")
                val found = frequentPlaceDao.findByName(name).firstOrNull()
                    ?: return JSONObject().put("error", "해당 즐겨찾기를 찾을 수 없습니다.")
                frequentPlaceDao.delete(found)
                JSONObject().put("success", true)
            }
            else -> JSONObject().put("error", "알 수 없는 action: $action")
        }
    }
}
