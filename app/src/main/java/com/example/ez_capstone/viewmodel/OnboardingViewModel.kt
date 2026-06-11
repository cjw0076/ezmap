package com.example.ez_capstone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.config.ApiKeyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

sealed class ValidationResult {
    data object Idle : ValidationResult()
    data object Loading : ValidationResult()
    data object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    private val httpClient: OkHttpClient,
) : ViewModel() {

    private val _geminiValidation = MutableStateFlow<ValidationResult>(ValidationResult.Idle)
    val geminiValidation: StateFlow<ValidationResult> = _geminiValidation.asStateFlow()

    private val _kakaoValidation = MutableStateFlow<ValidationResult>(ValidationResult.Idle)
    val kakaoValidation: StateFlow<ValidationResult> = _kakaoValidation.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    fun setTrialMode() {
        apiKeyProvider.isTrialMode = true
        apiKeyProvider.onboardingCompleted = true
    }

    fun nextStep() { _currentStep.value++ }
    fun prevStep() { if (_currentStep.value > 0) _currentStep.value-- }

    fun validateGeminiKey(key: String) {
        _geminiValidation.value = ValidationResult.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val body = """{"contents":[{"parts":[{"text":"hello"}]}]}"""
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(request).execute().use { result ->
                        if (result.isSuccessful) {
                            apiKeyProvider.geminiKey = key
                            _geminiValidation.value = ValidationResult.Success
                        } else {
                            _geminiValidation.value = ValidationResult.Error("API 키가 유효하지 않습니다 (${result.code})")
                        }
                    }
                }
            } catch (e: Exception) {
                _geminiValidation.value = ValidationResult.Error("네트워크 오류: ${e.message}")
            }
        }
    }

    fun validateKakaoKey(key: String) {
        _kakaoValidation.value = ValidationResult.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("https://dapi.kakao.com/v2/local/search/keyword.json?query=울산")
                        .addHeader("Authorization", "KakaoAK $key")
                        .build()
                    httpClient.newCall(request).execute().use { result ->
                        if (result.isSuccessful) {
                            apiKeyProvider.kakaoRestKey = key
                            _kakaoValidation.value = ValidationResult.Success
                        } else {
                            _kakaoValidation.value = ValidationResult.Error("API 키가 유효하지 않습니다 (${result.code})")
                        }
                    }
                }
            } catch (e: Exception) {
                _kakaoValidation.value = ValidationResult.Error("네트워크 오류: ${e.message}")
            }
        }
    }

    /** Step 3: 공공데이터 팩 저장 — data.go.kr 키 1개 + 오피넷 키 */
    fun savePublicDataPack(dataGoKr: String, opinet: String) {
        if (dataGoKr.isNotBlank()) apiKeyProvider.dataGoKrKey = dataGoKr
        if (opinet.isNotBlank()) apiKeyProvider.opinetKey = opinet
    }

    /** Step 4: 프리미엄 팩 저장 — 네이버 + ODsay */
    fun savePremiumPack(naverId: String, naverSecret: String, odsay: String) {
        if (naverId.isNotBlank()) apiKeyProvider.naverClientId = naverId
        if (naverSecret.isNotBlank()) apiKeyProvider.naverClientSecret = naverSecret
        if (odsay.isNotBlank()) apiKeyProvider.odsayKey = odsay
    }

    /** 하위 호환: 기존 saveOptionalKeys 유지 */
    fun saveOptionalKeys(dataGoKr: String, opinet: String, naverId: String, naverSecret: String, odsay: String) {
        savePublicDataPack(dataGoKr, opinet)
        savePremiumPack(naverId, naverSecret, odsay)
    }

    fun completeOnboarding() {
        apiKeyProvider.isTrialMode = false
        apiKeyProvider.onboardingCompleted = true
    }

    /** 팩별 활성화 기능 수 */
    val publicDataFeatureCount: Int get() = if (apiKeyProvider.isPublicDataConfigured) 7 else 0
    val premiumFeatureCount: Int get() = if (apiKeyProvider.isPremiumConfigured) 2 else 0
}
