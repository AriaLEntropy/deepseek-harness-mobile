package com.example.dsh.ui.home

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.ui.settings.DshSettingsCard
import com.example.dsh.ui.settings.DshSettingsCardGroupTitle
import com.example.dsh.ui.settings.DshSettingsRow
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 设置页里的电脑列表项；当前单机，后续多 host 直接复用这个结构。 */
internal data class BusSettingsComputer(
    val id: String,
    val name: String,
    val platform: String,
    val online: Boolean,
    val lastSeenAt: String,
    val appVersion: String,
    val platformAsset: String,
)

/**
 * Bus 版设置页：沿用旧设置页的顶栏与卡片规范，
 * 但只依赖 Bus 快照，不再接 DSH Host 的 settings/models/plugins 接口。
 */
internal fun ViewContainer<*, *>.BusSettingsOverlay(
    computers: List<BusSettingsComputer>,
    agents: List<BusHomeAgent>,
    themeModeLabel: () -> String,
    statusBarHeight: Float,
    pageWidth: Float,
    colors: () -> DshColorTokens,
    onClose: () -> Unit,
    onAddComputer: () -> Unit,
    onRenameComputer: (BusSettingsComputer) -> Unit,
    onRemoveComputer: (BusSettingsComputer) -> Unit,
    onPickTheme: () -> Unit,
) {
    // 不要用 Modal(inWindow=true)：主题选择器本身是 in-window Modal，
    // 两层原生窗口会导致选项点击命中错位。设置页用页面内全屏覆盖层即可。
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(colors().bgCanvas)
        }
        View {
            attr {
                height(statusBarHeight + 52f)
                paddingTop(statusBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(16f)
                paddingRight(12f)
                backgroundColor(colors().bgCanvas)
            }
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        size(40f, 40f)
                        borderRadius(20f)
                        backgroundColor(colors().bgModulePlatform)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { onClose() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("chevron-left.svg"))
                            size(20f, 20f)
                            tintColor(colors().labelPrimary)
                        }
                    }
                }
            }
            Text {
                attr {
                    text("设置")
                    fontSize(17f)
                    fontWeightBold()
                    color(colors().labelPrimary)
                }
            }
            View { attr { flex(1f); flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter() } }
        }
        Scroller {
            attr {
                flex(1f)
                width(pageWidth)
                backgroundColor(colors().bgCanvas)
            }
            DshSettingsCardGroupTitle("电脑", colors = colors)
            DshSettingsCard(colors = colors) {
                if (computers.isEmpty()) {
                    DshSettingsRow(
                        icon = "computer.svg",
                        title = "还没有连接电脑",
                        value = { "点击添加" },
                        onClick = onAddComputer,
                        colors = colors,
                        showDivider = false,
                    )
                } else {
                    computers.forEach { computer ->
                        BusComputerItem(
                            computer = computer,
                            colors = colors,
                            onRename = { onRenameComputer(computer) },
                            onRemove = { onRemoveComputer(computer) },
                        )
                        View {
                            attr {
                                height(1f)
                                marginLeft(16f)
                                marginRight(16f)
                                backgroundColor(colors().borderL2)
                            }
                        }
                    }
                }
                DshSettingsRow(
                    icon = "plus.svg",
                    title = "添加电脑",
                    value = { "扫码 / SSH" },
                    onClick = onAddComputer,
                    colors = colors,
                    showDivider = false,
                )
            }

            DshSettingsCardGroupTitle("应用", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow(
                    icon = "personalize.svg",
                    title = "外观",
                    value = themeModeLabel,
                    onClick = onPickTheme,
                    colors = colors,
                    showDivider = false,
                )
            }

            DshSettingsCardGroupTitle("Agents", colors = colors)
            DshSettingsCard(colors = colors) {
                if (agents.isEmpty()) {
                    DshSettingsRow(
                        icon = "agent-preset.svg",
                        title = "还没有 Agent",
                        value = { "连接后自动出现" },
                        onClick = {},
                        colors = colors,
                        showChevron = false,
                        showDivider = false,
                    )
                } else {
                    agents.forEachIndexed { index, agent ->
                        DshSettingsRow(
                            icon = "agent-preset.svg",
                            title = agent.name.ifBlank { agent.key },
                            value = { agentStatusLabel(agent) },
                            onClick = {},
                            colors = colors,
                            showChevron = false,
                            showDivider = index < agents.lastIndex,
                        )
                    }
                }
            }

            DshSettingsCardGroupTitle("关于", colors = colors)
            DshSettingsCard(colors = colors) {
                DshSettingsRow(
                    icon = "shield.svg",
                    title = "电脑程序版本",
                    value = { computers.firstOrNull()?.appVersion?.ifBlank { "—" } ?: "—" },
                    onClick = {},
                    colors = colors,
                    showChevron = false,
                )
                DshSettingsRow(
                    icon = "computer.svg",
                    title = "平台",
                    value = { platformLabel(computers.firstOrNull()?.platform.orEmpty()) },
                    onClick = {},
                    colors = colors,
                    showChevron = false,
                    showDivider = false,
                )
            }
            View { attr { height(24f) } }
        }
    }
}

