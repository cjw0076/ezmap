package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.navi.NavigationCameraMode
import com.example.ez_capstone.navi.NavigationGuideState
import com.example.ez_capstone.ui.theme.*

@Composable
fun NavigationTopBar(
    cameraMode: NavigationCameraMode,
    guideState: NavigationGuideState,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = guideState == NavigationGuideState.REROUTING,
        transitionSpec = {
            (slideInVertically(tween(250)) { -it } + fadeIn(tween(250))) togetherWith
                    (slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)))
        },
        modifier = modifier,
        label = "topBarContent"
    ) { isRerouting ->
        if (isRerouting) {
            ReroutingBanner()
        } else {
            NavigationStatusChip(cameraMode = cameraMode, guideState = guideState)
        }
    }
}

@Composable
private fun ReroutingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Warning.copy(alpha = 0.13f))
            .border(1.dp, Warning.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Accent
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "경로를 다시 찾고 있습니다...",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Warning
        )
    }
}

@Composable
internal fun NavigationStatusChip(
    cameraMode: NavigationCameraMode,
    guideState: NavigationGuideState,
    modifier: Modifier = Modifier
) {
    val label = when {
        guideState == NavigationGuideState.REROUTING -> "재탐색 중"
        cameraMode == NavigationCameraMode.GPS_WAITING -> "GPS 찾는 중"
        cameraMode == NavigationCameraMode.FOLLOW_USER -> "추적 중"
        cameraMode == NavigationCameraMode.USER_PANNING -> "지도 탐색 중"
        cameraMode == NavigationCameraMode.OVERVIEW -> "전체 경로"
        cameraMode == NavigationCameraMode.MANEUVER_PREVIEW -> "회전 미리보기"
        else -> "주행 중"
    }
    val color = when (cameraMode) {
        NavigationCameraMode.GPS_WAITING -> Warning
        NavigationCameraMode.FOLLOW_USER -> Success
        NavigationCameraMode.USER_PANNING,
        NavigationCameraMode.OVERVIEW -> Accent
        NavigationCameraMode.MANEUVER_PREVIEW -> Warning
    }

    Row(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard.copy(alpha = 0.78f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = label, color = TextPrimary, style = HudLabelStyle)
    }
}
