package com.example.dsh

import android.content.Context
import android.text.InputType
import com.tencent.kuikly.core.render.android.expand.component.KRTextFieldView

/**
 * 关闭系统拼写检查的 Kuikly 输入框（Android 渲染层同名覆盖 KRTextFieldView）。
 *
 * 背景：EditText 默认 inputType 含 TYPE_TEXT_FLAG_AUTO_CORRECT，每次输入/程序化 setText 都会触发
 * SpellCheckerSession 跨进程拼写检查。模拟器、低端设备或 TextServices 响应慢时，主线程可被阻塞超过
 * 5 秒，出现 "Input dispatching timed out" ANR（本工程已在模拟器实测复现，主线程栈含
 * SpellCheckerSession.getSentenceSuggestions ← KRTextFieldView.setInputText 完整链路）。
 *
 * DSH 的输入内容均为技术文本（协议名、事件类型、过滤关键词），系统拼写检查无价值且有害，故统一关闭。
 * 关闭方式：inputType 追加 TYPE_TEXT_FLAG_NO_SUGGESTIONS（TextView.isSuggestionsEnabled 会直接返回 false，
 * 不再发起拼写检查，对 IME 行为无其他影响）。
 */
class DshNoSpellCheckTextField(context: Context, softInputMode: Int?) : KRTextFieldView(context, softInputMode) {

    init {
        applyNoSuggestions()
    }

    /**
     * keyboardType / editable 等属性会直接重写 inputType，覆盖后需补回 NO_SUGGESTIONS 标志。
     */
    override fun setProp(propKey: String, propValue: Any): Boolean {
        val handled = super.setProp(propKey, propValue)
        if (propKey == KEYBOARD_TYPE_PROP || propKey == EDITABLE_PROP) {
            applyNoSuggestions()
        }
        return handled
    }

    private fun applyNoSuggestions() {
        setRawInputType(inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    }

    private companion object {
        const val KEYBOARD_TYPE_PROP = "keyboardType"
        const val EDITABLE_PROP = "editable"
    }
}
