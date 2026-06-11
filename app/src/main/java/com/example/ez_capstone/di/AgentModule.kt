package com.example.ez_capstone.di

import com.example.ez_capstone.agent.FallbackStrategy
import com.example.ez_capstone.resilience.SkillCache
import com.example.ez_capstone.agent.GeminiAgentEngine
import com.example.ez_capstone.agent.GeminiApiClient
import com.example.ez_capstone.agent.SystemPrompt
import com.example.ez_capstone.agent.ToolExecutor
import com.example.ez_capstone.context.AmbientContextEngine
import com.example.ez_capstone.governance.PermissionManager
import com.example.ez_capstone.memory.ConversationMemory
import com.example.ez_capstone.memory.MemoryGovernor
import com.example.ez_capstone.resilience.NetworkMonitor
import com.example.ez_capstone.safety.SafetyPolicyEngine
import com.example.ez_capstone.db.dao.AgentNoteDao
import com.example.ez_capstone.skill.CustomRoutineDao
import com.example.ez_capstone.skill.RoutineDetector
import com.example.ez_capstone.skill.SkillLifecycleManager
import com.example.ez_capstone.trace.DecisionTraceDao
import com.example.ez_capstone.api.EvChargerApi
import com.example.ez_capstone.api.KakaoLocalApi
import com.example.ez_capstone.api.KakaoMobilityApi
import com.example.ez_capstone.api.KmaWeatherApi
import com.example.ez_capstone.api.OpinetApi
import com.example.ez_capstone.api.NaverDirectionsApi
import com.example.ez_capstone.api.OdsayApi
import com.example.ez_capstone.api.ParkingApi
import com.example.ez_capstone.api.WeatherApi
import com.example.ez_capstone.api.AirKoreaApi
import com.example.ez_capstone.api.WikipediaApi
import com.example.ez_capstone.api.WebFetchApi
import com.example.ez_capstone.api.ExchangeRateApi
import com.example.ez_capstone.api.MedicalApi
import com.example.ez_capstone.api.TrafficInfoApi
import com.example.ez_capstone.api.HighwayApi
import com.example.ez_capstone.api.RealtimeParkingApi
import com.example.ez_capstone.api.RoadRiskApi
import com.example.ez_capstone.api.SpeedCameraApi
import com.example.ez_capstone.api.IncidentApi
import com.example.ez_capstone.api.RestAreaApi
import com.example.ez_capstone.api.SpotifyApi
import com.example.ez_capstone.analytics.AgentAnalytics
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.*
import com.example.ez_capstone.profile.MultiProfileManager
import com.example.ez_capstone.security.SensitiveDataMasker
import android.content.Context
import android.content.SharedPreferences
import com.example.ez_capstone.offline.OnDeviceLlm
import com.example.ez_capstone.predictive.PredictiveCache
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    @Provides
    @Singleton
    @Named("offline_cache_prefs")
    fun provideOfflineCachePrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("ezmap_offline_cache", Context.MODE_PRIVATE)

    @Provides @Singleton
    fun provideApiKeyProvider(@ApplicationContext context: Context): ApiKeyProvider =
        ApiKeyProvider(context)

    @Provides @Singleton
    fun provideKakaoLocalApi(apiKeyProvider: ApiKeyProvider): KakaoLocalApi =
        KakaoLocalApi(apiKeyProvider)

    @Provides @Singleton
    fun provideKakaoMobilityApi(apiKeyProvider: ApiKeyProvider): KakaoMobilityApi =
        KakaoMobilityApi(apiKeyProvider)

    @Provides @Singleton
    fun provideWeatherApi(apiKeyProvider: ApiKeyProvider): WeatherApi =
        WeatherApi(apiKeyProvider)

    @Provides @Singleton
    fun provideGeminiApiClient(
        apiKeyProvider: ApiKeyProvider,
        @Named("gemini") okHttpClient: OkHttpClient
    ): GeminiApiClient =
        GeminiApiClient(apiKeyProvider, okHttpClient)

    @Provides
    @Named("gemini_api_key")
    fun provideGeminiApiKey(apiKeyProvider: ApiKeyProvider): String =
        apiKeyProvider.activeGeminiKey

    @Provides @Singleton
    fun provideConversationMemory(
        conversationDao: ConversationDao,
        preferenceDao: PreferenceDao,
        multiProfileManager: MultiProfileManager
    ): ConversationMemory = ConversationMemory(conversationDao, preferenceDao, multiProfileManager)

    @Provides @Singleton
    fun provideAmbientContextEngine(
        scheduleDao: ScheduleDao,
        routeHistoryDao: RouteHistoryDao
    ): AmbientContextEngine = AmbientContextEngine(scheduleDao, routeHistoryDao)

    @Provides @Singleton
    fun provideMemoryGovernor(
        conversationDao: ConversationDao,
        preferenceDao: PreferenceDao,
        decisionTraceDao: DecisionTraceDao
    ): MemoryGovernor = MemoryGovernor(conversationDao, preferenceDao, decisionTraceDao)

    @Provides @Singleton
    fun provideSystemPrompt(
        profileDao: ProfileDao,
        conversationMemory: ConversationMemory,
        ambientContextEngine: AmbientContextEngine,
        agentNoteDao: AgentNoteDao
    ): SystemPrompt = SystemPrompt(profileDao, conversationMemory, ambientContextEngine, agentNoteDao)

    @Provides @Singleton
    fun provideOpinetApi(apiKeyProvider: ApiKeyProvider): OpinetApi = OpinetApi(apiKeyProvider)

    @Provides @Singleton
    fun provideEvChargerApi(apiKeyProvider: ApiKeyProvider): EvChargerApi = EvChargerApi(apiKeyProvider)

    @Provides @Singleton
    fun provideKmaWeatherApi(apiKeyProvider: ApiKeyProvider): KmaWeatherApi = KmaWeatherApi(apiKeyProvider)

    @Provides @Singleton
    fun provideParkingApi(apiKeyProvider: ApiKeyProvider): ParkingApi = ParkingApi(apiKeyProvider)

    @Provides @Singleton
    fun provideNaverDirectionsApi(apiKeyProvider: ApiKeyProvider): NaverDirectionsApi =
        NaverDirectionsApi(apiKeyProvider)

    @Provides @Singleton
    fun provideOdsayApi(apiKeyProvider: ApiKeyProvider): OdsayApi = OdsayApi(apiKeyProvider)

    @Provides @Singleton
    fun provideAirKoreaApi(apiKeyProvider: ApiKeyProvider): AirKoreaApi = AirKoreaApi(apiKeyProvider)

    @Provides @Singleton
    fun provideWikipediaApi(): WikipediaApi = WikipediaApi()

    @Provides @Singleton
    fun provideWebFetchApi(): WebFetchApi = WebFetchApi()

    @Provides @Singleton
    fun provideExchangeRateApi(): ExchangeRateApi = ExchangeRateApi()

    @Provides @Singleton
    fun provideMedicalApi(apiKeyProvider: ApiKeyProvider): MedicalApi = MedicalApi(apiKeyProvider)

    @Provides @Singleton
    fun provideTrafficInfoApi(apiKeyProvider: ApiKeyProvider): TrafficInfoApi = TrafficInfoApi(apiKeyProvider)

    @Provides @Singleton
    fun provideHighwayApi(apiKeyProvider: ApiKeyProvider): HighwayApi = HighwayApi(apiKeyProvider)

    @Provides @Singleton
    fun provideRealtimeParkingApi(apiKeyProvider: ApiKeyProvider): RealtimeParkingApi = RealtimeParkingApi(apiKeyProvider)

    @Provides @Singleton
    fun provideRoadRiskApi(apiKeyProvider: ApiKeyProvider): RoadRiskApi = RoadRiskApi(apiKeyProvider)

    @Provides @Singleton
    fun provideSpeedCameraApi(apiKeyProvider: ApiKeyProvider): SpeedCameraApi = SpeedCameraApi(apiKeyProvider)

    @Provides @Singleton
    fun provideIncidentApi(apiKeyProvider: ApiKeyProvider): IncidentApi = IncidentApi(apiKeyProvider)

    @Provides @Singleton
    fun provideRestAreaApi(apiKeyProvider: ApiKeyProvider): RestAreaApi = RestAreaApi(apiKeyProvider)

    @Provides @Singleton
    fun provideToolExecutor(
        kakaoLocalApi: KakaoLocalApi,
        kakaoMobilityApi: KakaoMobilityApi,
        weatherApi: WeatherApi,
        opinetApi: OpinetApi,
        evChargerApi: EvChargerApi,
        kmaWeatherApi: KmaWeatherApi,
        parkingApi: ParkingApi,
        naverDirectionsApi: NaverDirectionsApi,
        odsayApi: OdsayApi,
        airKoreaApi: AirKoreaApi,
        wikipediaApi: WikipediaApi,
        webFetchApi: WebFetchApi,
        exchangeRateApi: ExchangeRateApi,
        medicalApi: MedicalApi,
        trafficInfoApi: TrafficInfoApi,
        highwayApi: HighwayApi,
        realtimeParkingApi: RealtimeParkingApi,
        roadRiskApi: RoadRiskApi,
        speedCameraApi: SpeedCameraApi,
        incidentApi: IncidentApi,
        restAreaApi: RestAreaApi,
        spotifyApi: SpotifyApi,
        apiKeyProvider: ApiKeyProvider,
        profileDao: ProfileDao,
        preferenceDao: PreferenceDao,
        routeHistoryDao: RouteHistoryDao,
        contactDao: ContactDao,
        scheduleDao: ScheduleDao,
        frequentPlaceDao: FrequentPlaceDao,
        agentNoteDao: AgentNoteDao,
        gson: Gson,
        multiProfileManager: MultiProfileManager,
        @ApplicationContext appContext: Context,
        mcpGateway: com.example.ez_capstone.mcp.client.McpToolGateway
    ): ToolExecutor = ToolExecutor(
        kakaoLocalApi, kakaoMobilityApi, weatherApi,
        opinetApi, evChargerApi, kmaWeatherApi, parkingApi,
        naverDirectionsApi, odsayApi,
        airKoreaApi, wikipediaApi, webFetchApi, exchangeRateApi, medicalApi,
        trafficInfoApi, highwayApi, realtimeParkingApi, roadRiskApi,
        speedCameraApi, incidentApi, restAreaApi,
        spotifyApi,
        apiKeyProvider,
        profileDao, preferenceDao, routeHistoryDao, contactDao, scheduleDao,
        frequentPlaceDao, agentNoteDao, gson, multiProfileManager, appContext,
        mcpGateway
    )

    @Provides @Singleton
    fun provideFallbackStrategy(
        apiKeyProvider: ApiKeyProvider,
        skillLifecycleManager: SkillLifecycleManager,
        skillCache: SkillCache
    ): FallbackStrategy = FallbackStrategy(apiKeyProvider, skillLifecycleManager, skillCache)

    @Provides @Singleton
    fun provideSafetyPolicyEngine(): SafetyPolicyEngine = SafetyPolicyEngine()

    @Provides @Singleton
    fun provideRoutineDetector(
        routeHistoryDao: RouteHistoryDao,
        conversationDao: ConversationDao,
        gson: Gson
    ): RoutineDetector = RoutineDetector(routeHistoryDao, conversationDao, gson)

    @Provides @Singleton
    fun provideSkillLifecycleManager(
        @ApplicationContext context: Context,
        apiKeyProvider: ApiKeyProvider
    ): SkillLifecycleManager = SkillLifecycleManager(context, apiKeyProvider)

    @Provides @Singleton
    fun provideGeminiAgentEngine(
        apiClient: GeminiApiClient,
        apiKeyProvider: ApiKeyProvider,
        systemPrompt: SystemPrompt,
        toolExecutor: ToolExecutor,
        fallbackStrategy: FallbackStrategy,
        safetyPolicyEngine: SafetyPolicyEngine,
        permissionManager: PermissionManager,
        networkMonitor: NetworkMonitor,
        conversationDao: ConversationDao,
        decisionTraceDao: DecisionTraceDao,
        gson: Gson,
        sensitiveDataMasker: SensitiveDataMasker,
        agentAnalytics: AgentAnalytics,
        onDeviceLlm: OnDeviceLlm,
        predictiveCache: PredictiveCache,
        skillMatcher: com.example.ez_capstone.skill.SkillMatcher,
        skillExecutor: com.example.ez_capstone.skill.SkillExecutor,
        skillLearner: com.example.ez_capstone.skill.SkillLearner,
        mcpGateway: com.example.ez_capstone.mcp.client.McpToolGateway
    ): GeminiAgentEngine = GeminiAgentEngine(
        apiClient, apiKeyProvider, systemPrompt, toolExecutor,
        fallbackStrategy, safetyPolicyEngine, permissionManager, networkMonitor,
        conversationDao, decisionTraceDao, gson, sensitiveDataMasker, agentAnalytics,
        onDeviceLlm, predictiveCache,
        skillMatcher, skillExecutor, skillLearner,
        mcpGateway
    )
}
