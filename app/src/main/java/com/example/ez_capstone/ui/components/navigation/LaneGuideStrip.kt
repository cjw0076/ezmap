package com.example.ez_capstone.ui.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.TextMuted

/**
 * OBSIDIAN Lane Guide Strip — recommended lanes highlighted.
 *
 *   ▪ ▪ ■ ■ ▪
 *       ^--^  recommended
 */
@Composable
fun LaneGuideStrip(
    laneCount: Int = 5,
    recommendedLanes: List<Int> = emptyList(),
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && recommendedLanes.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            (0 until laneCount).forEach { i ->
                val isRecommended = i in recommendedLanes
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(6.dp)
                        .weight(1f, fill = false)
                        .defaultMinSize(minWidth = 28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (isRecommended) Accent
                            else TextMuted.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
}
