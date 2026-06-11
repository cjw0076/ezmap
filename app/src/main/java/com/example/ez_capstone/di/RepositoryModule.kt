package com.example.ez_capstone.di

import com.example.ez_capstone.server.AuthApi
import com.example.ez_capstone.server.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 서버 Repository는 AuthRepository만 남음 (카카오 로그인용).
 * Route/Schedule Repository는 Room DB로 전환 완료.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides @Singleton
    fun provideAuthRepository(api: AuthApi): AuthRepository = AuthRepository(api)
}
