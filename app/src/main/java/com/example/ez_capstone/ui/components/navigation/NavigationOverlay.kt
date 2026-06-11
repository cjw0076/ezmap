package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.navi.AiAlertUi
import com.example.ez_capstone.navi.DrivingHudState
import com.example.ez_capstone.navi.NavigationCameraMode
import com.example.ez_capstone.navi.SafetyType
import com.example.ez_capstone.navi.TtsEvent
import com.example.ez_capstone.ui.components.MicState
import com.example.ez_capstone.ui.theme.*

@Composable
fun NavigationOverlay(
    hudState: DrivingHudState,
    voiceState: MicState,
    activeTtsEvent: TtsEvent?,
    onVoiceClick: () -> Unit,
    onQuickAction: (String) -> Unit,
    onDismissAlert: (AiAlertUi) -> Unit,
    onExitClick: () -> Unit,
    cameraMode: NavigationCameraMode = NavigationCameraMode.GPS_WAITING,
    onRecenterClick: () -> Unit = {},
    onOverviewClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ── TOP HUD ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            NavigationTopBar(
                cameraMode = cameraMode,
                guideState = hudState.guideState
            )
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = hudState.turnDirection,
                transitionSpec = {
                    (slideInVertically(tween(220)) { -it / 2 } + fadeIn(tween(220))) togetherWith
                        fadeOut(tween(180))
                },
                label = "turnCard"
            ) { turn ->
                if (turn != null) {
                    Column {
                        TurnInstructionCard(
                            turn = turn,
                            nextTurn = hudState.nextTurnDirection,
                            distanceM = turn.distance
                        )
                        if (turn.distance <= 500) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val recommendedLanes = when (turn.directionCode) {
                                1 -> listOf(0, 1)
                                2 -> listOf(3, 4)
                                0 -> listOf(1, 2, 3)
                                else -> listOf(2, 3)
                            }
                            LaneGuideStrip(laneCount = 5, recommendedLanes = recommendedLanes, visible = true)
                        }
                    }
                }
            }

            val sectionAlert = hudState.speedCameraAlert
            if (sectionAlert != null && sectionAlert.type == SafetyType.SECTION_START && sectionAlert.sectionRemainingM > 0) {
                val sectionLengthM = sectionAlert.sectionRemainingM + sectionAlert.distanceM.coerceAtLeast(1)
                val progress = 1f - sectionAlert.sectionRemainingM.toFloat() / sectionLengthM.toFloat()
                Spacer(modifier = Modifier.height(8.dp))
                SectionControlBar(
                    sectionProgress = progress,
                    remainingM = sectionAlert.sectionRemainingM,
                    limitSpeed = sectionAlert.limitSpeed,
                    isOverspeed = sectionAlert.isOverSpeed,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }

            if (hudState.safetyAlerts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val alert = hudState.safetyAlerts.first()
                SafetyOverlay(
                    visible = true,
                    type = when (alert.type) {
                        SafetyType.CAMERA -> "SPEED_CAMERA"
                        SafetyType.SECTION_START -> "SECTION_START"
                        SafetyType.SECTION_END -> "SECTION_END"
                        SafetyType.CAUTION -> "CAUTION"
                        SafetyType.ICY_ROAD -> "ICY_ROAD"
                        SafetyType.FOG -> "FOG"
                        SafetyType.HEAVY_SNOW -> "HEAVY_SNOW"
                    },
                    speedLimit = alert.limitSpeed,
                    remainDistance = alert.distance,
                    currentSpeed = hudState.speed,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }

            AnimatedVisibility(
                visible = hudState.aiAlerts.isNotEmpty(),
                enter = slideInVertically(tween(250)) { -it / 2 } + fadeIn(tween(250)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                if (hudState.aiAlerts.isNotEmpty()) {
                    val latestAlert = hudState.aiAlerts.last()
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        AiAlertPanel(
                            alert = AiAlert(
                                message = latestAlert.message,
                                type = when (latestAlert.type) {
                                    com.example.ez_capstone.navi.AiAlertType.REROUTE -> AlertType.ROUTE_CHANGE
                                    com.example.ez_capstone.navi.AiAlertType.ROAD_EVENT -> AlertType.WARNING
                                    com.example.ez_capstone.navi.AiAlertType.WAYPOINT_ARRIVAL -> AlertType.ARRIVAL
                                    com.example.ez_capstone.navi.AiAlertType.ALTERNATIVE_ROUTE -> AlertType.ROUTE_CHANGE
                                    com.example.ez_capstone.navi.AiAlertType.AI_RESPONSE -> AlertType.INFO
                                    com.example.ez_capstone.navi.AiAlertType.INCIDENT -> AlertType.WARNING
                                    com.example.ez_capstone.navi.AiAlertType.HAZARD -> AlertType.WARNING
                                    com.example.ez_capstone.navi.AiAlertType.REST_AREA -> AlertType.INFO
                                    com.example.ez_capstone.navi.AiAlertType.PARKING -> AlertType.INFO
                                },
                                primaryAction = latestAlert.actions.getOrNull(0),
                                secondaryAction = latestAlert.actions.getOrNull(1),
                            ),
                            onPrimaryAction = { latestAlert.actions.getOrNull(0)?.let { onQuickAction(it) } },
                            onSecondaryAction = { onDismissAlert(latestAlert) },
                            onDismiss = { onDismissAlert(latestAlert) }
                        )
                    }
                }
            }
        }

        // ── TTS MANEUVER POPUP ────────────────────────────────
        TtsManeuverPopup(
            activeTtsEvent = activeTtsEvent,
            turn = hudState.turnDirection
        )

        // ── RIGHT FLOATING BUTTONS ────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FloatingMapButton(
                icon = { Icon(Icons.Filled.Navigation, null, tint = Accent, modifier = Modifier.size(22.dp)) },
                label = "전체 경로 보기",
                onClick = onOverviewClick
            )
            if (cameraMode != NavigationCameraMode.FOLLOW_USER && cameraMode != NavigationCameraMode.GPS_WAITING) {
                FloatingMapButton(
                    icon = { Icon(Icons.Filled.LocationOn, null, tint = Success, modifier = Modifier.size(22.dp)) },
                    label = "내 위치로 돌아가기",
                    onClick = onRecenterClick
                )
            }
        }

        // ── EXIT BUTTON ───────────────────────────────────────
        IconButton(
            onClick = onExitClick,
            colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceCard.copy(alpha = 0.8f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Border, RoundedCornerShape(10.dp))
        ) {
            Icon(Icons.Filled.Close, contentDescription = "내비게이션 종료", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }

        // ── BOTTOM NAV BAR ────────────────────────────────────
        BottomNavBar(
            speed = hudState.speed,
            remainDistance = hudState.remainDistance,
            remainTime = hudState.remainTime,
            speedLimit = hudState.safetyAlerts.firstOrNull()?.limitSpeed,
            voiceState = voiceState,
            onVoiceClick = onVoiceClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
        )
    }
}

@Composable
private fun FloatingMapButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceCard.copy(alpha = 0.86f)),
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderActive, RoundedCornerShape(16.dp))
            .semantics { contentDescription = label }
    ) {
        icon()
    }
}
