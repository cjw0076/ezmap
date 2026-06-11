package com.example.ez_capstone.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ez_capstone.agent.GeminiAgentEngine
import com.example.ez_capstone.agent.models.AgentContext
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.predictive.ContextSnapshot
import com.example.ez_capstone.predictive.PredictiveCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class PredictiveWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val agentEngine: GeminiAgentEngine,
    private val predictiveCache: PredictiveCache,
    private val routeHistoryDao: RouteHistoryDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PredictiveWorker"
        const val WORK_NAME = "ezmap_predictive_prefetch"
    }

    override suspend fun doWork(): Result {
        return try {
            val cal = Calendar.getInstance()
            val snapshot = ContextSnapshot(
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                isCharging = true,   // WorkManager constraint: requiresCharging = true
                isWifi = true,       // WorkManager constraint: NetworkType.UNMETERED
                latitude = 0.0,
                longitude = 0.0
            )

            val recentRoutes = routeHistoryDao.getRecent(3)
            val destinations = recentRoutes.mapNotNull { it.destName }.distinct()

            for (destination in destinations) {
                try {
                    val context = AgentContext(
                        locationX = snapshot.longitude,  // locationX = longitude
                        locationY = snapshot.latitude    // locationY = latitude
                    )
                    val response = agentEngine.chat("${destination}까지 경로 알려줘", context)
                    predictiveCache.putPrediction(snapshot, response)
                    Log.d(TAG, "Prefetched route to: $destination")
                } catch (e: Exception) {
                    Log.w(TAG, "Prefetch failed for $destination: ${e.message}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PredictiveWorker failed", e)
            Result.retry()
        }
    }
}
