package com.example.ez_capstone.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ez_capstone.skill.SkillCatalogLoader
import com.example.ez_capstone.skill.SkillHealth
import com.example.ez_capstone.skill.SkillLifecycleManager
import com.example.ez_capstone.skill.SkillStatus
import com.example.ez_capstone.ui.screens.SkillStoreScreen
import com.example.ez_capstone.ui.theme.EZMapTheme
import com.example.ez_capstone.viewmodel.SkillStoreViewModel
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SkillStoreScreen Compose UI 테스트.
 * 실기기 또는 에뮬레이터 필요 (./gradlew connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class SkillStoreScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val sampleSkills = listOf(
        SkillHealth("get_directions", SkillStatus.ACTIVE, 1f, 0, 10, 320),
        SkillHealth("get_directions_naver", SkillStatus.KEY_MISSING, 0f, 0, 0, 0),
        SkillHealth("get_weather", SkillStatus.DEGRADED, 0.7f, 1, 5, 450),
        SkillHealth("search_places", SkillStatus.ACTIVE, 1f, 0, 20, 250),
        SkillHealth("get_transit_route", SkillStatus.AUTO_DISABLED, 0f, 5, 5, 0),
    )

    private fun makeViewModel(): SkillStoreViewModel {
        val manager = mockk<SkillLifecycleManager>(relaxed = true)
        val catalog = mockk<SkillCatalogLoader>(relaxed = true)
        every { manager.getAllHealth() } returns sampleSkills
        every { catalog.getMeta(any()) } returns null  // fallback to skillName.replace("_", " ")
        return SkillStoreViewModel(manager, catalog)
    }

    @Test
    fun skillStore_showsAllSkillsOnStart() {
        val vm = makeViewModel()
        rule.setContent {
            EZMapTheme {
                SkillStoreScreen(
                    viewModel = vm,
                    onBack = {},
                    onNavigateSettings = {}
                )
            }
        }
        // ALL 탭 기본 선택 → 전체 스킬 표시
        rule.onNodeWithText("ALL").assertIsDisplayed()
        rule.onNodeWithText("get directions").assertIsDisplayed()
    }

    @Test
    fun skillStore_backButton_hasContentDescription() {
        val vm = makeViewModel()
        rule.setContent {
            EZMapTheme {
                SkillStoreScreen(
                    viewModel = vm,
                    onBack = {},
                    onNavigateSettings = {}
                )
            }
        }
        rule.onNodeWithContentDescription("뒤로 가기").assertIsDisplayed()
    }

    @Test
    fun skillStore_categoryTab_mobility_filtersSkills() {
        val vm = makeViewModel()
        rule.setContent {
            EZMapTheme {
                SkillStoreScreen(
                    viewModel = vm,
                    onBack = {},
                    onNavigateSettings = {}
                )
            }
        }
        // MOBILITY 탭 클릭
        rule.onNodeWithText("이동").performClick()
        // get_directions 표시, get_weather 미표시
        rule.onNodeWithText("get directions").assertIsDisplayed()
        rule.onNodeWithText("get weather").assertDoesNotExist()
    }

    @Test
    fun skillStore_keyMissingCard_showsDialog() {
        val vm = makeViewModel()
        rule.setContent {
            EZMapTheme {
                SkillStoreScreen(
                    viewModel = vm,
                    onBack = {},
                    onNavigateSettings = {}
                )
            }
        }
        // KEY_MISSING 스킬 카드 탭
        rule.onNodeWithContentDescription("get directions naver, 상태: KEY_MISSING, 탭하여 API 키 등록")
            .performClick()
        // 다이얼로그 표시
        rule.onNodeWithText("이 스킬을 활성화하려면 API 키가 필요합니다.").assertIsDisplayed()
    }

    @Test
    fun skillStore_dialog_dismissWorks() {
        val vm = makeViewModel()
        rule.setContent {
            EZMapTheme {
                SkillStoreScreen(
                    viewModel = vm,
                    onBack = {},
                    onNavigateSettings = {}
                )
            }
        }
        rule.onNodeWithContentDescription("get directions naver, 상태: KEY_MISSING, 탭하여 API 키 등록")
            .performClick()
        // 닫기 버튼
        rule.onNodeWithText("닫기").performClick()
        // 다이얼로그 사라짐
        rule.onNodeWithText("이 스킬을 활성화하려면 API 키가 필요합니다.").assertDoesNotExist()
    }

    @Test
    fun skillStore_activeCountBadge_isDisplayed() {
        val vm = makeViewModel()
        rule.setContent {
            EZMapTheme {
                SkillStoreScreen(
                    viewModel = vm,
                    onBack = {},
                    onNavigateSettings = {}
                )
            }
        }
        // 2개 ACTIVE (get_directions, search_places) / 5개 전체
        rule.onNodeWithText("2 / 5").assertIsDisplayed()
    }
}
