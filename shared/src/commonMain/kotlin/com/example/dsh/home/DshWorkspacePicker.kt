package com.example.dsh.home

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 工作区选择弹窗的两个界面：最近文件夹 / 添加文件夹。 */
internal enum class DshWorkspacePickerScreen { RECENT, ADD }

/** 参考图中的文件夹图标为暖黄色，浅色/深色主题下都可辨识。 */
private val DSH_FOLDER_ICON_TINT = Color(0xFFF3B44C)

/**
 * 新建会话-工作区选择弹窗（对齐参考图「最近的文件夹」）。
 *
 * - RECENT：列出 Host 已注册工作区（最近文件夹），单选圆圈标记当前工作区，点按立即切换。
 * - ADD：复用 Host 目录浏览（`host.listDirectory`，走 SSH/Relay 隧道）添加新文件夹。
 * 顶部返回按钮在 ADD 界面返回 RECENT，在 RECENT 界面关闭弹窗。
 */
internal fun ViewContainer<*, *>.DshWorkspacePickerModal(
    screen: () -> DshWorkspacePickerScreen,
    folders: () -> ObservableList<DshWorkspaceGroup>,
    activeWorkspaceId: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onSelectFolder: (String) -> Unit,
    onAddFolder: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    path: () -> String,
    home: () -> String,
    entries: () -> ObservableList<DshDirectoryEntry>,
    newName: () -> String,
    onDirectorySelect: (String) -> Unit,
    onNewNameChange: (String) -> Unit,
    onCreateDirectory: () -> Unit,
    onAdopt: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
            backgroundColor(Color(0x55000000))
        }
        // 点击遮罩关闭
        View {
            attr { flex(1f) }
            event { click { onClose() } }
        }
        View {
            attr {
                width(pagerData.pageViewWidth)
                height((pagerData.pageViewHeight * 0.82f).coerceAtLeast(320f))
                flexDirectionColumn()
                borderRadius(20f)
                backgroundColor(colors().bgLayer1)
                paddingBottom(maxOf(12f, pagerData.safeAreaInsets.bottom))
            }
            // 顶部拖拽把手
            View {
                attr { height(18f); allCenter() }
                View {
                    attr {
                        width(36f)
                        height(4f)
                        borderRadius(2f)
                        backgroundColor(colors().borderL2)
                    }
                }
            }
            // 头部：左侧圆形返回按钮 + 居中标题
            View {
                attr {
                    height(52f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(16f)
                    paddingRight(16f)
                }
                View {
                    attr {
                        size(40f, 40f)
                        borderRadius(20f)
                        backgroundColor(colors().bgModulePlatform)
                        allCenter()
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("chevron-left.svg"))
                            size(20f, 20f)
                            tintColor(colors().labelPrimary)
                        }
                    }
                    DshHitButton { onBack() }
                }
                Text {
                    attr {
                        text(if (screen() == DshWorkspacePickerScreen.ADD) "添加文件夹" else "最近的文件夹")
                        flex(1f)
                        textAlignCenter()
                        fontSize(20f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { size(40f, 40f) } }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginLeft(20f)
                        marginRight(20f)
                        marginTop(4f)
                        marginBottom(6f)
                        fontSize(12f)
                        color(colors().stateErrorPrimary)
                    }
                }
            }
            // ===== 最近文件夹 =====
            vif({ screen() == DshWorkspacePickerScreen.RECENT }) {
                Scroller {
                    attr { flex(1f); marginTop(4f) }
                    vif({ folders().none { it.workspaceId.isNotEmpty() } }) {
                        Text {
                            attr {
                                text("暂无最近文件夹，点击下方添加")
                                marginTop(48f)
                                textAlignCenter()
                                fontSize(13f)
                                color(colors().labelTertiary)
                            }
                        }
                    }
                    vfor({ folders() }) { group ->
                        View {
                            attr { flexDirectionColumn() }
                            View {
                                attr {
                                    height(64f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingLeft(20f)
                                    paddingRight(20f)
                                }
                                // 单选圆圈
                                View {
                                    attr {
                                        size(22f, 22f)
                                        borderRadius(11f)
                                        border(Border(
                                            1.5f,
                                            BorderStyle.SOLID,
                                            if (activeWorkspaceId() == group.workspaceId) {
                                                colors().stateBusinessPrimary
                                            } else {
                                                colors().borderL2
                                            },
                                        ))
                                        allCenter()
                                    }
                                    vif({ activeWorkspaceId() == group.workspaceId }) {
                                        View {
                                            attr {
                                                size(11f, 11f)
                                                borderRadius(5.5f)
                                                backgroundColor(colors().stateBusinessPrimary)
                                            }
                                        }
                                    }
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("folder.svg"))
                                        size(30f, 30f)
                                        marginLeft(16f)
                                        tintColor(DSH_FOLDER_ICON_TINT)
                                    }
                                }
                                Text {
                                    attr {
                                        text(group.title.ifEmpty { group.path })
                                        flex(1f)
                                        marginLeft(14f)
                                        lines(1)
                                        fontSize(17f)
                                        color(colors().labelPrimary)
                                    }
                                }
                                event { click { if (!busy()) onSelectFolder(group.workspaceId) } }
                            }
                            View {
                                attr {
                                    height(1f)
                                    marginLeft(76f)
                                    marginRight(20f)
                                    backgroundColor(colors().borderL1)
                                }
                            }
                        }
                    }
                }
                // 底部「添加文件夹」
                View {
                    attr {
                        height(52f)
                        marginLeft(20f)
                        marginRight(20f)
                        marginTop(8f)
                        borderRadius(14f)
                        backgroundColor(colors().bgModulePlatform)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("添加文件夹")
                            fontSize(16f)
                            fontWeightMedium()
                            color(colors().labelPrimary)
                        }
                    }
                    DshHitButton { if (!busy()) onAddFolder() }
                }
            }
            // ===== 添加文件夹（Host 目录浏览） =====
            vif({ screen() == DshWorkspacePickerScreen.ADD }) {
                // 当前路径 + 主目录/上一级
                View {
                    attr {
                        height(36f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(20f)
                        paddingRight(20f)
                    }
                    Text {
                        attr {
                            text(path().ifEmpty { home() })
                            flex(1f)
                            lines(1)
                            fontSize(13f)
                            color(colors().labelSecondary)
                        }
                    }
                    vif({ home().isNotEmpty() && path() != home() }) {
                        Text {
                            attr {
                                text("主目录")
                                marginLeft(8f)
                                fontSize(13f)
                                color(colors().stateBusinessPrimary)
                            }
                            event { click { if (!busy()) onDirectorySelect(home()) } }
                        }
                    }
                    vif({ path().isNotEmpty() }) {
                        Text {
                            attr {
                                text("上一级")
                                marginLeft(8f)
                                fontSize(13f)
                                color(colors().stateBusinessPrimary)
                            }
                            event { click { if (!busy()) onDirectorySelect(dshParentPath(path())) } }
                        }
                    }
                }
                Scroller {
                    attr {
                        flex(1f)
                        marginTop(8f)
                        marginLeft(20f)
                        marginRight(20f)
                        borderRadius(10f)
                        backgroundColor(colors().bgModulePlatform)
                    }
                    vfor({ entries() }) { entry ->
                        View {
                            attr {
                                height(48f)
                                flexDirectionRow()
                                alignItemsCenter()
                                paddingLeft(12f)
                                paddingRight(12f)
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("folder.svg"))
                                    size(20f, 20f)
                                    tintColor(DSH_FOLDER_ICON_TINT)
                                }
                            }
                            Text {
                                attr {
                                    text(entry.name)
                                    flex(1f)
                                    marginLeft(10f)
                                    lines(1)
                                    fontSize(15f)
                                    color(colors().labelPrimary)
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("chevron-right.svg"))
                                    size(14f, 14f)
                                    tintColor(colors().labelCaption)
                                }
                            }
                            event { click { if (!busy()) onDirectorySelect(entry.path) } }
                        }
                    }
                }
                // 新建子文件夹
                View {
                    attr {
                        height(44f)
                        marginTop(10f)
                        marginLeft(20f)
                        marginRight(20f)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Input {
                        attr {
                            flex(1f)
                            height(40f)
                            fontSize(14f)
                            placeholder("新建文件夹")
                            placeholderColor(colors().labelTertiary)
                        }
                        event { textDidChange { onNewNameChange(it.text) } }
                    }
                    Text {
                        attr {
                            text(if (busy()) "处理中..." else "新建")
                            marginLeft(10f)
                            width(56f)
                            textAlignCenter()
                            fontSize(14f)
                            color(colors().stateBusinessPrimary)
                        }
                        event { click { if (!busy() && newName().isNotBlank()) onCreateDirectory() } }
                    }
                }
                // 使用当前目录
                View {
                    attr {
                        height(52f)
                        marginTop(10f)
                        marginLeft(20f)
                        marginRight(20f)
                        borderRadius(14f)
                        backgroundColor(colors().stateBusinessPrimary)
                        allCenter()
                    }
                    Text {
                        attr {
                            text(if (busy()) "处理中..." else "使用此文件夹")
                            fontSize(16f)
                            fontWeightMedium()
                            color(Color(0xFFFFFFFF))
                        }
                    }
                    DshHitButton { if (!busy()) onAdopt() }
                }
            }
        }
    }
}

/** 计算目录的上一级；兼容 Windows 盘符根（`C:\`）与 Unix 根（`/`）。 */
internal fun dshParentPath(path: String): String {
    val normalized = path.replace('\\', '/').trimEnd('/')
    if (normalized.isEmpty() || normalized.endsWith(":")) return path
    val parent = normalized.substringBeforeLast('/', "")
    return when {
        parent.endsWith(":") -> "$parent/"
        parent.isEmpty() -> "/"
        else -> parent
    }
}
