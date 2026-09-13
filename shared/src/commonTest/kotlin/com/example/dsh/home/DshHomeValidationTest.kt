package com.example.dsh.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import com.example.dsh.models.DshProviderModel
import com.example.dsh.protocol.DshConnectionMode
import com.example.dsh.home.dshConnectionModeLabel
import com.example.dsh.home.dshReconnectLabel
import com.example.dsh.home.DshSshSettingsValidation
import com.example.dsh.home.dshSyncBusyLabel
import com.example.dsh.home.dshValidateCustomProvider
import com.example.dsh.home.dshValidateProviderDraft
import com.example.dsh.home.dshValidateSshSettings

class DshHomeValidationTest {

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
    fun customProviderValidationPrefersFirstError() {
        assertEquals(
            "请填写 Provider ID。",
            dshValidateCustomProvider("", "http://x", "openai", listOf(DshProviderModel("m")), emptyList()),
        )
        assertEquals(
            "自定义提供方需要填写 API 地址。",
            dshValidateCustomProvider("valid-id", "", "openai", listOf(DshProviderModel("m")), emptyList()),
        )
        assertEquals(
            "请选择 API 协议。",
            dshValidateCustomProvider("valid-id", "http://x", "", listOf(DshProviderModel("m")), emptyList()),
        )
        assertEquals(
            "自定义提供方至少需要一个模型，且模型 ID 不能为空。",
            dshValidateCustomProvider("valid-id", "http://x", "openai", emptyList(), emptyList()),
        )
        assertEquals(
            "模型 ID 不能重复。",
            dshValidateCustomProvider(
                "valid-id",
                "http://x",
                "openai",
                listOf(DshProviderModel("m"), DshProviderModel("m")),
                emptyList(),
            ),
        )
        assertNull(
            dshValidateCustomProvider("valid-id", "http://x", "openai", listOf(DshProviderModel("m")), emptyList()),
        )
    }

    @Test
    fun customProviderRejectsMalformedOrTakenRoute() {
        assertEquals(
            "需以小写字母开头，之后可用小写字母、数字和短横线。",
            dshValidateCustomProvider("Bad_ID", "http://x", "openai", listOf(DshProviderModel("m")), emptyList()),
        )
        assertEquals(
            "已有提供方使用了这个 ID。",
            dshValidateCustomProvider("taken-id", "http://x", "openai", listOf(DshProviderModel("m")), listOf("taken-id")),
        )
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

    @Test
    fun providerDraftValidation() {
        assertEquals("模型 ID 不能为空。", dshValidateProviderDraft(listOf(DshProviderModel(""))))
        assertEquals(
            "模型 ID 不能重复。",
            dshValidateProviderDraft(listOf(DshProviderModel("a"), DshProviderModel("a"))),
        )
        assertNull(dshValidateProviderDraft(listOf(DshProviderModel("a"), DshProviderModel("b"))))
    }
}
