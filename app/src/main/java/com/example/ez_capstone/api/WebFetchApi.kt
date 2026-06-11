package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.agent.FailureKind
import com.example.ez_capstone.agent.ToolFailureException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * fetch_url Tool — 임의 URL의 웹페이지를 온디바이스에서 가져와 본문 텍스트로 변환.
 *
 * 설계 원칙:
 * - 서버리스: 키 불필요, 디바이스가 직접 HTTP GET.
 * - silent fail 박멸: 실패는 중앙 [getBody] → [ToolFailureException]으로 분류·surface.
 * - 보안: 웹 본문은 '지시'가 아니라 '데이터'다. script/style 제거 + 태그 제거 + 길이 제한.
 *   사설/로컬 호스트는 차단(SSRF 위생).
 */
@Singleton
class WebFetchApi @Inject constructor() {

    companion object {
        private const val TAG = "WebFetchApi"
        private const val USER_AGENT =
            "EZmap/1.0 (on-device navigation agent; +https://github.com/cjw0076/Ezmap-server)"
        private const val DEFAULT_MAX_CHARS = 3000
        private const val HARD_MAX_CHARS = 6000
        private const val MAX_BODY_CHARS = 2_000_000  // 파싱 전 본문 상한(메모리 보호)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class FetchResult(
        val url: String,
        val title: String,
        val content: String,
        val truncated: Boolean,
        val length: Int
    )

    suspend fun fetch(url: String, maxChars: Int = DEFAULT_MAX_CHARS): FetchResult =
        withContext(Dispatchers.IO) {
            val normalized = normalizeAndValidate(url)
            val req = Request.Builder()
                .url(normalized)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .build()

            val raw = client.getBody(req)  // 실패(HTTP/네트워크/빈응답) 시 ToolFailureException
            val capped = if (raw.length > MAX_BODY_CHARS) raw.substring(0, MAX_BODY_CHARS) else raw

            val title = extractTitle(capped)
            val text = htmlToText(capped)
            val limit = maxChars.coerceIn(200, HARD_MAX_CHARS)
            val truncated = text.length > limit
            Log.d(TAG, "fetched ${normalized.take(80)} → ${text.length}자 (truncated=$truncated)")
            FetchResult(
                url = normalized,
                title = title,
                content = if (truncated) text.substring(0, limit) + "…" else text,
                truncated = truncated,
                length = text.length
            )
        }

    /** http/https 강제 + 사설/로컬 호스트 차단. */
    private fun normalizeAndValidate(url: String): String {
        val u = url.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        val host = try {
            java.net.URI(u).host?.lowercase()
        } catch (e: Exception) {
            throw ToolFailureException(FailureKind.UPSTREAM, "잘못된 URL: $url")
        } ?: throw ToolFailureException(FailureKind.UPSTREAM, "호스트를 해석할 수 없습니다: $url")

        val secondOctet = host.split(".").getOrNull(1)?.toIntOrNull()
        val blocked = host == "localhost" || host.endsWith(".local") ||
            host == "0.0.0.0" ||
            host.startsWith("127.") || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.startsWith("169.254.") ||
            (host.startsWith("172.") && secondOctet != null && secondOctet in 16..31)
        if (blocked) throw ToolFailureException(FailureKind.UPSTREAM, "접근 불가 호스트: $host")
        return u
    }

    private fun extractTitle(html: String): String {
        val m = Regex("<title[^>]*>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        return m?.groupValues?.get(1)?.let { decodeEntities(it).trim() }?.take(200) ?: ""
    }

    /** HTML → 평문. 파서 라이브러리 없이 정규식으로 (온디바이스, 의존성 최소). */
    private fun htmlToText(html: String): String {
        var s = html
        // script/style/noscript/template 블록 통째 제거 (인젝션 표면 축소)
        s = s.replace(Regex("(?is)<(script|style|noscript|template)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?is)<!--.*?-->"), " ")
        // 블록 종료 태그 → 줄바꿈
        s = s.replace(Regex("(?i)</(p|div|br|li|tr|h[1-6]|section|article)\\s*>"), "\n")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        // 나머지 태그 제거
        s = s.replace(Regex("(?s)<[^>]+>"), " ")
        s = decodeEntities(s)
        // 공백 정리
        s = s.replace(Regex("[ \\t\\x0B\\u000C]+"), " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s.trim()
    }

    private fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&apos;", "'")
}
