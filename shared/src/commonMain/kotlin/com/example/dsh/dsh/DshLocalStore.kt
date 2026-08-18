package com.example.dsh.dsh

/** Small durable cache used to make the native client feel continuous across launches. */
internal interface DshLocalStore {
    fun loadApiKey(): String
    fun saveApiKey(apiKey: String)
    fun loadSessions(): List<DshSession>
    fun saveSessions(sessions: List<DshSession>)
    fun loadMessages(sessionId: String): List<DshMessage>
    fun saveMessages(sessionId: String, messages: List<DshMessage>)
}

internal expect fun createDshLocalStore(path: String): DshLocalStore
