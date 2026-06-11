package com.example.ez_capstone.offline

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 온디바이스 텍스트 임베딩 엔진 (Phase 10).
 * MediaPipe Universal Sentence Encoder — 512-dim 벡터, ~25MB 모델.
 *
 * 사용처: SkillMatcher의 n-gram+Jaccard 유사도를 의미 기반 벡터 유사도로 보강.
 * "회사 가자" ≈ "직장으로 출발해줘" ≈ "사무실에 가야 해" → 같은 LearnedSkill 매칭.
 *
 * 초기화: 첫 embed() 호출 시 지연 초기화. 모델 없으면 null 반환 (graceful degradation).
 */
@Singleton
class EmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EmbeddingEngine"
        private const val MODEL_FILE = "universal_sentence_encoder.tflite"
        private const val EMBEDDING_DIM = 512
        private val CACHE_MAX = 100
    }

    private var embedder: TextEmbedder? = null
    private val cache = object : java.util.LinkedHashMap<String, FloatArray>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) =
            size > CACHE_MAX
    }

    private fun initIfNeeded(): Boolean {
        if (embedder != null) return true
        return try {
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_FILE)
                        .build()
                )
                .setL2Normalize(true)
                .setQuantize(false)
                .build()
            embedder = TextEmbedder.createFromOptions(context, options)
            Log.i(TAG, "EmbeddingEngine initialized")
            true
        } catch (e: Exception) {
            Log.w(TAG, "EmbeddingEngine 초기화 실패 — 모델 파일 없음. n-gram 폴백 사용.", e)
            false
        }
    }

    /**
     * 텍스트를 512-dim float 벡터로 변환.
     * 모델 없으면 null 반환 → 호출자는 기존 n-gram 방식으로 폴백.
     */
    fun embed(text: String): FloatArray? {
        cache[text]?.let { return it }
        if (!initIfNeeded()) return null
        return try {
            val result = embedder!!.embed(text)
            val vector = result.embeddingResult().embeddings().firstOrNull()
                ?.floatEmbedding() ?: return null
            cache[text] = vector
            vector
        } catch (e: Exception) {
            Log.w(TAG, "embed 실패: ${e.javaClass.simpleName}")
            null
        }
    }

    /** 코사인 유사도 [0.0, 1.0] — L2 정규화 후 내적 = 코사인 */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        return a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }.toFloat().coerceIn(0f, 1f)
    }

    fun close() {
        embedder?.close()
        embedder = null
    }
}
