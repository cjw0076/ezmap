package com.example.ez_capstone.viewmodel

import com.example.ez_capstone.skill.SkillCatalogLoader
import com.example.ez_capstone.skill.SkillHealth
import com.example.ez_capstone.skill.SkillLifecycleManager
import com.example.ez_capstone.skill.SkillStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SkillStoreViewModelTest {

    private lateinit var manager: SkillLifecycleManager
    private lateinit var catalog: SkillCatalogLoader
    private lateinit var vm: SkillStoreViewModel

    private val sampleSkills = listOf(
        SkillHealth("get_directions", SkillStatus.ACTIVE, 1f, 0, 10, 320),
        SkillHealth("get_directions_naver", SkillStatus.KEY_MISSING, 0f, 0, 0, 0),
        SkillHealth("get_weather", SkillStatus.DEGRADED, 0.7f, 1, 5, 450),
        SkillHealth("search_places", SkillStatus.ACTIVE, 1f, 0, 20, 250),
        SkillHealth("get_transit_route", SkillStatus.AUTO_DISABLED, 0f, 5, 5, 0),
        SkillHealth("get_gas_stations", SkillStatus.ACTIVE, 1f, 0, 3, 180),
        SkillHealth("search_knowledge", SkillStatus.ACTIVE, 1f, 0, 7, 600),
    )

    @Before
    fun setUp() {
        manager = mockk(relaxed = true)
        catalog = mockk(relaxed = true)
        every { manager.getAllHealth() } returns sampleSkills
        every { catalog.getMeta(any()) } returns null  // 테스트에서는 catalog 결과 불필요
        vm = SkillStoreViewModel(manager, catalog)
    }

    @Test
    fun `초기 카테고리는 ALL`() = runTest {
        assertEquals(SkillStoreViewModel.Category.ALL, vm.selectedCategory.first())
    }

    @Test
    fun `ALL 카테고리에서 filteredSkills는 전체 반환`() = runTest {
        val all = vm.skills.first()
        val filtered = vm.filteredSkills(all)
        assertEquals(all.size, filtered.size)
    }

    @Test
    fun `MOBILITY 카테고리 필터 — get_directions, get_directions_naver, get_transit_route 포함`() = runTest {
        vm.selectCategory(SkillStoreViewModel.Category.MOBILITY)
        val all = vm.skills.first()
        val filtered = vm.filteredSkills(all)
        val names = filtered.map { it.skillName }
        assertTrue(names.contains("get_directions"))
        assertTrue(names.contains("get_directions_naver"))
        assertTrue(names.contains("get_transit_route"))
        assertFalse(names.contains("get_weather"))
        assertFalse(names.contains("search_places"))
    }

    @Test
    fun `LIFE 카테고리 필터 — search_knowledge 포함`() = runTest {
        vm.selectCategory(SkillStoreViewModel.Category.LIFE)
        val all = vm.skills.first()
        val filtered = vm.filteredSkills(all)
        val names = filtered.map { it.skillName }
        assertTrue(names.contains("search_knowledge"))
        assertFalse(names.contains("get_directions"))
    }

    @Test
    fun `FOOD 카테고리 필터 — search_places, get_gas_stations 포함`() = runTest {
        vm.selectCategory(SkillStoreViewModel.Category.FOOD)
        val all = vm.skills.first()
        val filtered = vm.filteredSkills(all)
        val names = filtered.map { it.skillName }
        assertTrue(names.contains("search_places"))
        assertTrue(names.contains("get_gas_stations"))
        assertFalse(names.contains("get_weather"))
    }

    @Test
    fun `INFRA 카테고리 필터 — get_weather 포함`() = runTest {
        vm.selectCategory(SkillStoreViewModel.Category.INFRA)
        val all = vm.skills.first()
        val filtered = vm.filteredSkills(all)
        val names = filtered.map { it.skillName }
        assertTrue(names.contains("get_weather"))
        assertFalse(names.contains("get_directions"))
    }

    @Test
    fun `selectCategory 호출 후 selectedCategory StateFlow 업데이트`() = runTest {
        vm.selectCategory(SkillStoreViewModel.Category.MOBILITY)
        assertEquals(SkillStoreViewModel.Category.MOBILITY, vm.selectedCategory.first())

        vm.selectCategory(SkillStoreViewModel.Category.INFRA)
        assertEquals(SkillStoreViewModel.Category.INFRA, vm.selectedCategory.first())
    }

    @Test
    fun `resetSkill은 manager-resetHealth를 호출하고 skills를 갱신한다`() = runTest {
        vm.resetSkill("get_transit_route")
        verify { manager.resetHealth("get_transit_route") }
        verify(atLeast = 2) { manager.getAllHealth() } // init + after reset
    }

    @Test
    fun `skills StateFlow 초기값은 getAllHealth 반환값과 동일`() = runTest {
        val skills = vm.skills.first()
        assertEquals(sampleSkills.size, skills.size)
        assertEquals("get_directions", skills[0].skillName)
    }

    @Test
    fun `Category enum은 5개`() {
        assertEquals(5, SkillStoreViewModel.Category.entries.size)
    }

    @Test
    fun `빈 스킬 목록에서 filteredSkills는 빈 리스트 반환`() = runTest {
        every { manager.getAllHealth() } returns emptyList()
        val emptyVm = SkillStoreViewModel(manager, catalog)
        val filtered = emptyVm.filteredSkills(emptyList())
        assertTrue(filtered.isEmpty())
    }
}
