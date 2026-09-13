package com.example.dsh.home


import com.example.dsh.session.DshSession
import com.example.dsh.session.DshWorkspaceGroup
import com.example.dsh.infrastructure.currentTimeMillis
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.event.EventName
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Span
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

/**
 * 搜索命中行：一条会话内匹配的消息（标题命中时退化为标题行）。
 * 同一会话有多条内容命中时拆成多行，用 [matchBadge]（如「1/2」）标注序号。
 * 摘要拆成 before/match/after 三段，渲染时把 match 加粗。
 */
internal data class DshSessionSearchHit(
    val sessionId: String,
    val title: String,
    val dateLabel: String,
    val snippetBefore: String = "",
    val snippetMatch: String = "",
    val snippetAfter: String = "",
    val matchBadge: String = "",
)

internal data class DshSessionSearchSnippet(
    val before: String,
    val match: String,
    val after: String,
)

private const val DSH_SEARCH_SNIPPET_BEFORE = 12
private const val DSH_SEARCH_SNIPPET_AFTER = 26
private const val DSH_SEARCH_SNIPPET_MAX = DSH_SEARCH_SNIPPET_BEFORE + DSH_SEARCH_SNIPPET_AFTER

/** 抽屉底部固定区顶部的渐变遮罩高度：列表滑入该区间时被侧栏底色渐隐盖住。 */
private const val DSH_DRAWER_FOOTER_MASK_HEIGHT = 32f

private val DSH_SEARCH_FENCE = Regex("```[\\s\\S]*?```")
private val DSH_SEARCH_IMAGE = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
private val DSH_SEARCH_LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
private val DSH_SEARCH_HTML = Regex("<[^>]+>")
private val DSH_SEARCH_HEADING = Regex("(?m)^\\s*#{1,6}\\s*")
private val DSH_SEARCH_QUOTE = Regex("(?m)^\\s*>\\s?")
private val DSH_SEARCH_LIST = Regex("(?m)^\\s*(?:[-*+]|\\d+[.)])\\s+")
private val DSH_SEARCH_INLINE_CODE = Regex("`([^`]*)`")
private val DSH_SEARCH_EMPHASIS = Regex("(\\*\\*|__|\\*|_|~~)")
private val DSH_SEARCH_WHITESPACE = Regex("\\s+")

/** 去掉 Markdown/HTML 语法（代码块、图片、链接、标题、列表、强调、标签），压成单行纯文本。 */
internal fun dshSearchPlainText(content: String): String {
    var text = content
    text = DSH_SEARCH_FENCE.replace(text, " ")
    text = DSH_SEARCH_IMAGE.replace(text, " ")
    text = DSH_SEARCH_LINK.replace(text) { it.groupValues[1] }
    text = DSH_SEARCH_HTML.replace(text, " ")
    text = DSH_SEARCH_HEADING.replace(text, "")
    text = DSH_SEARCH_QUOTE.replace(text, "")
    text = DSH_SEARCH_LIST.replace(text, "")
    text = DSH_SEARCH_INLINE_CODE.replace(text) { it.groupValues[1] }
    text = DSH_SEARCH_EMPHASIS.replace(text, "")
    return DSH_SEARCH_WHITESPACE.replace(text, " ").trim()
}

/**
 * 生成单行摘要：以匹配词为中心取上下文，两端按需补省略号；
 * 匹配段单独返回，供结果行加粗显示。
 */
internal fun dshSearchSnippet(content: String, needle: String): DshSessionSearchSnippet {
    val plain = dshSearchPlainText(content)
    val index = plain.lowercase().indexOf(needle.lowercase())
    if (index < 0) return DshSessionSearchSnippet(plain.take(DSH_SEARCH_SNIPPET_MAX), "", "")
    val start = (index - DSH_SEARCH_SNIPPET_BEFORE).coerceAtLeast(0)
    val matchEnd = (index + needle.length).coerceAtMost(plain.length)
    val end = (matchEnd + DSH_SEARCH_SNIPPET_AFTER).coerceAtMost(plain.length)
    val before = (if (start > 0) "…" else "") + plain.substring(start, index)
    val match = plain.substring(index, matchEnd)
    val after = plain.substring(matchEnd, end) + (if (end < plain.length) "…" else "")
    return DshSessionSearchSnippet(before, match, after)
}

