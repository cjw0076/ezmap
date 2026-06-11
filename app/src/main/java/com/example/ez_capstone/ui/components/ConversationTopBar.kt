package com.example.ez_capstone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.profile.MultiProfileManager
import com.example.ez_capstone.profile.ProfileType
import com.example.ez_capstone.ui.theme.*

/**
 * ConversationScreen 상단바 — 반투명 글래스.
 * ui_flow.md §5 상단바 상세.
 * 로고 + Skill 수 배지 + 햄버거
 */
@Composable
fun ConversationTopBar(
    skillCount: Int = 0,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    multiProfileManager: MultiProfileManager? = null
) {
    val profile = multiProfileManager?.getActiveProfile()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Background.copy(alpha = 0.7f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 로고
        Text(
            text = "◆ EZMAP",
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(Modifier.weight(1f))

        // Skill 수 배지
        if (skillCount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Surface,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "$skillCount 도구",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 프로필 모드 배지 (OWNER가 아닐 때만 표시)
        if (profile != null && profile.type != ProfileType.OWNER) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(
                        color = when (profile.type) {
                            ProfileType.GUEST -> Color(0xBBBB86FC)
                            ProfileType.VALET -> Color(0xBBE94560)
                            else -> Color(0xBB4FC3F7)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(profile.name, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        // 검색 아이콘
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "장소 검색",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // 햄버거 메뉴
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "메뉴",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
