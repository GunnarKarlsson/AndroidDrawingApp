package com.example.drawingapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PageMeta(
    val layerCount: Int,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val layers: List<LayerMeta>? = null,
    val lastModified: Long = 0L,
    val thumbnailTimestamp: Long = 0L
) {
    fun toJson(): String = JSONObject()
        .apply {
            put("layerCount", layerCount)
            put("backgroundColor", backgroundColor)
            put("lastModified", lastModified)
            put("thumbnailTimestamp", thumbnailTimestamp)
            layers?.let { list ->
                put("layers", JSONArray().apply {
                    list.forEach { layer -> put(layer.toJson()) }
                })
            }
        }
        .toString()

    companion object {
        fun fromFile(file: File): PageMeta? {
            if (!file.exists()) return null
            return try {
                val json = file.readText()
                val obj = JSONObject(json)
                val layersArray = obj.optJSONArray("layers")
                val layers = layersArray?.let { arr ->
                    (0 until arr.length()).map { i -> LayerMeta.fromJson(arr.getJSONObject(i)) }
                }
                PageMeta(
                    layerCount = obj.optInt("layerCount", 1),
                    backgroundColor = obj.optInt("backgroundColor", 0xFFFFFFFF.toInt()),
                    layers = layers,
                    lastModified = obj.optLong("lastModified", 0L),
                    thumbnailTimestamp = obj.optLong("thumbnailTimestamp", 0L)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
