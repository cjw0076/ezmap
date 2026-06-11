package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.navi.TtsEvent
import com.example.ez_capstone.navi.TtsStage
import com.example.ez_capstone.navi.TurnDirectionUi
import com.example.ez_capstone.ui.theme.*

@Composable
fun TtsManeuverPopup(
    activeTtsEvent: TtsEvent?,
    turn: TurnDirectionUi?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = activeTtsEvent != null && turn != null,
            enter = slideInVertically(tween(200)) { -it / 2 } + fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            // 상단 회전 카드(TurnInstructionCard) 아래에 위치 — 하단(마커 근처)이 아닌 위로 올림
            modifier = Modifier.padding(top = 170.dp)
        ) {
            if (activeTtsEvent != null && turn != null) {
                PopupContent(event = activeTtsEvent, turn = turn)
            }
        }
    }
}

@Composable
private fun PopupContent(event: TtsEvent, turn: TurnDirectionUi) {
    val (iconSize, accentColor) = when (event.stage) {
        TtsStage.FAR      -> 48.dp to Accent
        TtsStage.NEAR     -> 56.dp to Warning
        TtsStage.IMMINENT -> 64.dp to Error
    }

    val infiniteTransition = rememberInfiniteTransition(label = "popupPulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "popupPulseFraction"
    )
    val scale = if (event.stage == TtsStage.IMMINENT) 1f + pulseFraction * 0.06f else 1f

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard.copy(alpha = 0.93f))
            .border(2.dp, accentColor.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (turn.directionCode == 12) {
                Icon(Icons.Filled.Flag, null, tint = accentColor, modifier = Modifier.size(iconSize * 0.55f))
            } else {
                TurnArrowCanvas(
                    directionCode = turn.directionCode,
                    color = accentColor,
                    modifier = Modifier.size(iconSize * 0.62f)
                )
            }
        }

        Column {
            Text(
                text = turn.distanceText,
                fontFamily = Orbitron,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = accentColor
            )
            Text(
                text = turn.dirName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}
