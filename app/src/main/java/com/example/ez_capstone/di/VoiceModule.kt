package com.example.ez_capstone.di

import android.content.Context
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.voice.LiveFunctionCallBridge
import com.example.ez_capstone.voice.LiveVoiceSession
import com.example.ez_capstone.voice.PorcupineWakeWordEngine
import com.example.ez_capstone.voice.VoskWakeWordEngine
import com.example.ez_capstone.voice.VoiceStateCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideVoskWakeWordEngine(
        @ApplicationContext context: Context
    ): VoskWakeWordEngine = VoskWakeWordEngine(context)

    @Provides
    @Singleton
    fun providePorcupineWakeWordEngine(
        @ApplicationContext context: Context
    ): PorcupineWakeWordEngine = PorcupineWakeWordEngine(context)

    @Provides
    @Singleton
    fun provideVoiceStateCoordinator(
        voskEngine: VoskWakeWordEngine,
        porcupineEngine: PorcupineWakeWordEngine,
        apiKeyProvider: ApiKeyProvider,
        liveVoiceSession: LiveVoiceSession,
        liveFunctionCallBridge: LiveFunctionCallBridge,
        @ApplicationContext context: Context
    ): VoiceStateCoordinator = VoiceStateCoordinator(
        voskEngine = voskEngine,
        porcupineEngine = porcupineEngine,
        context = context,
        apiKeyProvider = apiKeyProvider,
        liveVoiceSession = liveVoiceSession,
        liveFunctionCallBridge = liveFunctionCallBridge
    )
}
