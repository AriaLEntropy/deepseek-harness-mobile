package com.example.dsh.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DshModelValidationTest {

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
    fun providerDraftValidation() {
        assertEquals("模型 ID 不能为空。", dshValidateProviderDraft(listOf(DshProviderModel(""))))
        assertEquals(
            "模型 ID 不能重复。",
            dshValidateProviderDraft(listOf(DshProviderModel("a"), DshProviderModel("a"))),
        )
        assertNull(dshValidateProviderDraft(listOf(DshProviderModel("a"), DshProviderModel("b"))))
    }
}
