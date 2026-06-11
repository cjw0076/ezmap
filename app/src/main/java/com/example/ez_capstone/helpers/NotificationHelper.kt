package com.example.ez_capstone.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.ez_capstone.MainActivity
import com.example.ez_capstone.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_PROACTIVE = "ezmap_proactive"
        const val CHANNEL_NAVIGATION = "ezmap_navigation"
        private var nextId = 1000
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val proactive = NotificationChannel(
                CHANNEL_PROACTIVE, "프로액티브 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "출근/일정 사전 알림" }

            val navigation = NotificationChannel(
                CHANNEL_NAVIGATION, "내비게이션",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "경로 안내 알림" }

            manager.createNotificationChannel(proactive)
            manager.createNotificationChannel(navigation)
        }
    }

    fun postProactive(title: String, body: String, data: Map<String, String> = emptyMap()): Int {
        val id = nextId++
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PROACTIVE)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
        return id
    }

    fun postNavigation(title: String, body: String): Int {
        val id = nextId++
        val notification = NotificationCompat.Builder(context, CHANNEL_NAVIGATION)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(id, notification)
        return id
    }

    fun cancel(id: Int) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(id)
    }
}
