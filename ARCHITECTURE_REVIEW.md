Architecture review and improvement recommendations
Critical issues
1. Tool logic is scattered and hardcoded
Problem: Tool behavior is spread across DrawingScreen.kt using when statements:
Lines 213-221: Color calculation per tool
Lines 628-706: Tap handling per tool
Lines 1039-1086: Stroke rendering mixed with tool checks
Impact: Adding a new tool requires changes in multiple places, violating the Open/Closed Principle.
2. No tool abstraction
Problem: DrawTool is an enum with no behavior. All tool logic lives in the UI layer.
Impact: Cannot extend tools without modifying core UI code.
3. Tight coupling between UI and drawing logic
Problem: DrawingScreen directly manipulates bitmaps, creates Paint objects, and handles drawing.
Impact: Hard to test, hard to reuse, violates Single Responsibility Principle.
4. No command pattern for actions
Problem: Undo/redo uses a sealed class, but actions execute inline.
Impact: Adding new undoable actions requires modifying the sealed class and all handling code.
5. No renderer abstraction
Problem: Direct use of Android Canvas and Paint throughout.
Impact: Hard to swap rendering backends, test rendering, or add effects.
6. Mixed responsibilities
Problem: DrawingScreen handles UI state, tool selection, drawing operations, undo/redo, layer management, and export.
Impact: Violates Single Responsibility Principle.
Proposed architecture improvements
1. Tool strategy pattern
Create a tool abstraction:
interface DrawingTool {    val id: String    val displayName: String    val iconRes: Int        fun transformColor(baseColor: Color, settings: ToolSettings): Color    fun createAction(        points: List<Offset>,        settings: ToolSettings,        context: DrawingContext    ): DrawingAction        fun handleTap(        x: Float, y: Float,        settings: ToolSettings,        context: DrawingContext    ): DrawingAction?        fun supportsContinuousDrawing(): Boolean    fun affectsViewInteraction(): Boolean}data class ToolSettings(    val color: Color,    val strokeWidth: Float,    val strokeCapStyle: StrokeCapStyle,    val smoothingEnabled: Boolean,    val closingEnabled: Boolean)data class DrawingContext(    val currentLayerIndex: Int,    val canvasSize: IntSize,    val scale: Float)
Concrete tool example:
class PenTool : DrawingTool {    override val id = "pen"    override val displayName = "Pen"    override val iconRes = R.drawable.ic_pen        override fun transformColor(baseColor: Color, settings: ToolSettings) = baseColor        override fun createAction(        points: List<Offset>,        settings: ToolSettings,        context: DrawingContext    ): DrawingAction {        val smoothed = if (settings.smoothingEnabled) {            smoothStrokePoints(points)        } else points                val closed = settings.closingEnabled && shouldAutoClose(smoothed, CLOSE_THRESHOLD_PX)                return StrokeDrawingAction(            layerIndex = context.currentLayerIndex,            points = smoothed,            color = transformColor(settings.color, settings),            strokeWidth = settings.strokeWidth / context.scale,            strokeCapStyle = settings.strokeCapStyle,            closed = closed        )    }        override fun handleTap(x: Float, y: Float, settings: ToolSettings, context: DrawingContext) = null    override fun supportsContinuousDrawing() = true    override fun affectsViewInteraction() = false}
Benefits:
New tools can be added by implementing DrawingTool
Tool behavior is encapsulated
Easier to test individual tools
Supports a tool registry pattern
2. Command pattern for drawing actions
Replace inline drawing with commands:
interface DrawingAction {    val layerIndex: Int    fun execute(layer: LayerState): DrawingActionResult    fun undo(layer: LayerState, previousState: DrawingActionResult)}sealed class DrawingActionResult {    data class BitmapChanged(val previousBitmap: Bitmap) : DrawingActionResult()    data class StrokeAdded(val stroke: Stroke) : DrawingActionResult()    data class NoChange : DrawingActionResult()}class StrokeDrawingAction(    override val layerIndex: Int,    val points: List<Offset>,    val color: Color,    val strokeWidth: Float,    val strokeCapStyle: StrokeCapStyle,    val closed: Boolean) : DrawingAction {        override fun execute(layer: LayerState): DrawingActionResult {        val stroke = Stroke(            points = points,            color = color,            strokeWidth = strokeWidth,            tool = DrawTool.Pen, // from context            strokeCapStyle = strokeCapStyle,            closed = closed        )                StrokeRenderer().render(layer.bitmap, stroke)        layer.strokes.add(stroke)        return DrawingActionResult.StrokeAdded(stroke)    }        override fun undo(layer: LayerState, previousState: DrawingActionResult) {        when (previousState) {            is DrawingActionResult.StrokeAdded -> {                layer.strokes.remove(previousState.stroke)                // Redraw all strokes                layer.bitmap.eraseColor(Color.TRANSPARENT.toArgb())                layer.strokes.forEach { stroke ->                    StrokeRenderer().render(layer.bitmap, stroke)                }            }            else -> {}        }    }}
Benefits:
Actions are first-class objects
Easier to extend with new action types
Undo/redo becomes straightforward
Actions can be serialized for history
3. Renderer abstraction layer
Abstract rendering from Android-specific APIs:
interface Renderer {    fun render(bitmap: Bitmap, action: DrawingAction)}class StrokeRenderer : Renderer {    override fun render(bitmap: Bitmap, action: DrawingAction) {        if (action !is StrokeDrawingAction) return                val canvas = Canvas(bitmap)        val paint = createPaint(action)        val path = createPath(action.points, action.closed, action.strokeCapStyle)        canvas.drawPath(path, paint)    }        private fun createPaint(action: StrokeDrawingAction): Paint {        return Paint().apply {            color = action.color.toArgb()            style = Paint.Style.STROKE            strokeWidth = action.strokeWidth            isAntiAlias = true            strokeJoin = if (action.strokeCapStyle == StrokeCapStyle.ROUND)                 Paint.Join.ROUND else Paint.Join.BEVEL            strokeCap = if (action.strokeCapStyle == StrokeCapStyle.ROUND)                Paint.Cap.ROUND else Paint.Cap.BUTT        }    }}
Benefits:
Can swap rendering implementations
Easier to add rendering effects
Testable rendering logic
Can add GPU rendering later
4. Drawing engine / controller
Extract drawing logic into a dedicated controller:
class DrawingEngine(    private val toolRegistry: ToolRegistry) {    private val undoStack = mutableListOf<Pair<DrawingAction, DrawingActionResult>>()    private val redoStack = mutableListOf<Pair<DrawingAction, DrawingActionResult>>()        fun executeAction(action: DrawingAction, layers: List<LayerState>) {        val layer = layers.getOrNull(action.layerIndex) ?: return        val result = action.execute(layer)        undoStack.add(action to result)        redoStack.clear()    }        fun undo(layers: List<LayerState>) {        if (undoStack.isEmpty()) return        val (action, result) = undoStack.removeLast()        val layer = layers.getOrNull(action.layerIndex) ?: return        action.undo(layer, result)        redoStack.add(action to result)    }        fun redo(layers: List<LayerState>) {        if (redoStack.isEmpty()) return        val (action, _) = redoStack.removeLast()        executeAction(action, layers)    }}class ToolRegistry {    private val tools = mutableMapOf<String, DrawingTool>()        fun register(tool: DrawingTool) {        tools[tool.id] = tool    }        fun getTool(id: String): DrawingTool? = tools[id]    fun getAllTools(): List<DrawingTool> = tools.values.toList()}
Benefits:
Separates business logic from UI
Testable drawing operations
Centralized undo/redo
Can add action history/playback
5. Layer management abstraction
Create a dedicated layer manager:
class LayerManager(    private val canvasSize: IntSize) {    private val layers = mutableStateListOf<LayerState>()    var currentLayerIndex: Int = 0        fun addLayer(): LayerState {        val bitmap = Bitmap.createBitmap(            canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888        )        bitmap.eraseColor(Color.TRANSPARENT.toArgb())        val layer = LayerState(bitmap = bitmap)        layers.add(layer)        return layer    }        fun deleteLayer(index: Int): Boolean {        if (layers.size <= 1) return false        layers.removeAt(index)        return true    }        fun compositeLayers(backgroundColor: Int): Bitmap {        val composite = Bitmap.createBitmap(            canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888        )        composite.eraseColor(backgroundColor)        val canvas = Canvas(composite)        layers.forEach { layer ->            if (!layer.isHidden && !layer.isTransparent()) {                canvas.drawBitmap(layer.bitmap, 0f, 0f, null)            }        }        return composite    }}
Benefits:
Encapsulates layer operations
Easier to add layer effects (blend modes, opacity)
Can add layer groups/folders
Testable layer operations
Example: Adding a new tool (Rectangle Tool)
With the new architecture:
class RectangleTool : DrawingTool {    override val id = "rectangle"    override val displayName = "Rectangle"    override val iconRes = R.drawable.ic_rectangle        override fun createAction(        points: List<Offset>,        settings: ToolSettings,        context: DrawingContext    ): DrawingAction {        require(points.size >= 2)        val start = points.first()        val end = points.last()                return RectangleDrawingAction(            layerIndex = context.currentLayerIndex,            startX = start.x, startY = start.y,            endX = end.x, endY = end.y,            color = settings.color,            strokeWidth = settings.strokeWidth / context.scale,            filled = false        )    }        override fun supportsContinuousDrawing() = true    override fun affectsViewInteraction() = false    // ... other methods}// Register in ToolRegistryregistry.register(RectangleTool())
No changes needed to DrawingScreen, DrawingView, or other existing code.
Additional recommendations
1. Plugin architecture
Allow external tools via plugins:
interface ToolPlugin {    fun getTools(): List<DrawingTool>}
2. Tool presets
Save/load tool configurations:
data class ToolPreset(    val toolId: String,    val settings: ToolSettings,    val name: String)
3. Rendering pipeline
Add post-processing effects:
interface RenderingEffect {    fun apply(bitmap: Bitmap): Bitmap}
4. Layer blend modes
Add blend mode support:
enum class BlendMode { NORMAL, MULTIPLY, SCREEN, OVERLAY, ... }
Implementation roadmap
Phase 1: Foundation (Week 1-2)
Create DrawingTool interface and implementations
Create DrawingAction interface and command pattern
Create Renderer abstraction
Create ToolRegistry
Phase 2: Refactoring (Week 3-4)
Extract DrawingEngine from DrawingScreen
Extract LayerManager from DrawingScreen
Refactor DrawingScreen to use new abstractions
Update DrawingView to be tool-agnostic
Phase 3: Testing & polish (Week 5)
Add unit tests for tools
Add unit tests for drawing engine
Add integration tests
Performance optimization
Summary
These changes will:
Make the codebase more extensible — new tools without modifying existing code
Improve testability — components can be tested in isolation
Reduce coupling — clear separation of concerns
Enable new features — plugin system, tool presets, rendering effects
Improve maintainability — smaller, focused classes with single responsibilities
The refactoring can be done incrementally, starting with the tool abstraction and gradually moving logic out of DrawingScreen.
Should I create example implementations for any of these patterns, or focus on a specific area first?