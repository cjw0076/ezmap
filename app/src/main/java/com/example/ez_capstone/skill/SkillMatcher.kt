package com.example.ez_capstone.skill

import android.util.Log
import com.example.ez_capstone.offline.EmbeddingEngine
import com.example.ez_capstone.offline.VectorStore
import com.example.ez_capstone.safety.DrivingState
import com.example.ez_capstone.safety.SafetyContext
import com.example.ez_capstone.safety.SafetyPolicyEngine
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 매칭 결과 — LearnedSkill 또는 Template.
 */
data class SkillMatchResult(
    val source: Source,
    val learnedSkill: LearnedSkillEntity? = null,
    val template: SkillTemplate? = null,
    val slotValues: Map<String, String>,
    val score: Float,
    val fingerprint: String
) {
    enum class Source { LEARNED_SKILL, TEMPLATE }

    fun id(): String = learnedSkill?.id ?: template?.id ?: "unknown"
    fun toolChainJson(): String = learnedSkill?.toolChain ?: template?.toolChainJson() ?: "[]"
    fun replyTemplate(): String? = learnedSkill?.replyTemplate ?: template?.replyTemplate
    fun ttsTemplate(): String? = learnedSkill?.ttsTemplate ?: template?.ttsTemplate
    fun toolNames(): List<String> {
        val arr = JSONArray(toolChainJson())
        return (0 until arr.length()).map { arr.getJSONObject(it).optString("tool") }
    }
}

/**
 * L3 계층 — LearnedSkill + Template 매칭 엔진.
 *
 * 알고리즘:
 * 1. 발화 정규화 (어미/조사/공백/동의어)
 * 2. 토큰화
 * 3. fingerprint 정확 일치 검색 (hot path, <10ms)
 * 4. 후보 조회 (DAO + Template) 후 Jaccard+3-gram 하이브리드 유사도
 * 5. 슬롯 추출 (템플릿 정규식 기반)
 * 6. SafetyPolicyEngine.allowsLearnedSkill() 통과 여부 확인
 *
 * 실패 시 null 반환 → 호출자(GeminiAgentEngine)는 Gemini 경로로 폴백.
 */
