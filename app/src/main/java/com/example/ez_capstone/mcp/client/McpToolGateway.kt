package com.example.ez_capstone.mcp.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.ez_capstone.agent.ToolExposure
import com.example.ez_capstone.agent.ToolExternalExposure
import com.example.ez_capstone.agent.ToolKind
import com.example.ez_capstone.agent.ToolSpec
import com.example.ez_capstone.safety.ToolRiskTier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP 도구 게이트웨이 — 외부 MCP 서버들의 도구를 에이전트에 "플러그인"으로 노출.
 *
 * 역할:
 * 1) 서버 설정 영속화(암호화 prefs — 토큰 포함).
 * 2) refresh(): 각 서버 tools/list → Gemini Function Calling 선언으로 변환·캐시.
 *    (도구 이름은 "mcp_<serverId>_<tool>"로 prefix → 25개 내장 도구와 충돌 방지 + 라우팅 키.)
 * 3) cachedToolDeclarations(): 캐시된 선언을 GeminiAgentEngine이 도구 배열에 병합.
 * 4) call(): Gemini가 mcp 도구를 호출하면 해당 서버로 라우팅.
 *
 * 캐시는 메모리(빠름) — 선언 조회는 네트워크 0. 네트워크는 addServer/refresh/call에서만.
 */
