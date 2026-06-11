package com.example.ez_capstone.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.R
import com.example.ez_capstone.ui.theme.*
import com.example.ez_capstone.viewmodel.OnboardingViewModel
import com.example.ez_capstone.viewmodel.ValidationResult

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val step by viewModel.currentStep.collectAsState()

    // 온보딩 진입 시 앱이 쓰는 dangerous 권한을 한 번에 요청 (위치·마이크·SMS·연락처·카메라·전화·일정).
    val permContext = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }
    LaunchedEffect(Unit) {
        val needed = com.example.ez_capstone.governance.RuntimePermissions.ALL.filter {
            ContextCompat.checkSelfPermission(permContext, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    // step > 0이면 이전 단계로, step == 0이면 온보딩 이탈 (onBack 있을 때만)
    BackHandler(enabled = step > 0 || onBack != null) {
        if (step > 0) viewModel.prevStep() else onBack?.invoke()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        AnimatedContent(targetState = step, label = "onboarding_step") { currentStep ->
            when (currentStep) {
                0 -> WelcomePage(
                    onTrial = { viewModel.setTrialMode(); onComplete() },
                    onSetup = { viewModel.nextStep() }
                )
                1 -> GeminiKeyPage(viewModel) { viewModel.nextStep() }
                2 -> KakaoKeyPage(
                    viewModel = viewModel,
                    onNext = { viewModel.nextStep() },
                    onQuickStart = {
                        // 필수 키 2개만 입력하고 바로 시작 — 나머지는 스킬스토어에서 추가
                        viewModel.completeOnboarding()
                        onComplete()
                    }
                )
                3 -> PublicDataPackPage(viewModel) { viewModel.nextStep() }
                4 -> PremiumPackPage(viewModel) {
                    viewModel.completeOnboarding()
                    onComplete()
                }
            }
        }
    }
}

private data class IntroSlide(
    val icon: ImageVector,
    val iconTint: Color,
    val imageRes: Int,
    val title: String,
    val subtitle: String,
    val description: String
)

private val introSlides = listOf(
    IntroSlide(
        icon = Icons.Filled.Mic,
        iconTint = Color(0xFF4FC3F7),
        imageRes = R.drawable.img_onboarding_step1_rendered,
        title = "음성 AI 어시스턴트",
        subtitle = "이지(EZ)와 대화하세요",
        description = "25개 AI 도구를 자연어 한 마디로.\n\"근처 주유소 찾아줘\" — 바로 실행됩니다."
    ),
    IntroSlide(
        icon = Icons.Filled.Navigation,
        iconTint = Color(0xFF69F0AE),
        imageRes = R.drawable.img_onboarding_step3_rendered,
        title = "스마트 내비게이션",
        subtitle = "실시간 경로 안내",
        description = "구간단속 경보·결빙 경고·EV 충전소까지.\n모든 주행 정보가 한 화면에."
    ),
    IntroSlide(
        icon = Icons.Filled.CalendarMonth,
        iconTint = Color(0xFFBB86FC),
        imageRes = R.drawable.img_onboarding_step2_rendered,
        title = "스마트 루틴",
        subtitle = "AI가 먼저 챙겨드립니다",
        description = "캘린더 분석 → 출발 알림 → 최적 경로.\n터치 없이 자동으로 준비해드려요."
    )
)

@Composable
private fun WelcomePage(onTrial: () -> Unit, onSetup: () -> Unit) {
    var introIndex by remember { mutableIntStateOf(0) }
    val showCta = introIndex >= introSlides.size

    if (!showCta) {
        // ── Feature intro slides ──
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = introIndex,
                transitionSpec = {
                    (slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                },
                label = "intro_slide"
            ) { idx ->
                val slide = introSlides[idx]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Hero illustration
                    Image(
                        painter = painterResource(slide.imageRes),
                        contentDescription = slide.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = slide.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Orbitron,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = slide.subtitle,
                        fontSize = 14.sp,
                        color = slide.iconTint,
                        fontFamily = Rajdhani,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = slide.description,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            // ── Dot indicator ──
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                introSlides.indices.forEach { i ->
                    val active = i == introIndex
                    val dotWidth by animateFloatAsState(
                        targetValue = if (active) 24f else 8f,
                        animationSpec = tween(200),
                        label = "dot_w"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = dotWidth.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (active) Accent else TextDim.copy(alpha = 0.4f))
                    )
                }
            }

            // ── Bottom buttons ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (introIndex < introSlides.lastIndex) introIndex++
                        else introIndex = introSlides.size  // show CTA
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(
                        text = if (introIndex < introSlides.lastIndex) "다음" else "시작하기",
                        color = Background,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = { introIndex = introSlides.size },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("건너뛰기", color = TextDim, fontSize = 13.sp)
                }
            }
        }
    } else {
        // ── Original Welcome CTA ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "EZmap",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Orbitron,
                color = Accent
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "AI Navigation Assistant",
                fontSize = 16.sp,
                color = TextSecondary,
                fontFamily = Rajdhani,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(40.dp))
            Text(
                text = "나만의 AI 비서를 만들어보세요",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "AI가 길을 찾고, 일정을 관리하고,\n음성으로 대화합니다.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onTrial,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("지금 바로 체험하기", color = Background, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("일 30회 무료 체험", fontSize = 12.sp, color = TextDim)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onSetup,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
            ) {
                Text("나만의 AI 설정", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Gemini + 카카오 API 키 입력", fontSize = 12.sp, color = TextDim)
        }
    }
}

