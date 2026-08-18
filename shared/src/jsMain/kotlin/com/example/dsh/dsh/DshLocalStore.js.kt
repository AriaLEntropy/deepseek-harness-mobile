package com.example.dsh.dsh

internal actual fun createDshLocalStore(path: String): DshLocalStore = EmptyDshLocalStore

private object EmptyDshLocalStore : DshLocalStore {
    override fun loadApiKey(): String = ""
    override fun saveApiKey(apiKey: String) = Unit
    override fun loadSessions(): List<DshSession> = emptyList()
    override fun saveSessions(sessions: List<DshSession>) = Unit
    override fun loadMessages(sessionId: String): List<DshMessage> = emptyList()
    override fun saveMessages(sessionId: String, messages: List<DshMessage>) = Unit
}
