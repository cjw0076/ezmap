package com.example.ez_capstone.server

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // 개발: http://10.0.2.2:3000/ (에뮬레이터) 또는 http://<PC_IP>:3000/ (실기기)
    // 배포: https://<EC2 도메인>/
    // 에뮬레이터: http://10.0.2.2:3000/ | 실기기(adb reverse): http://localhost:3000/
    const val BASE_URL = "http://52.79.105.63/"

    private val authInterceptor = Interceptor { chain ->
        val token = TokenManager.getToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS  // BODY → HEADERS: 경로 좌표 로깅 방지 (성능 개선)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val routeApi: RouteApi = retrofit.create(RouteApi::class.java)
    val chatApi: ChatApi = retrofit.create(ChatApi::class.java)
    val scheduleApi: ScheduleApi = retrofit.create(ScheduleApi::class.java)
    val voiceApi: VoiceApi = retrofit.create(VoiceApi::class.java)
}
