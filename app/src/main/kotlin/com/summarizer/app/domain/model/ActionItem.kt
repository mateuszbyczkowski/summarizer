package com.summarizer.app.domain.model

/**
 * A task from a summary. Stored as JSON; [due] and [done] were added later, so their defaults
 * keep older rows readable.
 *
 * @property due Deadline as an ISO date (yyyy-MM-dd), when the model named one that could be resolved
 * @property done Whether the user ticked the task off
 */
@kotlinx.serialization.Serializable
data class ActionItem(
    val task: String,
    val assignedTo: String? = null,
    val priority: String = "medium",
    val due: String? = null,
    val done: Boolean = false
)