@Singleton
class McpToolGateway @Inject constructor(
    @ApplicationContext context: Context,
    private val client: McpClient
) {
    companion object {
        private const val TAG = "McpToolGateway"
        private const val KEY_SERVERS = "mcp_servers_json"
        private const val MAX_TOOLS = 32
        internal const val MAX_IMPORTED_TOOL_NAME_LENGTH = 63
        private val INVALID_FUNCTION_NAME_CHARS = Regex("[^a-zA-Z0-9_]")

        internal fun prefixedToolName(
            serverId: String,
            toolName: String,
            usedNames: Set<String> = emptySet()
        ): String {
            val sanitized = "mcp_${serverId}_$toolName".replace(INVALID_FUNCTION_NAME_CHARS, "_")
            val primary = sanitized.take(MAX_IMPORTED_TOOL_NAME_LENGTH)
            if (primary !in usedNames) return primary

            val hash = Integer.toUnsignedString(sanitized.hashCode(), 36).take(8)
            var counter = 1
            while (true) {
                val suffix = "_${hash}_$counter"
                val stemLength = (MAX_IMPORTED_TOOL_NAME_LENGTH - suffix.length).coerceAtLeast(1)
                val candidate = sanitized.take(stemLength) + suffix
                if (candidate !in usedNames) return candidate
                counter += 1
            }
        }

        // 외부 MCP 도구의 위험도를 이름+설명 휴리스틱으로 분류한다. 외부 도구는 ToolSpec
        // 메타데이터가 없어 과거엔 일괄 STATEFUL/WRITE(보수적이지만 부정확)였다. 실제 동작에
        // 가까운 risk를 부여해, 안전 게이트가 effectful 외부 도구만 정확히 차단하도록 한다.
        private val EFFECTFUL_HINTS = listOf(
            "send", "delete", "remove", "pay", "transfer", "purchase", "buy", "withdraw",
            "wire", "refund", "email", "sms", "checkout", "cancel", "publish", "deploy",
            "charge", "order", "revoke", "grant", "drop", "truncate"
        )
        private val READ_HINTS = listOf(
            "search", "get", "list", "read", "fetch", "query", "find", "view",
            "lookup", "describe", "show", "count", "browse"
        )

        internal fun classifyExternalRisk(name: String, description: String): ToolRiskTier {
            val hay = "$name $description".lowercase()
            if (EFFECTFUL_HINTS.any { hay.contains(it) }) return ToolRiskTier.EFFECTFUL
            if (READ_HINTS.any { hay.contains(it) }) return ToolRiskTier.SAFE
            return ToolRiskTier.STATEFUL  // 모호 → 보수적
        }

        internal fun importedToolSpec(prefixedName: String, declaration: JSONObject): ToolSpec {
            val parameters = JSONObject(
                (declaration.optJSONObject("parameters") ?: JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject())).toString()
            )
            if (!parameters.has("type")) parameters.put("type", "object")
            if (!parameters.has("properties")) parameters.put("properties", JSONObject())

            val description = declaration.optString("description", "Imported MCP tool").ifBlank {
                "Imported MCP tool"
            }
            val risk = classifyExternalRisk(prefixedName, description)
            val kind = when (risk) {
                ToolRiskTier.EFFECTFUL -> ToolKind.SENSITIVE
                ToolRiskTier.SAFE -> ToolKind.READ
                else -> ToolKind.WRITE
            }

            return ToolSpec(
                name = prefixedName,
                description = description,
                parameters = parameters,
                keyGroup = "mcp_external",
                kind = kind,
                riskTier = risk,
                exposures = setOf(ToolExposure.GEMINI_FUNCTION, ToolExposure.EXECUTOR),
                externalExposure = ToolExternalExposure.NONE
            )
        }
    }

    private val gson = Gson()

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "ezmap_mcp", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "EncryptedSharedPreferences 실패, 평문 폴백", e)
        context.getSharedPreferences("ezmap_mcp_fallback", Context.MODE_PRIVATE)
    }

    private data class Resolved(
        val config: McpServerConfig,
        val realName: String,
        val declaration: JSONObject,
        val spec: ToolSpec
    )

    // prefixedName → 라우팅 정보. 선언 병합과 호출 라우팅의 단일 진실원천.
    private val resolved = ConcurrentHashMap<String, Resolved>()

    // ── 설정 영속화 ──

    fun listServers(): List<McpServerConfig> = try {
        val json = prefs.getString(KEY_SERVERS, "[]") ?: "[]"
        gson.fromJson(json, object : TypeToken<List<McpServerConfig>>() {}.type) ?: emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "서버 목록 파싱 실패", e); emptyList()
    }

    private fun saveServers(servers: List<McpServerConfig>) {
        prefs.edit().putString(KEY_SERVERS, gson.toJson(servers)).commit()
    }

    /** 서버 등록/갱신. 기본 비활성 서버는 저장만 되고 도구 노출은 0개다. id 중복이면 교체. */
    suspend fun addServer(config: McpServerConfig): Result<Int> {
        val servers = listServers().filter { it.id != config.id } + config
        saveServers(servers)
        client.invalidate(config.id)
        return refreshServer(config)
    }

    fun removeServer(serverId: String) {
        saveServers(listServers().filter { it.id != serverId })
        client.invalidate(serverId)
        resolved.entries.removeIf { it.value.config.id == serverId }
    }

    /**
     * 서버 활성/비활성 — 핵심: 비활성 서버의 도구는 캐시(resolved)에서 빠져
     * system prompt(Gemini 도구 집합)에 주입되지 않는다. 도구 풀은 유지하되 켠 것만 노출.
     * 활성화 시 도구 새로고침, 비활성화 시 캐시에서 제거. 반환=활성 도구 수.
     */
    suspend fun setEnabled(serverId: String, enabled: Boolean): Result<Int> {
        val servers = listServers().map { if (it.id == serverId) it.copy(enabled = enabled) else it }
        saveServers(servers)
        client.invalidate(serverId)
        val cfg = servers.find { it.id == serverId } ?: return Result.success(0)
        // refreshServer가 enabled 분기 처리: enabled면 fetch, 아니면 resolved에서 제거 후 0 반환.
        return refreshServer(cfg)
    }

    // ── 도구 새로고침 ──

    /** 모든 활성 서버의 도구를 다시 가져와 캐시. 서버별 실패는 격리(다른 서버에 영향 X). */
    suspend fun refreshAll() {
        resolved.clear()
        listServers().filter { it.enabled }.forEach { runCatching { refreshServer(it) } }
    }

    /** 단일 서버 새로고침 → 등록된 도구 수 반환. */
    suspend fun refreshServer(config: McpServerConfig): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            resolved.entries.removeIf { it.value.config.id == config.id }
            if (!config.enabled) return@runCatching 0
            val tools = client.listTools(config).take(MAX_TOOLS)
            val usedNames = resolved.keys.toMutableSet()
            for (tool in tools) {
                val prefixed = prefixedToolName(config.id, tool.name, usedNames)
                usedNames.add(prefixed)
                val decl = JSONObject().apply {
                    put("name", prefixed)
                    put("description", "[${config.name}] ${tool.description}")
                    put("parameters", toGeminiSchema(tool.inputSchema))
                }
                resolved[prefixed] = Resolved(config, tool.name, decl, importedToolSpec(prefixed, decl))
            }
            Log.d(TAG, "[${config.id}] 도구 ${tools.size}개 병합")
            tools.size
        }
    }

    // ── 에이전트 연동 ──

    /** 캐시된 MCP 도구 선언(Gemini 형식). 비어있으면 빈 리스트. 네트워크 0(메모리). */
    fun cachedToolDeclarations(): List<JSONObject> = cachedToolSpecs().map { it.toFunctionDeclaration() }

    /** 캐시된 외부 MCP 도구를 ToolSpec 스냅샷으로 노출한다. 외부 재노출은 기본 차단된다. */
    fun cachedToolSpecs(): List<ToolSpec> = resolved.values.map { it.spec.copy() }

    fun isMcpTool(name: String): Boolean = resolved.containsKey(name)

    /** 캐시된 MCP 도구의 분류된 위험등급. 미상(MCP 도구 아님)이면 null. 안전 게이트가 참조. */
    fun riskTierOf(name: String): ToolRiskTier? = resolved[name]?.spec?.riskTier

    fun hasServers(): Boolean = listServers().any { it.enabled }

    /** 설정 UI용 — 서버별 캐시된 도구 수. */
    fun toolCount(serverId: String): Int = resolved.values.count { it.config.id == serverId }

    /** 설정 UI용 — 서버별 도구 이름 목록(원래 이름). */
    fun toolNames(serverId: String): List<String> =
        resolved.values.filter { it.config.id == serverId }.map { it.realName }

    /** Gemini가 호출한 mcp 도구를 해당 서버로 라우팅. 결과를 표준 JSON으로. */
    suspend fun call(name: String, args: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        val r = resolved[name]
            ?: return@withContext JSONObject().put("error", "알 수 없는 MCP 도구: $name")
        // 실패(ToolFailureException 등)는 던져서 ToolExecutor 중앙 catch가 분류·surface.
        val text = client.callTool(r.config, r.realName, args)
        JSONObject().apply {
            put("content", text)
            put("source", "mcp")
            put("server", r.config.name)
        }
    }

    // ── 헬퍼 ──

    /**
     * MCP inputSchema(JSON Schema) → Gemini parameters(OpenAPI 서브셋).
     * Gemini가 거부하는 키($schema, additionalProperties, title 등)를 재귀 제거.
     */
    private fun toGeminiSchema(inputSchema: Map<String, Any>): JSONObject {
        val sanitized = sanitize(inputSchema) as? Map<*, *> ?: emptyMap<String, Any>()
        val obj = JSONObject(gson.toJson(sanitized))
        if (!obj.has("type")) obj.put("type", "object")
        if (obj.optString("type") == "object" && !obj.has("properties")) {
            obj.put("properties", JSONObject())
        }
        return obj
    }

    private val allowedSchemaKeys =
        setOf("type", "description", "properties", "required", "items", "enum", "format", "nullable")

    private fun sanitize(node: Any?): Any? = when (node) {
        is Map<*, *> -> {
            // anyOf/oneOf/allOf → 첫 서브스키마로 collapse (Gemini는 union 스키마 미지원).
            val union = (node["anyOf"] ?: node["oneOf"] ?: node["allOf"]) as? List<*>
            if (union != null && union.isNotEmpty()) {
                val first = sanitize(union.first())
                @Suppress("UNCHECKED_CAST")
                if (first is MutableMap<*, *> && node["description"] is String &&
                    !first.containsKey("description")
                ) (first as MutableMap<String, Any?>)["description"] = node["description"]
                first
            } else {
                val out = LinkedHashMap<String, Any?>()
                for ((k, v) in node) {
                    if (k !is String || k !in allowedSchemaKeys) continue
                    out[k] = when (k) {
                        "properties" -> (v as? Map<*, *>)?.entries
                            ?.mapNotNull { (pk, pv) -> if (pk is String) pk to sanitize(pv) else null }
                            ?.toMap()
                        "items" -> sanitize(v)
                        else -> v
                    }
                }
                // 리프 프로퍼티가 타입을 잃었으면(union 제거 등) string 기본 — Gemini는 type 필수.
                if (!out.containsKey("type") && !out.containsKey("properties") && !out.containsKey("items")) {
                    out["type"] = "string"
                }
                out
            }
        }
        else -> node
    }
}
