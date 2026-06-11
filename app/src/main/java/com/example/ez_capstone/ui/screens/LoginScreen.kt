package com.example.ez_capstone.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.ui.theme.AccentEnd
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Secondary
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.viewmodel.LoginState
import com.example.ez_capstone.viewmodel.LoginViewModel
import com.kakao.sdk.user.UserApiClient

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as Activity
    val loginState by viewModel.loginState.collectAsState()

    LaunchedEffect(loginState) {
        when (loginState) {
            is LoginState.Success -> onLoginSuccess()
            is LoginState.Error -> {
                Toast.makeText(context, (loginState as LoginState.Error).message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "EZmap",
                style = MaterialTheme.typography.headlineLarge,
                color = Secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI 내비게이션 비서",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(48.dp))

            when (loginState) {
                is LoginState.Loading -> {
                    CircularProgressIndicator(color = Secondary)
                }
                else -> {
                    Button(
                        onClick = {
                            if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                                UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
                                    if (error != null) {
                                        loginWithKakaoAccount(activity, viewModel)
                                    } else if (token != null) {
                                        viewModel.loginWithKakao(token.accessToken)
                                    }
                                }
                            } else {
                                loginWithKakaoAccount(activity, viewModel)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEnd),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("카카오 로그인", color = TextPrimary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 게스트 모드 — 로그인 없이 시작
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.skipLogin() }
                    ) {
                        Text("로그인 없이 시작", color = com.example.ez_capstone.ui.theme.TextSecondary)
                    }
                }
            }
        }
    }
}

private fun loginWithKakaoAccount(activity: Activity, viewModel: LoginViewModel) {
    UserApiClient.instance.loginWithKakaoAccount(activity) { token, error ->
        if (error != null) {
            // handled by state
        } else if (token != null) {
            viewModel.loginWithKakao(token.accessToken)
        }
    }
}
