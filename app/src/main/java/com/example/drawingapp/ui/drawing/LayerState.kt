package com.example.drawingapp.ui.drawing

import android.graphics.Bitmap
import com.example.drawingapp.data.Stroke

data class LayerState(
    var bitmap: Bitmap,
    val strokes: MutableList<Stroke> = mutableListOf()
)