internal fun ViewContainer<*, *>.DshSessionDrawer(
    sessions: () -> ObservableList<DshSession>,
    workspaceGroups: () -> ObservableList<DshWorkspaceGroup>,
    isWebTimeline: () -> Boolean,
    activeId: () -> String,
    animated: () -> Boolean,
    expandedGroupIds: () -> Set<String>,
    onToggleGroup: (String) -> Unit,
    sessionPending: (String) -> Boolean,
    activeWorkspaceId: () -> String,
    overflowVisible: () -> Boolean,
    overflowActions: () -> ObservableList<DshOverflowAction>,
    onOverflowSelect: (String) -> Unit,
    onDismissOverflow: () -> Unit,
    onOpenOverflowFor: (String, Float, Float) -> Unit,
    overflowAnchorX: () -> Float = { -1f },
    overflowAnchorY: () -> Float = { -1f },
    dragState: () -> DshDrawerDrag = { DshDrawerDrag() },
    onSessionDragStart: (String, String, Int, Float) -> Unit = { _, _, _, _ -> },
    onWorkspaceDragStart: (String, Int, Float, Float) -> Unit = { _, _, _, _ -> },
    onDragMove: (Float, List<Float>) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    viewGroupBy: () -> String = { DSH_DRAWER_GROUP_WORKSPACE },
    viewOrderBy: () -> String = { DSH_DRAWER_ORDER_UPDATED },
    viewOptionsVisible: () -> Boolean = { false },
    viewOptionsAnchorX: () -> Float = { -1f },
    viewOptionsAnchorY: () -> Float = { -1f },
    onViewOptionsOpen: (Float, Float) -> Unit = { _, _ -> },
    onViewOptionsSelect: (String) -> Unit = {},
    onViewOptionsDismiss: () -> Unit = {},
    statusBarHeight: Float,
    pageViewWidth: Float,
    pageViewHeight: Float = 0f,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchive: () -> Unit,
    onSelect: (String) -> Unit,
    onOpenSearch: () -> Unit = {},
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    // 工作区拖拽按分组实际高度估算落点；展开状态变化时在拖拽回调内实时计算。
    val dshWorkspaceHeights: () -> List<Float> = {
        workspaceGroups().filter { it.workspaceId.isNotEmpty() }.map { group ->
            DSH_DRAWER_WORKSPACE_HEADER_HEIGHT +
                if (expandedGroupIds().contains(group.workspaceId)) {
                    group.sessions.size * DSH_DRAWER_ROW_HEIGHT
                } else {
                    0f
                }
        }
    }
    val dshWorkspaceIndexOf: (String) -> Int = { id ->
        workspaceGroups().filter { it.workspaceId.isNotEmpty() }.indexOfFirst { it.workspaceId == id }
    }
    // 视图选项的「分组方式」：远程模式按工作区分组，否则单列表（对齐电脑端 groupBy）。
    val dshGrouped: () -> Boolean = { isWebTimeline() && viewGroupBy() == DSH_DRAWER_GROUP_WORKSPACE }
    val drawerWidth = (pageViewWidth - 44f).coerceAtMost(340f)
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionRow()
            backgroundColor(Color(0x00000000))
        }
        View {
            attr {
                width(drawerWidth)
                height(pagerData.pageViewHeight)
                flexDirectionColumn()
                paddingTop(pagerData.statusBarHeight + 10f)
                paddingLeft(14f)
                paddingRight(14f)
                paddingBottom(18f)
                backgroundColor(colors().specificSidebarFill)
                transform(Translate(if (animated()) 0f else -1f, 0f))
                animation(Animation.easeOut(0.24f), animated())
            }
            View {
                attr {
                    height(48f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                DshWordmark(colors = { colors() })
                View { attr { flex(1f) } }
            }
            // 搜索入口：替换原先的「新会话」按钮，点击进入独立的全屏搜索界面。
            View {
                attr {
                    height(40f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(10f)
                    backgroundColor(colors().bgSkeleton)
                }
                Image { attr { src(ImageUri.commonAssets("tool-search.svg")); size(16f, 16f); tintColor(colors().labelTertiary) } }
                Text {
                    attr {
                        text("搜索对话内容…")
                        marginLeft(8f)
                        flex(1f)
                        fontSize(14f)
                        color(colors().labelTertiary)
                    }
                }
                DshHitButton { onOpenSearch() }
            }
            View {
                attr { marginTop(16f); marginBottom(6f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(if (dshGrouped()) "工作区" else "会话")
                        flex(1f)
                        fontSize(12f)
                        color(colors().labelTertiary)
                    }
                }
                // 视图选项入口：对齐电脑端侧边栏的 ViewOptionsMenu（个人化图标，右侧）。
                vif({ isWebTimeline() }) {
                    View {
                        attr { size(28f, 28f); allCenter(); borderRadius(14f) }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("view-options.svg"))
                                size(16f, 16f)
                                tintColor(colors().labelSecondary)
                            }
                        }
                        View {
                            attr { absolutePositionAllZero(); backgroundColor(Color(0x00000000)) }
                            event { click { params -> onViewOptionsOpen(params.pageX, params.pageY) } }
                        }
                    }
                }
            }
            Scroller {
                attr { flex(1f); scrollEnable(dragState().kind == DshDrawerDragKind.NONE) }
                vif({ !dshGrouped() }) {
                    vforIndex({ sessions() }) { session, index, count ->
                        DshSessionDrawerRow(
                            title = session.title,
                            subtitle = "",
                            active = { activeId() == session.id },
                            running = session.running,
                            updatedAt = session.updatedAt,
                            now = currentTimeMillis(),
                            indented = false,
                            pending = { sessionPending(session.id) },
                            onSelect = { onSelect(session.id) },
                            onOpenOverflow = { x, y -> onOpenOverflowFor(session.id, x, y) },
                            dragState = dragState,
                            dragScope = "",
                            dragId = session.id,
                            dragIndex = index,
                            dragItemCount = count,
                            onDragStart = { pageY -> onSessionDragStart("", session.id, index, pageY) },
                            onDragMove = onDragMove,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                            colors = colors,
                        )
                    }
                }
                vif({ dshGrouped() }) {
                    vfor({ workspaceGroups() }) { group ->
                        val groupKey = group.workspaceId
                        val workspaceIndex = dshWorkspaceIndexOf(groupKey)
                        View {
                            attr {
                                marginTop(4f)
                                marginBottom(2f)
                                flexDirectionColumn()
                                if (groupKey.isNotEmpty()) {
                                    val state = dragState()
                                    val offset = dshRowDragOffset(state, DSH_WORKSPACE_DRAG_SCOPE, groupKey, workspaceIndex)
                                    // 用百分比位移（而非 dp offset）：dp offset 会走 frame task 延迟生效，
                                    // 松手后可能残留；百分比立即写入，且每次显式复位为 0。
                                    val groupHeight = (
                                        40f + if (expandedGroupIds().contains(groupKey)) {
                                            group.sessions.size * DSH_DRAWER_ROW_HEIGHT
                                        } else {
                                            0f
                                        }
                                        ).coerceAtLeast(1f)
                                    transform(Translate(0f, offset / groupHeight))
                                    zIndex(if (offset != 0f) 1 else 0)
                                    opacity(if (offset != 0f) 0.9f else 1f)
                                }
                            }
                            // 文件夹行：内嵌菜单头（文件夹图标 + 标题 + 旋转箭头）
                            View {
                                attr {
                                    height(40f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingLeft(8f)
                                    paddingRight(8f)
                                    borderRadius(9f)
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets(
                                            if (expandedGroupIds().contains(groupKey)) "folder.svg" else "folder-closed.svg",
                                        ))
                                        size(16f, 16f)
                                        tintColor(if (groupKey.isNotEmpty() &&
                                            expandedGroupIds().contains(groupKey) &&
                                            activeWorkspaceId() == groupKey
                                        ) colors().stateBusinessPrimary else colors().labelTertiary)
                                    }
                                }
                                View { attr { width(4f) } }
                                Text {
                                    attr {
                                        text(group.title)
                                        flex(1f)
                                        lines(1)
                                        fontSize(14f)
                                        fontWeightMedium()
                                        color(colors().labelPrimary)
                                    }
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("chevron-down.svg"))
                                        size(14f, 14f)
                                        transform(Rotate(if (expandedGroupIds().contains(groupKey)) 0f else -90f))
                                        tintColor(colors().labelTertiary)
                                    }
                                }
                                event {
                                    click { onToggleGroup(groupKey) }
                                    if (groupKey.isNotEmpty()) {
                                        register(EventName.LONG_PRESS.value, { raw ->
                                            val press = LongPressParams.decode(raw)
                                            val heights = dshWorkspaceHeights()
                                            when {
                                                press.isCancel -> onDragCancel()
                                                press.isEnd -> onDragEnd()
                                                press.isStart -> onWorkspaceDragStart(
                                                    groupKey,
                                                    dshWorkspaceIndexOf(groupKey),
                                                    heights.getOrElse(dshWorkspaceIndexOf(groupKey)) {
                                                        DSH_DRAWER_WORKSPACE_HEADER_HEIGHT
                                                    },
                                                    press.pageY,
                                                )
                                                else -> onDragMove(press.pageY, heights)
                                            }
                                        }, isSync = true)
                                    }
                                }
                            }
                            vif({ expandedGroupIds().contains(groupKey) }) {
                                // 展开即显示全部会话（与原版一致）；vif 条件翻转时重建列表。
                                group.sessions.forEachIndexed { index, session ->
                                    DshSessionDrawerRow(
                                        title = session.title,
                                        subtitle = "",
                                        active = { activeId() == session.id },
                                        running = session.running,
                                        updatedAt = session.updatedAt,
                                        now = currentTimeMillis(),
                                        indented = true,
                                        pending = { sessionPending(session.id) },
                                        onSelect = { onSelect(session.id) },
                                        onOpenOverflow = { x, y -> onOpenOverflowFor(session.id, x, y) },
                                        dragState = dragState,
                                        dragScope = groupKey,
                                        dragId = session.id,
                                        dragIndex = index,
                                        dragItemCount = group.sessions.size,
                                        onDragStart = { pageY -> onSessionDragStart(groupKey, session.id, index, pageY) },
                                        onDragMove = onDragMove,
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragCancel,
                                        colors = colors,
                                    )
                                }
                            }
                        }
                    }
                }
                // web 时间线下列表底部预留遮罩高度，使最后一行可滚出渐变区而不被压在固定区下。
                vif({ isWebTimeline() }) {
                    View { attr { height(DSH_DRAWER_FOOTER_MASK_HEIGHT) } }
                }
            }
            // 底部固定区（已归档会话 + 设置）：不随列表滚动。列表滑到此处时，
            // 由顶部向上扩散的渐变遮罩盖住，形成柔和淡出的边界。
            View {
                attr { flexDirectionColumn() }
                vif({ isWebTimeline() }) {
                    // 半透明遮罩：底部取侧栏底色、向上渐隐并叠进滚动列表的底缘。
                    View {
                        attr {
                            positionAbsolute()
                            left(0f)
                            right(0f)
                            top(-DSH_DRAWER_FOOTER_MASK_HEIGHT)
                            height(DSH_DRAWER_FOOTER_MASK_HEIGHT)
                            backgroundLinearGradient(
                                Direction.TO_TOP,
                                ColorStop(colors().specificSidebarFill, 0f),
                                ColorStop(Color.TRANSPARENT, 1f),
                            )
                        }
                    }
                    View {
                        attr { height(44f); flexDirectionRow(); alignItemsCenter(); paddingLeft(12f); paddingRight(12f) }
                        Image { attr { src(ImageUri.commonAssets("archive.svg")); size(20f, 20f); tintColor(colors().labelSecondary) } }
                        Text { attr { text("已归档会话"); marginLeft(10f); fontSize(14f); color(colors().labelSecondary) } }
                        event { click { onOpenArchive() } }
                    }
                }
                // 底部固定「设置」入口（不随列表滚动），使用齿轮图标，与原版侧边栏一致
                View {
                    attr {
                        height(1f)
                        marginTop(8f)
                        backgroundColor(colors().borderL1)
                    }
                }
                View {
                    attr {
                        height(44f)
                        marginTop(4f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(12f)
                        paddingRight(12f)
                        borderRadius(9f)
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("settings.svg"))
                            size(20f, 20f)
                            tintColor(colors().labelSecondary)
                        }
                    }
                    Text {
                        attr {
                            text("设置")
                            marginLeft(10f)
                            fontSize(14f)
                            fontWeightMedium()
                            color(colors().labelSecondary)
                        }
                    }
                    event { click { onOpenSettings() } }
                }
            }
            DshDrawerEdgeShadow()
        }
        View {
            attr {
                flex(1f)
                height(pageViewHeight)
                // 右侧遮罩：统一覆盖半透明白（深色模式不透明度更低），
                // 让右侧内容始终比侧栏底色更浅，侧栏与右侧分界线更清晰。随抽屉开合渐变。
                backgroundColor(Color.WHITE.opacity(if (colors().isDark) 0.12f else 0.28f))
                opacity(if (animated()) 1f else 0f)
                animation(Animation.easeOut(0.24f), animated())
            }
            event { click { onClose() } }
        }
        // 复用主页面的 overflow menu：挂在抽屉 Modal 最上层，锚定到点击的会话行 ⋯。
        DshOverflowMenu(
            visible = overflowVisible,
            actions = overflowActions,
            onSelect = onOverflowSelect,
            onDismiss = onDismissOverflow,
            statusBarHeight = statusBarHeight,
            pageViewWidth = pageViewWidth,
            pageViewHeight = pageViewHeight,
            anchorX = overflowAnchorX,
            anchorY = overflowAnchorY,
            colors = colors,
        )
        // 视图选项菜单：锚定到抽屉头部右侧的个人化图标。
        DshViewOptionsMenu(
            visible = viewOptionsVisible,
            groupBy = viewGroupBy,
            orderBy = viewOrderBy,
            onSelect = onViewOptionsSelect,
            onDismiss = onViewOptionsDismiss,
            statusBarHeight = statusBarHeight,
            pageViewWidth = pageViewWidth,
            pageViewHeight = pageViewHeight,
            anchorX = viewOptionsAnchorX,
            anchorY = viewOptionsAnchorY,
            colors = colors,
        )
    }
}

