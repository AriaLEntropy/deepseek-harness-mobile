package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.message.contextCatalogEntries
import com.example.dsh.message.contextInstructions
import com.example.dsh.message.contextRecalls
import com.example.dsh.message.contextRelaySender
import com.example.dsh.message.contextSections
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.host.DshRawSessionEvent
import com.example.dsh.export.DshReadableContent
import com.example.dsh.tool.DshRemoteToolCallModels
import com.example.dsh.models.DshToolCardType
import com.example.dsh.tool.dshWireEvent
import com.example.dsh.tool.toRemoteMessage
import com.example.dsh.ui.rendering.dshLiveJobs
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.example.dsh.base.setTimeout
import com.example.dsh.host.attachmentIdsFromBlocks
import com.example.dsh.host.contextSummary
import com.example.dsh.host.inlineImageDataUrl
import com.example.dsh.host.textFromBlocks
import com.example.dsh.host.toolCardType
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.showRunningTool(event: DshRawSessionEvent) {
    val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
    val model = DshRemoteToolCallModels.fromLiveCall(payload) ?: return
    val id = "tool-${event.seq}"
    if (ui.messages.any { it.id == id }) return
    // The Host emits tool/call after the assistant block that introduced
    // it. Seal that block before appending its card so the list follows the
    // actual event order instead of grouping all cards at the turn end.
    splitStreamingAssistantBeforeTool()
    ui.messages.add(model.toRemoteMessage(id).copy(sourceSeq = event.seq))
    refreshSessionRenderTree(ui.activeSessionId)
    scrollMessagesToEnd()
}

internal fun DshHomePage.showContextInjection(event: DshRawSessionEvent) {
    val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
    val data = dshWireEvent(payload).optJSONObject("data") ?: return
    val source = data.optJSONObject("source") ?: return
    if (source.optString("kind") == "user") {
        val content = data.optJSONArray("content") ?: return
        val text = textFromBlocks(content)
        val index = ui.messages.indexOfLast { it.role == DshMessageRole.USER && it.content == text }
        if (index >= 0) ui.messages[index] = ui.messages[index].copy(
            readableContent = DshReadableContent.blocks(content),
            attachmentIds = attachmentIdsFromBlocks(content),
            sourceSeq = event.seq,
        )
        return
    }
    val id = "context-${event.seq}"
    if (ui.messages.any { it.id == id }) return
    val content = data.optJSONArray("content") ?: return
    val text = buildString {
        for (index in 0 until content.length()) {
            val block = content.optJSONObject(index) ?: continue
            if (block.optString("type") == "text") append(block.optString("text"))
        }
    }.trim()
    if (text.isEmpty()) return
    ui.messages.add(DshMessage(
        id = id,
        role = DshMessageRole.TOOL,
        content = text,
        toolName = contextSummary(source),
        isContextInjection = true,
        contextBody = text,
        contextForm = source.optString("form"),
        contextCatalog = contextCatalogEntries(source),
        contextSections = contextSections(source),
        contextRecalls = contextRecalls(source),
        contextInstructions = contextInstructions(source),
        contextRelaySender = contextRelaySender(source),
        sourceSeq = event.seq,
    ))
    scrollMessagesToEnd()
}

internal fun DshHomePage.showAssistantBlocks(event: DshRawSessionEvent) {
    val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
    val data = dshWireEvent(payload).optJSONObject("data") ?: return
    val blocks = (data.optJSONObject("message") ?: data).optJSONArray("content") ?: return
    if (ui.streaming) {
        streamingSourceSeq = event.seq
        flushAssistantDelta()
        val textIndex = ui.messages.indexOfFirst { it.id == ui.streamingAssistantId }
        if (textIndex >= 0) ui.messages[textIndex] = ui.messages[textIndex].copy(sourceSeq = event.seq)
    }
    for (index in 0 until blocks.length()) {
        val block = blocks.optJSONObject(index) ?: continue
        when (block.optString("type")) {
            "image" -> {
                val attachmentId = block.optJSONObject("attachment")?.optString("attachmentId").orEmpty()
                val inlineUrl = inlineImageDataUrl(block)
                if (attachmentId.isEmpty() && inlineUrl == null) continue
                val id = "image-${event.seq}-$index"
                if (ui.messages.none { it.id == id }) {
                    ui.messages.add(DshMessage(
                        id = id,
                        role = DshMessageRole.ASSISTANT,
                        content = "",
                        attachmentId = attachmentId.ifEmpty { null },
                        imagePreviews = listOfNotNull(inlineUrl),
                        readableContent = DshReadableContent.blocks(JSONArray().apply { put(block) }),
                        sourceSeq = event.seq,
                    ))
                }
                if (attachmentId.isNotEmpty()) loadAttachment(ui.activeSessionId, attachmentId)
            }
            "text", "reasoning", "tool-call" -> Unit
            else -> {
                val id = "block-${event.seq}-$index"
                if (ui.messages.none { it.id == id }) {
                    ui.messages.add(DshMessage(
                        id = id,
                        role = DshMessageRole.TOOL,
                        content = block.toString(),
                        toolName = "未知内容块",
                        toolCardType = DshToolCardType.JSON,
                        sourceSeq = event.seq,
                    ))
                }
            }
        }
    }
    scrollMessagesToEnd()
}

