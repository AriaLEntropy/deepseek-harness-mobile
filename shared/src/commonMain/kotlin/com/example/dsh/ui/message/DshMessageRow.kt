package com.example.dsh.ui.message

import com.example.dsh.attachment.DshFileAttachment
import com.example.dsh.attachment.DshFileHandle
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.tool.DshRemoteToolKind
import com.example.dsh.models.DshToolCardType
import com.example.dsh.tool.dshAskQuestionCard
import com.example.dsh.ui.rendering.DSH_CHAT_BODY_FONT
import com.example.dsh.ui.rendering.DSH_CHAT_BODY_LINE
import com.example.dsh.ui.rendering.DshContextDetail
import com.example.dsh.ui.rendering.DshDisclosureRow
import com.example.dsh.ui.rendering.DshExpandedPayload
import com.example.dsh.ui.rendering.DshMarkdown
import com.example.dsh.ui.rendering.DshStreamingMarkdown
import com.example.dsh.ui.rendering.DshToolDetail
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.example.dsh.host.toolCardType
import com.example.dsh.ui.chat.DshMessageFooterAction
import com.example.dsh.message.boundedContextText
import com.example.dsh.message.contextCanExpand
import com.example.dsh.message.contextInstructions
import com.example.dsh.message.contextRecalls
import com.example.dsh.message.contextRelaySender
import com.example.dsh.message.contextSections
import com.example.dsh.message.dshLooksLikeJson
import com.example.dsh.message.dshReasoningSummary
import com.example.dsh.message.iconAsset

