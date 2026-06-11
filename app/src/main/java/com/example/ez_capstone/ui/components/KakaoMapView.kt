package com.example.ez_capstone.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ez_capstone.server.models.Coord
import com.example.ez_capstone.server.models.PlaceItem
import com.example.ez_capstone.server.models.Waypoint
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineLayer
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.kakao.vectormap.route.RouteLineStylesSet
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.example.ez_capstone.R

private const val TAG = "KakaoMapView"
// 내비 헤딩업 틸트(도). Kakao는 tilt=0에서 카메라 회전을 렌더하지 않아 약간의 틸트로 회전을 활성화.
private const val NAV_TILT_DEG = 45.0
// 동적 카메라 중심+줌 보간 시간(ms). GPS틱(약 1s)보다 짧게 → 점프 대신 부드럽게.
private const val CAMERA_ANIM_MS = 500
// 주행 중심 추적은 즉시 이동(아래). 마커는 즉시 moveTo, 카메라 중심도 즉시여야 lockstep로
// 마커가 화면에 고정된다. 500ms 애니메이션은 매 GPS틱(500ms)마다 재타깃돼 영원히 마커를
// 못 따라잡아 "내 위치 바인딩 안 됨"을 유발했다.
// 하단 프레이밍: 카메라 중심을 진행방향 앞으로 이동(m) → 차량이 화면 하단, 전방 도로 노출.
private const val LOOKAHEAD_M = 35.0
// 틱 사이 부드러운 추종: 직전 적용상태 → 새 타깃을 N프레임으로 보간하되 **마커와 카메라를 함께
// (lockstep) 보간**한다. 둘이 같은 중간값으로 움직이므로 과거 CAMERA_ANIM_MS 방식의 "마커가 카메라를
// 못 따라잡음" 문제가 안 생기고, 위치·회전이 연속적으로 흘러 맵이 주행방향으로 부드럽게 돈다.
private const val CAM_INTERP_FRAMES = 10
private const val CAM_INTERP_FRAME_MS = 40L

private fun lerpD(a: Double, b: Double, t: Double): Double = a + (b - a) * t
/** 최단각 보간(0~360 경계 처리) — 359°→1° 같은 경우 +2°로 돌게. */
private fun lerpAngleD(a: Double, b: Double, t: Double): Double {
    val d = ((b - a + 540.0) % 360.0) - 180.0
    return a + d * t
}

/** 내비게이션용 방향 화살표 비트맵 — 0°=북쪽(위), bearing으로 회전 */
private fun makeCarMarker(context: Context, density: Float, overspeed: Boolean): Bitmap {
    val resId = if (overspeed) R.drawable.ic_nav_car_overspeed else R.drawable.ic_nav_car
    val src = BitmapFactory.decodeResource(context.resources, resId)
    val px = (44 * density).toInt().coerceIn(64, 160)
    return Bitmap.createScaledBitmap(src, px, px, true)
}

