package com.example.dsh.connection

import com.example.dsh.host.DshConnectionMode

fun dshConnectionModeLabel(mode: DshConnectionMode): String = when (mode) {
    DshConnectionMode.LOCAL -> "本地模式"
    DshConnectionMode.RELAY -> "扫码连接"
    DshConnectionMode.SSH -> "SSH 连接"
}

fun dshReconnectLabel(mode: DshConnectionMode): String = when (mode) {
    DshConnectionMode.SSH -> "远程连接重建中"
    DshConnectionMode.RELAY -> "扫码连接重建中"
    DshConnectionMode.LOCAL -> "本地 DSH 连接重建中"
}

fun dshSyncBusyLabel(mode: DshConnectionMode): String = when (mode) {
    DshConnectionMode.SSH -> "远程 DSH 正在同步，暂不能发送"
    DshConnectionMode.RELAY -> "扫码连接正在同步，暂不能发送"
    DshConnectionMode.LOCAL -> "本地 DSH 正在同步，暂不能发送"
}

/** SSH 连接表单校验结果：通过则携带解析后的端口。 */
sealed interface DshSshSettingsValidation {
    data class Valid(val sshPort: Int, val dshPort: Int) : DshSshSettingsValidation
    data class Invalid(val message: String) : DshSshSettingsValidation
}

/** SSH 连接设置校验；保持与界面一致的首错顺序。 */
fun dshValidateSshSettings(
    host: String,
    user: String,
    sshPort: String,
    dshPort: String,
    keyId: String,
): DshSshSettingsValidation {
    if (host.isBlank()) return DshSshSettingsValidation.Invalid("请输入 SSH 主机地址")
    if (user.isBlank()) return DshSshSettingsValidation.Invalid("请输入 SSH 用户名")
    val ssh = sshPort.toIntOrNull()
    if (ssh == null || ssh !in 1..65535) return DshSshSettingsValidation.Invalid("SSH 端口无效")
    val dsh = dshPort.toIntOrNull()
    if (dsh == null || dsh !in 1..65535) return DshSshSettingsValidation.Invalid("远程 DSH 端口无效")
    if (keyId.isBlank()) return DshSshSettingsValidation.Invalid("请先导入 SSH 私钥")
    return DshSshSettingsValidation.Valid(ssh, dsh)
}
