package com.summarizer.app.data.repository

import com.summarizer.app.data.local.entity.SummaryEntity
import com.summarizer.app.domain.model.Summary

internal fun SummaryEntity.toDomainModel() = Summary(
    id = id,
    threadId = threadId,
    threadName = threadName,
    overview = overview,
    keyTopics = keyTopics,
    actionItems = actionItems,
    announcements = announcements,
    participantHighlights = participantHighlights,
    messageCount = messageCount,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    generatedAt = generatedAt,
    rawAIResponse = rawAIResponse,
    keyDates = keyDates,
    engine = engine,
    rating = rating
)

internal fun Summary.toEntity() = SummaryEntity(
    id = id,
    threadId = threadId,
    threadName = threadName,
    overview = overview,
    keyTopics = keyTopics,
    actionItems = actionItems,
    announcements = announcements,
    participantHighlights = participantHighlights,
    messageCount = messageCount,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    generatedAt = generatedAt,
    rawAIResponse = rawAIResponse,
    keyDates = keyDates,
    engine = engine,
    rating = rating
)
