package com.example.drawingapp.data

import java.util.UUID

data class Notebook(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val color: Int? = null
) {
    companion object {
        const val DEFAULT_ID = "default"

        fun newId(): String = UUID.randomUUID().toString()
    }
}
