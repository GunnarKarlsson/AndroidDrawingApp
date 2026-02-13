package com.example.drawingapp.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/** Serializable stroke for persistence. */
data class StrokeData(
    val points: List<Pair<Float, Float>>,
    val colorArgb: Int,
    val strokeWidth: Float,
    val tool: String,
    val strokeCapStyle: String
) {
    fun toStroke(): Stroke = Stroke(
        points = points.map { Offset(it.first, it.second) },
        color = Color(colorArgb),
        strokeWidth = strokeWidth,
        tool = DrawTool.valueOf(tool),
        strokeCapStyle = StrokeCapStyle.valueOf(strokeCapStyle)
    )

    companion object {
        fun fromStroke(stroke: Stroke): StrokeData = StrokeData(
            points = stroke.points.map { it.x to it.y },
            colorArgb = stroke.color.toArgb(),
            strokeWidth = stroke.strokeWidth,
            tool = stroke.tool.name,
            strokeCapStyle = stroke.strokeCapStyle.name
        )
    }
}

/** Per-layer metadata for persistence (hasFill + strokes). */
data class LayerMeta(
    val hasFill: Boolean,
    val strokes: List<StrokeData>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("hasFill", hasFill)
        put("strokes", JSONArray().apply {
            strokes.forEach { s ->
                put(JSONObject().apply {
                    put("points", JSONArray().apply {
                        s.points.forEach { (x, y) ->
                            put(JSONArray().apply { put(x.toDouble()); put(y.toDouble()) })
                        }
                    })
                    put("colorArgb", s.colorArgb)
                    put("strokeWidth", s.strokeWidth.toDouble())
                    put("tool", s.tool)
                    put("strokeCapStyle", s.strokeCapStyle)
                })
            }
        })
    }

    companion object {
        fun fromJson(obj: JSONObject): LayerMeta {
            val hasFill = obj.optBoolean("hasFill", false)
            val strokesArray = obj.optJSONArray("strokes") ?: JSONArray()
            val strokes = (0 until strokesArray.length()).map { i ->
                val so = strokesArray.getJSONObject(i)
                val pointsArray = so.optJSONArray("points") ?: JSONArray()
                val points = (0 until pointsArray.length()).map { j ->
                    val pt = pointsArray.getJSONArray(j)
                    pt.getDouble(0).toFloat() to pt.getDouble(1).toFloat()
                }
                StrokeData(
                    points = points,
                    colorArgb = so.optInt("colorArgb", 0),
                    strokeWidth = so.optDouble("strokeWidth", 1.0).toFloat(),
                    tool = so.optString("tool", "Pen"),
                    strokeCapStyle = so.optString("strokeCapStyle", "ROUND")
                )
            }
            return LayerMeta(hasFill = hasFill, strokes = strokes)
        }
    }
}
