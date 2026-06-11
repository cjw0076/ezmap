package com.example.ez_capstone.skill

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SkillMeta(
    val name: String,
    val displayName: String,
    val category: String,
    val description: String,
    val requiredKey: String,
    val tier: String   // "essential" | "premium"
)

/**
 * assets/skill_catalog.json에서 스킬 메타데이터를 로드.
 * GitHub 원격 카탈로그의 온디바이스 번들 대체 구현.
 */
@Singleton
class SkillCatalogLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val catalog: Map<String, SkillMeta> by lazy { loadFromAssets() }

    fun getMeta(skillName: String): SkillMeta? = catalog[skillName]

    fun getAll(): List<SkillMeta> = catalog.values.toList()

    fun getByCategory(category: String): List<SkillMeta> =
        catalog.values.filter { it.category == category }

    private fun loadFromAssets(): Map<String, SkillMeta> {
        return try {
            val json = context.assets.open("skill_catalog.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val skills = root.getJSONArray("skills")
            buildMap {
                for (i in 0 until skills.length()) {
                    val obj = skills.getJSONObject(i)
                    val meta = SkillMeta(
                        name = obj.getString("name"),
                        displayName = obj.getString("displayName"),
                        category = obj.getString("category"),
                        description = obj.getString("description"),
                        requiredKey = obj.getString("requiredKey"),
                        tier = obj.getString("tier")
                    )
                    put(meta.name, meta)
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