/**
 * 会话行相对时间标签，桶位与 dsh 原版一致（刚刚/N分钟/N小时/N天/N个月/N年）；
 * updatedAt 无效（<=0）时返回空串，调用方隐藏时间位。
 */
internal fun dshRelativeTimeLabel(updatedAt: Long, now: Long): String {
    if (updatedAt <= 0L) return ""
    val diff = (now - updatedAt).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L}分钟"
        diff < 86_400_000L -> "${diff / 3_600_000L}小时"
        diff < 30L * 86_400_000L -> "${diff / 86_400_000L}天"
        diff < 365L * 86_400_000L -> "${diff / (30L * 86_400_000L)}个月"
        else -> "${diff / (365L * 86_400_000L)}年"
    }
}

internal fun ViewContainer<*, *>.DshSessionDrawerRow(
    title: String,
    subtitle: String,
    active: () -> Boolean,
    running: Boolean,
    pending: () -> Boolean,
    updatedAt: Long,
    now: Long,
    indented: Boolean,
    onSelect: () -> Unit,
    onOpenOverflow: (Float, Float) -> Unit,
    dragState: () -> DshDrawerDrag = { DshDrawerDrag() },
    dragScope: String = "",
    dragId: String = "",
    dragIndex: Int = -1,
    dragItemCount: Int = 0,
    onDragStart: ((Float) -> Unit)? = null,
    onDragMove: ((Float, List<Float>) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            height(46f)
            marginBottom(2f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(if (indented) 32f else 12f)
            paddingRight(4f)
            borderRadius(9f)
            val state = dragState()
            val dragging = state.kind != DshDrawerDragKind.NONE && state.key == dragId && state.groupKey == dragScope
            val offset = dshRowDragOffset(state, dragScope, dragId, dragIndex)
            // 用百分比位移（而非 dp offset）避免 frame task 延迟生效导致松手后残留；
            // 每次都显式写入，offset 为 0 时复位为 identity，避免行重叠。
            transform(Translate(0f, offset / DSH_DRAWER_ROW_HEIGHT))
            zIndex(if (offset != 0f) 1 else 0)
            opacity(if (offset != 0f) 0.9f else 1f)
            backgroundColor(
                when {
                    dragging -> colors().specificSelector
                    active() -> colors().specificSidebarNavItemActiveAccent
                    else -> Color(0x00FFFFFF)
                },
            )
        }
        // 状态点不常驻：待用户决策（琥珀）或进行中/有新消息（蓝）才显示；
        // 无状态时不留占位，文字靠左（与 ds 移动端一致）。
        vif({ pending() || running }) {
            View {
                attr {
                    width(7f)
                    allCenter()
                }
                vif({ pending() || running }) {
                    View {
                        attr {
                            size(7f, 7f)
                            borderRadius(4f)
                            backgroundColor(if (pending()) colors().stateWarnPrimary else colors().stateBusinessPrimary)
                        }
                    }
                }
            }
        }
        View {
            attr {
                flex(1f)
                marginLeft(10f)
                flexDirectionColumn()
                justifyContentCenter()
            }
            Text {
                attr {
                    text(title)
                    lines(1)
                    fontSize(14f)
                    color(if (active()) colors().stateBusinessPrimary else colors().labelPrimary)
                }
            }
            vif({ subtitle.isNotEmpty() }) {
                Text {
                    attr {
                        text(subtitle)
                        lines(1)
                        marginTop(2f)
                        fontSize(10f)
                        color(colors().labelTertiary)
                    }
                }
            }
        }
        vif({ updatedAt > 0L && !active() }) {
            Text {
                attr {
                    text(dshRelativeTimeLabel(updatedAt, now))
                    marginLeft(6f)
                    fontSize(11f)
                    color(colors().labelTertiary)
                }
            }
        }
        // 溢出按钮：仅选中项显示，点击弹出会话管理选项（横向三个点）。
        vif({ active() }) {
            View {
                attr { size(34f, 34f); marginLeft(2f); allCenter() }
                Image {
                    attr {
                        src(ImageUri.commonAssets("more-horizontal.svg"))
                        size(18f, 18f)
                        tintColor(colors().labelTertiary)
                    }
                }
                View {
                    attr { absolutePositionAllZero(); backgroundColor(Color(0x00000000)) }
                    event { click { params -> onOpenOverflow(params.pageX, params.pageY) } }
                }
            }
        }
        // 长按会话行进入拖拽排序；移动调整位置，松手提交。
        event {
            click { onSelect() }
            register(EventName.LONG_PRESS.value, { raw ->
                val press = LongPressParams.decode(raw)
                when {
                    press.isCancel -> onDragCancel?.invoke()
                    press.isEnd -> onDragEnd?.invoke()
                    press.isStart -> onDragStart?.invoke(press.pageY)
                    else -> onDragMove?.invoke(press.pageY, List(dragItemCount) { DSH_DRAWER_ROW_HEIGHT })
                }
            }, isSync = true)
        }
    }
}