@Singleton
class SkillMatcher @Inject constructor(
    private val dao: LearnedSkillDao,
    private val templateLoader: SkillTemplateLoader,
    private val safetyPolicy: SafetyPolicyEngine,
    private val embeddingEngine: EmbeddingEngine,
    private val vectorStore: VectorStore
) {
    companion object {
        private const val TAG = "SkillMatcher"

        /** 매칭 최소 유사도 임계 (confidence와 별개) */
        private const val MIN_SIMILARITY = 0.80f

        /** 후보 조회 범위 (lastUsedAt 기준 30일) */
        private const val CANDIDATE_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        /** Jaccard : 3-gram 가중치 (한국어 짧은 발화 특성) */
        private const val TOKEN_WEIGHT = 0.6f
        private const val NGRAM_WEIGHT = 0.4f

        /** 한글 어미 — 간단 제거 규칙 (과잉 매칭 방지 위해 최소만) */
        private val ENDING_REGEX = Regex("(자|아|어|요|세요|게요|아요|어요|하자|할게|할래|갈래|실래|가주세요|주세요|주십시오)$")

        /** 한글 조사 */
        private val PARTICLE_REGEX = Regex("(을|를|은|는|이|가|에서|에|으로|로|에게|께|한테|부터|까지|만|도|마저)$")

        /** 동의어 사전 — "표현" → "정규형" */
        private val SYNONYMS: Map<String, String> = mapOf(
            // 귀가/집
            "댁" to "집", "우리집" to "집", "집에" to "집",
            // 출근/회사
            "직장" to "회사", "사무실" to "회사", "일터" to "회사", "회사에" to "회사",
            // 근처/주변
            "주변" to "근처", "이근처" to "근처",
            // 들르다
            "들러서" to "들렀다", "들렀다가" to "들렀다", "들르고" to "들렀다",
            "가다가" to "들렀다",  // 의미 변경 주의 — 이 동의어는 보수적으로 제거하거나 별도 정책
            // 찾다
            "찾아" to "찾자", "찾아줘" to "찾자", "찾아봐" to "찾자", "알려줘" to "찾자"
        )

        /** 토큰 불용어 */
        private val STOPWORDS: Set<String> = setOf(
            "좀", "그", "그냥", "음", "어", "아", "그래", "네", "예"
        )
    }

    /**
     * 메인 매칭 함수.
     * @return 매칭된 Skill/Template + 슬롯 값, 실패 시 null
     */
    suspend fun tryMatch(userText: String, safetyCtx: SafetyContext): SkillMatchResult? {
        if (userText.isBlank()) return null

        val normalized = normalize(userText)
        val tokens = tokenize(normalized)
        if (tokens.isEmpty()) return null

        templateLoader.loadAll()  // 초기화 보장

        // ── 1) LearnedSkill 정확 fingerprint 매칭 (hot path) ──
        val exactFingerprint = tokens.joinToString(" ")
        dao.findByFingerprint(exactFingerprint)?.let { skill ->
            val slots = extractSlotsFromLearned(normalized, skill)
            if (passesSafetyGate(skill, slots, safetyCtx)) {
                Log.d(TAG, "L3 exact hit (learned): ${skill.id}")
                return SkillMatchResult(
                    source = SkillMatchResult.Source.LEARNED_SKILL,
                    learnedSkill = skill,
                    slotValues = slots,
                    score = 1.0f,
                    fingerprint = skill.fingerprint
                )
            }
        }

        // ── 2) LearnedSkill 유사도 매칭 (길이 ±2 필터) ──
        val now = System.currentTimeMillis()
        val candidates = dao.getCandidates(
            sinceMs = now - CANDIDATE_WINDOW_MS,
            minTokens = (tokens.size - 2).coerceAtLeast(1),
            maxTokens = tokens.size + 2,
            limit = 50
        )

        var bestLearned: Pair<LearnedSkillEntity, Float>? = null
        for (cand in candidates) {
            val sim = similarity(normalized, cand.fingerprint, cand.coreTokenSet)
            if (sim >= MIN_SIMILARITY && (bestLearned == null || sim > bestLearned.second)) {
                bestLearned = cand to sim
            }
        }

        if (bestLearned != null) {
            val (skill, sim) = bestLearned
            val slots = extractSlotsFromLearned(normalized, skill)
            if (passesSafetyGate(skill, slots, safetyCtx)) {
                Log.d(TAG, "L3 similarity hit (learned): ${skill.id} score=$sim")
                return SkillMatchResult(
                    source = SkillMatchResult.Source.LEARNED_SKILL,
                    learnedSkill = skill,
                    slotValues = slots,
                    score = sim,
                    fingerprint = skill.fingerprint
                )
            }
        }

        // ── 2-B) 벡터 보강 매칭 (Phase 10) — n-gram 유사도 미달 시 의미 벡터로 재시도 ──
        // EmbeddingEngine 모델이 없으면 searchKNN이 emptyList() 반환하여 자동 폴백.
        if (bestLearned == null) {
            val knnResults = vectorStore.searchKNN(normalized, k = 3)
            val topKnn = knnResults.firstOrNull()
            if (topKnn != null && topKnn.second >= 0.80f) {
                val (skillId, vecScore) = topKnn
                val skill = dao.getById(skillId)
                if (skill != null) {
                    val slots = extractSlotsFromLearned(normalized, skill)
                    if (passesSafetyGate(skill, slots, safetyCtx)) {
                        Log.d(TAG, "L3 벡터 보강 매칭: $skillId (vecScore=$vecScore)")
                        return SkillMatchResult(
                            source = SkillMatchResult.Source.LEARNED_SKILL,
                            learnedSkill = skill,
                            slotValues = slots,
                            score = vecScore,
                            fingerprint = skill.fingerprint
                        )
                    }
                }
            }
        }

        // ── 3) Template fallback 매칭 (신규 사용자 Day 0 대응) ──
        val templates = templateLoader.all()
        var bestTemplate: Pair<SkillTemplate, Pair<Float, Map<String, String>>>? = null
        for (t in templates) {
            val slots = extractSlotsFromTemplate(normalized, t) ?: continue
            val sim = similarity(normalized, t.fingerprint, null)
            if (sim >= MIN_SIMILARITY && (bestTemplate == null || sim > bestTemplate.second.first)) {
                bestTemplate = t to (sim to slots)
            }
        }

        if (bestTemplate != null) {
            val (t, scoreAndSlots) = bestTemplate
            val (score, slots) = scoreAndSlots
            // Template도 SafetyPolicyEngine 검증 통과해야 함
            val toolNames = t.toolChain.map { it.tool }
            if (!safetyPolicy.allowsLearnedSkill(toolNames, t.initialConfidence, safetyCtx)) {
                Log.d(TAG, "Template blocked by safety: ${t.id}")
                return null
            }
            Log.d(TAG, "L3 template hit: ${t.id} score=$score")
            return SkillMatchResult(
                source = SkillMatchResult.Source.TEMPLATE,
                template = t,
                slotValues = slots,
                score = score,
                fingerprint = t.fingerprint
            )
        }

        return null
    }

    // ── 정규화 ──

    internal fun normalize(text: String): String {
        var s = text.trim().lowercase()

        // 공백 통일
        s = s.replace(Regex("\\s+"), " ")

        // 토큰별 어미·조사·동의어 처리
        val tokens = s.split(" ")
            .filter { it.isNotBlank() }
            .map { token ->
                var t = token
                // 동의어 통일 (복합 어절 우선)
                SYNONYMS[t]?.let { return@map it }
                // 어미 제거
                t = ENDING_REGEX.replace(t, "")
                // 조사 제거
                t = PARTICLE_REGEX.replace(t, "")
                // 재동의어 (조사 제거 후)
                SYNONYMS[t] ?: t
            }
            .filter { it.isNotBlank() && it !in STOPWORDS }

        return tokens.joinToString(" ")
    }

    internal fun tokenize(normalized: String): List<String> =
        normalized.split(" ").filter { it.isNotBlank() }

    // ── 유사도 ──

    /**
     * 하이브리드 유사도: 토큰 Jaccard (60%) + 3-gram Jaccard (40%).
     * coreTokenSetJson이 있으면 더 빠른 토큰 비교에 사용.
     */
    internal fun similarity(queryNormalized: String, fingerprint: String, coreTokenSetJson: String?): Float {
        val qTokens = tokenize(queryNormalized).toSet()
        val fTokens = if (coreTokenSetJson != null) {
            runCatching {
                val arr = JSONArray(coreTokenSetJson)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }.getOrElse { fingerprint.split(" ").toSet() }
        } else {
            // 템플릿 fingerprint: 슬롯 토큰 {POI} 등은 제외
            fingerprint.split(" ").filter { !it.startsWith("{") }.toSet()
        }

        val tokenScore = jaccard(qTokens, fTokens)
        val ngramScore = ngramJaccard(queryNormalized, stripSlotMarkers(fingerprint), 3)

        return tokenScore * TOKEN_WEIGHT + ngramScore * NGRAM_WEIGHT
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() && b.isEmpty()) return 0f
        val inter = a.intersect(b).size
        val union = a.union(b).size
        return if (union == 0) 0f else inter.toFloat() / union
    }

    private fun ngramJaccard(a: String, b: String, n: Int): Float {
        if (a.length < n || b.length < n) return 0f
        val setA = a.windowed(n).toSet()
        val setB = b.windowed(n).toSet()
        return jaccard(setA, setB)
    }

    private fun stripSlotMarkers(fingerprint: String): String =
        fingerprint.replace(Regex("\\{[^}]+\\}"), "").replace(Regex("\\s+"), " ").trim()

    // ── 슬롯 추출 ──

    /**
     * 템플릿 fingerprint에서 슬롯 위치를 파악하여 사용자 발화에서 슬롯 값 추출.
     * 예: fingerprint="{POI} 들렀다 {PLACE_REF}" + query="스타벅스 들렀다 회사"
     *     → {POI=스타벅스, PLACE_REF=회사}
     *
     * 실패(구조 불일치) 시 null 반환.
     */
    internal fun extractSlotsFromTemplate(normalizedQuery: String, template: SkillTemplate): Map<String, String>? {
        if (template.slotSchema.isEmpty()) {
            // 슬롯 없는 템플릿은 fingerprint가 쿼리에 부분 포함되기만 해도 통과
            val fp = stripSlotMarkers(template.fingerprint).trim()
            return if (fp.isBlank() || normalizedQuery.contains(fp)) emptyMap() else null
        }

        // fingerprint의 {SLOT}을 regex capture group으로 변환
        val slotNames = mutableListOf<String>()
        val fpRegex = buildString {
            append("^")
            var i = 0
            val fp = template.fingerprint
            while (i < fp.length) {
                val c = fp[i]
                if (c == '{') {
                    val end = fp.indexOf('}', i)
                    if (end < 0) { append(Regex.escape(fp.substring(i))); break }
                    slotNames.add(fp.substring(i + 1, end))
                    append("(.+?)")
                    i = end + 1
                } else if (c.isWhitespace()) {
                    append("\\s+")
                    while (i < fp.length && fp[i].isWhitespace()) i++
                } else {
                    // 단일 문자 이스케이프
                    append(Regex.escape(c.toString()))
                    i++
                }
            }
            append("$")
        }

        val match = Regex(fpRegex).find(normalizedQuery.trim()) ?: return null
        val values = mutableMapOf<String, String>()
        slotNames.forEachIndexed { idx, name ->
            val v = match.groupValues.getOrNull(idx + 1)?.trim().orEmpty()
            if (v.isBlank()) return null
            values[name] = v
        }
        return values
    }

    /**
     * LearnedSkill의 slotSchema + fingerprint 기반 슬롯 추출.
     * Template 추출과 동일 로직이지만 slotSchema JSON 파싱 필요.
     */
    internal fun extractSlotsFromLearned(normalizedQuery: String, skill: LearnedSkillEntity): Map<String, String> {
        val schemaArr = runCatching { JSONArray(skill.slotSchema) }.getOrNull() ?: return emptyMap()
        if (schemaArr.length() == 0) return emptyMap()

        val slotNames = mutableListOf<String>()
        val fpRegex = buildString {
            append("^")
            var i = 0
            val fp = skill.fingerprint
            while (i < fp.length) {
                val c = fp[i]
                if (c == '{') {
                    val end = fp.indexOf('}', i)
                    if (end < 0) { append(Regex.escape(fp.substring(i))); break }
                    slotNames.add(fp.substring(i + 1, end))
                    append("(.+?)")
                    i = end + 1
                } else if (c.isWhitespace()) {
                    append("\\s+")
                    while (i < fp.length && fp[i].isWhitespace()) i++
                } else {
                    append(Regex.escape(c.toString()))
                    i++
                }
            }
            append("$")
        }

        val match = Regex(fpRegex).find(normalizedQuery.trim()) ?: return emptyMap()
        val values = mutableMapOf<String, String>()
        slotNames.forEachIndexed { idx, name ->
            val v = match.groupValues.getOrNull(idx + 1)?.trim().orEmpty()
            if (v.isNotBlank()) values[name] = v
        }
        return values
    }

    // ── 안전 게이트 ──

    private fun passesSafetyGate(
        skill: LearnedSkillEntity,
        slots: Map<String, String>,
        safetyCtx: SafetyContext
    ): Boolean {
        // 슬롯 추출 실패 시 + 슬롯 있는 스킬이면 차단
        val schemaLen = runCatching { JSONArray(skill.slotSchema).length() }.getOrDefault(0)
        if (schemaLen > 0 && slots.size < schemaLen) {
            Log.d(TAG, "Slot extraction incomplete: ${skill.id} expected=$schemaLen got=${slots.size}")
            return false
        }

        // 상태 필터
        if (skill.status !in setOf("ACTIVE", "DEGRADED")) return false

        // 도구 이름 추출
        val toolNames = runCatching {
            val arr = JSONArray(skill.toolChain)
            (0 until arr.length()).map { arr.getJSONObject(it).optString("tool") }
        }.getOrDefault(emptyList())

        // 결정 3: Pin된 Skill은 confidence 임계 우회 허용, 단 EFFECTFUL Tool 포함 시는 여전히 차단
        val now = System.currentTimeMillis()
        val isPinned = skill.pinnedUntil != null && skill.pinnedUntil > now
        if (isPinned) {
            val hasEffectful = toolNames.any {
                safetyPolicy.toolRiskTier(it) == com.example.ez_capstone.safety.ToolRiskTier.EFFECTFUL
            }
            // 주행 중 복잡성 제한은 Pin이어도 유지
            if (safetyCtx.drivingState == DrivingState.NAVIGATING && toolNames.size >= 3) return false
            return !hasEffectful
        }

        // 일반 경로: SafetyPolicyEngine의 계층 임계 검증
        // Phase B-3 GeneralUtteranceLearner Skill은 confidence ≤ 0.65 → Safe 임계 0.75 미달로 자동 차단
        return safetyPolicy.allowsLearnedSkill(toolNames, skill.confidence, safetyCtx)
    }
}
