package com.example.gemmatutor

import java.util.UUID

private val START_TURN = "<start_of_turn>"
private val END_TURN = "<end_of_turn>"

/**
 * Used to represent a ChatMessage
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val rawMessage: String = "",
    val author: String,
    val isLoading: Boolean = false
) {
    val isFromUser: Boolean
        get() = author == USER_PREFIX
    val message: String
        get() = rawMessage.trim()
    val plainText: String
        get() = rawMessage
            .trim()
            .removePrefix(START_TURN)
            .removeSuffix(END_TURN)
}
