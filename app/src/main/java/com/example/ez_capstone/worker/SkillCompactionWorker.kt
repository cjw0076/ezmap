package com.example.ez_capstone.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ez_capstone.skill.LearnedSkillDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 유휴 시간(충전 중 + WiFi + 화면 꺼짐)에 LearnedSkill DB를 정리.
 *
 * SELF_LEARNING_AGENT.md §3.3 하드웨어 활용 원칙:
 * - WorkManager Constraint: requiresCharging + NetworkType.UNMETERED
 * - 전력 비용 0에 가깝게 유지
 *
 * 작업:
 * 1. Tier별 TTL 만료 Skill 삭제 (purgeStale)
 * 2. 상한 500개 초과 시 LRU + 성공률 기반 pruning (pruneLowestScore)
 *
 * Phase B 확장 예정:
 * - 적응형 confidence 임계 조정 (부정 피드백율 > 5% → 임계 상향)
 * - 반복 발화 집계 기반 신규 LearnedSkill 생성 (Gemini 경로 학습)
 */
@HiltWorker
class SkillCompactionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val learnedSkillDao: LearnedSkillDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SkillCompactionWorker"
        const val WORK_NAME = "ezmap_skill_compaction"
        private const val SKILL_CAP = 500
    }

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()

            // 1) TTL 만료 Skill 삭제 (pinnedUntil 고려, AUTO_DISABLED는 보존)
            val purged = learnedSkillDao.purgeStale(now)
            if (purged > 0) Log.d(TAG, "purgeStale: removed $purged expired skills")

            // 2) 상한 초과 시 점수 낮은 것부터 pruning
            val total = learnedSkillDao.countTotal()
            if (total > SKILL_CAP) {
                val overflow = total - SKILL_CAP
                val pruned = learnedSkillDao.pruneLowestScore(overflow)
                Log.d(TAG, "pruneLowestScore: removed $pruned (cap=$SKILL_CAP, was=$total)")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SkillCompactionWorker failed", e)
            Result.retry()
        }
    }
}
