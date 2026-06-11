package com.example.ez_capstone.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Android Auto CarAppService entry point for EZmap.
 * SAFETY-CRITICAL: Only handles 2 quick shortcuts (집/회사).
 * Voice-first UX — tapping is secondary and limited by template constraints.
 */
class EZmapCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR  // dev only; prod: allowlisted hosts

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return object : Session() {
            override fun onCreateScreen(intent: android.content.Intent) =
                AutoMainScreen(carContext)
        }
    }
}
