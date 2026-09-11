package com.example.dsh.infrastructure

internal expect fun currentTimeMillis(): Long

/** 设备本地时区标识（IANA 名称，如 Asia/Shanghai）；无法获取时回退 UTC。 */
internal expect fun localTimeZoneId(): String

/**
 * 发送给 Host 的 `clientTimeZone`。
 *
 * Host 只接受 `UTC` 或 IANA `Area/Location` 名称；模拟器等环境可能返回
 * `GMT`、`GMT+08:00` 这类非 IANA 值，会导致 `session.prompt` 被拒绝，这里统一回退 `UTC`。
 */
internal fun clientTimeZoneId(): String {
    val id = localTimeZoneId()
    return if (id == "UTC" || id.contains('/')) id else "UTC"
}