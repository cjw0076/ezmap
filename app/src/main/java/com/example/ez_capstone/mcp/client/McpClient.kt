package com.example.ez_capstone.mcp.client

import android.util.Log
import com.example.ez_capstone.agent.FailureKind
import com.example.ez_capstone.agent.ToolFailureException
import com.example.ez_capstone.mcp.McpTool
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP 클라이언트 — 외부 MCP 서버를 Streamable HTTP(JSON-RPC 2.0)로 호출.
 *
 * 서버리스 원칙 유지: EZmap 서버 없이, 앱이 외부 MCP 엔드포인트를 직접 호출(Kakao API와 동일).
 * 실패는 중앙 [ToolFailureException]으로 분류·전파(silent fail 방지 시스템 재사용).
 *
 * 핸드셰이크: initialize → (Mcp-Session-Id 헤더 캡처) → notifications/initialized → tools/list·tools/call.
 * 응답은 application/json 또는 text/event-stream(SSE 프레이밍) 둘 다 파싱.
 *
 * 블로킹 호출 — 반드시 IO 디스패처에서 사용(호출자: McpToolGateway가 withContext(IO)로 감쌈).
 */
@Singleton
class McpClient @Inject constructor() {

    companion object {
        private const val TAG = "McpClient"
        private const val PROTOCOL_VERSION = "2024-11-05"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        // AI 생성형 MCP 도구(DeepWiki ask_question 등)는 응답이 20초+ 걸려 25초로는 종종 타임아웃 →
        // 40초로 상향(느린 외부 도구 실패 방지).
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val nextId = AtomicInteger(1)

    // 서버별 세션 ID(Streamable HTTP). 핸드셰이크 후 캡처해 이후 요청에 재사용.
    private val sessions = ConcurrentHashMap<String, String>()

    /** 도구 목록 조회. 필요 시 핸드셰이크 자동 수행. */
    fun listTools(config: McpServerConfig): List<McpTool> {
        ensureHandshake(config)
        val result = rpc(config, "tools/list", JsonObject())
        val toolsArr = result?.getAsJsonArray("tools") ?: return emptyList()
        val out = ArrayList<McpTool>(toolsArr.size())
        for (el in toolsArr) {
            val o = el.asJsonObject
            val name = o.get("name")?.asString ?: continue
            val desc = o.get("description")?.asString ?: ""
            @Suppress("UNCHECKED_CAST")
            val schema = o.get("inputSchema")?.let {
                gson.fromJson(it, Map::class.java) as? Map<String, Any>
            } ?: emptyMap()
            out.add(McpTool(name, desc, schema))
        }
        Log.d(TAG, "[${config.id}] tools/list → ${out.size}개")
        return out
    }

    /** 도구 실행. 결과 content(text 연결) 반환. */
    fun callTool(config: McpServerConfig, toolName: String, args: Map<String, Any?>): String {
        ensureHandshake(config)
        val params = JsonObject().apply {
            addProperty("name", toolName)
            add("arguments", gson.toJsonTree(args))
        }
        val result = rpc(config, "tools/call", params)
            ?: throw ToolFailureException(FailureKind.UPSTREAM, "MCP 빈 결과")
        if (result.get("isError")?.asBoolean == true) {
            throw ToolFailureException(FailureKind.UPSTREAM, "MCP 도구 오류: ${extractText(result).take(160)}")
        }
        return extractText(result)
    }

    /** initialize + notifications/initialized (세션 없을 때만). */
    private fun ensureHandshake(config: McpServerConfig) {
        if (sessions.containsKey(config.id)) return
        val initParams = JsonObject().apply {
            addProperty("protocolVersion", PROTOCOL_VERSION)
            add("capabilities", JsonObject())
            add("clientInfo", JsonObject().apply {
                addProperty("name", "EZmap"); addProperty("version", "1.0.0")
            })
        }
        rpc(config, "initialize", initParams, allowHandshake = true)
        // 세션 헤더가 없던 서버라도 빈 문자열로 마킹해 재핸드셰이크 루프 방지.
        sessions.putIfAbsent(config.id, sessions[config.id] ?: "")
        // initialized 알림(응답 없음). 실패해도 무시.
        runCatching { notify(config, "notifications/initialized") }
    }

    /** JSON-RPC 요청/응답. result 객체(JsonObject) 반환. 실패 시 ToolFailureException. */
    private fun rpc(
        config: McpServerConfig,
        method: String,
        params: JsonObject,
        allowHandshake: Boolean = false
    ): JsonObject? {
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", nextId.getAndIncrement())
            addProperty("method", method)
            add("params", params)
        }
        val (body, sessionId) = post(config, payload.toString())
        if (allowHandshake && !sessionId.isNullOrBlank()) sessions[config.id] = sessionId

        val json = extractJson(body)
            ?: throw ToolFailureException(FailureKind.UPSTREAM, "MCP 응답 파싱 실패")
        val obj = JsonParser.parseString(json).asJsonObject
        obj.getAsJsonObject("error")?.let { err ->
            throw ToolFailureException(
                FailureKind.UPSTREAM,
                "MCP ${err.get("code")?.asString}: ${err.get("message")?.asString}"
            )
        }
        return obj.getAsJsonObject("result")
    }

    /** 응답 불필요한 notification 전송(best-effort). */
    private fun notify(config: McpServerConfig, method: String) {
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
            add("params", JsonObject())
        }
        post(config, payload.toString())
    }

    /** HTTP POST. (body, Mcp-Session-Id 헤더) 반환. 실패 시 ToolFailureException. */
    private fun post(config: McpServerConfig, jsonBody: String): Pair<String, String?> {
        val builder = Request.Builder()
            .url(config.url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        if (config.authToken.isNotBlank()) builder.header("Authorization", "Bearer ${config.authToken}")
        sessions[config.id]?.takeIf { it.isNotBlank() }?.let { builder.header("Mcp-Session-Id", it) }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: java.io.IOException) {
            throw ToolFailureException(FailureKind.NETWORK, "MCP 연결 실패: ${e.message}")
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ToolFailureException(
                    FailureKind.fromHttp(resp.code),
                    "MCP HTTP ${resp.code}: ${text.take(120)}"
                )
            }
            return text to resp.header("Mcp-Session-Id")
        }
    }

    /** application/json 또는 SSE(text/event-stream) 본문에서 JSON-RPC 객체 추출. */
    private fun extractJson(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("{")) return t
        // SSE 프레이밍: "data: {...}" 라인들 중 마지막 유효 JSON.
        val dataLines = t.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.startsWith("{") }
            .toList()
        return dataLines.lastOrNull()
    }

    /** tools/call result → content[].text 연결. */
    private fun extractText(result: JsonObject): String {
        val content = result.getAsJsonArray("content") ?: return result.toString().take(2000)
        val sb = StringBuilder()
        for (el in content) {
            val o = el.asJsonObject
            when (o.get("type")?.asString) {
                "text" -> sb.append(o.get("text")?.asString ?: "")
                else -> o.get("text")?.asString?.let { sb.append(it) }
            }
            sb.append('\n')
        }
        return sb.toString().trim().ifBlank { result.toString().take(2000) }
    }

    /** 설정 변경 시 세션 무효화(다음 호출에 재핸드셰이크). */
    fun invalidate(serverId: String) {
        sessions.remove(serverId)
    }
}
