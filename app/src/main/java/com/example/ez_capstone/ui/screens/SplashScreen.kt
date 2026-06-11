package com.example.ez_capstone.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.R
import com.example.ez_capstone.ui.theme.*
import kotlinx.coroutines.delay

/**
 * SplashScreen — ui_flow.md §2.
 * 로고 스케일 + 텍스트 페이드 + 로딩바 + API 키 분기.
 */
@Composable
fun SplashScreen(
    hasApiKeys: Boolean = false,
    // 셋업 완료 여부(onboardingCompleted). 온보딩 재진입은 "지금 키가 쓸 수 있나"(hasApiKeys)가
    // 아니라 "셋업을 끝냈나"로만 판단해야 함 — 체험 한도 소진/일시적 키 미가용으로 hasApiKeys가
    // false여도, 이미 온보딩을 마친 사용자를 매 실행 온보딩으로 되돌리던 루프 버그 방지.
    onboardingCompleted: Boolean = false,
    onNavigateToLanding: () -> Unit,
    onNavigateToConversation: () -> Unit,
    // 첫 실행(셋업 미완료) 시 온보딩으로 보냄. 미지정이면 기존 동작(Landing) 유지.
    onNavigateToOnboarding: (() -> Unit)? = null
) {
    var logoVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    var loadingVisible by remember { mutableStateOf(false) }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 0ms: 로고
        logoVisible = true
        delay(200)
        // 200ms: 텍스트
        textVisible = true
        delay(300)
        // 500ms: 로딩바 시작
        loadingVisible = true
        progress.animateTo(1f, animationSpec = tween(1000, easing = LinearEasing))
        // 1500ms: 분기
        delay(100)
        when {
            // 온보딩은 "셋업 미완료 AND 키도 미가용"일 때만. 둘 중 하나라도 충족되면 재진입 안 함:
            //  - onboardingCompleted=true(체험 한도 소진 포함) → 매 실행 온보딩 루프 방지
            //  - hasApiKeys=true(키 저장됐는데 완료 플래그만 빠진 엣지) → 불필요한 온보딩 방지
            onNavigateToOnboarding != null && !onboardingCompleted && !hasApiKeys -> onNavigateToOnboarding()
            // 키 사용 가능 → 바로 대화 화면
            hasApiKeys -> onNavigateToConversation()
            // 셋업은 마쳤지만 지금 키가 미가용(체험 한도 소진 등) → 온보딩 루프 대신 Landing 홈으로.
            // 한도는 자정 리셋되고, 그 외 기능은 Landing→Conversation에서 정상 사용 가능.
            else -> onNavigateToLanding()
        }
    }

    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(400), label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(500), label = "logoScale"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(400), label = "textAlpha"
    )
    val loadingAlpha by animateFloatAsState(
        targetValue = if (loadingVisible) 1f else 0f,
        animationSpec = tween(300), label = "loadingAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.bg_splash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 로고
            Text(
                text = "◆ EZmap",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(listOf(Accent, AccentPurple))
                ),
                modifier = Modifier
                    .alpha(logoAlpha)
                    .scale(logoScale)
            )

            Spacer(Modifier.height(8.dp))

            // 서브텍스트
            Text(
                text = "AI Navigation Agent",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(Modifier.height(32.dp))

            // 로딩바 (가느다란 액센트 그래디언트)
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(3.dp)
                    .alpha(loadingAlpha)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Accent, AccentPurple))
                        )
                )
            }
        }
    }
}
