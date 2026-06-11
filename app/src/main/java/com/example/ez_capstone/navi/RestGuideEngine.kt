package com.example.ez_capstone.navi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.ez_capstone.api.KakaoLocalApi
import com.example.ez_capstone.server.models.Coord
import com.example.ez_capstone.server.models.Guide
import com.example.ez_capstone.server.models.RouteItem
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

import com.example.ez_capstone.voice.NaturalVoiceEngine

class RestGuideEngine(
    private val context: Context,
    private val kakaoLocalApi: KakaoLocalApi? = null,
    private val apiKeyProvider: com.example.ez_capstone.config.ApiKeyProvider? = null,
    private val speedEnforcement: SpeedEnforcement? = null,
    private val roadHazardEngine: RoadHazardEngine? = null,
    private val incidentReporter: IncidentReporter? = null
) {

    companion object {
        private const val TAG = "RestGuideEngine"
        private const val GUIDE_TRIGGER_DISTANCE = 50.0
        private const val ROUTE_DEVIATION_DISTANCE = 100.0
        private const val REROUTE_COOLDOWN_MS = 15_000L
        private const val ARRIVAL_DISTANCE_M = 25.0      // 목적지 좌표 근접 시 도착(type12 guide 없어도)
        private const val NEAREST_WINDOW = 50            // 전방 최근접 탐색 윈도우(매틱 O(n) 전수스캔 방지)
        private const val GPS_STALE_MS = 12_000L         // 이 시간 이상 위치 미수신 → GPS 약함 경고(세션당 1회)
        // 회전 안내 빈도 튜닝: 예고(FAR 300m)·근접(NEAR 100m)은 직전 음성 안내와 이 간격 이상일 때만.
        // 촘촘한 회전 구간에서 안내가 겹쳐 너무 잦던 문제 완화. 임박(IMMINENT 50m)은 안전상 항상 발화.
        private const val MIN_GUIDE_TTS_GAP_MS = 4_000L

        /**
         * 잔여 ETA(초). Kakao 경로 총 소요시간을 잔여거리 비율로 스케일 → 미리보기 ETA와 동일 기준(교통 반영).
         * 총량 정보가 없으면 거리/속도 추정으로 폴백(정지 시 30km/h 가정). 순수 함수 → 단위테스트 가능.
         */
        fun computeRemainTimeSec(remainMeters: Double, routeDurationS: Int, routeDistanceM: Int, speedKmh: Int): Int {
            if (routeDurationS > 0 && routeDistanceM > 0) {
                val frac = (remainMeters / routeDistanceM).coerceIn(0.0, 1.0)
                return (routeDurationS * frac).toInt().coerceAtLeast(0)
            }
            val speed = if (speedKmh > 5) speedKmh else 30
            return ((remainMeters / 1000.0 / speed) * 3600).toInt().coerceAtLeast(60)
        }
    }

    interface RerouteCallback {
        fun onRerouteNeeded(currentLat: Double, currentLng: Double)
    }

    interface LocationUpdateCallback {
        fun onLocationChanged(lat: Double, lng: Double, speedKmh: Int, bearing: Float)
    }

    private val _hudState = MutableStateFlow(DrivingHudState())
    val hudState: StateFlow<DrivingHudState> = _hudState.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _userBearing = MutableStateFlow(0f)
    val userBearing: StateFlow<Float> = _userBearing.asStateFlow()

    // TTS 3-stage events
    private val _ttsEvents = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 5)
    val ttsEvents: SharedFlow<TtsEvent> = _ttsEvents.asSharedFlow()
    // ConcurrentHashMap.newKeySet() — emitTtsIfNeeded(Main)과 updateRoute(호출자) 동시 접근 안전
    private val spokenStages: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // 세그먼트 투영 맵매칭 — spec §4. 매 GPS 틱에서 update() → RouteProgress 파생.
    private val tracker = RouteProgressTracker()

    private var coords: List<Coord> = emptyList()
    private var guides: List<Guide> = emptyList()
    private var nextGuideIndex = 0
    // 경로 좌표별 "끝까지 남은 거리" 누적(시작/재탐색 시 1회 계산) → tracker null 폴백용 유지
    private var suffixDistances: DoubleArray = DoubleArray(0)
    private var progressIdx = 0                 // 전방 진행 인덱스(forward-only 최근접)
    @Volatile private var lastLocationUpdateTime = 0L
    // Kakao 경로의 총 소요시간/거리 — 잔여 ETA를 미리보기와 동일 기준(교통 반영)으로 계산
    private var routeDurationS = 0
    private var routeDistanceM = 0
    private var isActive = false
    // 이탈 K=3 연속 필터 — spec §5: 단일 틱 GPS 노이즈에 즉시 재탐색하지 않도록
    private var offRouteTickCount = 0
    private val OFF_ROUTE_TICK_THRESHOLD = 3

    private var prevLat = 0.0
    private var prevLng = 0.0
    private var lastRerouteTime = 0L
    private var lastIncidentCheckTime = 0L
    private var lastHazardCheckTime = 0L

    // Driving summary tracking
    private var startTime = 0L
    private var totalDistanceTraveled = 0.0

    private var rerouteCallback: RerouteCallback? = null
    private var locationCallback: LocationUpdateCallback? = null
    private var fusedClient: FusedLocationProviderClient? = null

    // 각도 보간 (0°↔360° 경계 처리)
    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var diff = ((to - from + 540f) % 360f) - 180f
        return (from + diff * t + 360f) % 360f
    }

    // POI 사전 캐시: guideIndex → 인근 랜드마크명 (null = 없음)
    private val poiCache = mutableMapOf<Int, String?>()
    private var engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val fusedLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val speedKmh = (loc.speed * 3.6f).toInt()
                // FusedLocation bearing: GPS+sensor fused by Android.
                // 고속(>5km/h): lerp 0.7 (부드럽게). 저속: lerp 0.3 (노이즈 억제하면서 회전 반영).
                if (loc.hasBearing()) {
                    val lerpFactor = if (loc.speed > 1.5f) 0.7f else 0.3f
                    _userBearing.value = lerpAngle(_userBearing.value, loc.bearing, lerpFactor)
                }
                onLocationUpdate(loc.latitude, loc.longitude, speedKmh)
            }
        }
    }

    fun start(route: RouteItem, callback: RerouteCallback, locationCb: LocationUpdateCallback? = null) {
        coords = route.coords
        guides = route.guides ?: emptyList()
        routeDurationS = route.duration_s
        routeDistanceM = route.distance_m
        nextGuideIndex = 0
        progressIdx = 0
        buildSuffixDistances()
        tracker.init(coords, guides)
        rerouteCallback = callback
        locationCallback = locationCb
        isActive = true
        startTime = System.currentTimeMillis()
        lastLocationUpdateTime = System.currentTimeMillis()
        totalDistanceTraveled = 0.0
        spokenStages.clear()

        _hudState.value = DrivingHudState(
            guideState = NavigationGuideState.ACTIVE,
            turnDirection = guides.firstOrNull()?.toTurnUi()
        )

        fusedClient = LocationServices.getFusedLocationProviderClient(context)
        if (!startLocationUpdates()) {
            // 위치 권한 없음 → 조용히 멈추지 말고 사용자에게 사유를 알림(무음 freeze 방지)
            addAiAlert(AiAlertUi(
                type = AiAlertType.HAZARD,
                message = "위치 권한이 필요해요. 설정에서 위치 권한을 허용해주세요."
            ))
        }
        startGpsWatchdog()

        // POI pre-fetch (카카오 로컬 API 사용 가능 시)
        if (kakaoLocalApi != null) {
            engineScope.launch { prefetchPoi() }
        }
        // Phase 7: 구간단속 카메라 프리패치
        if (speedEnforcement != null) {
            engineScope.launch { speedEnforcement.loadCamerasForRoute(coords) }
        }
        incidentReporter?.reset()
    }

    // 회전 지점에서 운전자가 실제로 보는 '눈에 띄는' 랜드마크 우선순위.
    // (기존 버그: query="랜드마크" 리터럴 → 그런 이름의 장소가 없어 항상 0건이었다.)
    // 지하철역·주유소는 강한 시각 단서, 편의점은 도심 어디서나 잡히는 폴백.
    private val POI_PRIORITY = listOf("지하철역", "주유소", "편의점")
    private suspend fun prefetchPoi() {
        guides.forEachIndexed { idx, guide ->
            var picked: String? = null
            for (q in POI_PRIORITY) {
                try {
                    val results = kakaoLocalApi!!.searchKeyword(
                        query = q,
                        x = guide.lng,
                        y = guide.lat,
                        radius = 150,
                        sort = "distance",
                        size = 1
                    )
                    // 회전점 90m 이내만 채택 → "○○ 앞에서 좌회전"이 실제로 맞도록.
                    val near = results.firstOrNull()?.takeIf { (it.distanceM ?: Int.MAX_VALUE) <= 90 }
                    if (near != null && near.name.isNotBlank()) { picked = near.name; break }
                } catch (_: Exception) { /* 다음 카테고리 시도 */ }
            }
            poiCache[idx] = picked
        }
        Log.d(TAG, "POI pre-fetch 완료: ${poiCache.count { it.value != null }}개 랜드마크")
    }

    fun updateRoute(route: RouteItem) {
        coords = route.coords
        guides = route.guides ?: emptyList()
        routeDurationS = route.duration_s
        routeDistanceM = route.distance_m
        nextGuideIndex = 0
        progressIdx = 0
        buildSuffixDistances()
        tracker.init(coords, guides)
        spokenStages.clear()  // 새 경로의 첫 회전 TTS가 옛 stage로 억제되지 않게
        _hudState.update { it.copy(
            guideState = NavigationGuideState.ACTIVE,
            turnDirection = guides.firstOrNull()?.toTurnUi(),
            aiAlerts = it.aiAlerts + AiAlertUi(
                type = AiAlertType.REROUTE,
                message = "새 경로를 찾았습니다."
            )
        ) }
    }

    fun addAiAlert(alert: AiAlertUi) {
        _hudState.update { it.copy(aiAlerts = it.aiAlerts + alert) }
    }

    fun dismissAlert(alert: AiAlertUi) {
        _hudState.update { it.copy(aiAlerts = it.aiAlerts.filter { a -> a.id != alert.id }) }
    }

    // ── [DEBUG] 주행 시뮬레이션: 실 GPS를 끊고 좌표를 위치 업데이트로 주입 ──
    @Volatile private var debugSimMode = false

    /** 시뮬 시작: 실제 GPS 콜백 해제(충돌 방지) */
    fun debugStartSim() {
        debugSimMode = true
        fusedClient?.removeLocationUpdates(fusedLocationCallback)
    }

    /** 시뮬 좌표 1스텝 주입 (실 GPS와 동일 경로인 onLocationUpdate 사용). bearing도 주입해 헤딩업 동작. */
    fun debugFeedLocation(lat: Double, lng: Double, speedKmh: Int, bearingDeg: Float) {
        if (!debugSimMode) return
        _userBearing.value = bearingDeg  // 진행방향 → 맵 회전(헤딩업)
        onLocationUpdate(lat, lng, speedKmh)
    }

    fun stop() {
        isActive = false
        debugSimMode = false
        fusedClient?.removeLocationUpdates(fusedLocationCallback)
        engineScope.cancel()
        engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        speedEnforcement?.reset()
        incidentReporter?.reset()
        // 도착(ARRIVED)으로 멈춘 경우 FINISHED로 덮어쓰지 않는다 — 덮으면 화면이 도착을
        // 못 보고 도착안내/주행기록/RouteHistory가 모두 누락된다(잠복 버그).
        if (_hudState.value.guideState != NavigationGuideState.ARRIVED) {
            _hudState.update { it.copy(guideState = NavigationGuideState.FINISHED) }
        }
    }

    /** @return 위치 구독 시작 성공 여부(권한 있으면 true). */
    private fun startLocationUpdates(): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        // GPS: 500ms 간격. 최소이동거리 제약 없음(0) — 정지 시에도 주기 업데이트가 와야
        // 마커가 실위치를 유지하고(바인딩) watchdog가 "정지"를 "신호약함"으로 오해하지 않는다.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
            .setMinUpdateIntervalMillis(300L)
            .build()
        fusedClient?.requestLocationUpdates(request, fusedLocationCallback, Looper.getMainLooper())
        return true
    }

    /**
     * GPS 신호 끊김 감시 — 첫 fix 이후 GPS_STALE_MS 이상 끊기면 **세션당 1회만** 경고.
     * 정지 상태에서도 주기 업데이트가 오므로(최소이동 제약 제거) 진짜 신호 손실에만 반응.
     * 재무장(re-arm) 없음 → 멘트 남발 방지. 시뮬 중엔 비활성.
     */
    private fun startGpsWatchdog() {
        engineScope.launch {
            var hadFirstFix = false
            var warnedOnce = false
            while (this@RestGuideEngine.isActive) {
                kotlinx.coroutines.delay(5_000L)
                if (!this@RestGuideEngine.isActive || debugSimMode) continue
                if (lastLocationUpdateTime > 0L) hadFirstFix = true
                if (!hadFirstFix) continue  // 최초 fix 전엔 GPS_WAITING 오버레이가 담당
                val stale = System.currentTimeMillis() - lastLocationUpdateTime > GPS_STALE_MS
                if (stale && !warnedOnce) {
                    warnedOnce = true
                    addAiAlert(AiAlertUi(
                        type = AiAlertType.HAZARD,
                        message = "GPS 신호가 약해요. 잠시 후 다시 잡힐 거예요.",
                        autoDismissMs = 8000
                    ))
                }
            }
        }
    }

    /**
     * 전방 윈도우 최근접 경로점. 매틱 O(window). 윈도우 밖으로 점프(이탈 의심) 시에만 1회 전수확인.
     * @return (최근접 인덱스, 그 거리 m)
     */
    private fun nearestOnRoute(lat: Double, lng: Double): Pair<Int, Double> {
        if (coords.isEmpty()) return 0 to 0.0
        val lo = (progressIdx - 2).coerceAtLeast(0)
        val hi = (progressIdx + NEAREST_WINDOW).coerceAtMost(coords.size - 1)
        var bestIdx = lo
        var bestDist = Double.MAX_VALUE
        for (i in lo..hi) {
            val d = haversine(lat, lng, coords[i].lat, coords[i].lng)
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        // 윈도우 안 최근접이 이탈 임계를 넘으면 GPS 점프 가능성 → 전수 1회 재확인(드묾)
        if (bestDist > ROUTE_DEVIATION_DISTANCE) {
            var fIdx = 0; var fDist = Double.MAX_VALUE
            for (i in coords.indices) {
                val d = haversine(lat, lng, coords[i].lat, coords[i].lng)
                if (d < fDist) { fDist = d; fIdx = i }
            }
            if (fDist < bestDist) return fIdx to fDist
        }
        return bestIdx to bestDist
    }

    private fun buildSuffixDistances() {
        val n = coords.size
        if (n == 0) { suffixDistances = DoubleArray(0); return }
        val arr = DoubleArray(n)
        for (i in n - 2 downTo 0) {
            arr[i] = arr[i + 1] + haversine(coords[i].lat, coords[i].lng, coords[i + 1].lat, coords[i + 1].lng)
        }
        suffixDistances = arr
    }

    private fun onLocationUpdate(lat: Double, lng: Double, speedKmh: Int = 0) {
        if (!isActive) return
        lastLocationUpdateTime = System.currentTimeMillis()
        _userLocation.value = lat to lng

        // Distance traveled tracking
        if (prevLat != 0.0 || prevLng != 0.0) {
            totalDistanceTraveled += haversine(prevLat, prevLng, lat, lng)
        }
        prevLat = lat; prevLng = lng

        // Notify location callback
        locationCallback?.onLocationChanged(lat, lng, speedKmh, _userBearing.value)

        // 세그먼트 투영 맵매칭 — snapped 위치·잔여거리·이탈·방위를 일관 파생
        val fix = RouteFix(lat, lng, speedKmh, _userBearing.value)
        val progress = tracker.update(fix)

        // 카메라·마커는 스냅 위치 사용(raw GPS 아님) — 흔들림 제거
        _userLocation.value = (progress?.snappedLat ?: lat) to (progress?.snappedLng ?: lng)

        // 저속(< 5 km/h)에서는 세그먼트 방위로 헤딩 안정화
        if (speedKmh < 5 && progress != null) {
            _userBearing.update { progress.bearingAlongRoute }
        }

        // 잔여거리: tracker 우선, 없으면 suffix-distance 폴백
        val totalRemaining = progress?.distanceRemainingM
            ?: run {
                val (ni, nd) = nearestOnRoute(lat, lng)
                if (suffixDistances.isNotEmpty()) suffixDistances[ni] + nd else 0.0
            }
        val remainTimeSec = computeRemainTimeSec(totalRemaining, routeDurationS, routeDistanceM, speedKmh)

        // 도착 폴백
        if (totalDistanceTraveled > 50.0 && totalRemaining <= ARRIVAL_DISTANCE_M && coords.isNotEmpty()) {
            _hudState.update { it.copy(guideState = NavigationGuideState.ARRIVED) }
            stop()
            return
        }

        // 회전 안내 — distance-along 기반(투영 결과)으로 다음 회전까지 거리 계산
        var currentTurn = _hudState.value.turnDirection
        var nextTurn = _hudState.value.nextTurnDirection

        if (guides.isNotEmpty() && nextGuideIndex < guides.size) {
            val guide = guides[nextGuideIndex]

            // distance-along 기반 거리 (정확) / tracker null 시 haversine 폴백
            val distToGuide = if (progress != null) {
                val guideAlong = tracker.guideDistanceAlong(nextGuideIndex)
                (guideAlong - progress.distanceAlongM).coerceAtLeast(0.0).toInt()
            } else {
                haversine(lat, lng, guide.lat, guide.lng).toInt()
            }

            currentTurn = guide.toTurnUi().copy(distance = distToGuide)
            nextTurn = if (nextGuideIndex + 1 < guides.size) guides[nextGuideIndex + 1].toTurnUi() else null

            emitTtsIfNeeded(distToGuide, guide)

            if (distToGuide <= GUIDE_TRIGGER_DISTANCE) {
                nextGuideIndex++
                if (guide.type == 12) {
                    _hudState.update { it.copy(guideState = NavigationGuideState.ARRIVED) }
                    stop()
                    return
                }
                currentTurn = if (nextGuideIndex < guides.size) guides[nextGuideIndex].toTurnUi() else null
                nextTurn = if (nextGuideIndex + 1 < guides.size) guides[nextGuideIndex + 1].toTurnUi() else null
            }
        }

        // Phase 7: 구간단속 카메라 경고
        val cameraAlert = speedEnforcement?.checkAlerts(lat, lng, speedKmh)

        // Safety alerts: 카메라 알림 + 고속도로 진출입 주의
        val safetyAlerts = buildList {
            if (cameraAlert != null) {
                add(SafetyAlertUi(
                    type = cameraAlert.type,
                    limitSpeed = cameraAlert.limitSpeed,
                    distance = cameraAlert.distanceM,
                    isOverSpeed = cameraAlert.isOverSpeed
                ))
            }
            addAll(buildSafetyAlerts(lat, lng, speedKmh))
        }.take(1)

        // Phase 7: 돌발상황 (2분 쿨다운)
        val now7 = System.currentTimeMillis()
        if (incidentReporter != null && now7 - lastIncidentCheckTime > 120_000L) {
            lastIncidentCheckTime = now7
            engineScope.launch {
                val incidents = incidentReporter.getRelevantIncidents(lat, lng, _userBearing.value)
                incidents.forEach { alert -> addAiAlert(alert) }
            }
        }

        // Phase 7: 도로 위험 (5분 쿨다운)
        val hazards = if (roadHazardEngine != null && now7 - lastHazardCheckTime > 300_000L) {
            lastHazardCheckTime = now7
            engineScope.launch {
                val h = roadHazardEngine.checkHazards(lat, lng)
                if (h.isNotEmpty()) _hudState.update { it.copy(roadHazards = h) }
            }
            _hudState.value.roadHazards
        } else _hudState.value.roadHazards

        _hudState.update { it.copy(
            speed = speedKmh,
            remainDistance = totalRemaining.toInt(),
            remainTime = remainTimeSec,
            turnDirection = currentTurn,
            nextTurnDirection = nextTurn,
            currentRoadName = if (nextGuideIndex < guides.size) guides[nextGuideIndex].name else "",
            safetyAlerts = safetyAlerts,
            speedCameraAlert = cameraAlert,
            roadHazards = hazards
        ) }

        // Route deviation — tracker offRouteM 기반, K=3 연속 틱 필터 (spec §5)
        val minDist = progress?.offRouteM ?: 0.0
        if (minDist > ROUTE_DEVIATION_DISTANCE && isActive) {
            offRouteTickCount++
            if (offRouteTickCount >= OFF_ROUTE_TICK_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastRerouteTime > REROUTE_COOLDOWN_MS) {
                    lastRerouteTime = now
                    offRouteTickCount = 0
                    _hudState.update { it.copy(guideState = NavigationGuideState.REROUTING) }
                    rerouteCallback?.onRerouteNeeded(lat, lng)
                }
            }
        } else {
            offRouteTickCount = 0
        }
    }

    var ttsStyle: String = "polite"  // casual / polite / ez

    private var lastGuideTtsMs = 0L

    private fun emitTtsIfNeeded(distToGuide: Int, guide: Guide) {
        val idx = nextGuideIndex
        // 직진(type 0)은 음성 안내 불필요 — HUD로 충분하고, 매 직진마다 안내하면 너무 잦다.
        // 하위 stage를 소진 처리해 다음 회전 안내만 남긴다.
        if (guide.type == 0) {
            spokenStages.add("${idx}_FAR")
            spokenStages.add("${idx}_NEAR")
            spokenStages.add("${idx}_IMMINENT")
            return
        }
        val naturalText = NaturalVoiceEngine.convert(
            guideText = guide.guidance,
            guideType = guide.type,
            roadName = guide.name,
            nearbyPoi = poiCache[nextGuideIndex],
            distanceM = distToGuide,
            ttsStyle = ttsStyle
        )
        val now = System.currentTimeMillis()
        fun emit(stage: TtsStage) {
            _ttsEvents.tryEmit(TtsEvent(idx, stage, naturalText))
            lastGuideTtsMs = now
        }
        // 거리 구간별로 단 1개 stage만 발화. GPS 갱신이 느려 300m→40m로 뛰어도 중복 방지.
        when {
            distToGuide <= 50 -> {
                // 임박(50m)은 안전상 항상 발화. 하위 stage는 발화 없이 소진만 처리.
                spokenStages.add("${idx}_FAR")
                spokenStages.add("${idx}_NEAR")
                if (spokenStages.add("${idx}_IMMINENT")) emit(TtsStage.IMMINENT)
            }
            distToGuide <= 300 -> {
                // 회전당 안내를 2회로 축소: 예고(FAR) + 임박(IMMINENT)만. NEAR(100m) 단계는 제거.
                // (기존 3단계가 너무 잦다는 피드백 — 회전당 발화 1/3 감소.)
                spokenStages.add("${idx}_NEAR")  // NEAR 단계 소진(미발화)
                // 예고는 1회만, 직전 안내와 간격 충분할 때만 → 촘촘한 회전 구간 예고 스택 방지.
                if ("${idx}_FAR" !in spokenStages && now - lastGuideTtsMs >= MIN_GUIDE_TTS_GAP_MS) {
                    spokenStages.add("${idx}_FAR"); emit(TtsStage.FAR)
                }
            }
        }
    }

    private fun buildSafetyAlerts(lat: Double, lng: Double, speedKmh: Int): List<SafetyAlertUi> {
        if (speedKmh < 10) return emptyList()
        val alerts = mutableListOf<SafetyAlertUi>()
        guides.forEach { g ->
            val dist = haversine(lat, lng, g.lat, g.lng).toInt()
            if (dist in 50..500 && g.type in listOf(8, 9)) {
                // Highway entrance/exit — suggest 60km/h limit
                alerts.add(SafetyAlertUi(
                    type = SafetyType.CAUTION,
                    limitSpeed = 60,
                    distance = dist,
                    isOverSpeed = speedKmh > 60
                ))
            }
        }
        return alerts.take(1)
    }

    fun getDrivingSummary(): DrivingSummary {
        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt().coerceAtLeast(1)
        val avgSpeed = (totalDistanceTraveled / 1000.0 / elapsed * 3600).toInt()
        return DrivingSummary(totalDistanceTraveled.toInt(), elapsed, avgSpeed)
    }

    private fun Guide.toTurnUi() = TurnDirectionUi(
        directionCode = type,
        distance = distance,
        roadName = name,
        dirName = guidance
    )

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

}
