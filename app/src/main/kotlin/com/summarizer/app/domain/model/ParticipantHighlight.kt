package com.summarizer.app.domain.model

@kotlinx.serialization.Serializable
data class ParticipantHighlight(
    val participant: String,
    val contribution: String
)
