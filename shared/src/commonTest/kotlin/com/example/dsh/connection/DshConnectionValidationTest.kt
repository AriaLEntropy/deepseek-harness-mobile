package com.example.dsh.connection

import com.example.dsh.host.DshConnectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DshConnectionValidationTest {

    @Test
    fun connectionLabels() {
        assertEquals("本地模式", dshConnectionModeLabel(DshConnectionMode.LOCAL))
        assertEquals("扫码连接", dshConnectionModeLabel(DshConnectionMode.RELAY))
        assertEquals("SSH 连接", dshConnectionModeLabel(DshConnectionMode.SSH))
        assertEquals("远程连接重建中", dshReconnectLabel(DshConnectionMode.SSH))
        assertEquals("扫码连接重建中", dshReconnectLabel(DshConnectionMode.RELAY))
        assertEquals("本地 DSH 正在同步，暂不能发送", dshSyncBusyLabel(DshConnectionMode.LOCAL))
    }

    @Test
    fun sshSettingsValidation() {
        assertIs<DshSshSettingsValidation.Invalid>(dshValidateSshSettings("", "u", "22", "3080", "k"))
        assertIs<DshSshSettingsValidation.Invalid>(dshValidateSshSettings("h", "", "22", "3080", "k"))
        assertIs<DshSshSettingsValidation.Invalid>(dshValidateSshSettings("h", "u", "0", "3080", "k"))
        assertIs<DshSshSettingsValidation.Invalid>(dshValidateSshSettings("h", "u", "22", "70000", "k"))
        assertIs<DshSshSettingsValidation.Invalid>(dshValidateSshSettings("h", "u", "22", "3080", ""))
        val valid = dshValidateSshSettings(" h ", " u ", "2200", "3080", "key-1")
        assertIs<DshSshSettingsValidation.Valid>(valid)
        assertEquals(2200, valid.sshPort)
        assertEquals(3080, valid.dshPort)
    }
}
