package com.example.ez_capstone.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class CalendarEvent(
        val id: Long,
        val title: String,
        val startMs: Long,
        val endMs: Long,
        val location: String?,
        val allDay: Boolean
    )

    fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    /** 오늘 이벤트 전체 */
    suspend fun getTodayEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val startOfDay = now - (now % (24 * 60 * 60 * 1000))
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000

        queryEvents(startOfDay, endOfDay)
    }

    /** 지금부터 withinMinutes 이내 시작하는 이벤트 */
    suspend fun getUpcomingEvents(withinMinutes: Int = 120): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val end = now + withinMinutes * 60 * 1000L

        queryEvents(now, end)
    }

    private fun queryEvents(startMs: Long, endMs: Long): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startMs.toString(), endMs.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val events = mutableListOf<CalendarEvent>()

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)

                while (cursor.moveToNext()) {
                    events.add(CalendarEvent(
                        id = cursor.getLong(idIdx),
                        title = cursor.getString(titleIdx) ?: "",
                        startMs = cursor.getLong(startIdx),
                        endMs = cursor.getLong(endIdx),
                        location = cursor.getString(locIdx)?.ifBlank { null },
                        allDay = cursor.getInt(allDayIdx) == 1
                    ))
                }
            }
        } catch (e: SecurityException) {
            // 권한 거부 — 빈 리스트 반환
        }

        return events
    }
}
