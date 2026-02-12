package com.example.drawingapp.data

import org.json.JSONObject
import java.io.File

data class PageMeta(val layerCount: Int, val backgroundColor: Int = 0xFFFFFFFF.toInt()) {
    fun toJson(): String = JSONObject()
        .apply {
            put("layerCount", layerCount)
            put("backgroundColor", backgroundColor)
        }
        .toString()

    companion object {
        fun fromFile(file: File): PageMeta? {
            if (!file.exists()) return null
            return try {
                val json = file.readText()
                val obj = JSONObject(json)
                PageMeta(
                    layerCount = obj.optInt("layerCount", 1),
                    backgroundColor = obj.optInt("backgroundColor", 0xFFFFFFFF.toInt())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
