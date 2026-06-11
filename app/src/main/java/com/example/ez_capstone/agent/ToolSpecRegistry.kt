package com.example.ez_capstone.agent

import com.example.ez_capstone.safety.ToolRiskTier
import org.json.JSONArray
import org.json.JSONObject

enum class ToolExposure {
    GEMINI_FUNCTION,
    LIVE_API,
    EXECUTOR
}

enum class ToolExternalExposure {
    NONE,
    MCP_SERVER,
    A2A_AGENT
}

class ToolSpec(
    val name: String,
    val description: String,
    parameters: JSONObject,
    val keyGroup: String,
    val kind: ToolKind,
    val riskTier: ToolRiskTier,
    val exposures: Set<ToolExposure>,
    val externalExposure: ToolExternalExposure = ToolExternalExposure.NONE
) {
    private val parameterSchema = parameters.toString()

    val parameters: JSONObject
        get() = JSONObject(parameterSchema)

    fun toFunctionDeclaration(): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", parameters)

    fun copy(
        name: String = this.name,
        description: String = this.description,
        parameters: JSONObject = this.parameters,
        keyGroup: String = this.keyGroup,
        kind: ToolKind = this.kind,
        riskTier: ToolRiskTier = this.riskTier,
        exposures: Set<ToolExposure> = this.exposures,
        externalExposure: ToolExternalExposure = this.externalExposure
    ): ToolSpec =
        ToolSpec(name, description, parameters, keyGroup, kind, riskTier, exposures, externalExposure)
}

class ToolRegistry private constructor(private val specs: List<ToolSpec>) {

    val names: List<String> = specs.map { it.name }

    val kinds: Map<String, ToolKind> = specs.associate { it.name to it.kind }

    fun allSpecs(): List<ToolSpec> = specs.toList()

    fun requireSpec(name: String): ToolSpec =
        specOrNull(name) ?: error("Unknown tool spec: $name")

    fun specOrNull(name: String): ToolSpec? =
        specs.firstOrNull { it.name == name }

    fun functionDeclarations(): JSONArray =
        declarationsFor(exposure = ToolExposure.GEMINI_FUNCTION)

    fun liveApiFormat(): JSONObject =
        JSONObject().put(
            "functionDeclarations",
            declarationsFor(exposure = ToolExposure.LIVE_API)
        )

    fun declarationsFor(
        enabledKeyGroups: Set<String>? = null,
        allowedNames: Set<String>? = null,
        exposure: ToolExposure = ToolExposure.GEMINI_FUNCTION
    ): JSONArray {
        val arr = JSONArray()
        specs
            .asSequence()
            .filter { enabledKeyGroups == null || it.keyGroup in enabledKeyGroups }
            .filter { allowedNames == null || it.name in allowedNames }
            .filter { exposure in it.exposures }
            .forEach { arr.put(it.toFunctionDeclaration()) }
        return arr
    }

    fun liveApiFormat(
        enabledKeyGroups: Set<String>? = null,
        allowedNames: Set<String>? = null
    ): JSONObject =
        JSONObject().put(
            "functionDeclarations",
            declarationsFor(enabledKeyGroups, allowedNames, ToolExposure.LIVE_API)
        )

    fun specsForExternalExposure(exposure: ToolExternalExposure): List<ToolSpec> =
        specs.filter { it.externalExposure == exposure }

    companion object {
        val builtIn: ToolRegistry by lazy {
            from(ToolDeclarations.allToolSpecs())
        }

        fun from(specs: List<ToolSpec>): ToolRegistry {
            require(specs.isNotEmpty()) { "ToolRegistry requires at least one ToolSpec." }
            val names = mutableSetOf<String>()
            for (spec in specs) {
                validateSpec(spec)
                require(names.add(spec.name)) { "Duplicate tool name: ${spec.name}" }
            }
            return ToolRegistry(specs.toList())
        }

        private fun validateSpec(spec: ToolSpec) {
            require(spec.name.isNotBlank()) { "ToolSpec name must not be blank." }
            require(spec.description.isNotBlank()) { "ToolSpec description must not be blank: ${spec.name}" }
            require(spec.keyGroup.isNotBlank()) { "ToolSpec keyGroup must not be blank: ${spec.name}" }
            require(spec.exposures.isNotEmpty()) { "ToolSpec exposures must not be empty: ${spec.name}" }
            require(spec.parameters.optString("type") == "object") {
                "ToolSpec parameters must be an object schema: ${spec.name}"
            }
            require(spec.parameters.has("properties")) {
                "ToolSpec parameters must declare properties: ${spec.name}"
            }
        }
    }
}

