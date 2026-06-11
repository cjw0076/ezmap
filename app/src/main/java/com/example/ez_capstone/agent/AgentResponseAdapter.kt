package com.example.ez_capstone.agent

import android.util.Log
import com.example.ez_capstone.agent.models.AgentResponse
import org.json.JSONArray
import org.json.JSONObject

class AgentResponseAdapter {
    fun adapt(
        rawText: String,
        userText: String,
        toolStore: Map<String, Any>
    ): AgentResponse {
        val parsed = parseAgentResponse(rawText)
        val normalized = normalizeResponse(parsed, toolStore)
        return if (normalized.uiAction == "show_route" &&
            normalized.uiData["routes"] != null &&
            isDepartureIntent(userText)
        ) {
            Log.d(TAG, "Departure intent -> forcing start_navigation")
            normalized.copy(uiAction = "start_navigation")
        } else {
            normalized
        }
    }

    private fun isDepartureIntent(text: String): Boolean {
        val compact = text.replace(" ", "").lowercase()
        val questionMarkers = listOf(
            "어떻게가", "가는길", "경로알려", "얼마나걸려", "몇분", "몇시", "걸려", "걸리",
            "도착예정", "예정시간", "도착시간", "언제도착", "소요시간", "eta", "시간알려"
        )
        if (questionMarkers.any { compact.contains(it) }) return false
        if (compact.contains("출발하면") || compact.contains("출발하니") || compact.contains("출발하는")) {
            return false
        }
        val triggers = listOf("안내시작", "출발", "바로가", "바로안내", "데려다", "태워다", "가자", "출발하자")
        return triggers.any { compact.contains(it) }
    }

    private fun parseAgentResponse(text: String): AgentResponse {
        val jsonCandidate = text
            .replace(Regex("^```(?:json)?\\s*"), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        val json = tryParseJson(jsonCandidate) ?: tryExtractJsonFromText(text)

        if (json != null && (json.has("reply_text") || json.has("ui_action"))) {
            val replyText = json.optString("reply_text", "").ifBlank {
                extractNaturalText(text)
            }
            return AgentResponse(
                replyText = replyText,
                ttsText = replyText,
                uiAction = json.optString("ui_action", "none"),
                uiData = AgentJson.toMap(json.optJSONObject("ui_data") ?: JSONObject())
            )
        }

        val looksLikeJson = text.trimStart().startsWith("{") || text.contains("\"reply_text\"")
        val display = if (looksLikeJson) cleanForDisplay(text) else text.trim()
        Log.d(TAG, "natural-language response (${display.length} chars) -> ui_action from tools")
        return AgentResponse(
            replyText = display,
            ttsText = display,
            uiAction = "none"
        )
    }

    private fun tryParseJson(text: String): JSONObject? =
        try {
            if (text.startsWith("{")) JSONObject(text) else null
        } catch (_: Exception) {
            null
        }

    private fun tryExtractJsonFromText(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(text.substring(start, end + 1))
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanForDisplay(text: String): String =
        text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .replace(Regex("\\{[^}]*\"reply_text\"[^}]*\\}"), "")
            .replace(Regex("\"[a-z_]+\"\\s*:"), "")
            .replace(Regex("[{}\"\\[\\]]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "응답을 처리하는 중 문제가 발생했어요. 다시 말씀해주세요." }

    private fun extractNaturalText(text: String): String {
        val withoutJson = text
            .replace(Regex("```json[\\s\\S]*?```"), "")
            .replace(Regex("\\{[\\s\\S]*\\}"), "")
            .trim()
        return withoutJson.ifBlank { cleanForDisplay(text) }
    }

    private class UiRecovery(
        val storeKey: String,
        val force: Boolean,
        val recover: (raw: String, uiData: MutableMap<String, Any?>) -> String?
    )

    private fun normalizeResponse(
        parsed: AgentResponse,
        toolStore: Map<String, Any>
    ): AgentResponse {
        val uiData = parsed.uiData.toMutableMap()
        var uiAction = parsed.uiAction

        (toolStore["directions"] as? String)?.let { dirStr ->
            try {
                val dirJson = JSONObject(dirStr)
                val routesArr = dirJson.optJSONArray("routes")
                if (routesArr != null && routesArr.length() > 0) {
                    val routesList = mutableListOf<Map<String, Any?>>()
                    for (i in 0 until routesArr.length()) {
                        routesList.add(AgentJson.toMap(routesArr.getJSONObject(i)))
                    }
                    uiData["routes"] = routesList
                    uiData["routes_json"] = dirStr
                    if (uiAction == "none" || uiAction.isBlank()) {
                        uiAction = "show_route"
                    }
                    Log.d(TAG, "normalizeResponse: injected ${routesList.size} routes, uiAction=$uiAction")
                }
            } catch (e: Exception) {
                Log.e(TAG, "normalizeResponse: routes injection failed", e)
            }
        }

        if (uiAction == "start_navigation" && uiData["routes"] == null) {
            (toolStore["directions"] as? String)?.let { dirStr ->
                try {
                    val dirJson = JSONObject(dirStr)
                    val routesArr = dirJson.optJSONArray("routes")
                    if (routesArr != null && routesArr.length() > 0) {
                        val routesList = mutableListOf<Map<String, Any?>>()
                        for (i in 0 until routesArr.length()) {
                            routesList.add(AgentJson.toMap(routesArr.getJSONObject(i)))
                        }
                        uiData["routes"] = routesList
                        Log.d(TAG, "normalizeResponse: recovered ${routesList.size} routes for start_navigation")
                    }
                } catch (_: Exception) {
                }
            }
        }

        for (rule in UI_RECOVERIES) {
            val raw = toolStore[rule.storeKey] as? String ?: continue
            try {
                val recovered = rule.recover(raw, uiData) ?: continue
                if (rule.force || uiAction == "none" || uiAction.isBlank()) {
                    uiAction = recovered
                }
            } catch (_: Exception) {
            }
        }

        return parsed.copy(uiAction = uiAction, uiData = uiData)
    }

    companion object {
        private const val TAG = "AgentResponseAdapter"

        private val UI_RECOVERIES = listOf(
            UiRecovery("places", force = false) { raw, ui ->
                ui["places_json"] = raw
                "show_places"
            },
            UiRecovery("schedule", force = false) { raw, ui ->
                ui["schedule_json"] = raw
                "show_schedule"
            },
            UiRecovery("weather", force = false) { raw, ui ->
                ui["weather_json"] = raw
                "show_weather"
            },
            UiRecovery("message", force = true) { raw, ui ->
                val message = JSONObject(raw)
                if (message.optString("status") == "draft_pending_confirmation") {
                    ui["recipient"] = message.optString("recipient")
                    ui["phone"] = message.optString("phone")
                    ui["message"] = message.optString("message")
                    ui["method"] = message.optString("method", "sms")
                    "show_message_draft"
                } else {
                    null
                }
            },
            UiRecovery("call", force = true) { raw, ui ->
                val call = JSONObject(raw)
                if (call.optString("status") == "ready") {
                    ui["name"] = call.optString("name")
                    ui["phone"] = call.optString("phone")
                    "show_call"
                } else {
                    null
                }
            }
        )
    }
}