internal fun DshHomePage.settleRunningTool(event: DshRawSessionEvent) {
    val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
    val eventData = dshWireEvent(payload).optJSONObject("data") ?: return
    val message = eventData.optJSONObject("message")
    val resultBlock = message?.optJSONArray("content")?.optJSONObject(0)
    val callId = resultBlock?.optString("toolCallId")
        ?: message?.optJSONObject("source")?.optString("callId")
        ?: eventData.optString("callId")
    if (callId.isEmpty()) return
    val index = ui.messages.indexOfFirst { it.role == DshMessageRole.TOOL && it.toolCallId == callId }
    if (index < 0) return
    val previous = ui.messages[index].remoteTool ?: return
    val model = DshRemoteToolCallModels.settleLiveResult(previous, payload) ?: return
    ui.messages[index] = model.toRemoteMessage(ui.messages[index].id).copy(sourceSeq = ui.messages[index].sourceSeq)
}

internal fun DshHomePage.attachmentDataUrl(attachmentId: String): String? {
    ui.attachmentRevision // Read the reactive revision so image rows rerender after downloads.
    return cachedAttachmentDataUrls[attachmentId]
}

internal fun DshHomePage.refreshQueueDock() {
    if (!isRemoteHost) {
        ui.queueItems = ObservableList()
        return
    }
    val repository = remoteRepo ?: return
    val items = repository.queue(ui.activeSessionId)
    ui.queueItems = ObservableList(items.toMutableList())
    if (items.isEmpty()) {
        ui.queueDockExpanded = false
        cancelQueueItemEdit()
    } else if (ui.queueEditingId.isNotEmpty() && items.none { it.id == ui.queueEditingId }) {
        cancelQueueItemEdit()
    }
}

internal fun DshHomePage.refreshJobsPanel() {
    if (!isRemoteHost) {
        ui.jobItems = ObservableList()
        ui.liveJobItems = ObservableList()
        ui.jobsPanelExpanded = false
        return
    }
    val repository = remoteRepo ?: return
    val items = repository.jobs(ui.activeSessionId)
    ui.jobItems = ObservableList(items.toMutableList())
    ui.liveJobItems = ObservableList(dshLiveJobs(items).toMutableList())
    if (ui.liveJobItems.isEmpty()) ui.jobsPanelExpanded = false
    if (ui.jobsPanelExpanded) {
        ui.jobsNow = bridgeModule.currentTimeStamp()
        scheduleJobsClock()
    }
}

internal fun DshHomePage.toggleJobsPanel() {
    ui.jobsPanelExpanded = !ui.jobsPanelExpanded
    if (ui.jobsPanelExpanded) {
        ui.jobsNow = bridgeModule.currentTimeStamp()
        scheduleJobsClock()
    }
}

internal fun DshHomePage.scheduleJobsClock() {
    if (!ui.jobsPanelExpanded || ui.jobsClockScheduled || ui.liveJobItems.isEmpty()) return
    ui.jobsClockScheduled = true
    setTimeout(pagerId, 1_000) {
        ui.jobsClockScheduled = false
        if (!ui.jobsPanelExpanded) return@setTimeout
        ui.jobsNow = bridgeModule.currentTimeStamp()
        scheduleJobsClock()
    }
}

internal fun DshHomePage.editQueueItem(itemId: String) {
    val item = ui.queueItems.firstOrNull { it.id == itemId } ?: return
    val text = item.text ?: return
    ui.queueDockExpanded = true
    ui.queueEditingId = itemId
    ui.queueEditingText = text
}

internal fun DshHomePage.saveQueueItem(itemId: String) {
    val repository = remoteRepo ?: return
    val text = ui.queueEditingText.trim()
    if (ui.queueActionBusy || itemId != ui.queueEditingId || text.isEmpty()) return
    ui.queueActionBusy = true
    repository.updateQueue(
        sessionId = ui.activeSessionId,
        itemId = itemId,
        action = JSONObject().apply {
            put("kind", "edit")
            put("content", JSONArray().apply { put(JSONObject().apply { put("type", "text"); put("text", text) }) })
        },
    ) { _, _ ->
        postToUi {
            ui.queueActionBusy = false
            cancelQueueItemEdit()
            refreshQueueDock()
        }
    }
}

internal fun DshHomePage.cancelQueueItemEdit() {
    ui.queueEditingId = ""
    ui.queueEditingText = ""
}

internal fun DshHomePage.removeQueueItem(itemId: String) {
    updateQueueItem(itemId, JSONObject().apply { put("kind", "remove") })
}

internal fun DshHomePage.steerQueueItem(itemId: String) {
    updateQueueItem(itemId, JSONObject().apply { put("kind", "steer") })
}

internal fun DshHomePage.updateQueueItem(itemId: String, action: JSONObject) {
    val repository = remoteRepo ?: return
    if (ui.queueActionBusy) return
    ui.queueActionBusy = true
    repository.updateQueue(
        sessionId = ui.activeSessionId,
        itemId = itemId,
        action = action,
    ) { _, _ ->
        postToUi {
            ui.queueActionBusy = false
            refreshQueueDock()
        }
    }
}
