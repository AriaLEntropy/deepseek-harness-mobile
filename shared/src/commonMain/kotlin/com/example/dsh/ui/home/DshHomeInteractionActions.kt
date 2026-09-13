package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.ui.message.DshMessageActionItem
import com.example.dsh.ui.chat.DshMessageFooterAction
import com.example.dsh.message.buildQuestionAnswer
import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.message.DshMessage
import com.example.dsh.interaction.DshQuestionDraft
import com.example.dsh.export.DshReadableContent
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.host.DshRpcError
import com.example.dsh.base.bridgeModule
import com.example.dsh.base.blurModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.base.setTimeout
import com.example.dsh.interaction.interactionFailureLabel
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.goalMutation(
    action: (DshRemoteRepository, DshGoalSnapshot, (DshRpcError?) -> Unit) -> Unit,
    onDone: (Boolean) -> Unit = {},
) {
    val goal = goalSnapshot ?: return
    val remote = remoteRepo ?: return
    if (goalActionBusy) return
    goalActionBusy = true
    goalActionError = ""
    action(remote, goal) { error ->
        postToUi {
            goalActionBusy = false
            if (error != null) goalActionError = "${error.message} (${error.code})"
            else goalActionError = ""
            onDone(error == null)
        }
    }
}

internal fun DshHomePage.pauseGoal() = goalMutation(action = { remote, goal, callback -> remote.goalPause(activeSessionId, goal, callback) })

internal fun DshHomePage.resumeGoal() = goalMutation(action = { remote, goal, callback -> remote.goalResume(activeSessionId, goal, callback) })

internal fun DshHomePage.editGoal(objective: String, onDone: (Boolean) -> Unit) = goalMutation(
    action = { remote, goal, callback -> remote.goalEdit(activeSessionId, goal, objective, callback) },
    onDone = onDone,
)

internal fun DshHomePage.clearGoal() = goalMutation(action = { remote, goal, callback ->
    remote.goalClear(activeSessionId, goal) { error ->
        if (error == null) goalSnapshot = null
        callback(error)
    }
})

internal fun DshHomePage.refreshPendingInteractions() {
    refreshPendingSessionIds()
    if (!isRemoteHost) {
        pendingApproval = null
        pendingQuestion = null
        selectedQuestionOptions.clear()
        questionCustom = ""
        questionIndex = 0
        questionError = ""
        questionDrafts.clear()
        return
    }
    val repository = remoteRepo ?: return
    val (approval, question) = repository.pendingInteractions(activeSessionId)
    val hadInteraction = pendingApproval != null || pendingQuestion != null
    pendingApproval = approval
    pendingQuestion = question
    // 授权/提问交互刚出现时收起键盘，让底部提问卡片覆盖输入框，而非浮在键盘上方
    if (!hadInteraction && (approval != null || question != null)) dismissKeyboard()
    questionIndex = questionIndex.coerceIn(0, (question?.questions?.size ?: 1) - 1)
    loadQuestionDraft(questionIndex)
}

internal fun DshHomePage.answerApproval(outcome: String) {
    val repository = remoteRepo ?: return
    val approval = pendingApproval ?: return
    interactionBusy = true
    repository.respondApproval(
        rpcId = approval.rpcId,
        sessionId = approval.sessionId,
        approvalId = approval.approvalId,
        outcome = outcome,
    ) { accepted, reason ->
        postToUi {
            interactionBusy = false
            if (!accepted) {
                connectionLabel = interactionFailureLabel(reason)
                return@postToUi
            }
            refreshPendingInteractions()
        }
    }
}

internal fun DshHomePage.toggleQuestionOption(label: String) {
    val item = pendingQuestion?.questions?.getOrNull(questionIndex) ?: return
    if (!item.multiSelect) {
        selectedQuestionOptions.clear()
        questionCustom = ""
    }
    if (selectedQuestionOptions.contains(label)) selectedQuestionOptions.remove(label)
    else selectedQuestionOptions.add(label)
    questionError = ""
    questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
    questionHasSelection = selectedQuestionOptions.isNotEmpty() || questionCustom.isNotBlank()
}

