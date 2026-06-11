package com.example.ez_capstone.skill

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 슬롯 정의 — Gemini Tool 호출 시 치환할 변수.
 */
data class SlotDef(val name: String, val type: String) {
    companion object {
        fun fromJson(obj: JSONObject) = SlotDef(
            name = obj.getString("name"),
            type = obj.getString("type")
        )
    }
}

/**
 * Tool 호출 단위 — toolChain의 원소.
 * params는 JSON object로 보관 (실행 시점에 슬롯 치환).
 */
data class ToolCall(val tool: String, val paramsJson: String) {
    fun paramsObject(): JSONObject = JSONObject(paramsJson)

    companion object {
        fun fromJson(obj: JSONObject) = ToolCall(
            tool = obj.getString("tool"),
            paramsJson = obj.getJSONObject("params").toString()
        )
    }
}

/**
 * 스켈레톤 Skill 템플릿 — assets/skill_templates.json에서 로드.
 * 사용자 첫 발화 매칭 시 2회 반복 조건 면제하여 LearnedSkill로 즉시 승격.
 */
data class SkillTemplate(
    val id: String,
    val version: Int,
    val intent: String,
    val fingerprint: String,
    val canonicalUtterance: String,
    val slotSchema: List<SlotDef>,
    val toolChain: List<ToolCall>,
    val replyTemplate: String?,
    val ttsTemplate: String?,
    val maxToolRiskTier: String,
    val sensitivity: String,
    val initialConfidence: Float,
    val policy: SkillPolicy? = null
) {
    /** toolChain 전체를 JSON 문자열로 직렬화 (LearnedSkillEntity 저장용) */
    fun toolChainJson(): String {
        val arr = JSONArray()
        toolChain.forEach { tc ->
            arr.put(JSONObject().apply {
                put("tool", tc.tool)
                put("params", JSONObject(tc.paramsJson))
            })
        }
        return arr.toString()
    }

    /** slotSchema를 JSON 문자열로 직렬화 */
    fun slotSchemaJson(): String {
        val arr = JSONArray()
        slotSchema.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("type", s.type)
            })
        }
        return arr.toString()
    }
}

/**
 * 앱 시작 시 1회 assets/skill_templates.json을 파싱하여 in-memory 인덱스 유지.
 *
 * 사용처:
 * - SkillMatcher: fingerprint 매칭 시 LearnedSkill hit 없으면 Template 후보 검색
 * - SkillLearner: Template 매칭 성공 시 LearnedSkill로 즉시 승격 (2회 조건 면제)
 */
@Singleton
class SkillTemplateLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SkillTemplateLoader"
        private const val ASSET_PATH = "skill_templates.json"
    }

    private val mutex = Mutex()
    @Volatile private var cached: List<SkillTemplate>? = null
    @Volatile private var byFingerprint: Map<String, SkillTemplate> = emptyMap()

    /**
     * 모든 템플릿 로드 (idempotent). 첫 호출 시 파싱, 이후는 캐시 반환.
     */
    suspend fun loadAll(): List<SkillTemplate> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                runCatching { parseAssets() }
                    .onSuccess { Log.d(TAG, "Loaded ${it.size} skill templates") }
                    .onFailure { Log.e(TAG, "Template load failed: ${it.message}") }
                    .getOrDefault(emptyList())
                    .also {
                        cached = it
                        byFingerprint = it.associateBy { t -> t.fingerprint }
                    }
            }
        }
    }

    /** 정확 fingerprint 매칭 (SkillMatcher hot path) */
    fun findByFingerprint(fingerprint: String): SkillTemplate? = byFingerprint[fingerprint]

    /** 전체 리스트 (loadAll() 호출 이후) */
    fun all(): List<SkillTemplate> = cached ?: emptyList()

    /** 테스트용: 수동으로 템플릿 세트를 대체 */
    internal fun setForTest(templates: List<SkillTemplate>) {
        cached = templates
        byFingerprint = templates.associateBy { it.fingerprint }
    }

    private fun parseAssets(): List<SkillTemplate> {
        val jsonText = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("templates") ?: return emptyList()
        val out = mutableListOf<SkillTemplate>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            runCatching { parseTemplate(obj) }
                .onSuccess { out.add(it) }
                .onFailure { Log.w(TAG, "Skip malformed template[$i]: ${it.message}") }
        }
        return out
    }

    private fun parseTemplate(obj: JSONObject): SkillTemplate {
        val slotArr = obj.optJSONArray("slotSchema") ?: JSONArray()
        val slots = (0 until slotArr.length()).map { SlotDef.fromJson(slotArr.getJSONObject(it)) }
        val chainArr = obj.getJSONArray("toolChain")
        val chain = (0 until chainArr.length()).map { ToolCall.fromJson(chainArr.getJSONObject(it)) }
        return SkillTemplate(
            id = obj.getString("id"),
            version = obj.optInt("version", 1),
            intent = obj.optString("intent", "GENERAL"),
            fingerprint = obj.getString("fingerprint"),
            canonicalUtterance = obj.getString("canonicalUtterance"),
            slotSchema = slots,
            toolChain = chain,
            replyTemplate = obj.optString("replyTemplate").takeIf { it.isNotEmpty() },
            ttsTemplate = obj.optString("ttsTemplate").takeIf { it.isNotEmpty() },
            maxToolRiskTier = obj.optString("maxToolRiskTier", "SAFE"),
            sensitivity = obj.optString("sensitivity", "PUBLIC"),
            initialConfidence = obj.optDouble("initialConfidence", 0.80).toFloat(),
            policy = parsePolicy(obj.optJSONObject("policy"))
        )
    }

    private fun parsePolicy(obj: JSONObject?): SkillPolicy? {
        obj ?: return null
        val driving = obj.optJSONObject("driving")?.let {
            DrivingPolicy(
                allowedWhileDriving = it.optBoolean("allowedWhileDriving", false),
                requiresParked = it.optBoolean("requiresParked", false),
                rationale = it.optString("rationale").takeIf { text -> text.isNotBlank() }
            )
        }
        val confirmation = obj.optJSONObject("confirmation")?.let {
            ConfirmationPolicy(
                required = it.optBoolean("required", true),
                prompt = it.optString("prompt").takeIf { text -> text.isNotBlank() }
            )
        }
        val fallback = obj.optJSONObject("fallback")?.let {
            FallbackPolicy(
                userMessage = it.optString("userMessage"),
                retryable = it.optBoolean("retryable", true)
            )
        }
        return SkillPolicy(driving = driving, confirmation = confirmation, fallback = fallback)
    }
}
