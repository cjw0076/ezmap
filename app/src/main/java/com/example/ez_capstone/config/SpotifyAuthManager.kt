package com.example.ez_capstone.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spotify OAuth 2.0 Authorization Code + PKCE 인증 관리.
 *
 * 서버리스 온디바이스 원칙 유지 — 백엔드 없이 앱이 직접 Spotify 인증 서버와 토큰을 교환한다.
 * PKCE(Public client)이므로 client secret이 필요 없고 client ID만으로 동작한다.
 *
 * 흐름:
 *  1. buildAuthUrl() → Custom Tab으로 사용자 로그인/동의
 *  2. redirect(ezmap://spotify/callback?code=...) → SpotifyAuthActivity가 exchangeCode() 호출
 *  3. 토큰을 EncryptedSharedPreferences에 저장, 만료 시 refresh로 자동 갱신
 */
@Singleton
class SpotifyAuthManager @Inject constructor(
    @ApplicationContext context: Context,
    private val apiKeyProvider: ApiKeyProvider
) {
    companion object {
        private const val TAG = "SpotifyAuth"
        const val REDIRECT_URI = "ezmap://spotify/callback"
        private const val AUTH_BASE = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        // 재생 제어 + 현재 곡 조회에 필요한 최소 스코프
        private const val SCOPES =
            "user-read-playback-state user-modify-playback-state user-read-currently-playing"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "ezmap_spotify", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedSharedPreferences 초기화 실패, 평문 폴백", e)
        context.getSharedPreferences("ezmap_spotify_fallback", Context.MODE_PRIVATE)
    }

    private val client = OkHttpClient()
    private val refreshMutex = Mutex()

    /** 인증 완료(refresh token 보유) 여부 — 설정 UI에서 연결 상태 표시용. */
    val isConnected: Boolean
        get() = prefs.getString("refresh_token", "").isNullOrBlank().not()

    /** buildAuthUrl()에서 저장한 CSRF 방지용 state. redirect 검증에 사용. */
    fun savedAuthState(): String = prefs.getString("auth_state", "") ?: ""

    // ── 1단계: 인증 URL 생성 (Custom Tab으로 열기) ──────────────────────────

    /**
     * PKCE code_verifier를 새로 생성·저장하고 인증 URL을 반환.
     * @return 인증 URL. clientId 미설정이면 null.
     */
    fun buildAuthUrl(): String? {
        val clientId = apiKeyProvider.spotifyClientId
        if (clientId.isBlank()) return null

        val verifier = generateCodeVerifier()
        prefs.edit().putString("code_verifier", verifier).apply()
        val challenge = codeChallenge(verifier)
        val state = generateCodeVerifier().take(16)
        prefs.edit().putString("auth_state", state).apply()

        return AUTH_BASE.toHttpUrlBuilder(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "code_challenge_method" to "S256",
            "code_challenge" to challenge,
            "scope" to SCOPES,
            "state" to state
        )
    }

    // ── 2단계: 인가 코드 → 토큰 교환 ────────────────────────────────────────

    /**
     * redirect로 받은 code를 access/refresh 토큰으로 교환.
     * @return 성공 여부
     */
    suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val clientId = apiKeyProvider.spotifyClientId
        val verifier = prefs.getString("code_verifier", "") ?: ""
        if (clientId.isBlank() || verifier.isBlank()) return@withContext false

        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()

        runCatching {
            client.newCall(Request.Builder().url(TOKEN_URL).post(form).build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Token exchange failed ${resp.code}: ${body.take(200)}")
                    return@use false
                }
                storeTokens(JSONObject(body))
                // 일회성 값 정리(재사용 방지)
                prefs.edit().remove("code_verifier").remove("auth_state").apply()
                true
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Log.e(TAG, "exchangeCode error: ${it.message}")
            false
        }
    }

    // ── 토큰 조회(자동 갱신) ────────────────────────────────────────────────

    /**
     * 유효한 access token 반환. 만료 60초 전이면 refresh로 자동 갱신.
     * 미연결이거나 갱신 실패면 null.
     */
    suspend fun getValidAccessToken(): String? = refreshMutex.withLock {
        val access = prefs.getString("access_token", "") ?: ""
        val expiresAt = prefs.getLong("expires_at", 0L)
        if (access.isNotBlank() && System.currentTimeMillis() < expiresAt - 60_000L) {
            return@withLock access
        }
        refreshAccessToken()
    }

    private suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val clientId = apiKeyProvider.spotifyClientId
        val refresh = prefs.getString("refresh_token", "") ?: ""
        if (clientId.isBlank() || refresh.isBlank()) return@withContext null

        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()

        runCatching {
            client.newCall(Request.Builder().url(TOKEN_URL).post(form).build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Token refresh failed ${resp.code}: ${body.take(200)}")
                    return@use null
                }
                storeTokens(JSONObject(body))
                prefs.getString("access_token", null)
            }
        }.getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Log.e(TAG, "refresh error: ${it.message}")
            null
        }
    }

    /** 연결 해제 — 저장 토큰·일회성 값 삭제. */
    fun disconnect() {
        prefs.edit().remove("access_token").remove("refresh_token")
            .remove("expires_at").remove("code_verifier").remove("auth_state").apply()
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private fun storeTokens(json: JSONObject) {
        val access = json.optString("access_token", "")
        val expiresIn = json.optInt("expires_in", 3600)
        val refresh = json.optString("refresh_token", "")  // refresh 시엔 없을 수 있음
        prefs.edit().apply {
            if (access.isNotBlank()) putString("access_token", access)
            putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000L)
            if (refresh.isNotBlank()) putString("refresh_token", refresh)
        }.apply()
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun String.toHttpUrlBuilder(vararg params: Pair<String, String>): String {
        val sb = StringBuilder(this).append('?')
        params.forEachIndexed { i, (k, v) ->
            if (i > 0) sb.append('&')
            sb.append(k).append('=').append(java.net.URLEncoder.encode(v, "UTF-8"))
        }
        return sb.toString()
    }
}
