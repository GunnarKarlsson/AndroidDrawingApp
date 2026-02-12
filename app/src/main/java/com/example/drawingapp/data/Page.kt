package com.example.drawingapp.data

import java.util.UUID

data class Page(
    val id: String,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String = "Page"
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
