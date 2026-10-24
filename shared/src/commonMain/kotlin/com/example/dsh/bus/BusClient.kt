package com.example.dsh.bus

/**
 * Minimal Bus client on top of [BusTransport]. The sync loop still lives in
 * the Android host for now; this class is the device-independent entry the
 * M2 pages and tests can use.
 */
class BusClient(private val transport: BusTransport) {
    suspend fun describe(attempts: Int = 3): DescribeDecision {
        val tracker = DescribeAttempts()
        repeat(attempts) {
            when (val result = transport.request("GET", "/bus/describe")) {
                is BusHttpResult.Http -> tracker.onHttp(result.status, result.body)?.let { return it }
                is BusHttpResult.Failure -> tracker.onTransportFailure()?.let { return it }
            }
        }
        return DescribeDecision.Unknown
    }

    suspend fun snapshot(since: Long = -1): BusHttpResult =
        transport.request("GET", if (since < 0) "/bus/snapshot" else "/bus/snapshot?since=$since")

    suspend fun command(json: String): BusHttpResult =
        transport.request("POST", "/bus/commands", json)

    fun openSocket(
        since: Long = -1,
        onOpen: () -> Unit,
        onFrame: (String) -> Unit,
        onFailure: (String) -> Unit,
    ): BusSocket = transport.openSocket("/bus/ws?since=$since", onOpen, onFrame, onFailure)
}
