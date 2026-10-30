package com.example.dsh.bus

import com.example.dsh.platform.currentTimeMillis

/** Host-side limit for one command body, counted in UTF-8 bytes. */
const val BUS_COMMAND_MAX_BYTES: Int = 16 * 1024

const val BUS_COMMAND_TOO_LONG: String = "内容太长"
const val BUS_COMMAND_UNCONFIRMED: String = "电脑未确认，请稍后重试"

fun busCommandByteSize(json: String): Int = json.encodeToByteArray().size

/** Exactly 16 KiB is allowed; only strictly larger bodies are rejected. */
fun busCommandTooLong(json: String): Boolean = busCommandByteSize(json) > BUS_COMMAND_MAX_BYTES

/** HTTP-ish acknowledgement from POST `/bus/commands`. `status == 0` means the call never landed. */
data class BusCommandAck(
    val status: Int,
    val body: String = "",
)

/**
 * Boundary used by [BusCommandSender]: POST one command, read the host snapshot and
 * schedule the next poll. Android/Kuikly and tests each supply their own implementation.
 */
interface BusCommandTransport {
    fun send(json: String, onAck: (BusCommandAck) -> Unit)
    fun snapshotJson(): String?
    fun schedule(delayMs: Long, action: () -> Unit)
}

/**
 * One command identity. `id`/`createdAt` are generated once; retries must reuse this
 * exact object so the host dedups by `id` instead of executing twice.
 */
data class BusCommand(
    val id: String,
    val createdAt: String,
    val json: String,
) {
    val tooLong: Boolean get() = busCommandTooLong(json)
}

sealed class BusSendResult {
    abstract val retryable: Boolean
    abstract val message: String

    /** Host answered for this exact id: success or a definitive `ok=false` failure. */
    data class Confirmed(
        val ok: Boolean,
        val taskId: String = "",
        val error: String = "",
    ) : BusSendResult() {
        override val retryable: Boolean get() = false
        override val message: String get() = error
    }

    /** No 200/202: network or HTTP failure. The command never entered "waiting". */
    data class Failed(
        val status: Int,
        val body: String,
        override val message: String,
    ) : BusSendResult() {
        override val retryable: Boolean get() = true
    }

    /** 60 seconds passed without a `recentResults`/`commandResult` for this id. */
    data class Unconfirmed(
        override val message: String = BUS_COMMAND_UNCONFIRMED,
    ) : BusSendResult() {
        override val retryable: Boolean get() = true
    }

    /** Local guard: body is larger than 16 KiB, so nothing was sent. */
    data class TooLong(
        override val message: String = BUS_COMMAND_TOO_LONG,
    ) : BusSendResult() {
        override val retryable: Boolean get() = false
    }
}

/**
 * Single send path for every Bus command.
 *
 * - Generate `id` + `createdAt` once through [prepare], then reuse the returned
 *   [BusCommand] for retries via [retry].
 * - Callback protocol: 200/202 means "accepted" and enters waiting; anything else
 *   (including status 0 for a network failure) returns [BusSendResult.Failed]
 *   immediately without tracking, so the caller may resend the same command.
 * - Waiting polls the snapshot's `recentResults` every [pollIntervalMs] until the
 *   same id appears or the tracker's timeout (60s by default) expires.
 * - Everything is in memory; a process restart drops the tracker and never resends.
 */
class BusCommandSender(
    private val transport: BusCommandTransport,
    private val tracker: BusCommandTracker = BusCommandTracker(nowMs = { currentTimeMillis() }),
    private val pollIntervalMs: Long = 300,
    private val newId: () -> String = { BusCommands.newCommandId() },
    private val newCreatedAt: () -> String = { BusCommands.createdAt() },
) {
    /** Generate the identity once; every retry must send the returned [BusCommand]. */
    fun prepare(build: (id: String, createdAt: String) -> String): BusCommand {
        val id = newId()
        val createdAt = newCreatedAt()
        return BusCommand(id = id, createdAt = createdAt, json = build(id, createdAt))
    }

    fun send(command: BusCommand, onDone: (BusSendResult) -> Unit) {
        if (command.tooLong) {
            onDone(BusSendResult.TooLong())
            return
        }
        transport.send(command.json) { ack ->
            if (ack.status == 200 || ack.status == 202) {
                // 202 only means the host accepted the command. The real outcome arrives
                // later through the snapshot's recentResults; poll until it does.
                tracker.track(command.id)
                await(command, onDone)
            } else {
                // Network failure (status 0) or an HTTP rejection: no waiting state.
                onDone(BusSendResult.Failed(ack.status, ack.body, failureMessage(ack)))
            }
        }
    }

    /** Resend the same id + createdAt; safe because the host dedups by id. */
    fun retry(command: BusCommand, onDone: (BusSendResult) -> Unit) {
        send(command, onDone)
    }

    private fun await(command: BusCommand, onDone: (BusSendResult) -> Unit) {
        when (val outcome = busCommandOutcome(transport.snapshotJson().orEmpty(), command.id)) {
            is BusCommandOutcome.Success -> {
                tracker.onResult(command.id)
                onDone(BusSendResult.Confirmed(ok = true, taskId = outcome.taskId))
            }
            is BusCommandOutcome.Failure -> {
                tracker.onResult(command.id)
                onDone(BusSendResult.Confirmed(ok = false, error = outcome.error))
            }
            BusCommandOutcome.Pending -> {
                if (tracker.isExpired(command.id)) {
                    tracker.onResult(command.id)
                    onDone(BusSendResult.Unconfirmed())
                } else {
                    transport.schedule(pollIntervalMs) { await(command, onDone) }
                }
            }
        }
    }

    private fun failureMessage(ack: BusCommandAck): String = when {
        ack.body.isNotBlank() -> ack.body
        ack.status == 0 -> "网络失败，请重试"
        else -> "发送失败（${ack.status}）"
    }
}