internal fun DshHomePage.updateQuestionCustom(value: String) {
    val item = pendingQuestion?.questions?.getOrNull(questionIndex) ?: return
    if (!item.multiSelect) selectedQuestionOptions.clear()
    questionCustom = value
    questionError = ""
    questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
    questionHasSelection = selectedQuestionOptions.isNotEmpty() || questionCustom.isNotBlank()
}

internal fun DshHomePage.skipQuestion() {
    val count = pendingQuestion?.questions?.size ?: return
    questionDrafts[questionIndex] = DshQuestionDraft(skipped = true)
    selectedQuestionOptions.clear()
    questionCustom = ""
    questionError = ""
    questionHasSelection = false
    if (questionIndex < count - 1) {
        questionIndex += 1
        loadQuestionDraft(questionIndex)
    } else {
        submitQuestion()
    }
}

/**
 * 取消提问（关闭按钮）：以 ok=false + code=cancelled 拒绝整个等待
 * （与原版 apiproxy respond 的 cancelled 分支一致，wire 上表现为 ASK_CANCELLED），
 * 同时清除本地 UI 状态。不再把全部题目标记 skipped 后当作正常答案提交。
 */

internal fun DshHomePage.cancelQuestion() {
    val question = pendingQuestion ?: return
    if (question.rpcId.isEmpty()) {
        questionError = "这个问题已失效，请等 Agent 重新提问"
        return
    }
    val repository = remoteRepo
    if (repository == null) {
        return
    }
    questionError = ""
    interactionBusy = true
    repository.respondQuestionCancel(question.rpcId, question.sessionId) { accepted, reason ->
        postToUi {
            interactionBusy = false
            if (!accepted) {
                questionError = interactionFailureLabel(reason)
                return@postToUi
            }
            repository.clearPending(question.rpcId)
            if (pendingQuestion?.rpcId == question.rpcId) {
                pendingQuestion = null
                selectedQuestionOptions.clear()
                questionCustom = ""
                questionError = ""
                questionDrafts.clear()
            }
            refreshPendingInteractions()
            if (activeSessionId == question.sessionId) {
                loadWebTimeline(question.sessionId, scrollToEndAfterLoad = true)
            }
        }
    }
}

internal fun DshHomePage.navigateQuestion(delta: Int) {
    val count = pendingQuestion?.questions?.size ?: return
    val next = (questionIndex + delta).coerceIn(0, count - 1)
    if (next == questionIndex) return
    questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
    questionIndex = next
    questionError = ""
    loadQuestionDraft(next)
}

internal fun DshHomePage.loadQuestionDraft(index: Int) {
    val draft = questionDrafts[index] ?: DshQuestionDraft()
    selectedQuestionOptions.clear()
    selectedQuestionOptions.addAll(draft.selected)
    questionCustom = draft.custom
    questionHasSelection = selectedQuestionOptions.isNotEmpty() || questionCustom.isNotBlank()
}

