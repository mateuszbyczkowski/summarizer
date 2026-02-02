package com.summarizer.app.domain.model

@kotlinx.serialization.Serializable
data class ActionItem(
    val task: String,
    val assignedTo: String? = null,
    val priority: String = "medium"
)
