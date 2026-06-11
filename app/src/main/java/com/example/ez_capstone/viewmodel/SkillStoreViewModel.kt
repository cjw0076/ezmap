package com.example.ez_capstone.viewmodel

import androidx.lifecycle.ViewModel
import com.example.ez_capstone.skill.SkillCatalogLoader
import com.example.ez_capstone.skill.SkillHealth
import com.example.ez_capstone.skill.SkillLifecycleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SkillStoreViewModel @Inject constructor(
    private val skillLifecycleManager: SkillLifecycleManager,
    private val catalogLoader: SkillCatalogLoader
) : ViewModel() {

    enum class Category(val label: String) {
        ALL("전체"),
        MOBILITY("이동"),
        LIFE("생활"),
        FOOD("장소"),
        INFRA("인프라")
    }

    private val categoryMap = mapOf(
        Category.MOBILITY to setOf(
            "geocode", "get_directions", "get_directions_naver", "get_transit_route",
            "get_traffic_speed", "get_traffic_incidents", "get_highway_alerts", "get_road_risk"
        ),
        Category.LIFE to setOf(
            "search_knowledge", "get_exchange_rate", "get_air_quality"
        ),
        Category.FOOD to setOf(
            "search_places", "search_pharmacies", "search_hospitals", "get_gas_stations"
        ),
        Category.INFRA to setOf(
            "get_weather", "get_weather_kma", "get_ev_chargers", "get_parking", "get_realtime_parking"
        )
    )

    private val _selectedCategory = MutableStateFlow(Category.ALL)
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _skills = MutableStateFlow(skillLifecycleManager.getAllHealth())
    val skills = _skills.asStateFlow()

    fun selectCategory(cat: Category) { _selectedCategory.value = cat }

    fun filteredSkills(all: List<SkillHealth>): List<SkillHealth> {
        val cat = _selectedCategory.value
        return if (cat == Category.ALL) all
        else all.filter { it.skillName in (categoryMap[cat] ?: emptySet()) }
    }

    fun resetSkill(skillName: String) {
        skillLifecycleManager.resetHealth(skillName)
        _skills.value = skillLifecycleManager.getAllHealth()
    }

    /** 카탈로그 표시 이름 — 없으면 skillName 그대로 반환 */
    fun displayName(skillName: String): String =
        catalogLoader.getMeta(skillName)?.displayName ?: skillName.replace("_", " ")

    /** 카탈로그 설명 */
    fun description(skillName: String): String? =
        catalogLoader.getMeta(skillName)?.description
}