@Composable
private fun GeminiKeyPage(viewModel: OnboardingViewModel, onNext: () -> Unit) {
    val validation by viewModel.geminiValidation.collectAsState()
    var key by remember { mutableStateOf("") }
    val context = LocalContext.current

    KeyInputPage(
        title = "Gemini API Key",
        subtitle = "Google AI Studio에서 발급받은 API 키를 입력하세요",
        keyValue = key,
        onKeyChange = { key = it },
        validation = validation,
        onValidate = { viewModel.validateGeminiKey(key) },
        onNext = onNext,
        onOpenLink = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        },
        linkText = "AI Studio 열기",
        stepNumber = 1,
        totalSteps = 4
    )
}

@Composable
private fun KakaoKeyPage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit,
    onQuickStart: (() -> Unit)? = null
) {
    val validation by viewModel.kakaoValidation.collectAsState()
    var key by remember { mutableStateOf("") }
    val context = LocalContext.current

    KeyInputPage(
        title = "카카오 REST API Key",
        subtitle = "카카오 디벨로퍼스에서 발급받은 REST API 키를 입력하세요",
        keyValue = key,
        onKeyChange = { key = it },
        validation = validation,
        onValidate = { viewModel.validateKakaoKey(key) },
        onQuickStart = onQuickStart,
        onNext = onNext,
        onOpenLink = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.kakao.com/console/app")))
        },
        linkText = "카카오 디벨로퍼스 열기",
        stepNumber = 2,
        totalSteps = 4
    )
}

