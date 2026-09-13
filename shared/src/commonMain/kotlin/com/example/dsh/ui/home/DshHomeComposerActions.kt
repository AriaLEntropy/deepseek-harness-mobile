package com.example.dsh.ui.home

import com.example.dsh.ui.chat.DshCommand
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.KeyboardParams

internal fun DshHomePage.dismissKeyboard() {
    if (!inputFocused && keyboardHeight <= 0f) return
    inputFocused = false
    inputView?.blur()
    bridgeModule.closeKeyboard()
    keyboardHeight = 0f
}

internal fun DshHomePage.updateKeyboard(params: KeyboardParams) {
    // 仅当首页输入框自身聚焦时才让键盘顶起底页；重命名等弹窗里的输入框
    // 也会唤起软键盘，其高度不应驱动会话区布局。高度归零始终允许（收起态）。
    if (!inputFocused && params.height > 0f) return
    keyboardAnimation = Animation.easeInOut(DshHomePage.ANIMATION_DURATION_S)
    keyboardHeight = effectiveKeyboardHeight(params.height)
    // Closing the keyboard after send must not undo the scroll to the
    // newly sent user message. Scroll to the end only when the composer
    // is opening while no response is being anchored.
    if (keyboardHeight > 0f && !streaming) scrollMessagesToEnd()
}

internal fun DshHomePage.effectiveKeyboardHeight(rawHeight: Float): Float {
    if (rawHeight <= 0f) return 0f
    // Kuikly's Android watcher already reports IME height minus the
    // navigation bar. Subtracting the safe area here would lift the
    // composer a second time and leave a visible gap above the keyboard.
    return if (pagerData.isAndroid) {
        rawHeight
    } else {
        (rawHeight - pagerData.safeAreaInsets.bottom).coerceAtLeast(0f)
    }
}

internal fun DshHomePage.toggleCommandSheet() {
    dismissKeyboard()
    commandSheetVisible = !commandSheetVisible
}

/** 点击命令：把 "/命令 " 写入输入框（补全草稿 + 原生输入框文本），并关闭面板 */

internal fun DshHomePage.insertCommand(command: DshCommand) {
    insertDraftText("/${command.name} ")
    commandSheetVisible = false
}

/** 把文本写入 draft 状态，并同步到原生输入框（draft observable 不会自动回流到 TextArea）。 */

internal fun DshHomePage.insertDraftText(text: String) {
    draft = text
    inputView?.setText(text)
}
