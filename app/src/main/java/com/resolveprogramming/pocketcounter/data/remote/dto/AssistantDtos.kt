package com.resolveprogramming.pocketcounter.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AssistantAskRequestDto(val question: String)

@Serializable
data class AssistantAskResponseDto(
    val answer: String,
    val elapsedMs: Long = 0,
    val remainingQuestions: Int = 0,
)
