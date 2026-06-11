package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.config.ApiKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 오피넷 주유소 API.
 * Tool: get_gas_stations
 * 반경 내 최저가 주유소 검색, 유종별 가격 비교.
 */
@Singleton
class OpinetApi @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "OpinetApi"
        private const val BASE = "http://www.opinet.co.kr/api"

        /**
         * Opinet aroundAll.do는 WGS84가 아닌 KATEC (GRS80 TM central belt) 좌표를 요구.
         * KATEC 파라미터: 중부원점 128°E, 위도원점 38°N, 가성X=400000, 가성Y=600000, 축척=1.0
         */
        private val E2 = run { val f = 1.0/298.257222101; 2*f - f*f }
        private val LON0 = Math.toRadians(128.0)
        private val LAT0 = Math.toRadians(38.0)
        private const val FE = 400_000.0
        private const val FN = 600_000.0
        private const val A  = 6_378_137.0

        private fun meridionalArc(phi: Double): Double {
            val e4 = E2*E2; val e6 = e4*E2
            return A * (
                (1 - E2/4 - 3*e4/64 - 5*e6/256) * phi
                - (3*E2/8 + 3*e4/32 + 45*e6/1024) * sin(2*phi)
                + (15*e4/256 + 45*e6/1024) * sin(4*phi)
                - (35*e6/3072) * sin(6*phi)
            )
        }

        /** WGS84 (lat, lng) → KATEC (x, y) */
        fun wgs84ToKatec(lat: Double, lng: Double): Pair<Double, Double> {
            val phi = Math.toRadians(lat); val lam = Math.toRadians(lng)
            val sinPhi = sin(phi); val cosPhi = cos(phi); val tanPhi = tan(phi)
            val N = A / sqrt(1 - E2 * sinPhi * sinPhi)
            val T = tanPhi * tanPhi
            val C = (E2/(1-E2)) * cosPhi * cosPhi
            val Av = cosPhi * (lam - LON0)
            val M = meridionalArc(phi); val M0 = meridionalArc(LAT0)
            val x = N*(Av + (1-T+C)*Av.pow(3)/6 + (5-18*T+T*T+72*C-58*(E2/(1-E2)))*Av.pow(5)/120) + FE
            val y = (M - M0 + N*tanPhi*(Av*Av/2 + (5-T+9*C+4*C*C)*Av.pow(4)/24 + (61-58*T+T*T+600*C-330*(E2/(1-E2)))*Av.pow(6)/720)) + FN
            return Pair(x, y)
        }

        /** KATEC (x, y) → WGS84 (lat, lng) */
        fun katecToWgs84(x: Double, y: Double): Pair<Double, Double> {
            val M0 = meridionalArc(LAT0)
            val M1 = M0 + (y - FN)
            val e4 = E2*E2; val e6 = e4*E2
            val n  = (A - A*sqrt(1-E2)) / (A + A*sqrt(1-E2))
            val n2 = n*n; val n3 = n*n2; val n4 = n2*n2
            val mu1 = M1 / (A*(1 - E2/4 - 3*e4/64 - 5*e6/256))
            val phi1 = mu1 +
                (3*n/2  - 27*n3/32)   * sin(2*mu1) +
                (21*n2/16 - 55*n4/32) * sin(4*mu1) +
                (151*n3/96)            * sin(6*mu1) +
                (1097*n4/512)          * sin(8*mu1)
            val sinPhi1 = sin(phi1); val cosPhi1 = cos(phi1); val tanPhi1 = tan(phi1)
            val N1 = A / sqrt(1 - E2*sinPhi1*sinPhi1)
            val R1 = A*(1-E2) / (1 - E2*sinPhi1*sinPhi1).pow(1.5)
            val T1 = tanPhi1*tanPhi1; val C1 = (E2/(1-E2))*cosPhi1*cosPhi1
            val D  = (x - FE) / N1
            val lat = phi1 - (N1*tanPhi1/R1)*(
                D*D/2 - (5+3*T1+10*C1-4*C1*C1-9*(E2/(1-E2)))*D.pow(4)/24 +
                (61+90*T1+298*C1+45*T1*T1-252*(E2/(1-E2))-3*C1*C1)*D.pow(6)/720
            )
            val lon = LON0 + (D - (1+2*T1+C1)*D.pow(3)/6 + (5-2*C1+28*T1-3*C1*C1+8*(E2/(1-E2))+24*T1*T1)*D.pow(5)/120)/cosPhi1
            return Pair(Math.toDegrees(lat), Math.toDegrees(lon))
        }
    }

    private val client = OkHttpClient()

    data class GasStation(
        val name: String,
        val brand: String,
        val price: Int,
        val lat: Double,
        val lng: Double,
        val address: String = "",
        val hasSelfService: Boolean = false
    )

    /**
     * 반경 내 주유소 검색 (가격순).
     * @param fuelType B034=휘발유, D047=경유, K015=LPG
     * @param sort 1=가격순, 2=거리순
     */
    suspend fun getGasStations(
        lat: Double, lng: Double,
        radius: Int = 5000,
        fuelType: String = "B034",
        sort: Int = 1
    ): List<GasStation> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.opinetKey
        if (key.isBlank()) {
            Log.w(TAG, "Opinet API key not configured — gas station prices unavailable")
            throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.MISSING_KEY, "오피넷(주유소) API 키 없음")
        }

        try {
            // aroundAll.do는 WGS84가 아닌 KATEC 좌표를 입력으로 요구
            val (katecX, katecY) = wgs84ToKatec(lat, lng)
            val url = "$BASE/aroundAll.do".toHttpUrl().newBuilder().apply {
                addQueryParameter("code", key)
                addQueryParameter("x", "%.0f".format(katecX))
                addQueryParameter("y", "%.0f".format(katecY))
                addQueryParameter("radius", radius.toString())
                addQueryParameter("sort", sort.toString())
                addQueryParameter("prodcd", fuelType)
                addQueryParameter("out", "json")
            }.build()

            Log.d(TAG, "Fetching gas stations lat=$lat lng=$lng → KATEC(${katecX.toInt()},${katecY.toInt()}) radius=${radius}m fuel=$fuelType")
            val request = Request.Builder().url(url).build()
            val body = client.getBody(request)  // 중앙 헬퍼: HTTP 실패/네트워크 분류해 던짐
            Log.d(TAG, "Opinet response (${body.length}B): ${body.take(120)}")

            val json = JSONObject(body)
            val result = json.optJSONObject("RESULT") ?: run {
                Log.e(TAG, "No RESULT object in response")
                return@withContext emptyList()
            }
            val oils = result.optJSONArray("OIL") ?: run {
                Log.w(TAG, "No OIL array — possibly no stations in radius")
                return@withContext emptyList()
            }

            Log.d(TAG, "Found ${oils.length()} stations")
            (0 until oils.length()).mapNotNull { i ->
                runCatching {
                    val oil = oils.getJSONObject(i)
                    val price = oil.optInt("PRICE", 0)
                    // 출력 좌표도 KATEC → WGS84 변환 필요
                    val katecStationX = oil.optDouble("GIS_X_COOR", 0.0)
                    val katecStationY = oil.optDouble("GIS_Y_COOR", 0.0)
                    if (price <= 0 || katecStationX == 0.0 || katecStationY == 0.0) return@runCatching null
                    val (stationLat, stationLng) = katecToWgs84(katecStationX, katecStationY)
                    GasStation(
                        name = oil.optString("OS_NM", "주유소"),
                        brand = brandName(oil.optString("POLL_DIV_CD", "")),
                        price = price,
                        lat = stationLat,
                        lng = stationLng,
                        address = oil.optString("UNI_ADDR", "").ifBlank { oil.optString("NEW_ADR", "") },
                        hasSelfService = oil.optString("SELF_YN", "N") == "Y"
                    )
                }.getOrNull()
            }.take(10)
        } catch (e: com.example.ez_capstone.agent.ToolFailureException) {
            throw e  // 분류된 실패 전파
        } catch (e: Exception) {
            Log.e(TAG, "주유소 검색 실패: ${e.message}")
            throw com.example.ez_capstone.agent.ToolFailureException(
                com.example.ez_capstone.agent.FailureKind.fromThrowable(e), e.message)
        }
    }

    /**
     * 전국 최저가 Top10에서 브랜드별 대표가 추출.
     * aroundAll.do가 데모 키로 빈 결과를 반환할 때 가격 참조용.
     * @return 브랜드코드 → 최저 가격 (원/ℓ)
     */
    suspend fun getBrandPrices(fuelType: String = "B034"): Map<String, Int> = withContext(Dispatchers.IO) {
        val key = apiKeyProvider.opinetKey
        if (key.isBlank()) return@withContext emptyMap()
        try {
            val url = "$BASE/lowTop10.do".toHttpUrl().newBuilder().apply {
                addQueryParameter("code", key)
                addQueryParameter("prodcd", fuelType)
                addQueryParameter("out", "json")
            }.build()
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string() ?: return@withContext emptyMap()
            val oils = JSONObject(body).optJSONObject("RESULT")?.optJSONArray("OIL")
                ?: return@withContext emptyMap()
            val map = mutableMapOf<String, Int>()
            for (i in 0 until oils.length()) {
                val o = oils.getJSONObject(i)
                val brand = o.optString("POLL_DIV_CD", "")
                val price = o.optInt("PRICE", 0)
                if (brand.isNotBlank() && price > 0) {
                    map[brand] = minOf(map.getOrDefault(brand, Int.MAX_VALUE), price)
                }
            }
            Log.d(TAG, "Brand prices: $map")
            map
        } catch (e: Exception) {
            Log.e(TAG, "getBrandPrices 실패: ${e.message}")
            emptyMap()
        }
    }

    fun brandCode(stationName: String): String = when {
        stationName.contains("SK") -> "SKE"
        stationName.contains("GS") -> "GSC"
        stationName.contains("현대") || stationName.contains("HD") -> "HDO"
        stationName.contains("S-OIL") || stationName.contains("에쓰오일") -> "SOL"
        else -> "ETC"
    }

    private fun brandName(code: String): String = when (code) {
        "SKE" -> "SK에너지"
        "GSC" -> "GS칼텍스"
        "HDO" -> "현대오일뱅크"
        "SOL" -> "S-OIL"
        "ETC" -> "기타"
        else -> code
    }
}
