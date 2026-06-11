package com.example.ez_capstone.di

import com.example.ez_capstone.BuildConfig
import com.example.ez_capstone.server.AuthApi
import com.example.ez_capstone.server.HealthApi
import com.example.ez_capstone.server.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 서버 API는 거의 사용하지 않음 (온디바이스 전환 완료).
 * 남아있는 것: AuthApi (카카오 로그인), HealthApi (서버 상태 체크)
 * 향후 카카오 로그인도 로컬로 전환하면 이 모듈 전체 삭제 가능.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "http://52.79.105.63/"

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
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
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Certificate-pinned OkHttpClient for on-device AI/map API calls.
     *
     * Pinning is DISABLED in DEBUG builds so developers can use Charles/Fiddler proxies
     * and the app won't break on intermediate certificates during development.
     * In RELEASE builds, only known Google Trust Services and DigiCert root CA public keys
     * are accepted, protecting against MITM attacks on production devices.
     *
     * Pin sources:
     *  - GTS Root R1 (sha256/hxqRlPTu1bMS…) — Google primary root for googleapis.com
     *  - GTS Root R1 backup (sha256/58qRu/xt…) — Google backup root
     *  - DigiCert Global Root G2 (sha256/Dr7yKT…) — Kakao dapi root
     */
    @Provides @Singleton @Named("gemini")
    fun provideGeminiOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!BuildConfig.DEBUG) {
            // 여러 Google Trust Services 루트를 함께 핀해 인증서 로테이션에 견디게 한다.
            // OkHttp은 체인 내 인증서 중 하나라도 핀과 일치하면 통과시키므로, R1·R4를
            // 모두 핀하면 Google이 엔드포인트를 R1↔R4로 옮겨도 깨지지 않는다.
            // (R1만 핀했다가 generativelanguage가 GTS Root R4로 이전돼 릴리즈가 막힌 이력 → R4 추가)
            val GTS_ROOT_R1 = "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc="
            val GTS_ROOT_R1_BACKUP = "sha256/58qRu/xt/lD51HcvHKq3bIDOz60U4m/8EPhf5h4HZGQ="
            val GTS_ROOT_R4 = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="
            val DIGICERT_G2 = "sha256/Dr7yKTnHlnpVjFj+n9l2AvQVfMoACx1l1FMGx5F6Bts="
            val pinner = CertificatePinner.Builder()
                // generativelanguage.googleapis.com (현재 GTS Root R4 체인)
                .add("generativelanguage.googleapis.com", GTS_ROOT_R4)
                .add("generativelanguage.googleapis.com", GTS_ROOT_R1)
                .add("generativelanguage.googleapis.com", GTS_ROOT_R1_BACKUP)
                // dapi.kakao.com (DigiCert G2 / GTS 루트 혼용 대비)
                .add("dapi.kakao.com", DIGICERT_G2)
                .add("dapi.kakao.com", GTS_ROOT_R4)
                .add("dapi.kakao.com", GTS_ROOT_R1)
                .build()
            builder.certificatePinner(pinner)
        }

        return builder.build()
    }

    @Provides @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun provideHealthApi(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)
}
