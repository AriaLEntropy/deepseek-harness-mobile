package com.example.dsh.home

import com.example.dsh.conversation.DshWorkspaceGroup
import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 归档页排序方式。 */
internal enum class DshArchiveSort(val label: String) {
    UPDATED("更新时间"),
    CREATED("创建时间"),
    NAME("按字母顺序"),
}

/** 归档页项目筛选的一项。 */
internal data class DshArchiveProjectOption(val id: String, val title: String)

/** 待删除目标类型。 */
internal enum class DshArchiveConfirmKind { SESSION, PROJECT, ALL }

/** 删除二次确认上下文。 */
internal data class DshArchiveConfirm(
    val kind: DshArchiveConfirmKind,
    val id: String = "",
    val title: String = "",
    val count: Int = 0,
)

/**
 * 归档聊天浏览器：搜索、项目分组/筛选、排序、单条删除、项目内全部删除、全部删除。
 * 取消归档需要 Host unarchive 能力，本页不提供。
 */
internal fun ViewContainer<*, *>.DshArchivedSessions(
    groups: () -> ObservableList<DshWorkspaceGroup>,
    projectOptions: () -> ObservableList<DshArchiveProjectOption>,
    selectedProject: () -> String,
    sort: () -> DshArchiveSort,
    openMenu: () -> String,
    search: () -> String,
    loading: () -> Boolean,
    error: () -> String,
    notice: () -> String,
    busy: () -> Boolean,
    openingId: () -> String,
    confirm: () -> DshArchiveConfirm?,
    onSearch: (String) -> Unit,
    onToggleMenu: (String) -> Unit,
    onPickProject: (String) -> Unit,
    onPickSort: (DshArchiveSort) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRequestDeleteProject: (DshWorkspaceGroup) -> Unit,
    onRequestDeleteAll: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelConfirm: () -> Unit,
    formatDate: (Long) -> String,
    colors: () -> DshColorTokens,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            backgroundColor(colors().bgBase)
            paddingTop(pagerData.statusBarHeight)
            paddingBottom(pagerData.safeAreaInsets.bottom)
        }
        DshArchiveHeader(
            busy = busy,
            onClose = onClose,
            onRefresh = onRefresh,
            onRequestDeleteAll = onRequestDeleteAll,
            colors = colors,
        )
        DshArchiveSearchBar(search = search, onSearch = onSearch, colors = colors)
        DshArchiveFilters(
            projectOptions = projectOptions,
            selectedProject = selectedProject,
            sort = sort,
            openMenu = openMenu,
            onToggleMenu = onToggleMenu,
            onPickProject = onPickProject,
            onPickSort = onPickSort,
            colors = colors,
        )
        DshArchiveBody(
            groups = groups,
            loading = loading,
            error = error,
            notice = notice,
            busy = busy,
            openingId = openingId,
            onOpen = onOpen,
            onRequestDelete = onRequestDelete,
            onRequestDeleteProject = onRequestDeleteProject,
            formatDate = formatDate,
            colors = colors,
        )
        vif({ confirm() != null }) {
            val target = confirm() ?: return@vif
            DshArchiveConfirmDialog(
                target = target,
                busy = busy,
                onCancel = onCancelConfirm,
                onConfirm = onConfirmDelete,
                colors = colors,
            )
        }
    }
}

