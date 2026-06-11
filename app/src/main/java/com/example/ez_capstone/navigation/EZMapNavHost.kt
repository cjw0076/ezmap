package com.example.ez_capstone.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ez_capstone.ui.screens.ContactsScreen
import com.example.ez_capstone.ui.screens.ConversationScreen
import com.example.ez_capstone.ui.screens.LandingScreen
import com.example.ez_capstone.ui.screens.LoginScreen
import com.example.ez_capstone.ui.screens.NavigationScreen
import com.example.ez_capstone.ui.screens.OnboardingScreen
import com.example.ez_capstone.ui.screens.ProfileScreen
import com.example.ez_capstone.ui.screens.ScheduleScreen
import com.example.ez_capstone.ui.screens.SettingsScreen
import com.example.ez_capstone.ui.screens.SplashScreen
import com.example.ez_capstone.ui.screens.DevScreen
import com.example.ez_capstone.ui.screens.RouteSelectionScreen
import com.example.ez_capstone.ui.screens.RouteOption
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.voice.VoiceStateCoordinator
import com.example.ez_capstone.ui.screens.AgentMemoryScreen
import com.example.ez_capstone.ui.screens.McpServersScreen
import com.example.ez_capstone.ui.screens.SearchScreen
import com.example.ez_capstone.ui.screens.SkillStoreScreen
import com.example.ez_capstone.viewmodel.AgentEvaluatorViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.gson.Gson