internal object ToolSpecMetadata {

    val KIND_BY_NAME: Map<String, ToolKind> = buildMap {
        listOf(
            "search_places", "geocode", "reverse_geocode", "get_directions", "get_future_eta",
            "get_schedule", "get_notes", "get_user_preferences", "get_user_profile",
            "lookup_contact", "get_weather", "analyze_route_patterns", "get_gas_stations",
            "get_ev_chargers", "get_weather_kma", "get_parking", "get_directions_naver",
            "get_transit_route", "get_air_quality", "search_knowledge", "get_exchange_rate", "fetch_url",
            "search_pharmacies", "search_hospitals", "get_traffic_speed", "get_traffic_incidents",
            "get_traffic_cctv", "get_highway_alerts", "get_realtime_parking", "get_road_risk",
            "get_speed_cameras", "get_road_incidents", "get_rest_areas", "suggest_parking",
            "current_track"
        ).forEach { put(it, ToolKind.READ) }

        listOf(
            "save_note", "delete_note", "update_user_preferences", "update_user_profile",
            "manage_contacts", "create_schedule", "update_schedule", "delete_schedule",
            "set_alarm", "manage_favorites",
            "play_music", "pause_music", "next_track", "previous_track"
        ).forEach { put(it, ToolKind.WRITE) }

        listOf("send_message", "make_call").forEach { put(it, ToolKind.SENSITIVE) }
    }

    val RISK_BY_NAME: Map<String, ToolRiskTier> = buildMap {
        listOf(
            "search_places", "geocode", "reverse_geocode", "get_future_eta",
            "get_weather", "get_weather_kma", "get_air_quality", "get_exchange_rate",
            "search_knowledge", "fetch_url", "search_pharmacies", "search_hospitals",
            "get_gas_stations", "get_ev_chargers", "get_parking", "get_realtime_parking",
            "suggest_parking", "get_traffic_speed", "get_traffic_incidents", "get_traffic_cctv",
            "get_highway_alerts", "get_road_risk", "get_speed_cameras", "get_road_incidents",
            "get_rest_areas", "get_user_profile", "get_user_preferences", "get_schedule",
            "get_notes", "analyze_route_patterns", "lookup_contact", "current_track"
        ).forEach { put(it, ToolRiskTier.SAFE) }

        listOf(
            "get_directions", "get_directions_naver", "get_transit_route",
            "update_user_profile", "update_user_preferences", "create_schedule", "update_schedule",
            "delete_schedule", "save_note", "delete_note", "manage_favorites", "manage_contacts",
            "set_alarm", "play_music", "pause_music", "next_track", "previous_track"
        ).forEach { put(it, ToolRiskTier.STATEFUL) }

        listOf("make_call", "send_message").forEach { put(it, ToolRiskTier.EFFECTFUL) }
    }

    fun requireKind(name: String): ToolKind =
        KIND_BY_NAME[name] ?: error("Missing ToolKind metadata: $name")

    fun requireRiskTier(name: String): ToolRiskTier =
        RISK_BY_NAME[name] ?: error("Missing ToolRiskTier metadata: $name")

    val MCP_SERVER_EXPOSED_NAMES: Set<String> = setOf(
        "search_places",
        "get_directions",
        "get_weather",
        "get_transit_route",
        "get_gas_stations",
        "get_ev_chargers",
        "get_parking",
        "get_air_quality",
        "search_pharmacies",
        "search_hospitals",
        "get_exchange_rate",
        "search_knowledge"
    )

    fun externalExposureOf(name: String): ToolExternalExposure =
        if (name in MCP_SERVER_EXPOSED_NAMES) ToolExternalExposure.MCP_SERVER else ToolExternalExposure.NONE
}
