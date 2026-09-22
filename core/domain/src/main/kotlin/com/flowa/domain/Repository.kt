package com.flowa.domain

import kotlinx.coroutines.flow.StateFlow

interface AppRepository {
    val state: StateFlow<AppState>
    suspend fun load()
    suspend fun update(transform: (AppState) -> AppState)
    suspend fun exportNotes(): ByteArray
    suspend fun importNotes(bytes: ByteArray, zip: Boolean): Pair<Int, Int>
    suspend fun exportConfiguration(): String
    suspend fun importConfiguration(json: String)
}

interface AiGateway {
    suspend fun complete(
        settings: AiSettings,
        messages: List<Message>,
        onDelta: (String) -> Unit = {},
    ): String

    suspend fun agent(
        settings: AiSettings,
        messages: List<Message>,
        onDelta: (String) -> Unit = {},
        onTool: (String) -> Unit = {},
        sessionId: String = newId(),
    ): String

    suspend fun models(settings: AiSettings): List<String>
}

interface DeviceGateway {
    suspend fun copy(text: String)
    suspend fun post(step: Step, text: String, returnKey: Boolean = false)
}