@Composable
fun EZMapNavHost(
    navController: NavHostController,
    coordinator: VoiceStateCoordinator,
    apiKeyProvider: com.example.ez_capstone.config.ApiKeyProvider? = null,
    agentAnalytics: AgentAnalytics? = null
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH
    ) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                // isReady = 직접 키 설정(isConfigured) OR 체험모드 활성. 체험모드 사용자가
                // 매 실행 온보딩으로 되돌아가던 버그 수정(isConfigured만 보면 트라이얼=키없음).
                hasApiKeys = apiKeyProvider?.isReady == true,
                // 온보딩 재진입은 셋업 완료 여부로만 판단 — 체험 한도 소진으로 isReady=false여도
                // 이미 온보딩 마친 사용자를 매 실행 온보딩으로 보내던 루프 방지.
                onboardingCompleted = apiKeyProvider?.onboardingCompleted == true,
                onNavigateToLanding = {
                    navController.navigate(NavRoutes.LANDING) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToConversation = {
                    navController.navigate(NavRoutes.CONVERSATION) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    // 첫 실행(키 미설정) → 온보딩에서 키 입력
                    navController.navigate(NavRoutes.ONBOARDING) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoutes.LANDING) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(
            NavRoutes.LANDING,
            // ui_flow.md: Splash→Landing 400ms ease-out
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            LandingScreen(
                onStartClick = {
                    navController.navigate(NavRoutes.CONVERSATION)
                    // LANDING을 back stack에 유지 — CONVERSATION에서 뒤로가면 LANDING으로 복귀
                }
            )
        }
        composable(
            NavRoutes.CONVERSATION,
            enterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { -it / 4 } },
            exitTransition = { fadeOut(tween(150)) }
        ) {
            ConversationScreen(navController = navController, coordinator = coordinator)
        }
        composable(
            NavRoutes.NAVIGATION,
            // ui_flow.md: Conversation→Navigation: 지도 줌인 + 카드 슬라이드 다운 (500ms)
            enterTransition = { fadeIn(tween(500)) },
            exitTransition = { fadeOut(tween(400)) }
        ) {
            val routeJson = navController.previousBackStackEntry
                ?.savedStateHandle?.get<String>("routeJson") ?: ""
            NavigationScreen(
                routeJson = routeJson,
                onNavigationFinished = {
                    navController.popBackStack(NavRoutes.CONVERSATION, false)
                },
                coordinator = coordinator
            )
        }
        composable(
            NavRoutes.ROUTE_SELECTION,
            enterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { it / 4 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 4 } }
        ) {
            val originName = navController.previousBackStackEntry
                ?.savedStateHandle?.get<String>("originName") ?: "현재 위치"
            val destName = navController.previousBackStackEntry
                ?.savedStateHandle?.get<String>("destName") ?: "목적지"
            val routeOptionsJson = navController.previousBackStackEntry
                ?.savedStateHandle?.get<String>("routeOptionsJson") ?: "[]"
            val routeOptions = try {
                Gson().fromJson(routeOptionsJson, Array<RouteOption>::class.java).toList()
            } catch (_: Exception) { emptyList() }

            RouteSelectionScreen(
                originName = originName,
                destName = destName,
                routeOptions = routeOptions,
                onRouteSelected = { route ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("routeJson", route.routeJson)
                    navController.navigate(NavRoutes.NAVIGATION)
                },
                onBack = { navController.popBackStack(NavRoutes.CONVERSATION, false) }
            )
        }
        composable(
            NavRoutes.PROFILE,
            enterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { it / 4 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 4 } }
        ) {
            ProfileScreen(onBackClick = { navController.popBackStack() })
        }
        composable(
            NavRoutes.SETTINGS,
            enterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { it / 4 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 4 } }
        ) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToDev = { navController.navigate(NavRoutes.DEV) },
                onLogout = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    // Settings에서 재진입 시 백스택 유지 — 뒤로가면 Settings로 복귀
                    navController.navigate(NavRoutes.ONBOARDING)
                },
                onNavigateToAgentMemory = { navController.navigate(NavRoutes.AGENT_MEMORY) },
                onNavigateToMcp = { navController.navigate(NavRoutes.MCP_SERVERS) }
            )
        }
        composable(
            NavRoutes.SCHEDULE,
            enterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { it / 4 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 4 } }
        ) {
            ScheduleScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToPlace = { place ->
                    // ConversationScreen에 자동 경로 탐색 요청 전달
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set("autoNavigate", place)
                    navController.popBackStack()
                }
            )
        }
        composable(
            NavRoutes.ONBOARDING,
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) }
        ) {
            val fromSettings = navController.previousBackStackEntry?.destination?.route == NavRoutes.SETTINGS
            OnboardingScreen(
                onComplete = {
                    navController.navigate(NavRoutes.LANDING) {
                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                    }
                },
                // 설정에서 진입 시에만 뒤로가기로 이탈 허용 (첫 실행 시엔 뒤로가기 막음)
                onBack = if (fromSettings) {
                    { navController.popBackStack() }
                } else null
            )
        }
        composable(
            NavRoutes.DEV,
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) }
        ) {
            if (apiKeyProvider != null) {
                val evalVm: AgentEvaluatorViewModel = hiltViewModel()
                DevScreen(
                    apiKeyProvider = apiKeyProvider,
                    agentAnalytics = agentAnalytics,
                    agentEvaluator = evalVm.evaluator,
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            NavRoutes.SKILL_STORE,
            enterTransition = { fadeIn(tween(150)) + slideInHorizontally(tween(150)) { it / 4 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 4 } }
        ) {
            SkillStoreScreen(
                onBack = { navController.popBackStack() },
                onNavigateSettings = { navController.navigate(NavRoutes.SETTINGS) }
            )
        }
        composable(
            NavRoutes.SEARCH,
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 3 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 3 } }
        ) {
            SearchScreen(navController = navController)
        }
        composable(
            NavRoutes.AGENT_MEMORY,
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 3 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 3 } }
        ) {
            AgentMemoryScreen(onBackClick = { navController.popBackStack() })
        }
        composable(
            NavRoutes.MCP_SERVERS,
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 3 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 3 } }
        ) {
            McpServersScreen(onBackClick = { navController.popBackStack() })
        }
        composable(
            NavRoutes.CONTACTS,
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 3 } },
            exitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 3 } }
        ) {
            ContactsScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
