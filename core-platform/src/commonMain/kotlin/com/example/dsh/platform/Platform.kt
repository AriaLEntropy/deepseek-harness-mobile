package com.example.dsh.platform

expect fun currentTimeMillis(): Long

/** 设备本地时区标识（IANA 名称，如 Asia/Shanghai）；无法获取时返回 UTC。 */
internal expect fun localTimeZoneId(): String

/**
 * 上报给 Host 的 `clientTimeZone`。
 *
 * Host 只接受 `UTC` 和 IANA `Area/Location` 名称，模拟器往往返回可解析但非
 * IANA 的 `GMT`、`GMT+08:00`，这类值会被 `session.prompt` 拒绝；因此统一回退 `UTC`。
 */
fun clientTimeZoneId(): String {
    val id = localTimeZoneId()
    return if (id == "UTC" || id.contains('/')) id else "UTC"
}
