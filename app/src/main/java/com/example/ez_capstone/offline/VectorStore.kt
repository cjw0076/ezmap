package com.example.ez_capstone.offline

import android.util.Log
import com.example.ez_capstone.skill.LearnedSkillDao
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 온디바이스 벡터 스토어 (Phase 10).
 * LearnedSkill.embedding BLOB을 인메모리 KNN 인덱스로 캐싱.
 *
 * 영속성: loadFromDb()로 앱 기동 시 DB 로딩, upsert() 시 메모리만 갱신.
 * DB BLOB 저장은 SkillLearner에서 embeddingBytes()를 통해 처리.
 */
@Singleton
class VectorStore @Inject constructor(
    private val embeddingEngine: EmbeddingEngine,
    private val learnedSkillDao: LearnedSkillDao
) {
    companion object {
        private const val TAG = "VectorStore"
        private const val DIM = 512
    }

    private val index = mutableMapOf<String, FloatArray>()

    /** 앱 기동 시 1회 호출 — DB에 저장된 embedding BLOB을 메모리에 로딩 */
    suspend fun loadFromDb() {
        val skills = learnedSkillDao.getAll()
        var loaded = 0
        for (skill in skills) {
            val blob = skill.embedding ?: continue
            val vec = blobToFloatArray(blob)
            if (vec != null) {
                index[skill.id] = vec
                loaded++
            }
        }
        Log.d(TAG, "VectorStore loaded $loaded/${skills.size} embeddings from DB")
    }

    /** LearnedSkill fingerprint 임베딩 등록/갱신 (메모리만) */
    fun upsert(skillId: String, fingerprint: String) {
        val vec = embeddingEngine.embed(fingerprint) ?: return
        index[skillId] = vec
    }

    /** 임베딩을 BLOB으로 직렬화 — LearnedSkillEntity.embedding 저장 시 사용 */
    fun embeddingBytes(fingerprint: String): ByteArray? {
        val vec = embeddingEngine.embed(fingerprint) ?: return null
        return floatArrayToBlob(vec)
    }

    /** 발화 텍스트와 코사인 유사도 기준 상위 k개 skillId 반환 */
    fun searchKNN(utterance: String, k: Int = 5): List<Pair<String, Float>> {
        val queryVec = embeddingEngine.embed(utterance) ?: return emptyList()
        return index.entries
            .map { (id, vec) -> id to embeddingEngine.cosineSimilarity(queryVec, vec) }
            .filter { it.second >= 0.75f }
            .sortedByDescending { it.second }
            .take(k)
    }

    fun remove(skillId: String) { index.remove(skillId) }
    fun clear() { index.clear() }
    val size: Int get() = index.size

    private fun floatArrayToBlob(arr: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        arr.forEach { buf.putFloat(it) }
        return buf.array()
    }

    private fun blobToFloatArray(blob: ByteArray): FloatArray? {
        if (blob.size != DIM * 4) return null
        val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(DIM) { buf.float }
    }
}
