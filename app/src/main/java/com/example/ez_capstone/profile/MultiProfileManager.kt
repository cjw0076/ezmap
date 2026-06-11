package com.example.ez_capstone.profile

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class ProfileType { OWNER, FAMILY, GUEST, VALET }

data class ActiveProfile(
    val type: ProfileType,
    val name: String,
    val memoryEnabled: Boolean,
    val proactiveEnabled: Boolean,
    val blockedSkills: Set<String>
)

@Singleton
class MultiProfileManager @Inject constructor(
    @Named("profile_mode_prefs") private val prefs: SharedPreferences
) {
    companion object {
        private const val KEY_PROFILE_TYPE = "active_profile_type"
        val VALET_BLOCKED_SKILLS = setOf(
            "get_user_profile", "update_user_profile",
            "get_user_preferences", "update_user_preferences",
            "get_schedule", "send_message", "manage_contacts",
            "analyze_route_patterns"
        )
    }

    fun getActiveProfile(): ActiveProfile {
        val type = ProfileType.valueOf(
            prefs.getString(KEY_PROFILE_TYPE, ProfileType.OWNER.name) ?: ProfileType.OWNER.name
        )
        return when (type) {
            ProfileType.OWNER -> ActiveProfile(ProfileType.OWNER, "나", true, true, emptySet())
            ProfileType.GUEST -> ActiveProfile(ProfileType.GUEST, "게스트", false, false,
                setOf("get_user_profile", "update_user_profile", "get_schedule", "manage_contacts"))
            ProfileType.VALET -> ActiveProfile(ProfileType.VALET, "대리운전", false, false, VALET_BLOCKED_SKILLS)
            ProfileType.FAMILY -> ActiveProfile(ProfileType.FAMILY, "가족", true, false, emptySet())
        }
    }

    fun setProfileType(type: ProfileType) {
        prefs.edit().putString(KEY_PROFILE_TYPE, type.name).apply()
    }

    fun isOwner() = getActiveProfile().type == ProfileType.OWNER
    fun isGuest() = getActiveProfile().type == ProfileType.GUEST
    fun isValet() = getActiveProfile().type == ProfileType.VALET
}