internal fun DshHomePage.submitQuestion() {
    val repository = remoteRepo
    if (repository == null) {
        return
    }
    val question = pendingQuestion
    if (question == null) {
        return
    }
    // 保留已跳过的草稿：skipQuestion 已把当前题标记为 skipped=true，不能再被未作答草稿覆盖，
    // 否则“跳过最后一题/单题”时会被下方的未作答校验拦下。其余路径的草稿在交互时已写入。
    val currentDraft = questionDrafts[questionIndex]
    if (currentDraft?.skipped != true) {
        questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
    }
    val missing = question.questions.indexOfFirst { item ->
        val draft = questionDrafts[question.questions.indexOf(item)] ?: DshQuestionDraft()
        draft.selected.isEmpty() && draft.custom.isBlank() && !draft.skipped
    }
    if (missing >= 0) {
        questionIndex = missing
        loadQuestionDraft(missing)
        questionError = "请先选择一项，或自己写答案"
        return
    }
    if (question.rpcId.isEmpty()) {
        questionError = "这个问题已失效，请等 Agent 重新提问"
        return
    }
    questionError = ""
    interactionBusy = true
    val answer = buildQuestionAnswer(question, questionDrafts)
    repository.respondQuestion(
        rpcId = question.rpcId,
        sessionId = question.sessionId,
        answer = answer,
    ) { accepted, reason ->
        postToUi {
            interactionBusy = false
            if (!accepted) {
                questionError = interactionFailureLabel(reason)
                return@postToUi
            }
            repository.clearPending(question.rpcId)
            if (pendingQuestion?.rpcId == question.rpcId) {
                pendingQuestion = null
                selectedQuestionOptions.clear()
                questionCustom = ""
                questionError = ""
                questionDrafts.clear()
            }
            refreshPendingInteractions()
            if (activeSessionId == question.sessionId) {
                loadWebTimeline(question.sessionId, scrollToEndAfterLoad = true)
            }
        }
    }
}

internal fun DshHomePage.forkMessage(message: DshMessage) {
    closeMessageActions()
    dismissKeyboard()
    if (messageForkBusy) { bridgeModule.toast("正在创建分支，请稍候"); return }
    val remote = remoteRepo
    if (remote == null || !remote.isProductReady()) { bridgeModule.toast("请先连接 Host 后再分叉"); return }
    // Footer/menu closures can hold a pre-settle row. Resolve the latest metadata by identity.
    val target = messages.firstOrNull { it.id == message.id } ?: run {
        bridgeModule.toast("消息已更新，请重新选择后分叉")
        return
    }
    val sourceSessionId = activeSessionId
    val connection = activeConnectionId
    val version = ++messageForkVersion
    var createdSessionId: String? = null
    messageForkBusy = true
    if (target.sourceSeq != null && !target.streaming) bridgeModule.toast("正在创建分支…")
    fun current() = pageAlive && messageForkVersion == version && repository === remote && activeConnectionId == connection
    remote.forkMessage(sourceSessionId, target) { childSessionId, error ->
        postToUi {
            if (!current()) return@postToUi
            if (error != null || childSessionId == null) {
                messageForkBusy = false
                if (error?.code == "message-unsynced" && activeSessionId == sourceSessionId) {
                    bridgeModule.toast("正在同步消息，请稍后再次分叉")
                    loadWebTimeline(sourceSessionId)
                } else {
                    bridgeModule.toast("分叉失败：${error?.message ?: "Host 未返回新会话 ID"}")
                }
                return@postToUi
            }
            createdSessionId = childSessionId
            if (activeSessionId != sourceSessionId) {
                messageForkBusy = false
                bridgeModule.toast("分支已创建，可从会话列表打开")
                loadRepository(preferredSessionId = activeSessionId, restoreOnError = false)
                return@postToUi
            }
            jumpToSession(childSessionId, isCurrent = { current() && activeSessionId == sourceSessionId }) { ok, detail ->
                if (!current()) return@jumpToSession
                messageForkBusy = false
                bridgeModule.toast(if (ok) "已在新对话中分支" else "分支已创建，打开失败：$detail")
                loadRepository(preferredSessionId = activeSessionId, restoreOnError = false)
            }
        }
    }
    setTimeout(pagerId, 35_000) {
        if (!current() || !messageForkBusy) return@setTimeout
        messageForkVersion++
        messageForkBusy = false
        bridgeModule.toast(if (createdSessionId == null) "创建分支超时，请刷新会话列表确认结果" else "分支已创建，请从会话列表打开")
    }
}