internal fun ViewContainer<*, *>.DshMessageRow(
    message: DshMessage,
    pageStreaming: () -> Boolean,
    isWebTimeline: Boolean,
    isExpanded: () -> Boolean,
    onToggle: () -> Unit,
    isJsonNodeExpanded: (String) -> Boolean = { false },
    onToggleJsonNode: (String) -> Unit = {},
    onCopyToolContent: (String) -> Unit = {},
    onCopyMessageContent: (DshMessage) -> Unit = {},
    copied: () -> Boolean = { false },
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    onLongPress: (DshMessage, String, Float, Float) -> Unit = { _, _, _, _ -> },
    onFooterAction: (DshMessage, DshMessageFooterAction) -> Unit = { _, _ -> },
    isTurnTail: () -> Boolean = { true },
    attachmentDataUrl: (String) -> String? = { null },
    attachmentRevision: () -> Int = { 0 },
    onPreviewImage: (String) -> Unit = {},
    contentProvider: (() -> String)? = null,
    /** 回合过程分组展开时，成员卡片直接铺开、不可再折叠。 */
    bodyLocked: Boolean = false,
    /** 弹窗模式：点击展开行时改为回调页面打开底部弹层。 */
    expandInModal: () -> Boolean = { false },
    onRequestModal: (DshExpandedPayload) -> Unit = {},
    /** 是否用结构化结果卡片渲染 web/grep/glob 明细。 */
    showResultCards: () -> Boolean = { true },
    /** 是否给过程行加左侧装饰连接线（经典模式）。 */
    showConnectors: () -> Boolean = { true },
) {
    if (message.hidden) return
    val rowWidth = getPager().pageData.pageViewWidth - 36f
    val isUser = message.role == DshMessageRole.USER
    val isError = message.role == DshMessageRole.ERROR
    val renderedContent = contentProvider?.invoke() ?: message.content
    // 旧 Host 文件附件以 `[file] ... path: ...` handle 行嵌在用户正文里：
    // 气泡可见正文去掉 handle，文件另渲染为卡片。
    val userFileAttachments = if (isUser) DshFileHandle.parseAll(message.content) else emptyList()
    // 上传中：handle 尚未写入正文，先用本地草稿卡片占位，handle 落地后由正文解析接管。
    val userPendingFiles = if (isUser) message.pendingFileAttachments else emptyList()
    val userFileCards = if (userPendingFiles.isNotEmpty()) {
        userPendingFiles.map { DshFileAttachment(it.name, it.bytes, it.sha256, it.path) }
    } else {
        userFileAttachments
    }
    val userHasFiles = userFileCards.isNotEmpty()
    val visibleUserText = if (isUser) DshFileHandle.strip(message.content) else message.content
    if (
        message.role == DshMessageRole.ASSISTANT &&
        !message.isReasoning &&
        pageStreaming() &&
        renderedContent.isEmpty()
    ) {
        return
    }
    // 上下文注入卡片：Web 时间线下展示注入到上下文的资料（可展开）
    // 与 dsh web 对齐：展开后直接显示全文，不做 body 二次折叠；recall 类型标题为“上下文回忆”。
    if (isWebTimeline && message.isContextInjection) {
        View {
            attr {
                width(rowWidth)
                marginBottom(6f)
            }
            DshDisclosureRow {
                attr {
                    title = if (message.contextForm == "recall") "上下文回忆" else "上下文注入"
                    iconAsset = "context.svg"
                    this.colors = colors()
                    summary = message.toolName.orEmpty()
                    body = ""
                    open = bodyLocked || isExpanded()
                    expandable = !bodyLocked && message.contextCanExpand()
                    this.onToggle = onToggle
                    this.expandInModal = expandInModal()
                    this.onRequestModal = onRequestModal
                    compact = true
                    bodyChrome = true
                    connector = showConnectors()
                    bodyMaxHeight = 300f
                    contextDetail = DshContextDetail(
                        form = message.contextForm,
                        body = boundedContextText(message.contextBody),
                        catalog = message.contextCatalog,
                        sections = message.contextSections,
                        recalls = message.contextRecalls,
                        instructions = message.contextInstructions,
                        relaySender = message.contextRelaySender,
                    )
                }
            }
        }
        return
    }
    // 附件图片卡片：消息附带的上传图片，加载中显示占位文案
    val webAttachmentId = message.attachmentId
    if (isWebTimeline && webAttachmentId != null) {
        val dataUrl = attachmentDataUrl(webAttachmentId)
        View {
            attr {
                width(rowWidth.coerceAtLeast(0f))
                height(220f)
                marginBottom(12f)
                borderRadius(8f)
                backgroundColor(colors().bgModulePlatform)
                border(Border(1f, BorderStyle.SOLID, colors().borderL1))
                justifyContentCenter()
                alignItemsCenter()
            }
            if (dataUrl != null) {
                Image {
                    attr {
                        src(dataUrl)
                width((rowWidth - 4f).coerceAtLeast(0f))
                height(216f)
                resizeCover()
                    }
                }
            } else {
                Text {
                    attr {
                        text("图片加载中")
                        fontSize(12f)
                        color(colors().labelTertiary)
                    }
                }
            }
        }
        return
    }
    // 推理过程卡片（Think）：展示模型思考摘要，可展开全文
    if (isWebTimeline && message.isReasoning) {
        View {
            attr {
                width(rowWidth)
                marginBottom(12f)
            }
            // vforLazy 复用 cell 时不会重新执行 DshMessageRow，
            // 用 vbind 包裹让 reasoning 流式更新时 summary/body 实时刷新。
            vbind({ contentProvider?.invoke() ?: message.content }) {
                val liveContent = contentProvider?.invoke() ?: message.content
                DshDisclosureRow {
                    attr {
                        title = "Think"
                        iconAsset = "think.svg"
                        this.colors = colors()
                        summary = liveContent.dshReasoningSummary(message.streaming)
                        body = liveContent
                        open = bodyLocked || isExpanded()
                        expandable = !bodyLocked && liveContent.isNotEmpty()
                        this.onToggle = onToggle
                        this.expandInModal = expandInModal()
                        this.onRequestModal = onRequestModal
                        plainBody = true
                        compact = true
                        bodyChrome = true
                        connector = showConnectors()
                        // 长思考限高并提供内部滚动，避免展开后撑爆消息列表。
                        bodyMaxHeight = 320f
                    }
                }
            }
        }
        return
    }
    // Skill 调用卡片：展示技能执行摘要与结果
    val remoteTool = message.remoteTool
    if (isWebTimeline && remoteTool?.kind == DshRemoteToolKind.SKILL) {
        View {
            attr {
                width(rowWidth.coerceAtLeast(0f))
                marginBottom(6f)
            }
            DshDisclosureRow {
                attr {
                 
                    title = "Skill"
                    iconAsset = "tool-skill.svg"
                    this.colors = colors()
                    summary = remoteTool.summary
                    errorSummary = message.toolError
                    stopped = message.toolStopped
                    body = message.content
                    open = bodyLocked || isExpanded()
                    expandable = !bodyLocked && message.content.isNotEmpty()
                    this.onToggle = onToggle
                    this.expandInModal = expandInModal()
                    this.onRequestModal = onRequestModal
                    running = message.toolRunning
                    compact = true
                    connector = showConnectors()
                }
            }
        }
        return
    }
    if (isWebTimeline && message.role == DshMessageRole.TOOL) {
        val remoteTool = message.remoteTool
        val isRemoteSpecial = remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION ||
            remoteTool?.kind == DshRemoteToolKind.TODO
        val rawBody = remoteTool?.output?.takeIf { it.isNotEmpty() }
            ?: remoteTool?.body?.takeIf { it.isNotEmpty() }
            ?: remoteTool?.input?.takeIf { it.isNotEmpty() }
            ?: message.content
        // body 直接用 settle 阶段生成的结果（问答可读文本 / 取消中断文案），
        // 不再重算兜底：中断/取消时 dshAskReadableBody 返回空，旧逻辑会错误兜成"已回答"。
        val toolBody = remoteTool?.body?.takeIf { it.isNotEmpty() } ?: rawBody
        // 结构化提问卡片：解析成功时用专门组件渲染，替代纯文本 body。
        val askCancelled = remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION &&
            remoteTool.summary == "已取消"
        val askAborted = remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION &&
            message.toolStopped
        val askCardData = if (remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION) {
            dshAskQuestionCard(remoteTool.input, remoteTool.output ?: "", askCancelled, askAborted)
        } else null
        // 结构化结果卡片（可配置开关）：web 检索/抓取、grep/glob。
        val webResult = if (showResultCards()) remoteTool?.webCard else null
        val searchResult = if (showResultCards()) remoteTool?.searchCard else null
        val hasStructuredResult = askCardData != null || webResult != null || searchResult != null
        val effectiveBody = if (hasStructuredResult) "" else toolBody
        val trimmedBody = effectiveBody.trimStart()
        val isJson = !isRemoteSpecial && !hasStructuredResult &&
            (trimmedBody.startsWith("{") || trimmedBody.startsWith("["))
        val cardLabel = remoteTool?.title ?: when (message.toolCardType) {
            DshToolCardType.TERMINAL -> "Bash"
            DshToolCardType.READ -> "Read"
            DshToolCardType.DIFF -> "Diff"
            DshToolCardType.SEARCH -> "Search"
            DshToolCardType.WEB -> "Web"
            DshToolCardType.JSON -> "JSON"
            DshToolCardType.GENERIC -> message.toolName ?: "工具"
        }
        val summary = remoteTool?.summary?.takeUnless { it.dshLooksLikeJson() }
            ?: if (remoteTool?.kind == DshRemoteToolKind.ASK_QUESTION) "" else
                toolBody.lineSequence().firstOrNull().orEmpty().takeUnless { it.dshLooksLikeJson() }.orEmpty()
        // 工具调用行：Bash/Read 等，JSON 结果可折叠展开
        View {
            attr {
                width(rowWidth.coerceAtLeast(0f))
                marginBottom(6f)
            }
            DshDisclosureRow {
                attr {
                    title = if (cardLabel.dshLooksLikeJson()) (remoteTool?.toolName ?: "工具") else cardLabel
                    iconAsset = remoteTool?.iconAsset() ?: message.toolCardType.iconAsset()
                    this.colors = colors()
                    this.summary = summary
                    errorSummary = message.toolError
                    stopped = message.toolStopped
                    body = ""
                    jsonContent = if (isJson) effectiveBody else ""
                    open = bodyLocked || isExpanded()
                    expandable = !bodyLocked
                    this.onToggle = onToggle
                    this.expandInModal = expandInModal()
                    this.onRequestModal = onRequestModal
                    this.isJsonNodeExpanded = isJsonNodeExpanded
                    this.onToggleJsonNode = onToggleJsonNode
                    running = message.toolRunning
                    askCard = askCardData
                    webCard = webResult
                    searchCard = searchResult
                    compact = true
                    connector = showConnectors()
                    onCopyToolCommand = onCopyToolContent
                    toolDetail = if (isJson || hasStructuredResult) null else DshToolDetail(
                        kind = remoteTool?.kind ?: DshRemoteToolKind.GENERIC,
                        input = remoteTool?.input.orEmpty(),
                        output = remoteTool?.output.orEmpty(),
                        fallback = effectiveBody,
                        running = message.toolRunning,
                        error = message.toolError,
                        filePath = remoteTool?.filePath,
                    )
                }
            }
        }
        return
    }
        // 普通消息行：用户气泡右对齐，助手/错误左对齐
        View {
            attr {
                flexDirectionColumn()
                alignItems(if (isUser) FlexAlign.FLEX_END else FlexAlign.FLEX_START)
                marginBottom(18f)
            }
        // 消息内容容器：用户/错误为气泡底色，助手为纯文本。长按助手内容弹操作菜单。
        View {
            attr {
                if (!isUser && !isError) {
                    width(rowWidth.coerceAtMost(620f).coerceAtLeast(0f))
                }
                maxWidth(620f)
                padding(if (isUser) 10f else 0f, if (isUser) 14f else 0f, if (isUser) 10f else 0f, if (isUser) 14f else 0f)
                if (isUser) {
                    borderRadius(BorderRectRadius(18f, 18f, 18f, 6f))
                } else {
                    borderRadius(0f)
                }
                backgroundColor(when {
                        isUser -> colors().specificBubble
                        isError -> Color(0xFFFFEEEE)
                        else -> Color(0x00FFFFFF)
                    })
            }
            event {
                if (!isUser && !isError) {
                    longPress {
                        onLongPress(message, renderedContent, it.pageX, it.pageY)
                    }
                }
            }
            if (isUser || isError) {
                vif({ visibleUserText.isNotEmpty() || !userHasFiles }) {
                    Text {
                        attr {
                            text(visibleUserText)
                            lines(Int.MAX_VALUE)
                            fontSize(DSH_CHAT_BODY_FONT)
                            lineHeight(DSH_CHAT_BODY_LINE)
                            color(if (isUser) colors().specificBubbleForeground else colors().stateErrorPrimary)
                        }
                    }
                }
            // 助手回复内容：Markdown 渲染 + 流式光标
            } else {
                View {
                    attr {
                        flexDirectionColumn()
                    }
                    DshMarkdown {
                        attr {
                            contentWidth = rowWidth.coerceAtLeast(0f)
                            val raw = contentProvider?.invoke() ?: message.content
                            val live = pageStreaming()
                            content = raw
                            liveContent = contentProvider
                            streamingProvider = pageStreaming
                            streaming = live
                            darkMode = colors().isDark
                        }
                    }
                    vif({ pageStreaming() && (contentProvider?.invoke() ?: message.content).isNotEmpty() }) {
                        Text {
                            attr {
                                text(DshStreamingMarkdown.CURSOR)
                                fontSize(14f)
                                color(colors().stateBusinessPrimary)
                                marginTop(2f)
                            }
                        }
                    }
                }
            }
        }
        // 用户消息随文图片（本地内存预览；历史恢复走 Host timeline attachmentId，异步加载后通过 attachmentRevision 触发重渲染）
        vif({
            val rev = attachmentRevision() // 建立响应式依赖，attachment 加载完成后重渲染
            isUser && message.imagePreviews.isNotEmpty()
        }) {
            View {
                attr {
                    flexDirectionRow()
                    flexWrap(FlexWrap.WRAP)
                    justifyContent(FlexJustifyContent.FLEX_END)
                    marginTop(6f)
                    marginBottom(2f)
                }
                vforIndex({ ObservableList<String>().apply { addAll(message.imagePreviews) } }) { preview, _, _ ->
                    View {
                        attr {
                            size(72f, 72f)
                            marginLeft(6f)
                            borderRadius(8f)
                            backgroundColor(colors().bgModulePlatform)
                            border(Border(1f, BorderStyle.SOLID, colors().borderL1))
                        }
                        Image {
                            attr {
                                src(preview)
                                width(72f)
                                height(72f)
                                resizeCover()
                            }
                            event {
                                click { onPreviewImage(preview) }
                            }
                        }
                        vif({ message.attachmentUploading }) {
                            View {
                                attr {
                                    absolutePositionAllZero()
                                    borderRadius(8f)
                                    backgroundColor(Color(0x66000000))
                                    allCenter()
                                }
                                DshLoadingSpinner(diameter = 24f, tint = Color.WHITE)
                            }
                        }
                    }
                }
            }
        }
        // 用户消息随文文件（旧 Host：prompt handle 行解析为卡片）：横向排布可滑动
        vif({ isUser && userFileCards.isNotEmpty() }) {
            val filesWidth = (192f * userFileCards.size).coerceAtMost(rowWidth.coerceAtMost(620f))
            Scroller {
                attr {
                    width(filesWidth)
                    height(64f)
                    flexDirection(FlexDirection.ROW)
                    justifyContent(FlexJustifyContent.FLEX_END)
                    showScrollerIndicator(false)
                    scrollWithParent(false)
                }
                vfor({ ObservableList<DshFileAttachment>().apply { addAll(userFileCards) } }) { file ->
                    DshUserFileCard(file = file, uploading = message.attachmentUploading, colors = colors)
                }
            }
        }
        // AI 回答下方的横向操作容器（footer），对齐 dsh 原版 IconActions 行。
        // 仅在回答结算（非流式）且为该轮最后一段时出现，避免分段重复渲染。
        // 用 vif 而非普通 if：流式结算发生在 cell 建好之后，只有响应式条件才会在
        // settle 时补挂 footer，否则操作栏（尤其是首次回复）永远不渲染。
        vif({ message.role == DshMessageRole.ASSISTANT && !pageStreaming() && isTurnTail() }) {
            DshMessageFooter(copied = copied(), colors = colors) { action ->
                // COPY 复制整个回合的完整正文（跨工具调用的所有正文段），由页面层聚合
                if (action == DshMessageFooterAction.COPY) {
                    onCopyMessageContent(message)
                } else {
                    onFooterAction(message, action)
                }
            }
        }
    }
}
