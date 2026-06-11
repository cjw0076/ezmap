package com.example.ez_capstone.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ez_capstone.db.entity.ProfileEntity
import com.example.ez_capstone.db.entity.ScheduleEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room DB 통합 테스트 — 인메모리 DB로 실행.
 * 실기기 또는 에뮬레이터 필요 (./gradlew connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class EZMapDatabaseTest {

    private lateinit var db: EZMapDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EZMapDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── ProfileDao ──

    @Test
    fun profile_upsertAndGet() = runTest {
        val profile = ProfileEntity(homeAddress = "서울시 강남구", vehicleType = "sedan")
        db.profileDao().upsertProfile(profile)

        val loaded = db.profileDao().getProfile()
        assertNotNull(loaded)
        assertEquals("서울시 강남구", loaded!!.homeAddress)
        assertEquals("sedan", loaded.vehicleType)
    }

    @Test
    fun profile_updateHome() = runTest {
        db.profileDao().upsertProfile(ProfileEntity())
        db.profileDao().updateHome("서울 광화문", 37.5759, 126.9768)

        val loaded = db.profileDao().getProfile()
        assertEquals("서울 광화문", loaded?.homeAddress)
        assertEquals(37.5759, loaded?.homeLat)
        assertEquals(126.9768, loaded?.homeLng)
    }

    @Test
    fun profile_updateWork() = runTest {
        db.profileDao().upsertProfile(ProfileEntity())
        db.profileDao().updateWork("서울 강남역", 37.4979, 127.0276)

        val loaded = db.profileDao().getProfile()
        assertEquals("서울 강남역", loaded?.workAddress)
    }

    @Test
    fun profile_initiallyNull() = runTest {
        val result = db.profileDao().getProfile()
        assertNull(result)
    }

    // ── ScheduleDao ──

    @Test
    fun schedule_insertAndGetAll() = runTest {
        db.scheduleDao().insert(ScheduleEntity(title = "아침 회의", isActive = true))
        db.scheduleDao().insert(ScheduleEntity(title = "점심 약속", isActive = false))

        val all = db.scheduleDao().getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun schedule_getActiveOnly() = runTest {
        db.scheduleDao().insert(ScheduleEntity(title = "활성 일정", isActive = true))
        db.scheduleDao().insert(ScheduleEntity(title = "비활성 일정", isActive = false))

        val active = db.scheduleDao().getActive()
        assertEquals(1, active.size)
        assertEquals("활성 일정", active[0].title)
    }

    @Test
    fun schedule_setActive() = runTest {
        val id = db.scheduleDao().insert(ScheduleEntity(title = "테스트 일정", isActive = true))
        db.scheduleDao().setActive(id, false)

        val active = db.scheduleDao().getActive()
        assertTrue(active.isEmpty())
    }

    @Test
    fun schedule_delete() = runTest {
        val id = db.scheduleDao().insert(ScheduleEntity(title = "삭제할 일정"))
        val entity = db.scheduleDao().getAll().first { it.id == id }
        db.scheduleDao().delete(entity)

        val all = db.scheduleDao().getAll()
        assertTrue(all.none { it.id == id })
    }

    @Test
    fun schedule_withDestination() = runTest {
        db.scheduleDao().insert(
            ScheduleEntity(
                title = "부산 출장",
                destinationName = "부산역",
                destinationLat = 35.1147,
                destinationLng = 129.0420
            )
        )
        val schedule = db.scheduleDao().getAll().first()
        assertEquals("부산역", schedule.destinationName)
        assertEquals(35.1147, schedule.destinationLat)
        assertEquals(129.0420, schedule.destinationLng)
    }
}
