package com.example.ez_capstone.skill

import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.db.entity.RouteHistoryEntity
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * RoutineDetector 패턴 감지 단위 테스트.
 *
 * RouteHistoryDao를 mockk으로 대체하여 JVM에서 실행.
 * MIN_FREQUENCY=3: 동일 목적지로 3회 이상 같은 요일에 이동해야 패턴으로 인식.
 */
class RoutineDetectorTest {

    private val routeHistoryDao: RouteHistoryDao = mockk(relaxed = true)
    private val conversationDao: ConversationDao = mockk(relaxed = true)
    private val gson: Gson = Gson()

    private lateinit var detector: RoutineDetector

    @Before
    fun setUp() {
        // 기본: 모든 요일 빈 리스트 반환
        for (dow in 0..6) {
            coEvery { routeHistoryDao.getByDayOfWeek(dow) } returns emptyList()
        }
        detector = RoutineDetector(routeHistoryDao, conversationDao, gson)
    }

    // ── 헬퍼: 특정 요일 + 출발 시각으로 RouteHistoryEntity 생성 ──

    private fun makeRoute(
        destName: String,
        dayOfWeek: Int,
        hourOfDay: Int,
        minute: Int = 0
    ): RouteHistoryEntity {
        val cal = Calendar.getInstance().apply {
            // 해당 요일로 설정 (기준: 이번 주)
            set(Calendar.DAY_OF_WEEK, dayOfWeek + 1) // Calendar.DAY_OF_WEEK: 1=일
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return RouteHistoryEntity(
            originName = "집",
            originLat = 37.5,
            originLng = 127.0,
            destName = destName,
            destLat = 37.55,
            destLng = 127.05,
            distanceM = 3000,
            durationS = 600,
            departedAt = cal.timeInMillis,
            dayOfWeek = dayOfWeek
        )
    }

    // ── Test 1: 데이터 없을 때 빈 리스트 반환 ──

    @Test
    fun `히스토리가 없으면 빈 리스트를 반환한다`() = runTest {
        val result = detector.detectPatterns()
        assertTrue("히스토리 없으면 후보 없음", result.isEmpty())
    }

    // ── Test 2: 3회 미만은 패턴으로 인식하지 않는다 ──

    @Test
    fun `같은 요일 같은 목적지 2회는 패턴으로 인식하지 않는다`() = runTest {
        // 월요일(dayOfWeek=1)에 이마트 2회
        val routes = listOf(
            makeRoute("이마트", dayOfWeek = 1, hourOfDay = 10),
            makeRoute("이마트", dayOfWeek = 1, hourOfDay = 10)
        )
        coEvery { routeHistoryDao.getByDayOfWeek(1) } returns routes

        val result = detector.detectPatterns()
        val imarteCandidate = result.filter { it.destination == "이마트" }
        assertTrue("2회는 MIN_FREQUENCY(3) 미만이므로 후보 없음", imarteCandidate.isEmpty())
    }

    // ── Test 3: 3회 이상이면 패턴으로 감지 ──

    @Test
    fun `같은 요일 같은 목적지 3회면 패턴 후보로 감지된다`() = runTest {
        // 금요일(dayOfWeek=5)에 스타벅스 3회 (비슷한 시간대)
        val routes = listOf(
            makeRoute("스타벅스", dayOfWeek = 5, hourOfDay = 9, minute = 0),
            makeRoute("스타벅스", dayOfWeek = 5, hourOfDay = 9, minute = 15),
            makeRoute("스타벅스", dayOfWeek = 5, hourOfDay = 9, minute = 30)
        )
        coEvery { routeHistoryDao.getByDayOfWeek(5) } returns routes

        val result = detector.detectPatterns()
        val candidate = result.find { it.destination == "스타벅스" }

        assertTrue("3회 반복이므로 후보 탐지 기대", candidate != null)
        assertEquals(5, candidate!!.dayOfWeek)
        assertTrue("frequency >= 3", candidate.frequency >= 3)
        assertTrue("confidence > 0", candidate.confidence > 0f)
    }

    // ── Test 4: 결과는 confidence 내림차순으로 정렬 ──

    @Test
    fun `결과는 confidence 내림차순으로 정렬된다`() = runTest {
        // 화요일(dayOfWeek=2): A 목적지 5회, B 목적지 3회
        val routesA = List(5) { makeRoute("헬스장", dayOfWeek = 2, hourOfDay = 7, minute = it * 5) }
        val routesB = List(3) { makeRoute("카페", dayOfWeek = 2, hourOfDay = 14, minute = it * 10) }
        coEvery { routeHistoryDao.getByDayOfWeek(2) } returns (routesA + routesB)

        val result = detector.detectPatterns()

        // 정렬 검증
        for (i in 0 until result.size - 1) {
            assertTrue(
                "index $i 의 confidence >= index ${i + 1}",
                result[i].confidence >= result[i + 1].confidence
            )
        }
    }

    // ── Test 5: candidateToEntity — 필드 매핑 검증 ──

    @Test
    fun `candidateToEntity가 RoutineCandidate를 올바르게 변환한다`() {
        val candidate = RoutineCandidate(
            name = "토요일 9시 스타벅스",
            dayOfWeek = 6,
            timeStartMin = 480,
            timeEndMin = 600,
            destination = "스타벅스",
            frequency = 4,
            confidence = 0.8f
        )

        val entity = detector.candidateToEntity(candidate)

        assertEquals("토요일 9시 스타벅스", entity.name)
        assertEquals(6, entity.triggerDayOfWeek)
        assertEquals(480, entity.triggerTimeStart)
        assertEquals(600, entity.triggerTimeEnd)
        assertTrue("stepsJson에 get_directions 포함", entity.stepsJson.contains("get_directions"))
        assertTrue("stepsJson에 목적지 포함", entity.stepsJson.contains("스타벅스"))
    }
}
