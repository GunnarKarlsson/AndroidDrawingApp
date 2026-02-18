package com.example.drawingapp.tools

import com.example.drawingapp.data.DrawTool

/**
 * Registry of drawing tools. Look up by id or by DrawTool enum.
 * Order of getAllTools() matches the tool picker list.
 */
class ToolRegistry {
    private val byId = mutableMapOf<String, DrawingTool>()
    private val orderedTools = mutableListOf<DrawingTool>()

    fun register(tool: DrawingTool) {
        byId[tool.id] = tool
        if (tool !in orderedTools) {
            orderedTools.add(tool)
        }
    }

    fun getTool(id: String): DrawingTool? = byId[id]

    fun getToolByEnum(drawTool: DrawTool): DrawingTool? =
        orderedTools.find { it.drawTool == drawTool }

    fun getAllTools(): List<DrawingTool> = orderedTools.toList()
}

/** Builds a registry with all built-in tools in picker order. */
fun createDefaultToolRegistry(): ToolRegistry {
    val registry = ToolRegistry()
    registry.register(PenTool())
    registry.register(PencilTool())
    registry.register(MarkerPenTool())
    registry.register(EraserTool())
    registry.register(FillTool())
    registry.register(EyedropperTool())
    registry.register(PanTool())
    return registry
}