@Composable
fun KakaoMapCompose(
    modifier: Modifier = Modifier,
    routeCoords: List<Coord> = emptyList(),
    waypoints: List<Waypoint> = emptyList(),
    placePins: List<PlaceItem> = emptyList(),
    centerLat: Double = 35.5433,
    centerLng: Double = 129.2599,
    zoomLevel: Int = 15,
    trackUser: Boolean = false,
    routeColor: Color = Color(0xFF1976D2),
    bearing: Float = 0f,
    isOverspeed: Boolean = false,
    navZoom: Float? = null,      // 동적 카메라(주행) 줌. null이면 zoomLevel 사용
    navTiltDeg: Double? = null,  // 동적 틸트. null이면 NAV_TILT_DEG
    onMapGesture: (() -> Unit)? = null,
    onMapReady: ((KakaoMap) -> Unit)? = null
) {
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    val mapView = remember {
        MapView(context)
    }

    DisposableEffect(mapView) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    Log.d(TAG, "Map destroyed")
                }
                override fun onMapError(error: Exception?) {
                    Log.e(TAG, "Map error: ${error?.message}")
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    onMapReady?.invoke(map)
                    Log.d(TAG, "Map ready")
                }
                override fun getPosition(): LatLng = LatLng.from(centerLat, centerLng)
                override fun getZoomLevel(): Int = zoomLevel
            }
        )

        onDispose {
            mapView.finish()
        }
    }

    // 경로 폴리라인 그리기
    LaunchedEffect(routeCoords, waypoints, routeColor, trackUser, kakaoMap) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (routeCoords.size < 2) return@LaunchedEffect

        try {
            val routeLineLayer = map.routeLineManager?.layer
            val labelLayer = map.labelManager?.layer

            // 기존 라인 제거
            routeLineLayer?.removeAll()

            // 기존 경로 라벨 제거
            listOf("route_start", "route_end").forEach { id ->
                try { labelLayer?.remove(labelLayer.getLabel(id)) } catch (_: Exception) {}
            }
            waypoints.indices.forEach { i ->
                try { labelLayer?.remove(labelLayer.getLabel("route_wp_$i")) } catch (_: Exception) {}
            }

            val latLngs = routeCoords.map { LatLng.from(it.lat, it.lng) }

            val style = RouteLineStyle.from(
                8f,
                android.graphics.Color.argb(
                    (routeColor.alpha * 255).toInt(),
                    (routeColor.red * 255).toInt(),
                    (routeColor.green * 255).toInt(),
                    (routeColor.blue * 255).toInt()
                )
            )
            val styles = RouteLineStyles.from(style)
            val segment = RouteLineSegment.from(latLngs, styles)

            val options = RouteLineOptions.from(segment)
            routeLineLayer?.addRouteLine(options)

            // 출발(S) 마커 — 초록색
            val startStyle = LabelStyle.from()
                .setTextStyles(32, android.graphics.Color.WHITE, 2, android.graphics.Color.parseColor("#4CAF50"))
            val startLabelStyles = LabelStyles.from(startStyle)
            labelLayer?.addLabel(
                LabelOptions.from("route_start", latLngs.first())
                    .setStyles(startLabelStyles)
                    .setTexts(LabelTextBuilder().setTexts("S"))
            )

            // 도착(D) 마커 — 빨간색
            val endStyle = LabelStyle.from()
                .setTextStyles(32, android.graphics.Color.WHITE, 2, android.graphics.Color.parseColor("#E94560"))
            val endLabelStyles = LabelStyles.from(endStyle)
            labelLayer?.addLabel(
                LabelOptions.from("route_end", latLngs.last())
                    .setStyles(endLabelStyles)
                    .setTexts(LabelTextBuilder().setTexts("D"))
            )

            // 경유지(1, 2, 3...) 마커 — 파란색
            waypoints.forEachIndexed { i, wp ->
                val wpStyle = LabelStyle.from()
                    .setTextStyles(28, android.graphics.Color.WHITE, 2, android.graphics.Color.parseColor("#1976D2"))
                val wpLabelStyles = LabelStyles.from(wpStyle)
                labelLayer?.addLabel(
                    LabelOptions.from("route_wp_$i", LatLng.from(wp.lat, wp.lng))
                        .setStyles(wpLabelStyles)
                        .setTexts(LabelTextBuilder().setTexts("${i + 1}"))
                )
            }

            // 카메라 맞추기: 주행 추적 중에는 현재 위치 카메라가 단독으로 제어한다.
            if (MapCameraPolicy.shouldFitRoute(latLngs.size, trackUser)) {
                val latLngArray = latLngs.toTypedArray()
                val cameraUpdate = CameraUpdateFactory.fitMapPoints(latLngArray, 100)
                map.moveCamera(cameraUpdate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Route drawing error: ${e.message}")
        }
    }

    // 장소 핀 마커 그리기 (6개 이상 시 그리드 클러스터링 적용)
    LaunchedEffect(placePins, trackUser, kakaoMap) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (placePins.isEmpty()) return@LaunchedEffect

        try {
            val labelLayer = map.labelManager?.layer
            val toRender = if (placePins.size >= 6) clusterPins(placePins) else
                placePins.map { ClusteredPin(it.lat, it.lng, it.name, 1) }

            toRender.forEachIndexed { index, pin ->
                val pos = LatLng.from(pin.lat, pin.lng)
                val color = if (pin.count > 1) android.graphics.Color.parseColor("#FF8C00")
                            else android.graphics.Color.parseColor("#1976D2")
                val label = if (pin.count > 1) "${pin.count}개" else pin.name
                val style = LabelStyle.from()
                    .setTextStyles(32, android.graphics.Color.WHITE, 1, color)
                val labelStyles = LabelStyles.from(style)
                val labelOptions = LabelOptions.from("place_$index", pos)
                    .setStyles(labelStyles)
                    .setTexts(LabelTextBuilder().setTexts(label))
                labelLayer?.addLabel(labelOptions)
            }

            // 모든 핀을 포함하는 카메라 이동
            if (MapCameraPolicy.shouldFitPlaces(placePins.size, trackUser)) {
                val latLngs = placePins.map { LatLng.from(it.lat, it.lng) }.toTypedArray()
                val cameraUpdate = CameraUpdateFactory.fitMapPoints(latLngs, 100)
                map.moveCamera(cameraUpdate)
            } else if (MapCameraPolicy.shouldCenterSinglePlace(placePins.size, trackUser)) {
                val cameraUpdate = CameraUpdateFactory.newCenterPosition(
                    LatLng.from(placePins[0].lat, placePins[0].lng), zoomLevel
                )
                map.moveCamera(cameraUpdate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Place pin error: ${e.message}")
        }
    }

    // 마커 아이콘 재생성 판단용 — 과속 상태가 바뀔 때만 라벨 교체
    val lastOverspeed = remember { booleanArrayOf(false) }
    // 직전 적용된(보간된) 카메라 상태 [markerLat, markerLng, bearingDeg, zoom, tiltDeg]. 틱 간 연속성 유지.
    val appliedCamHolder = remember { arrayOfNulls<DoubleArray>(1) }

    // Camera tracking + current location marker for navigation mode
    LaunchedEffect(centerLat, centerLng, bearing, trackUser, isOverspeed, navZoom, navTiltDeg, kakaoMap) {
        if (!trackUser) return@LaunchedEffect
        val map = kakaoMap ?: return@LaunchedEffect
        try {
            // 마커 스타일 준비(과속 변화 시 재생성). 위치는 아래 보간 루프에서 매 프레임 moveTo.
            val labelLayer = map.labelManager?.layer
            val marker = if (labelLayer != null) {
                val existing = try { labelLayer.getLabel("current_location") } catch (_: Exception) { null }
                if (existing != null && lastOverspeed[0] == isOverspeed) existing
                else {
                    existing?.let { try { labelLayer.remove(it) } catch (_: Exception) {} }
                    val car = makeCarMarker(context, context.resources.displayMetrics.density, isOverspeed)
                    lastOverspeed[0] = isOverspeed
                    labelLayer.addLabel(
                        LabelOptions.from("current_location", LatLng.from(centerLat, centerLng))
                            .setStyles(LabelStyles.from(LabelStyle.from(car)))
                    )
                }
            } else null

            // 이번 틱의 목표 카메라 상태. from = 직전 적용상태(없으면 즉시 적용).
            val target = doubleArrayOf(
                centerLat, centerLng, bearing.toDouble(),
                (navZoom?.let { Math.round(it) } ?: zoomLevel).toDouble(),
                navTiltDeg ?: NAV_TILT_DEG
            )
            val from = appliedCamHolder[0] ?: target
            val frames = if (appliedCamHolder[0] == null) 1 else CAM_INTERP_FRAMES

            // 마커와 카메라를 함께(lockstep) 보간 → 위치·회전이 연속적으로 흘러 맵이 부드럽게 회전.
            // (둘이 같은 중간값으로 움직이므로 마커가 화면에 고정 유지된다.)
            for (i in 1..frames) {
                val t = i.toDouble() / frames
                val mLat = lerpD(from[0], target[0], t)
                val mLng = lerpD(from[1], target[1], t)
                val brg = lerpAngleD(from[2], target[2], t)
                val zoomLvl = Math.round(lerpD(from[3], target[3], t)).toInt()
                val tilt = lerpD(from[4], target[4], t)
                // 하단 프레이밍: 카메라 중심을 진행방향 앞으로 LOOKAHEAD_M 이동(차량=화면 하단, 전방 도로 노출).
                val camCenter = if (navZoom != null) {
                    val th = Math.toRadians(brg)
                    val dLat = LOOKAHEAD_M * Math.cos(th) / 111320.0
                    val dLng = LOOKAHEAD_M * Math.sin(th) / (111320.0 * Math.cos(Math.toRadians(mLat)))
                    LatLng.from(mLat + dLat, mLng + dLng)
                } else LatLng.from(mLat, mLng)
                marker?.moveTo(LatLng.from(mLat, mLng))
                // 순서 고정: 중심(회전·틸트 리셋) → 틸트 → 회전(마지막에 적용돼 살아남음).
                map.moveCamera(CameraUpdateFactory.newCenterPosition(camCenter, zoomLvl))
                map.moveCamera(CameraUpdateFactory.tiltTo(Math.toRadians(tilt)))
                map.moveCamera(CameraUpdateFactory.rotateTo(Math.toRadians(brg)))
                appliedCamHolder[0] = doubleArrayOf(mLat, mLng, brg, zoomLvl.toDouble(), tilt)
                if (i < frames) delay(CAM_INTERP_FRAME_MS)
            }
            appliedCamHolder[0] = target
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Camera tracking error: ${e.message}")
        }
    }

    // 팬 제스처 감지용 — 20px 이상 이동 시 1회만 onMapGesture 발동
    val gesturePts = remember { floatArrayOf(0f, 0f) }   // [downX, downY]
    val gestureFired = remember { booleanArrayOf(false) }

    @OptIn(ExperimentalComposeUiApi::class)
    AndroidView(
        factory = { mapView },
        modifier = modifier
            .pointerInteropFilter { event ->
                // 지도 터치 시 부모(Drawer, BottomSheet)의 터치 인터셉션을 차단
                // → 지도 드래그/핀치가 Drawer 스와이프나 Sheet 드래그에 먹히지 않음
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        gesturePts[0] = event.x
                        gesturePts[1] = event.y
                        gestureFired[0] = false
                        mapView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!gestureFired[0] && onMapGesture != null) {
                            val dx = event.x - gesturePts[0]
                            val dy = event.y - gesturePts[1]
                            if (dx * dx + dy * dy > 400f) {  // 20px 이상 이동
                                gestureFired[0] = true
                                onMapGesture.invoke()
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mapView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false  // false = 지도 뷰에 이벤트 전달 (consume하지 않음)
            }
    )
}

private data class ClusteredPin(
    val lat: Double,
    val lng: Double,
    val name: String,
    val count: Int
)

/**
 * 그리드 기반 마커 클러스터링.
 * 위경도 0.005° (~500m) 격자로 그룹화 — 도심 내 장소 검색 결과에 적합.
 */
private fun clusterPins(pins: List<PlaceItem>): List<ClusteredPin> {
    val gridSize = 0.005
    return pins
        .groupBy { pin ->
            val row = Math.floor(pin.lat / gridSize).toInt()
            val col = Math.floor(pin.lng / gridSize).toInt()
            row to col
        }
        .map { (_, group) ->
            val centerLat = group.sumOf { it.lat } / group.size
            val centerLng = group.sumOf { it.lng } / group.size
            val label = if (group.size == 1) group[0].name else group[0].name
            ClusteredPin(centerLat, centerLng, label, group.size)
        }
}