@Composable
private fun PublicDataPackPage(viewModel: OnboardingViewModel, onNext: () -> Unit) {
    var dataGoKr by remember { mutableStateOf("") }
    var opinet by remember { mutableStateOf("") }
    val context = LocalContext.current
    val hasKey = dataGoKr.isNotBlank()

    // 해금 기능 목록
    val features = listOf(
        "날씨" to "실시간 기상 정보",
        "미세먼지" to "PM2.5/PM10 대기질",
        "충전소" to "전기차 충전소",
        "주차장" to "공영 주차장 정보",
        "약국" to "근처 약국 찾기",
        "병원" to "응급실/병원 찾기"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Step 3 / 4", fontSize = 14.sp, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        Text(
            "공공데이터 연결",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "키 1개로 6가지 생활 정보를 사용합니다",
            fontSize = 14.sp, color = Accent, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(24.dp))

        // data.go.kr 키 입력
        OutlinedTextField(
            value = dataGoKr,
            onValueChange = { dataGoKr = it },
            label = { Text("공공데이터 포털 인증키") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = onboardingTextFieldColors()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.data.go.kr/")))
        }) {
            Icon(Icons.Default.OpenInNew, null, tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("data.go.kr에서 발급 (30초)", color = Accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))

        // 오피넷 키 입력
        OutlinedTextField(
            value = opinet,
            onValueChange = { opinet = it },
            label = { Text("오피넷 API키 (주유소 가격)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = onboardingTextFieldColors()
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.opinet.co.kr/")))
        }) {
            Icon(Icons.Default.OpenInNew, null, tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("opinet.co.kr에서 발급", color = Accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))

        // 해금 기능 그리드
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "해금되는 기능",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasKey) Accent else TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                // 2열 그리드
                features.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { (name, desc) ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (hasKey) Accent.copy(alpha = 0.1f) else TextDim.copy(alpha = 0.2f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasKey) Accent else TextDim
                                    )
                                    Text(
                                        desc,
                                        fontSize = 11.sp,
                                        color = if (hasKey) TextSecondary else TextDim,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        // 홀수 개일 때 빈 공간 채우기
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // 다음 버튼
        Button(
            onClick = {
                viewModel.savePublicDataPack(dataGoKr, opinet)
                onNext()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(
                if (hasKey) "저장하고 다음" else "다음",
                color = Background, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNext) {
            Text("건너뛰기 (나중에 설정)", color = TextSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PremiumPackPage(viewModel: OnboardingViewModel, onComplete: () -> Unit) {
    var naverId by remember { mutableStateOf("") }
    var naverSecret by remember { mutableStateOf("") }
    var odsay by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Step 4 / 4", fontSize = 14.sp, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        Text(
            "프리미엄 기능",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "더 정확한 경로 + 대중교통 비교",
            fontSize = 14.sp, color = AccentDim, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(24.dp))

        // 네이버 길찾기 섹션
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("네이버 길찾기 (듀얼 경로 비교)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = naverId,
                    onValueChange = { naverId = it },
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = onboardingTextFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = naverSecret,
                    onValueChange = { naverSecret = it },
                    label = { Text("Client Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = onboardingTextFieldColors()
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.ncloud.com/product/applicationService/maps")))
                }) {
                    Icon(Icons.Default.OpenInNew, null, tint = Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("console.ncloud.com 열기", color = Accent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ODsay 대중교통 섹션
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ODsay 대중교통 (버스+지하철 경로)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = odsay,
                    onValueChange = { odsay = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = onboardingTextFieldColors()
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://lab.odsay.com/guide/releaseReference")))
                }) {
                    Icon(Icons.Default.OpenInNew, null, tint = Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("lab.odsay.com 열기", color = Accent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        // 완료 버튼
        Button(
            onClick = {
                viewModel.savePremiumPack(naverId, naverSecret, odsay)
                onComplete()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("설정 완료!", color = Background, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onComplete) {
            Text("건너뛰기 (나중에 설정 가능)", color = TextSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyInputPage(
    title: String,
    subtitle: String,
    keyValue: String,
    onKeyChange: (String) -> Unit,
    validation: ValidationResult,
    onValidate: () -> Unit,
    onNext: () -> Unit,
    onOpenLink: () -> Unit,
    linkText: String,
    stepNumber: Int,
    totalSteps: Int,
    onQuickStart: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // Step indicator + progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Step $stepNumber / $totalSteps", fontSize = 13.sp, color = TextSecondary)
            Text("필수", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { stepNumber.toFloat() / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = Accent,
            trackColor = TextDim.copy(alpha = 0.2f)
        )
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = keyValue,
            onValueChange = onKeyChange,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            colors = onboardingTextFieldColors(),
            trailingIcon = {
                when (validation) {
                    is ValidationResult.Success -> Icon(Icons.Default.CheckCircle, "Valid", tint = Success)
                    is ValidationResult.Error -> Icon(Icons.Default.Error, "Invalid", tint = Error)
                    else -> {}
                }
            }
        )

        if (validation is ValidationResult.Error) {
            Spacer(Modifier.height(4.dp))
            Text((validation as ValidationResult.Error).message, fontSize = 12.sp, color = Error)
        }

        Spacer(Modifier.height(16.dp))

        // Open link button
        TextButton(onClick = onOpenLink) {
            Icon(Icons.Default.OpenInNew, null, tint = Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(linkText, color = Accent, fontSize = 14.sp)
        }

        Spacer(Modifier.height(24.dp))

        // Validate button
        Button(
            onClick = onValidate,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = keyValue.isNotBlank() && validation !is ValidationResult.Loading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDim)
        ) {
            if (validation is ValidationResult.Loading) {
                CircularProgressIndicator(color = TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("검증하기", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Next button (enabled only after validation success)
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = validation is ValidationResult.Success,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("다음", color = Background, fontWeight = FontWeight.Bold)
        }

        // 빠른 시작 — 필수 키 완료 후 바로 앱 사용 가능 (선택 키는 스킬스토어에서 추가)
        if (onQuickStart != null && validation is ValidationResult.Success) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onQuickStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "필수 설정만 완료하고 시작 (추가 기능은 나중에)",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun onboardingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = TextDim,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
)
