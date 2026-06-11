package com.example.ez_capstone.api

import android.util.Log
import android.util.LruCache
import com.example.ez_capstone.agent.models.GeoResult
import com.example.ez_capstone.agent.models.PlaceResult
import com.example.ez_capstone.agent.models.ReverseGeoResult
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카카오 로컬 REST API 직접 호출.
 * 장소 검색, 지오코딩, 역지오코딩.
 */
@Singleton
class KakaoLocalApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "KakaoLocalApi"
        private const val BASE = "https://dapi.kakao.com/v2/local"
    }

    private val client = OkHttpClient()
    private val geocodeCache = LruCache<String, GeoResult>(50)

    private fun authHeader() = "KakaoAK ${apiKeyProvider.activeKakaoKey}"

    /** 키워드 장소 검색 */
    suspend fun searchKeyword(
        query: String,
        x: Double? = null,
        y: Double? = null,
        radius: Int = 20000,
        categoryGroupCode: String? = null,
        sort: String? = null,
        size: Int = 5
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        val url = "$BASE/search/keyword.json".toHttpUrl().newBuilder().apply {
            addQueryParameter("query", query)
            addQueryParameter("size", size.toString())
            x?.let { addQueryParameter("x", it.toString()) }
            y?.let { addQueryParameter("y", it.toString()) }
            if (x != null && y != null) addQueryParameter("radius", radius.toString())
            categoryGroupCode?.let { addQueryParameter("category_group_code", it) }
            sort?.let { addQueryParameter("sort", it) }
        }.build()

        val request = Request.Builder().url(url).header("Authorization", authHeader()).build()
        // 중앙 헬퍼: HTTP 실패/네트워크를 분류해 던짐(silent failure 방지). 200+빈결과는 정당.
        val body = client.getBody(request)

        val json = JSONObject(body)
        val documents = json.optJSONArray("documents") ?: return@withContext emptyList()

        (0 until documents.length()).map { i ->
            val doc = documents.getJSONObject(i)
            PlaceResult(
                name = doc.optString("place_name", ""),
                address = doc.optString("address_name", ""),
                category = doc.optString("category_group_name", ""),
                lat = doc.optString("y", "0").toDoubleOrNull() ?: 0.0,
                lng = doc.optString("x", "0").toDoubleOrNull() ?: 0.0,
                distanceM = doc.optString("distance", "0").toIntOrNull(),
                phone = doc.optString("phone", "").ifBlank { null }
            )
        }
    }

    /** 주소 → 좌표 (지오코딩) */
    suspend fun geocodeAddress(address: String): GeoResult = withContext(Dispatchers.IO) {
        geocodeCache.get(address)?.let { return@withContext it }

        val url = "$BASE/search/address.json".toHttpUrl().newBuilder()
            .addQueryParameter("query", address)
            .build()

        val request = Request.Builder().url(url).header("Authorization", authHeader()).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Geocode: empty response")

        val json = JSONObject(body)
        val documents = json.optJSONArray("documents")

        if (documents == null || documents.length() == 0) {
            // fallback: 키워드 검색으로 시도
            return@withContext geocodePlace(address)
        }

        val doc = documents.getJSONObject(0)
        val result = GeoResult(
            address = doc.optString("address_name", address),
            lat = doc.optString("y", "0").toDouble(),
            lng = doc.optString("x", "0").toDouble()
        )
        geocodeCache.put(address, result)
        result
    }

    /** 장소명 → 좌표 (키워드 검색 기반 fallback) */
    suspend fun geocodePlace(placeName: String): GeoResult {
        // 상위 N개 중 랜드마크 우선 선택. 첫 결과만 취하면 "울산대학교"가 "울산대학교점"(상호),
        // "울산역"이 엉뚱한 POI로 잡힘 → 정확 이름일치 > 쿼리 포함 중 최단 이름 > 첫 결과.
        val places = searchKeyword(placeName, size = 15)
        if (places.isEmpty()) throw Exception("장소를 찾을 수 없습니다: $placeName")
        val q = placeName.trim()
        val p = places.firstOrNull { it.name.trim() == q }
            ?: places.filter { it.name.contains(q) }.minByOrNull { it.name.length }
            ?: places.first()
        Log.d(TAG, "geocodePlace('$placeName') → '${p.name}' (${p.lat},${p.lng}) [${places.size}건 중]")
        val result = GeoResult(address = p.address, lat = p.lat, lng = p.lng)
        geocodeCache.put(placeName, result)
        return result
    }

    /** 좌표 → 주소 (역지오코딩) */
    suspend fun reverseGeocode(x: Double, y: Double): ReverseGeoResult = withContext(Dispatchers.IO) {
        val url = "$BASE/geo/coord2address.json".toHttpUrl().newBuilder()
            .addQueryParameter("x", x.toString())
            .addQueryParameter("y", y.toString())
            .build()

        val request = Request.Builder().url(url).header("Authorization", authHeader()).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext ReverseGeoResult(null, null)

        val json = JSONObject(body)
        val documents = json.optJSONArray("documents")
        if (documents == null || documents.length() == 0) {
            return@withContext ReverseGeoResult(null, null)
        }

        val doc = documents.getJSONObject(0)
        ReverseGeoResult(
            address = doc.optJSONObject("address")?.optString("address_name"),
            roadAddress = doc.optJSONObject("road_address")?.optString("address_name")
        )
    }
}
