package com.example.ez_capstone.ui.components.navigation

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.ez_capstone.R

/**
 * Maps a Kakao Mobility Guide.type (directionCode) to its VectorDrawable resource ID.
 * Falls back to the straight arrow for any unrecognised code.
 */
@DrawableRes
fun maneuverIconRes(directionCode: Int): Int = when (directionCode) {
    0 -> R.drawable.ic_maneuver_straight        // 직진
    1 -> R.drawable.ic_maneuver_turn_left        // 좌회전
    2 -> R.drawable.ic_maneuver_turn_right       // 우회전
    3 -> R.drawable.ic_maneuver_uturn            // U턴
    5 -> R.drawable.ic_maneuver_elevated         // 고가도로
    6 -> R.drawable.ic_maneuver_underground      // 지하차도
    11 -> R.drawable.ic_maneuver_straight        // 출발
    12 -> R.drawable.ic_maneuver_arrive          // 도착
    16 -> R.drawable.ic_maneuver_fork_left       // 왼쪽 분기
    17 -> R.drawable.ic_maneuver_fork_right      // 오른쪽 분기
    else -> R.drawable.ic_maneuver_straight      // 미분류 → 직진
}

/**
 * Static maneuver icon with runtime tinting (urgency color changes with distance).
 * Code 12 (arrive) uses the dedicated flag drawable.
 */
@Composable
fun ManeuverIcon(directionCode: Int, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id = maneuverIconRes(directionCode)),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}
