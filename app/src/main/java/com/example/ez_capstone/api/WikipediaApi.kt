package com.example.ez_capstone.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wikipedia API — 키 불필요.
 * Tool: search_knowledge
 * 드라이브 중 "OO가 뭐야?" 에 위키백과 요약으로 답변.
 */
@Singleton
class WikipediaApi @Inject constructor() {

    companion object {
        private const val TAG = "WikipediaApi"
        private const val SEARCH_BASE = "https://ko.wikipedia.org/w/api.php"
        private const val SUMMARY_BASE = "https://ko.wikipedia.org/api/rest_v1/page/summary"
        // Wikimedia 정책: UA 없으면/일반적(okhttp)이면 차단됨(T400119) → 식별 가능한 UA 필수
        private const val USER_AGENT = "EZmap/1.0 (https://github.com/cjw0076/Ezmap-server; on-device navigation agent)"
    }

    private val client = OkHttpClient()

    data class KnowledgeResult(
        val title: String = "",
        val summary: String = "",
        val thumbnailUrl: String? = null,
        val wikiUrl: String = ""
    )

    suspend fun search(query: String): KnowledgeResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: OpenSearch로 가장 적합한 제목 찾기
            val searchUrl = SEARCH_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("action", "opensearch")
                .addQueryParameter("search", query)
                .addQueryParameter("limit", "3")
                .addQueryParameter("format", "json")
                .build()

            val searchReq = Request.Builder().url(searchUrl).header("User-Agent", USER_AGENT).build()
            val searchResp = client.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string() ?: return@withContext KnowledgeResult()

            val arr = JSONArray(searchBody)
            val titles = arr.optJSONArray(1)
            if (titles == null || titles.length() == 0) {
                return@withContext KnowledgeResult(summary = "'$query'에 대한 정보를 찾지 못했습니다.")
            }

            val bestTitle = titles.getString(0)

            // Step 2: 요약 페이지 가져오기
            val encoded = URLEncoder.encode(bestTitle, "UTF-8")
            val summaryUrl = "$SUMMARY_BASE/$encoded"
            val summaryReq = Request.Builder().url(summaryUrl).header("User-Agent", USER_AGENT).build()
            val summaryResp = client.newCall(summaryReq).execute()
            val summaryBody = summaryResp.body?.string() ?: return@withContext KnowledgeResult()

            val json = JSONObject(summaryBody)
            val extract = json.optString("extract", "")
            val thumbnail = json.optJSONObject("thumbnail")?.optString("source")
            val pageUrl = json.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page", "")

            KnowledgeResult(
                title = json.optString("title", bestTitle),
                summary = if (extract.length > 300) extract.substring(0, 300) + "..." else extract,
                thumbnailUrl = thumbnail,
                wikiUrl = pageUrl ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Wikipedia search failed", e)
            KnowledgeResult(summary = "지식 검색에 실패했습니다: ${e.message}")
        }
    }
}