internal fun ViewContainer<*, *>.DshArchiveHeader(
    busy: () -> Boolean,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRequestDeleteAll: () -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr { height(52f); flexDirectionRow(); alignItemsCenter(); paddingLeft(8f); paddingRight(12f) }
        View {
            attr { width(56f); height(44f); allCenter() }
            Text { attr { text("返回"); fontSize(14f); color(colors().labelPrimary) } }
            event { click { onClose() } }
        }
        Text {
            attr {
                text("已归档的聊天")
                flex(1f); textAlignCenter(); fontSize(18f); fontWeightMedium(); color(colors().labelPrimary)
            }
        }
        View {
            attr { height(32f); paddingLeft(8f); paddingRight(8f); allCenter(); opacity(if (busy()) 0.5f else 1f) }
            Text { attr { text("刷新"); fontSize(13f); color(colors().stateBusinessPrimary) } }
            event { click { if (!busy()) onRefresh() } }
        }
        View {
            attr {
                height(32f); marginLeft(4f); paddingLeft(12f); paddingRight(12f); allCenter()
                borderRadius(8f); backgroundColor(Color(0x1FF25A5A)); opacity(if (busy()) 0.5f else 1f)
            }
            Text { attr { text("全部删除"); fontSize(13f); color(colors().stateErrorPrimary) } }
            event { click { if (!busy()) onRequestDeleteAll() } }
        }
    }
}

internal fun ViewContainer<*, *>.DshArchiveSearchBar(
    search: () -> String,
    onSearch: (String) -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr {
            height(36f); marginLeft(16f); marginRight(16f); borderRadius(8f)
            backgroundColor(colors().bgSkeleton); flexDirectionRow(); alignItemsCenter()
            paddingLeft(10f); paddingRight(8f)
        }
        Image { attr { src(ImageUri.commonAssets("tool-search.svg")); size(14f, 14f); tintColor(colors().labelTertiary) } }
        Input {
            attr {
                flex(1f); height(36f); marginLeft(6f); fontSize(13f)
                text(search()); placeholder("搜索已归档聊天")
                placeholderColor(colors().labelTertiary); color(colors().labelPrimary)
            }
            event { textDidChange { onSearch(it.text) } }
        }
    }
}

