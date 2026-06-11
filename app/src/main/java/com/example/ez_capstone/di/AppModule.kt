package com.example.ez_capstone.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    @Named("analytics_prefs")
    fun provideAnalyticsPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("ezmap_analytics", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @Named("profile_mode_prefs")
    fun provideProfileModePrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("ezmap_profile_mode", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @Named("predictive_cache_prefs")
    fun providePredictiveCachePrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("ezmap_predictive_cache", Context.MODE_PRIVATE)

    // 백그라운드 워커 등록은 BackgroundWorkScheduler.schedule()로 EZApplication.onCreate에서 명시 호출.
    // (이전엔 @Provides WorkManager 안에 enqueue가 있었으나 아무도 주입하지 않아 미실행이었음.)
}
