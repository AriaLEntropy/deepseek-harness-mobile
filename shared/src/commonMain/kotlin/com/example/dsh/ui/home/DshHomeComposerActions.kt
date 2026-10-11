package com.example.dsh.ui.home

import com.example.dsh.ui.chat.DshCommand
import com.example.dsh.ui.chat.DshCommandKind
import com.example.dsh.ui.chat.DshCommandPopup
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.session.DshSession
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.KeyboardParams

internal fun DshHomePage.dismissKeyboard() {
    if (!inputFocused && ui.keyboardHeight <= 0f) return
    inputFocused = false
    inputView?.blur()
    bridgeModule.closeKeyboard()
    ui.keyboardHeight = 0f
}

internal fun DshHomePage.updateKeyboard(params: KeyboardParams) {
    // 仅当首页输入框自身聚焦时才让键盘顶起底页；重命名等弹窗里的输入框
    // 也会唤起软键盘，其高度不应驱动会话区布局。高度归零始终允许（收起态）。
    if (!inputFocused && params.height > 0f) return
    ui.keyboardAnimation = Animation.easeInOut(DshHomePage.ANIMATION_DURATION_S)
    ui.keyboardHeight = effectiveKeyboardHeight(params.height)
    // Closing the keyboard after send must not undo the scroll to the
    // newly sent user message. Scroll to the end only when the composer
    // is opening while no response is being anchored.
    if (ui.keyboardHeight > 0f && !ui.streaming) scrollMessagesToEnd()
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
    ui.commandSheetVisible = !ui.commandSheetVisible
}

/**
 * 点击命令（建议浮层或「+」半屏面板）：按命令执行类型分派，对齐 web 命令分发
 * （`packages/client/ui-commands/src/client/service.ts` · `dispatch`）：
 * - 直接执行类：立刻执行对应动作，不写入输入框；
 * - Popup 类：弹出对应选择器；
 * - 写入类：把 `/name ` 写入输入框并给出参数提示，等用户补全参数回车。
 */
internal fun DshHomePage.pickCommand(command: DshCommand) {
    dismissKeyboard()
    ui.commandSheetVisible = false
    when (command.kind) {
        DshCommandKind.WRITE_INPUT -> insertCommandText(command)
        DshCommandKind.DIRECT_EXECUTE -> runDirectCommand(command)
        DshCommandKind.POPUP -> when (command.popup) {
            DshCommandPopup.PERMISSION -> openPermissionPicker()
            DshCommandPopup.MODEL -> openModelPicker()
            DshCommandPopup.NONE -> Unit
        }
    }
}

/** 写入类命令：把 "/命令 " 写入输入框（补全草稿 + 原生输入框文本），聚焦等待补全参数。 */
internal fun DshHomePage.insertCommandText(command: DshCommand) {
    insertDraftText("/${command.name} ")
    inputView?.focus()
    inputFocused = true
}

/**
 * 直接执行类命令（`/compact`、`/export`）：不进输入框、不作为普通消息。
 * `/export` 复用已有的会话 ZIP 导出动作；`/compact` 走 detached 命令执行。
 */
internal fun DshHomePage.runDirectCommand(command: DshCommand) {
    when (command.name) {
        "export" -> exportActiveSession()
        "compact" -> runDetachedCommand("/compact")
        else -> Unit
    }
}

/**
 * 在输入框之外直接执行一条 host 命令（`/compact`、`/plan off`）：
 * 通过 `session.prompt` 下发命令行（host 会将其识别为命令并直接执行，返回 command 结果，
 * 而非普通模型回合），不占用输入框，也不产生本地用户气泡。命令结果作为一条助手消息落屏，
 * 与 web 命令生命周期节点等价（移动端无独立命令节点类型）。
 */
internal fun DshHomePage.runDetachedCommand(line: String) {
    val hostRepository = remoteRepo ?: run {
        connectionLabel = "本地内核尚未连接"
        return
    }
    if (!hostRepository.isProductReady()) {
        connectionLabel = syncBusyLabel()
        return
    }
    if (ui.sessions.isEmpty() || ui.activeSessionId.isEmpty()) {
        hostRepository.createSession(null, { sessionId ->
            ui.sessions = ui.sessions + DshSession(
                sessionId, "新会话", "Host", "", blank = true,
                permission = ui.permissionValue, agentPreset = ui.agentModeValue,
            )
            touchSessionActivity(sessionId)
            ui.activeSessionId = sessionId
            loadModels(sessionId)
            runDetachedCommand(line)
        }, { error ->
            ui.messages.add(DshMessage(
                "command-session-error-${ui.messages.size}",
                DshMessageRole.ERROR,
                "无法创建会话：$error",
            ))
        }, permission = ui.permissionValue, agentPreset = ui.agentModeValue)
        return
    }
    val sessionId = ui.activeSessionId
    touchSessionActivity(sessionId)
    hostRepository.streamReply(
        pagerId = pagerId,
        sessionId = sessionId,
        prompt = line,
        onDelta = { },
        onComplete = { result ->
            if (!connectionCoordinator.isActive(ui.connectionMode)) return@streamReply
            // 直接执行类命令不落一条普通消息（对齐 web：命令生命周期不是聊天气泡），仅以 toast 反馈结果。
            bridgeModule.toast(result.ifEmpty { "已执行 $line" })
        },
        onError = { error ->
            if (!connectionCoordinator.isActive(ui.connectionMode)) return@streamReply
            bridgeModule.toast(error)
        },
    )
}

/** 退出 plan 模式：直接执行 `/plan off`（对齐 web plan chip 通过 command.execute 退出）。 */
internal fun DshHomePage.exitPlanMode() {
    runDetachedCommand("/plan off")
}

/** 把文本写入 ui.draft 状态，并同步到原生输入框（ui.draft observable 不会自动回流到 TextArea）。 */

internal fun DshHomePage.insertDraftText(text: String) {
    ui.draft = text
    inputView?.setText(text)
}
