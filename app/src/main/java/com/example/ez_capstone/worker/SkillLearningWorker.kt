package com.example.ez_capstone.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ez_capstone.skill.GeneralUtteranceLearner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 일반 반복 학습 Worker — 매일 1회 자정 근처 실행.
 *
 * SELF_LEARNING_AGENT.md Phase B-3.
 *
 * SkillCompactionWorker와 분리 이유 (전문가 제안):
 *  - Compaction: 12h 주기, 유휴 시간 (idle+charging+wifi)
 *  - Learning: 24h 주기, 자정 근처 (네트워크 제약 없음, 배터리 낮아도 동작)
 *
 * 학습은 엄격한 가드레일(3회 반복, confidence ≤ 0.65, Pin 전까지 Gemini 우회 금지)로
 * 비평가 지적한 false positive 위험(오발화 고정, 맥락 오버피팅)을 억제.
 */
@HiltWorker
class SkillLearningWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val learner: GeneralUtteranceLearner
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SkillLearningWorker"
        const val WORK_NAME = "ezmap_skill_learning"
    }

    override suspend fun doWork(): Result {
        return try {
            val created = learner.runOnce()
            Log.d(TAG, "GeneralUtteranceLearner created $created new LearnedSkill(s)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SkillLearningWorker failed", e)
            Result.retry()
        }
    }
}
