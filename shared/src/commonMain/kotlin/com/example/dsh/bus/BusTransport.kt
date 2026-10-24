package com.example.dsh.bus

/** Result of one Bus HTTP call, without depending on a platform HTTP client. */
sealed class BusHttpResult {
    data class Http(val status: Int, val body: String) : BusHttpResult()
    data class Failure(val message: String) : BusHttpResult()
}

interface BusSocket {
    fun close()
}

/**
 * Transport boundary for the Bus client. Android implements this over the
 * existing loopback tunnel; tests use an in-memory fake.
 */
interface BusTransport {
    suspend fun request(method: String, path: String, body: String? = null): BusHttpResult

    fun openSocket(
        path: String,
        onOpen: () -> Unit,
        onFrame: (String) -> Unit,
        onFailure: (String) -> Unit,
    ): BusSocket
}