/**
 * 搜索结果行（对齐deepseek app）：左侧会话图标（与「新会话」一致），标题与日期同行，
 * 下方单行摘要，摘要中命中关键词加粗；同一会话多条命中时以「n/total」角标标注序号。
 */
internal fun ViewContainer<*, *>.DshSessionSearchResultRow(
    title: String,
    dateLabel: String,
    snippetBefore: String,
    snippetMatch: String,
    snippetAfter: String,
    badge: String,
    active: () -> Boolean,
    onSelect: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            flexDirectionRow()
            paddingTop(10f)
            paddingBottom(10f)
            paddingLeft(6f)
            paddingRight(6f)
            marginBottom(2f)
            borderRadius(10f)
            backgroundColor(if (active()) colors().specificSidebarNavItemActive else Color(0x00FFFFFF))
        }
        // 圆形线框包裹图标：线框以图标为中心，图标尺寸保持不变。
        View {
            attr {
                size(28f, 28f)
                marginTop(2f)
                allCenter()
                borderRadius(14f)
                border(Border(1f, BorderStyle.SOLID, colors().borderL2))
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("session.svg"))
                    size(20f, 20f)
                    tintColor(colors().labelPrimary)
                }
            }
        }
        View {
            attr { flex(1f); marginLeft(10f); flexDirectionColumn() }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(title)
                        flex(1f)
                        lines(1)
                        fontSize(15f)
                        fontWeightMedium()
                        color(colors().labelPrimary)
                    }
                }
                Text {
                    attr {
                        text(dateLabel)
                        marginLeft(8f)
                        fontSize(11f)
                        color(colors().labelTertiary)
                    }
                }
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter(); marginTop(5f) }
                vif({ badge.isNotEmpty() }) {
                    View {
                        attr {
                            height(16f)
                            paddingLeft(5f)
                            paddingRight(5f)
                            marginRight(6f)
                            borderRadius(5f)
                            allCenter()
                            backgroundColor(colors().bgSkeleton)
                        }
                        Text { attr { text(badge); fontSize(10f); color(colors().labelSecondary) } }
                    }
                }
                RichText {
                    attr { flex(1f); lines(1); fontSize(12f) }
                    Span { text(snippetBefore); fontSize(12f); color(colors().labelSecondary) }
                    Span { text(snippetMatch); fontSize(12f); fontWeightBold(); color(colors().labelPrimary) }
                    Span { text(snippetAfter); fontSize(12f); color(colors().labelSecondary) }
                }
            }
        }
        event { click { onSelect() } }
    }
}

