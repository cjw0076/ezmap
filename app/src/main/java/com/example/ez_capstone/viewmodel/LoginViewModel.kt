package com.example.ez_capstone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.db.entity.ProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val apiKeyProvider: ApiKeyProvider
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    /**
     * 카카오 로그인 성공 후 — 서버 없이 로컬 프로필에 저장.
     * accessToken은 카카오 API 호출용으로 로컬 보관만.
     */
    fun loginWithKakao(accessToken: String) {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                // 프로필이 없으면 기본 프로필 생성
                val existing = profileDao.getProfile()
                if (existing == null) {
                    profileDao.upsertProfile(ProfileEntity())
                }
                _loginState.value = LoginState.Success
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("로그인 처리 실패: ${e.message}")
            }
        }
    }

    /**
     * 카카오 로그인 건너뛰기 — 게스트 모드.
     */
    fun skipLogin() {
        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val existing = profileDao.getProfile()
            if (existing == null) {
                profileDao.upsertProfile(ProfileEntity())
            }
            _loginState.value = LoginState.Success
        }
    }
}