internal fun ViewContainer<*, *>.DshArchiveFilters(
    projectOptions: () -> ObservableList<DshArchiveProjectOption>,
    selectedProject: () -> String,
    sort: () -> DshArchiveSort,
    openMenu: () -> String,
    onToggleMenu: (String) -> Unit,
    onPickProject: (String) -> Unit,
    onPickSort: (DshArchiveSort) -> Unit,
    colors: () -> DshColorTokens,
) {
    View {
        attr { marginTop(10f); marginLeft(16f); marginRight(16f); flexDirectionRow() }
        DshArchiveFilterButton(
            label = { projectOptions().firstOrNull { it.id == selectedProject() }?.title ?: "所有项目" },
            active = openMenu() == "project",
            onClick = { onToggleMenu(if (openMenu() == "project") "" else "project") },
            colors = colors,
            first = true,
        )
        DshArchiveFilterButton(
            label = { sort().label },
            active = openMenu() == "sort",
            onClick = { onToggleMenu(if (openMenu() == "sort") "" else "sort") },
            colors = colors,
            first = false,
        )
    }
    vif({ openMenu() == "project" }) {
        View {
            attr {
                marginTop(6f); marginLeft(16f); marginRight(16f); borderRadius(10f)
                backgroundColor(colors().bgLayer1); border(Border(1f, BorderStyle.SOLID, colors().borderL1))
            }
            DshArchiveMenuItem("所有项目", selectedProject().isEmpty(), colors) { onPickProject("") }
            projectOptions().forEach { option ->
                DshArchiveMenuItem(option.title, selectedProject() == option.id, colors) { onPickProject(option.id) }
            }
        }
    }
    vif({ openMenu() == "sort" }) {
        View {
            attr {
                marginTop(6f); marginLeft(16f); marginRight(16f); borderRadius(10f)
                backgroundColor(colors().bgLayer1); border(Border(1f, BorderStyle.SOLID, colors().borderL1))
            }
            DshArchiveSort.entries.forEach { entry ->
                DshArchiveMenuItem(entry.label, sort() == entry, colors) { onPickSort(entry) }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshArchiveFilterButton(
    label: () -> String,
    active: Boolean,
    onClick: () -> Unit,
    colors: () -> DshColorTokens,
    first: Boolean,
) {
    View {
        attr {
            flex(1f); height(34f); flexDirectionRow(); alignItemsCenter()
            paddingLeft(10f); paddingRight(10f); borderRadius(8f)
            border(Border(1f, BorderStyle.SOLID, if (active) colors().stateBusinessPrimary else colors().borderL2))
            if (!first) marginLeft(8f)
        }
        Text { attr { text(label()); flex(1f); lines(1); fontSize(13f); color(colors().labelPrimary) } }
        Image {
            attr {
                src(ImageUri.commonAssets("chevron-down.svg"))
                size(12f, 12f)
                tintColor(colors().labelTertiary)
            }
        }
        event { click { onClick() } }
    }
}

internal fun ViewContainer<*, *>.DshArchiveMenuItem(
    label: String,
    selected: Boolean,
    colors: () -> DshColorTokens,
    onClick: () -> Unit,
) {
    View {
        attr { height(40f); flexDirectionRow(); alignItemsCenter(); paddingLeft(14f); paddingRight(14f) }
        Text {
            attr {
                text(label)
                flex(1f); lines(1); fontSize(14f)
                color(if (selected) colors().stateBusinessPrimary else colors().labelPrimary)
            }
        }
        vif({ selected }) {
            Image { attr { src(ImageUri.commonAssets("check.svg")); size(16f, 16f); tintColor(colors().stateBusinessPrimary) } }
        }
        event { click { onClick() } }
    }
}

internal fun ViewContainer<*, *>.DshArchiveBody(
    groups: () -> ObservableList<DshWorkspaceGroup>,
    loading: () -> Boolean,
    error: () -> String,
    notice: () -> String,
    busy: () -> Boolean,
    openingId: () -> String,
    onOpen: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRequestDeleteProject: (DshWorkspaceGroup) -> Unit,
    formatDate: (Long) -> String,
    colors: () -> DshColorTokens,
) {
    vif({ loading() }) {
        Text { attr { text("正在同步 Host 归档列表…"); margin(12f, 20f, 4f, 20f); fontSize(13f); color(colors().labelSecondary) } }
    }
    vif({ error().isNotEmpty() }) {
        Text { attr { text(error()); margin(4f, 20f, 4f, 20f); fontSize(13f); color(colors().stateErrorPrimary) } }
    }
    vif({ notice().isNotEmpty() }) {
        Text { attr { text(notice()); margin(4f, 20f, 4f, 20f); fontSize(13f); color(colors().labelSecondary) } }
    }
    vif({ !loading() && error().isEmpty() && groups().isEmpty() }) {
        View {
            attr { width(pagerData.pageViewWidth); height(160f); allCenter() }
            Text { attr { text("暂无匹配的已归档聊天"); fontSize(15f); color(colors().labelSecondary) } }
        }
    }
    Scroller {
        attr { flex(1f); marginTop(6f) }
        vfor({ groups() }) { group ->
            DshArchiveGroup(
                group = group,
                busy = busy,
                openingId = openingId,
                onOpen = onOpen,
                onRequestDelete = onRequestDelete,
                onRequestDeleteProject = onRequestDeleteProject,
                formatDate = formatDate,
                colors = colors,
            )
        }
    }
}

internal fun ViewContainer<*, *>.DshArchiveGroup(
    group: DshWorkspaceGroup,
    busy: () -> Boolean,
    openingId: () -> String,
    onOpen: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onRequestDeleteProject: (DshWorkspaceGroup) -> Unit,
    formatDate: (Long) -> String,
    colors: () -> DshColorTokens,
) {
    View {
        attr { marginTop(10f); flexDirectionColumn() }
        View {
            attr { height(38f); flexDirectionRow(); alignItemsCenter(); paddingLeft(16f); paddingRight(16f) }
            Image { attr { src(ImageUri.commonAssets("folder.svg")); size(15f, 15f); tintColor(colors().labelTertiary) } }
            View { attr { width(6f) } }
            Text { attr { text(group.title); flex(1f); lines(1); fontSize(14f); fontWeightMedium(); color(colors().labelPrimary) } }
            Text { attr { text("${group.sessions.size} 个聊天"); fontSize(12f); color(colors().labelTertiary) } }
            if (group.workspaceId.isNotEmpty()) {
                View {
                    attr { width(28f); height(28f); marginLeft(4f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("more.svg")); size(16f, 16f); tintColor(colors().labelTertiary) } }
                    event { click { if (!busy()) onRequestDeleteProject(group) } }
                }
            }
        }
        View {
            attr {
                marginLeft(12f); marginRight(12f); borderRadius(12f)
                backgroundColor(colors().bgLayer1); border(Border(1f, BorderStyle.SOLID, colors().borderL1))
                flexDirectionColumn()
            }
            group.sessions.forEach { session ->
                View {
                    attr { minHeight(64f); padding(10f, 14f, 10f, 14f); flexDirectionRow(); alignItemsCenter() }
                    View {
                        attr { flex(1f); flexDirectionColumn(); opacity(if (openingId() == session.id) 0.5f else 1f) }
                        Text { attr { text(session.title.ifBlank { "尚无标题" }); lines(2); fontSize(15f); color(colors().labelPrimary) } }
                        Text {
                            attr {
                                text(
                                    if (openingId() == session.id) "正在读取历史…"
                                    else formatDate(if (session.createdAt > 0) session.createdAt else session.updatedAt),
                                )
                                marginTop(4f); fontSize(12f); color(colors().labelSecondary)
                            }
                        }
                        event { click { if (!busy() && openingId().isEmpty()) onOpen(session.id) } }
                    }
                    View {
                        attr { width(36f); height(36f); allCenter() }
                        Image { attr { src(ImageUri.commonAssets("delete.svg")); size(17f, 17f); tintColor(colors().labelTertiary) } }
                        event { click { if (!busy()) onRequestDelete(session.id) } }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshArchiveConfirmDialog(
    target: DshArchiveConfirm,
    busy: () -> Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    colors: () -> DshColorTokens,
) {
    val title = when (target.kind) {
        DshArchiveConfirmKind.SESSION -> "删除这条聊天？"
        DshArchiveConfirmKind.PROJECT -> "删除「${target.title}」中的全部内容？"
        DshArchiveConfirmKind.ALL -> "删除全部已归档聊天？"
    }
    val message = when (target.kind) {
        DshArchiveConfirmKind.SESSION -> "将永久删除该会话及其本地日志，此操作不可恢复。"
        DshArchiveConfirmKind.PROJECT -> "将永久删除该项目下的 ${target.count} 条会话，此操作不可恢复。"
        DshArchiveConfirmKind.ALL -> "将永久删除全部 ${target.count} 条已归档会话，此操作不可恢复。"
    }
    View {
        attr { absolutePositionAllZero(); backgroundColor(Color(0x66000000)); allCenter(); padding(24f) }
        View {
            attr {
                width(pagerData.pageViewWidth - 48f); maxWidth(360f); borderRadius(14f)
                backgroundColor(colors().bgLayer1); padding(18f)
            }
            Text { attr { text(title); fontSize(16f); fontWeightBold(); color(colors().labelPrimary) } }
            Text { attr { text(message); marginTop(10f); fontSize(13f); color(colors().labelSecondary) } }
            View {
                attr { marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                View {
                    attr {
                        height(38f); paddingLeft(16f); paddingRight(16f); allCenter(); borderRadius(9f)
                        border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                    }
                    Text { attr { text("取消"); fontSize(14f); color(colors().labelPrimary) } }
                    event { click { onCancel() } }
                }
                View {
                    attr {
                        height(38f); marginLeft(10f); paddingLeft(16f); paddingRight(16f); allCenter(); borderRadius(9f)
                        backgroundColor(colors().stateErrorPrimary); opacity(if (busy()) 0.5f else 1f)
                    }
                    Text { attr { text(if (busy()) "删除中…" else "删除"); fontSize(14f); color(colors().labelPrimaryInverted) } }
                    event { click { if (!busy()) onConfirm() } }
                }
            }
        }
        event { click { } }
    }
}