/**
 * 独立的全屏会话搜索界面（对齐deepseek app）：顶部返回箭头 + 搜索框，下方为命中结果。
 * 输入框挂载后自动聚焦；输入为空时不展示结果。
 */
internal fun ViewContainer<*, *>.DshSessionSearchOverlay(
    input: () -> String,
    hasInput: () -> Boolean,
    results: () -> ObservableList<DshSessionSearchHit>,
    activeId: () -> String,
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onInputRef: (ViewRef<InputView>) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            backgroundColor(colors().bgBase)
            paddingTop(pagerData.statusBarHeight + 8f)
            paddingLeft(12f)
            paddingRight(12f)
            paddingBottom(12f)
        }
        View {
            attr { height(48f); flexDirectionRow(); alignItemsCenter() }
            View {
                attr { size(36f, 44f); allCenter(); marginRight(4f) }
                Image {
                    attr {
                        src(ImageUri.commonAssets("chevron-left.svg"))
                        size(22f, 22f)
                        tintColor(colors().labelPrimary)
                    }
                }
                DshHitButton { onBack() }
            }
            View {
                attr {
                    flex(1f)
                    height(40f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(6f)
                    borderRadius(20f)
                    backgroundColor(colors().bgSkeleton)
                }
                Image { attr { src(ImageUri.commonAssets("tool-search.svg")); size(16f, 16f); tintColor(colors().labelTertiary) } }
                Input {
                    attr {
                        flex(1f)
                        height(40f)
                        marginLeft(8f)
                        fontSize(14f)
                        text(input())
                        placeholder("搜索对话内容…")
                        placeholderColor(colors().labelTertiary)
                        color(colors().labelPrimary)
                        autofocus(true)
                    }
                    ref { onInputRef(it) }
                    event { textDidChange { onInput(it.text) } }
                }
                vif({ hasInput() }) {
                    View {
                        attr { size(30f, 30f); allCenter() }
                        Image { attr { src(ImageUri.commonAssets("x.svg")); size(16f, 16f); tintColor(colors().labelTertiary) } }
                        DshHitButton { onClear() }
                    }
                }
            }
        }
        vif({ hasInput() && results().isEmpty() }) {
            Text {
                attr {
                    text("未找到相关对话")
                    marginTop(28f)
                    marginLeft(4f)
                    fontSize(13f)
                    color(colors().labelTertiary)
                }
            }
        }
        Scroller {
            attr { flex(1f); marginTop(8f) }
            vfor({ results() }) { hit ->
                DshSessionSearchResultRow(
                    title = hit.title,
                    dateLabel = hit.dateLabel,
                    snippetBefore = hit.snippetBefore,
                    snippetMatch = hit.snippetMatch,
                    snippetAfter = hit.snippetAfter,
                    badge = hit.matchBadge,
                    active = { activeId() == hit.sessionId },
                    onSelect = { onSelect(hit.sessionId) },
                    colors = colors,
                )
            }
        }
    }
}
