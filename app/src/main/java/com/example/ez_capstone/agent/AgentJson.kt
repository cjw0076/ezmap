package com.example.ez_capstone.agent

import org.json.JSONArray
import org.json.JSONObject

object AgentJson {
    fun toMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> toMap(value)
                is JSONArray -> toList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    fun toList(arr: JSONArray): List<Any?> =
        (0 until arr.length()).map { i ->
            when (val value = arr.get(i)) {
                is JSONObject -> toMap(value)
                is JSONArray -> toList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
}
