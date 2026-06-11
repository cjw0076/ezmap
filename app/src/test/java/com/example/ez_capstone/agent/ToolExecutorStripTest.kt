package com.example.ez_capstone.agent

import android.content.Context
import com.example.ez_capstone.api.*
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.*
import com.example.ez_capstone.profile.MultiProfileManager
import com.google.gson.Gson
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ToolExecutor.stripForGemini() — 순수 JSONObject 변환 로직 테스트.
 * 네트워크/DB 의존성 없음. 모든 의존성은 relaxed mock.
 */
class ToolExecutorStripTest {

    private lateinit var executor: ToolExecutor

    @Before
    fun setUp() {
        executor = ToolExecutor(
            kakaoLocalApi = mockk(relaxed = true),
            kakaoMobilityApi = mockk(relaxed = true),
            weatherApi = mockk(relaxed = true),
            opinetApi = mockk(relaxed = true),
            evChargerApi = mockk(relaxed = true),
            kmaWeatherApi = mockk(relaxed = true),
            parkingApi = mockk(relaxed = true),
            naverDirectionsApi = mockk(relaxed = true),
            odsayApi = mockk(relaxed = true),
            airKoreaApi = mockk(relaxed = true),
            wikipediaApi = mockk(relaxed = true),
            webFetchApi = mockk(relaxed = true),
            exchangeRateApi = mockk(relaxed = true),
            medicalApi = mockk(relaxed = true),
            trafficInfoApi = mockk(relaxed = true),
            highwayApi = mockk(relaxed = true),
            realtimeParkingApi = mockk(relaxed = true),
            roadRiskApi = mockk(relaxed = true),
            speedCameraApi = mockk(relaxed = true),
            incidentApi = mockk(relaxed = true),
            restAreaApi = mockk(relaxed = true),
            spotifyApi = mockk(relaxed = true),
            apiKeyProvider = mockk(relaxed = true),
            profileDao = mockk(relaxed = true),
            preferenceDao = mockk(relaxed = true),
            routeHistoryDao = mockk(relaxed = true),
            contactDao = mockk(relaxed = true),
            scheduleDao = mockk(relaxed = true),
            frequentPlaceDao = mockk(relaxed = true),
            agentNoteDao = mockk(relaxed = true),
            gson = Gson(),
            multiProfileManager = mockk(relaxed = true),
            appContext = mockk(relaxed = true),
            mcpGateway = mockk(relaxed = true)
        )
    }

    @Test
    fun `get_directions strip removes coords and keeps distance_m and duration_s`() {
        val route = JSONObject().apply {
            put("distance_m", 5000)
            put("duration_s", 600)
            put("summary", "강남대로")
            put("coords", JSONArray().put(JSONObject().put("lat", 37.5).put("lng", 127.0)))
            put("guides", JSONArray().put(JSONObject().put("name", "직진")))
        }
        val input = JSONObject().put("routes", JSONArray().put(route))

        val stripped = executor.stripForGemini("get_directions", input)

        val routes = stripped.getJSONArray("routes")
        assertEquals(1, routes.length())
        val r = routes.getJSONObject(0)
        assertEquals(5000, r.getInt("distance_m"))
        assertEquals(600, r.getInt("duration_s"))
        assertEquals("강남대로", r.getString("summary"))
        assertFalse("coords 필드가 제거돼야 함", r.has("coords"))
        assertFalse("guides 필드가 제거돼야 함", r.has("guides"))
    }

    @Test
    fun `get_transit_route strip keeps total_time_min transfer_count fare`() {
        val route = JSONObject().apply {
            put("total_time_min", 35)
            put("transfer_count", 1)
            put("fare", 1350)
            put("type", 1)
            put("steps", JSONArray().put("step1"))
        }
        val input = JSONObject().put("routes", JSONArray().put(route))

        val stripped = executor.stripForGemini("get_transit_route", input)

        val routes = stripped.getJSONArray("routes")
        val r = routes.getJSONObject(0)
        assertEquals(35, r.getInt("total_time_min"))
        assertEquals(1, r.getInt("transfer_count"))
        assertEquals(1350, r.getInt("fare"))
        assertFalse("steps 필드가 제거돼야 함", r.has("steps"))
    }

    @Test
    fun `search_knowledge strip truncates summary to 100 chars`() {
        val longSummary = "a".repeat(200)
        val input = JSONObject().apply {
            put("title", "테스트 항목")
            put("summary", longSummary)
        }

        val stripped = executor.stripForGemini("search_knowledge", input)

        assertEquals(100, stripped.getString("summary").length)
        assertEquals("테스트 항목", stripped.getString("title"))
    }

    @Test
    fun `get_gas_stations strip returns at most 3 items`() {
        val stations = JSONArray()
        repeat(10) { i -> stations.put(JSONObject().put("name", "주유소 $i")) }
        val input = JSONObject().put("stations", stations)

        val stripped = executor.stripForGemini("get_gas_stations", input)

        assertEquals(3, stripped.getJSONArray("stations").length())
        assertEquals(10, stripped.getInt("count"))
    }

    @Test
    fun `unknown tool name returns result unchanged`() {
        val input = JSONObject().put("foo", "bar")
        val stripped = executor.stripForGemini("unknown_tool_xyz", input)
        assertEquals("bar", stripped.getString("foo"))
    }
}
