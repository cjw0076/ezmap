package com.example.ez_capstone.api

import android.util.Log
import com.example.ez_capstone.config.SpotifyAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify Web API 클라이언트 — 음성 음악 제어.
 *
 * 재생 제어(play/pause/next/previous)는 Spotify Premium + 활성 기기가 필요하다.
 * 활성 기기가 없으면(앱이 어디서도 재생 중이 아니면) 404가 오므로, 사용자에게
 * "스포티파이 앱을 먼저 켜 주세요"로 안내한다.
 *
 * Tool: play_music, pause_music, next_track, previous_track, current_track
 */
@Singleton
class SpotifyApi @Inject constructor(
    private val authManager: SpotifyAuthManager
) {
    companion object {
        private const val TAG = "SpotifyApi"
        private const val BASE = "https://api.spotify.com/v1"
        private val EMPTY_JSON = "{}".toRequestBody("application/json".toMediaType())
    }

    private val client = OkHttpClient()

    /** 모든 결과의 공통 형태 — ok=성공, message=사용자 안내, track=현재/재생 곡(있으면). */
    data class SpotifyResult(
        val ok: Boolean,
        val message: String,
        val trackName: String? = null,
        val artist: String? = null
    )

    private suspend fun token(): String? = authManager.getValidAccessToken()

    private fun notConnected() =
        SpotifyResult(false, "스포티파이가 연결되지 않았어요. 설정에서 먼저 연결해 주세요.")

    /** 응답 코드별 공통 해석 (재생 제어 계열: 성공은 204 No Content). */
    private fun interpret(code: Int, body: String, successMsg: String): SpotifyResult = when (code) {
        in 200..204 -> SpotifyResult(true, successMsg)
        404 -> SpotifyResult(false, "재생 중인 기기가 없어요. 스포티파이 앱을 먼저 실행해 주세요.")
        403 -> SpotifyResult(false, "이 기능은 스포티파이 프리미엄에서만 동작해요.")
        401 -> SpotifyResult(false, "스포티파이 인증이 만료됐어요. 설정에서 다시 연결해 주세요.")
        else -> {
            Log.e(TAG, "Spotify error $code: ${body.take(150)}")
            SpotifyResult(false, "음악 제어에 실패했어요.")
        }
    }

    // ── 검색 후 재생 / 이어 재생 ────────────────────────────────────────────

    /**
     * query가 있으면 곡을 검색해 재생, 없으면 일시정지된 재생을 이어서 재생.
     */
    suspend fun play(query: String?): SpotifyResult = withContext(Dispatchers.IO) {
        val tk = token() ?: return@withContext notConnected()

        if (!query.isNullOrBlank()) {
            val track = searchTrack(tk, query)
                ?: return@withContext SpotifyResult(false, "\"$query\"를 찾지 못했어요.")
            val uri = track.optString("uri")
            val name = track.optString("name")
            val artist = track.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
            val bodyJson = JSONObject().put("uris", JSONArray().put(uri)).toString()
            val req = Request.Builder()
                .url("$BASE/me/player/play")
                .header("Authorization", "Bearer $tk")
                .put(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()
            return@withContext exec(req) {
                interpret(it.first, it.second, "${artist} - ${name} 재생할게요.")
                    .copy(trackName = name, artist = artist)
            }
        }

        // 이어 재생(resume)
        val req = Request.Builder()
            .url("$BASE/me/player/play")
            .header("Authorization", "Bearer $tk")
            .put(EMPTY_JSON)
            .build()
        exec(req) { interpret(it.first, it.second, "다시 재생할게요.") }
    }

    suspend fun pause(): SpotifyResult = withContext(Dispatchers.IO) {
        val tk = token() ?: return@withContext notConnected()
        val req = Request.Builder()
            .url("$BASE/me/player/pause")
            .header("Authorization", "Bearer $tk")
            .put(EMPTY_JSON)
            .build()
        exec(req) { interpret(it.first, it.second, "음악을 멈췄어요.") }
    }

    suspend fun next(): SpotifyResult = withContext(Dispatchers.IO) {
        val tk = token() ?: return@withContext notConnected()
        val req = Request.Builder()
            .url("$BASE/me/player/next")
            .header("Authorization", "Bearer $tk")
            .post(EMPTY_JSON)
            .build()
        exec(req) { interpret(it.first, it.second, "다음 곡으로 넘길게요.") }
    }

    suspend fun previous(): SpotifyResult = withContext(Dispatchers.IO) {
        val tk = token() ?: return@withContext notConnected()
        val req = Request.Builder()
            .url("$BASE/me/player/previous")
            .header("Authorization", "Bearer $tk")
            .post(EMPTY_JSON)
            .build()
        exec(req) { interpret(it.first, it.second, "이전 곡으로 돌아갈게요.") }
    }

    /** 현재 재생 중인 곡 조회 (READ). */
    suspend fun current(): SpotifyResult = withContext(Dispatchers.IO) {
        val tk = token() ?: return@withContext notConnected()
        val req = Request.Builder()
            .url("$BASE/me/player/currently-playing")
            .header("Authorization", "Bearer $tk")
            .get()
            .build()
        exec(req) { (code, body) ->
            when (code) {
                200 -> {
                    val item = JSONObject(body.ifBlank { "{}" }).optJSONObject("item")
                    if (item == null) SpotifyResult(false, "지금 재생 중인 곡이 없어요.")
                    else {
                        val name = item.optString("name")
                        val artist = item.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: ""
                        SpotifyResult(true, "지금 ${artist}의 ${name} 재생 중이에요.", name, artist)
                    }
                }
                204 -> SpotifyResult(false, "지금 재생 중인 곡이 없어요.")
                else -> interpret(code, body, "")
            }
        }
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private suspend fun searchTrack(token: String, query: String): JSONObject? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "1")
            .addQueryParameter("market", "KR")
            .build()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val items = JSONObject(resp.body?.string() ?: "{}")
                    .optJSONObject("tracks")?.optJSONArray("items") ?: return null
                if (items.length() == 0) null else items.getJSONObject(0)
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            null
        }
    }

    private inline fun exec(req: Request, map: (Pair<Int, String>) -> SpotifyResult): SpotifyResult =
        runCatching {
            client.newCall(req).execute().use { resp ->
                map(resp.code to (resp.body?.string() ?: ""))
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Log.e(TAG, "request error: ${it.message}")
            SpotifyResult(false, "네트워크 오류로 음악 제어에 실패했어요.")
        }
}
