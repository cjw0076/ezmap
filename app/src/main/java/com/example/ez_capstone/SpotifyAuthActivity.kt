package com.example.ez_capstone

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.ez_capstone.config.SpotifyAuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Spotify OAuth redirect(ezmap://spotify/callback) 수신 액티비티.
 * Custom Tab에서 인증 완료 후 돌아온 인가 코드를 토큰으로 교환하고 종료한다.
 */
@AndroidEntryPoint
class SpotifyAuthActivity : ComponentActivity() {

    @Inject lateinit var authManager: SpotifyAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data
        val code = uri?.getQueryParameter("code")
        val error = uri?.getQueryParameter("error")
        val returnedState = uri?.getQueryParameter("state")

        if (error != null || code.isNullOrBlank()) {
            Log.w("SpotifyAuth", "Auth canceled/failed: error=$error")
            Toast.makeText(this, "스포티파이 연결이 취소됐어요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // CSRF/Authorization Code Injection 방지 — 저장한 state와 일치해야만 진행.
        val savedState = authManager.savedAuthState()
        if (savedState.isBlank() || returnedState != savedState) {
            Log.e("SpotifyAuth", "State mismatch — possible CSRF (saved=$savedState, returned=$returnedState)")
            Toast.makeText(this, "인증 보안 검증에 실패했어요. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val ok = authManager.exchangeCode(code)
            Toast.makeText(
                this@SpotifyAuthActivity,
                if (ok) "스포티파이 연결 완료!" else "스포티파이 연결에 실패했어요.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
