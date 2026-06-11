package com.example.ez_capstone.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 백그라운드 주기 워커 등록 — EZApplication.onCreate에서 명시적으로 호출.
 *
 * (이전: AppModule의 @Provides WorkManager 안에 enqueue가 있었으나 아무도 주입하지 않아
 *  워커가 한 번도 스케줄되지 않던 dead-wire였음. 명시적 호출로 전환.)
 * 모든 워커는 @HiltWorker → HiltWorkerFactory(Configuration.Provider) 필요.
 */
object BackgroundWorkScheduler {

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)

        // 1) PredictiveWorker — 30분, UNMETERED+충전 (프리워밍)
        wm.enqueueUniquePeriodicWork(
            PredictiveWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PredictiveWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        )

        // 2) SkillCompactionWorker — 12시간, 유휴(WiFi+충전+idle)
        wm.enqueueUniquePeriodicWork(
            SkillCompactionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SkillCompactionWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build()
                )
                .build()
        )

        // 3) SkillLearningWorker — 24시간, DB 학습만
        wm.enqueueUniquePeriodicWork(
            SkillLearningWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SkillLearningWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        )

        // 4) ProactiveWorker — 30분, 일정 기반 선제 알림
        wm.enqueueUniquePeriodicWork(
            ProactiveWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ProactiveWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
        )
    }
}