internal fun DshHomePage.openMessageActions(
    message: DshMessage,
    content: String,
    x: Float,
    y: Float,
) {
    // 长按事件可能重复触发，菜单已打开时直接忽略，避免重复截图/模糊
    if (messageActionsMessage != null) return
    if (exportSelectMode) return
    bridgeModule.log("openMessageActions id=${message.id} role=${message.role} x=$x y=$y")
    dismissKeyboard()
    messageActionsX = x
    messageActionsY = y
    menuBlurUri = ""
    val sessionId = activeSessionId
    val remote = repository
    // 菜单是页面内覆盖层，必须先截图模糊再显示菜单，否则模糊图会包含菜单自身
    blurModule.captureBlur(24) { uri ->
        if (!pageAlive || activeSessionId != sessionId || repository !== remote) return@captureBlur
        menuBlurUri = uri
        messageActionsMessage = message
    }
}

internal fun DshHomePage.closeMessageActions() {
    messageActionsMessage = null
    menuBlurUri = ""
}

/** 复制回答正文；工具卡片使用独立复制入口，完整记录由分享承载。 */

internal fun DshHomePage.copyMessageBody(message: DshMessage) {
    val text = DshReadableContent.copyText(sessionMessageState(activeSessionId), message.id) { m ->
        if (streaming && streamingAssistantId == m.id && streamingAssistantContent.isNotEmpty()) {
            streamingAssistantContent
        } else {
            m.content
        }
    }
    if (text.isEmpty()) {
        bridgeModule.toast("没有可复制的内容")
        return
    }
    bridgeModule.copyToPasteboard(text)
    bridgeModule.toast("已复制")
    copiedMessageId = message.id
    setTimeout(pagerId, 1500) {
        if (copiedMessageId == message.id) copiedMessageId = ""
    }
}

/** 复制长按菜单目标消息所在回合的完整正文 */

internal fun DshHomePage.copyMessageActionsText() {
    val message = messageActionsMessage ?: return
    closeMessageActions()
    copyMessageBody(message)
}

/** 「选择文本」：打开弹窗，以单个可选中文本节点承载完整正文，供原生选区复制 */

internal fun DshHomePage.selectMessageActionsText() {
    val message = messageActionsMessage ?: return
    closeMessageActions()
    if (streaming && streamingAssistantId == message.id) {
        bridgeModule.toast("内容生成中，请稍候")
        return
    }
    val text = DshReadableContent.copyText(sessionMessageState(activeSessionId), message.id) { m ->
        if (streaming && streamingAssistantId == m.id && streamingAssistantContent.isNotEmpty()) {
            streamingAssistantContent
        } else {
            m.content
        }
    }
    if (text.isEmpty()) {
        bridgeModule.toast("没有可复制的内容")
        return
    }
    selectTextModalContent = text
    selectTextModalVisible = true
}

internal fun DshHomePage.closeSelectTextModal() {
    selectTextModalVisible = false
    selectTextModalContent = ""
}

internal fun DshHomePage.onMessageFooterAction(message: DshMessage, action: DshMessageFooterAction) {
    when (action) {
        DshMessageFooterAction.COPY -> copyMessageBody(message)
        DshMessageFooterAction.BRANCH -> forkMessage(message)
        DshMessageFooterAction.SHARE -> shareMessageSelection(message)
        DshMessageFooterAction.GOOD,
        DshMessageFooterAction.BAD -> {
            // 反馈后续实现，本期先完成 UI
        }
    }
}

internal fun DshHomePage.messageActionsItems(): ObservableList<DshMessageActionItem> {
    val result = ObservableList<DshMessageActionItem>()
    result.addAll(listOf(
        DshMessageActionItem("复制", "copy.svg", { copyMessageActionsText() }),
        DshMessageActionItem("选择文本", "text-select.svg", { selectMessageActionsText() }),
        DshMessageActionItem("好的回答", "like.svg", { closeMessageActions() }),
        DshMessageActionItem("有问题的回答", "dislike.svg", { closeMessageActions() }),
        DshMessageActionItem("在新对话中分支", "branch.svg", { messageActionsMessage?.let(::forkMessage) }),
        DshMessageActionItem("分享", "share.svg", { messageActionsMessage?.let(::shareMessageSelection) }),
    ))
    return result
}