private fun ViewContainer<*, *>.BusComputerItem(
    computer: BusSettingsComputer,
    colors: () -> DshColorTokens,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    View {
        attr {
            flexDirectionColumn()
            paddingLeft(16f)
            paddingRight(16f)
            paddingTop(13f)
            paddingBottom(12f)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    size(34f, 34f)
                    borderRadius(17f)
                    backgroundColor(if (colors().isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets(computer.platformAsset.ifBlank { "computer.svg" }))
                        size(20f, 20f)
                        tintColor(colors().labelSecondary)
                    }
                }
            }
            View {
                attr { flex(1f); flexDirectionColumn(); marginLeft(12f) }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    View {
                        attr {
                            size(6f, 6f)
                            borderRadius(3f)
                            backgroundColor(
                                if (computer.online) colors().stateSuccessPrimary else colors().labelTertiary,
                            )
                        }
                    }
                    Text {
                        attr {
                            text(computer.name)
                            fontSize(14f)
                            fontWeightBold()
                            color(colors().labelPrimary)
                            marginLeft(6f)
                        }
                    }
                }
                Text {
                    attr {
                        text(
                            if (computer.online) "在线"
                            else "离线 · 上次同步 ${displayTime(computer.lastSeenAt)}",
                        )
                        fontSize(11f)
                        color(colors().labelTertiary)
                        marginTop(3f)
                    }
                }
                Text {
                    attr {
                        val detail = buildString {
                            append(platformLabel(computer.platform))
                            if (computer.appVersion.isNotBlank()) append(" · Agent Host ${computer.appVersion}")
                        }
                        text(detail)
                        fontSize(11f)
                        color(colors().labelTertiary)
                        marginTop(2f)
                    }
                }
            }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginTop(10f)
                paddingLeft(46f)
            }
            Text {
                attr {
                    text("编辑名称")
                    fontSize(13f)
                    fontWeightMedium()
                    color(colors().stateBusinessPrimary)
                }
                event { click { onRename() } }
            }
            Text {
                attr {
                    text("移除连接")
                    fontSize(13f)
                    fontWeightMedium()
                    color(colors().stateErrorPrimary)
                    marginLeft(20f)
                }
                event { click { onRemove() } }
            }
        }
    }
}

private fun agentStatusLabel(agent: BusHomeAgent): String = when {
    !agent.available -> "未安装"
    !agent.enabled -> "已停用"
    agent.status == "degraded" -> "需确认"
    agent.status == "error" -> "异常"
    else -> "可用"
}

private fun platformLabel(platform: String): String = when (platform.lowercase()) {
    "macos", "mac", "darwin" -> "macOS"
    "windows", "win32" -> "Windows"
    "linux" -> "Linux"
    "harmonyos", "ohos" -> "HarmonyOS"
    else -> "未知"
}
