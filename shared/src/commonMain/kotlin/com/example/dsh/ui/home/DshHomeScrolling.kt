package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.views.ListContentView
import com.tencent.kuikly.core.views.ScrollParams
import com.example.dsh.message.messageRowKey
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.refreshMountedSessionRenderTrees() {
    conversationPanelIds.toList().forEach { refreshSessionRenderTree(it) }
}

internal fun DshHomePage.refreshSessionRenderTree(sessionId: String) {
    val list = messageScrollerRefs[sessionId]?.view ?: return
    (list.contentView as? ListContentView)?.createRenderViewsOnVisibleRect()
}

internal fun DshHomePage.realizeSessionAfterData(
    sessionId: String,
    scrollToEndAfterLoad: Boolean = true,
) {
    refreshSessionRenderTree(sessionId)
    addTaskWhenPagerUpdateLayoutFinish {
        refreshSessionRenderTree(sessionId)
        if (scrollToEndAfterLoad && activeSessionId == sessionId) scrollMessagesToEnd()
    }
    setTimeout(pagerId, 16) {
        refreshSessionRenderTree(sessionId)
        if (scrollToEndAfterLoad && activeSessionId == sessionId) scrollMessagesToEnd()
    }
}

internal fun DshHomePage.scrollMessagesToEnd() {
    if (!followListTail) return
    val generation = ++scrollSettleGeneration
    ensureLiveMessageCell()
    realizeVisibleMessages()
    addTaskWhenPagerUpdateLayoutFinish {
        settleScrollToEnd(generation, 0)
    }
}

internal fun DshHomePage.scrollMessagesToMessage(messageId: String) {
    val generation = ++scrollSettleGeneration
    addTaskWhenPagerUpdateLayoutFinish {
        settleScrollToMessage(messageId, generation, 0)
    }
}

/**
 * Markdown and LazyLoop can add/layout children over several frames.
 * Re-apply the bottom offset while that burst settles, otherwise the first
 * offset is calculated from a shorter content height and the user sees the
 * list walk down a few screens after launch.
 */

internal fun DshHomePage.settleScrollToEnd(generation: Int, attempt: Int) {
    if (generation != scrollSettleGeneration || !followListTail) return
    ensureLiveMessageCell()
    realizeVisibleMessages()
    scrollMessagesToEndAfterLayout()
    if (attempt >= SCROLL_SETTLE_ATTEMPTS) return
    setTimeout(pagerId, SCROLL_SETTLE_DELAYS_MS[attempt]) {
        addTaskWhenPagerUpdateLayoutFinish {
            settleScrollToEnd(generation, attempt + 1)
        }
    }
}

internal fun DshHomePage.realizeVisibleMessages() {
    val scroller = messageScrollerRefs[activeSessionId]?.view ?: return
    val content = scroller.contentView as? ListContentView ?: return
    content.flexNode.markDirty()
    content.createRenderViewsOnVisibleRect()
}

internal fun DshHomePage.onConversationUserScroll(params: ScrollParams) {
    val maxOffset = (params.contentHeight - params.viewHeight).coerceAtLeast(0f)
    val nearBottom = params.offsetY >= maxOffset - FOLLOW_LIST_SLACK_PX
    if (nearBottom) {
        followListTail = true
        return
    }
    if (params.isDragging) cancelFollowListTail()
}

internal fun DshHomePage.cancelFollowListTail() {
    followListTail = false
    scrollSettleGeneration += 1
}

internal fun DshHomePage.pinFollowListTail() {
    followListTail = true
}

internal fun DshHomePage.scrollMessagesToEndAfterLayout() {
    if (!followListTail) return
    val scroller = messageScrollerRefs[activeSessionId]?.view ?: return
    val contentHeight = scroller.contentView?.flexNode?.layoutFrame?.height ?: return
    val viewportHeight = scroller.flexNode?.layoutFrame?.height ?: return
    scroller.setContentOffset(0f, (contentHeight - viewportHeight).coerceAtLeast(0f), animated = false)
}

internal fun DshHomePage.settleScrollToMessage(messageId: String, generation: Int, attempt: Int) {
    if (generation != scrollSettleGeneration) return
    val row = messageRowRefs[messageRowKey(activeSessionId, messageId)]?.view
    val rowY = row?.flexNode?.layoutFrame?.y
    if (rowY != null) {
        messageScrollerRefs[activeSessionId]?.view?.setContentOffset(
            0f,
            rowY.coerceAtLeast(0f),
            animated = false,
        )
    }
    if (attempt >= SCROLL_SETTLE_ATTEMPTS) return
    setTimeout(pagerId, SCROLL_SETTLE_DELAYS_MS[attempt]) {
        addTaskWhenPagerUpdateLayoutFinish {
            settleScrollToMessage(messageId, generation, attempt + 1)
        }
    }
}
