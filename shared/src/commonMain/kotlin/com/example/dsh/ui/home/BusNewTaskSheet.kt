package com.example.dsh.ui.home

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.ui.rendering.DshBottomSheet
import com.example.dsh.ui.rendering.DshPromptInput
import com.example.dsh.ui.rendering.DshSheetDetent
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 新建任务（BotBus 风格 sheet）。
 *
 * 注意：这里不再使用 vbind。弹层是 Modal，内部 vbind 重建会把 Modal 一起
 * 销毁（表现为点 Agent 弹层消失）。所有会变化的颜色/文字都直接读页面
 * observable 的 lambda，在 attr 内完成局部刷新。
 */
internal fun ViewContainer<*, *>.BusNewTaskSheet(
    agents: List<BusHomeAgent>,
    projects: List<BusHomeProject>,
    selectedAgentKey: () -> String,
    selectedProjectPath: () -> String,
    modelLabel: () -> String,
    modelEnabled: () -> Boolean,
    modelListVisible: () -> Boolean,
    modelOptions: () -> List<BusHomeModelOption>,
    selectedModelId: () -> String,
    selectedEffort: () -> String,
    agentName: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    canSubmit: () -> Boolean,
    keyboardHeight: () -> Float,
    colors: DshColorTokens,
    onClose: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onModelClick: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onKeyboardHeightChange: (Float) -> Unit,
    onSubmit: () -> Unit,
) {
    DshBottomSheet(
        colors = { colors },
        onClose = { if (!busy()) onClose() },
        initialDetent = DshSheetDetent.LARGE,
        panelHeight = 640f,
        panelBackground = { colors.bgBase },
        keyboardHeight = keyboardHeight,
    ) {
        View {
            attr {
                height(42f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(20f)
                paddingRight(20f)
                marginTop(6f)
            }
            Text {
                attr {
                    text("新建任务")
                    fontSize(17f)
                    fontWeightBold()
                    color(colors.labelPrimary)
                    flex(1f)
                }
            }
            View {
                attr {
                    size(30f, 30f)
                    borderRadius(15f)
                    backgroundColor(sheetSurface(colors))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { if (!busy()) onClose() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("x.svg"))
                        size(13f, 13f)
                        tintColor(colors.labelSecondary)
                    }
                }
            }
        }
        Scroller {
            attr { flex(1f) }
            View {
                attr {
                    flexDirectionColumn()
                    paddingLeft(20f)
                    paddingRight(20f)
                }
                NewTaskLabel("Agent", colors)
                View {
                    attr {
                        flexDirectionRow()
                        flexWrap(FlexWrap.WRAP)
                    }
                    if (agents.isEmpty()) {
                        Text {
                            attr {
                                text("电脑上没有可启动的 Agent")
                                fontSize(12f)
                                color(colors.stateErrorPrimary)
                            }
                        }
                    } else {
                        agents.forEach { agent ->
                            NewTaskChip(
                                label = agent.name.ifBlank { agent.key },
                                selected = { agent.key == selectedAgentKey() },
                                colors = colors,
                                onClick = { onSelectAgent(agent.key) },
                            )
                        }
                    }
                }

                NewTaskLabel("要做什么", colors, marginTop = 14f)
                DshPromptInput(
                    placeholder = "描述任务，例如：修复登录页的布局问题",
                    modelLabel = modelLabel,
                    modelEnabled = modelEnabled,
                    onPromptChange = onPromptChange,
                    onModelClick = onModelClick,
                    colors = { colors },
                    onKeyboardHeightChange = onKeyboardHeightChange,
                )
                vif({ modelListVisible() }) {
                    NewTaskModelList(
                        agentName = agentName(),
                        models = modelOptions(),
                        selectedId = selectedModelId(),
                        selectedEffort = selectedEffort(),
                        colors = colors,
                        onSelect = onSelectModel,
                        onSelectEffort = onSelectEffort,
                    )
                }

                NewTaskLabel("项目", colors, marginTop = 14f)
                View {
                    attr {
                        flexDirectionRow()
                        flexWrap(FlexWrap.WRAP)
                    }
                    NewTaskChip(
                        label = "默认目录",
                        selected = { selectedProjectPath().isEmpty() },
                        colors = colors,
                        onClick = { onSelectProject("") },
                    )
                    projects.forEach { project ->
                        NewTaskChip(
                            label = project.name.ifBlank { project.path },
                            selected = { project.path == selectedProjectPath() },
                            colors = colors,
                            onClick = { onSelectProject(project.path) },
                        )
                    }
                }
                View { attr { height(12f) } }
            }
        }
        View {
            attr {
                flexDirectionColumn()
                paddingLeft(20f)
                paddingRight(20f)
                paddingBottom(12f)
                backgroundColor(colors.bgBase)
            }
            Text {
                attr {
                    text(error())
                    fontSize(12f)
                    color(colors.stateErrorPrimary)
                    opacity(if (error().isEmpty()) 0f else 1f)
                    marginBottom(8f)
                }
            }
            View {
                attr {
                    height(48f)
                    marginBottom(12f)
                    flexDirectionRow()
                    alignItemsCenter()
                    justifyContentCenter()
                    borderRadius(12f)
                    backgroundColor(
                        if (canSubmit() && !busy() && agents.isNotEmpty()) primaryButtonColor(colors)
                        else colors.specificSelector,
                    )
                }
                event { click { if (!busy() && canSubmit() && agents.isNotEmpty()) onSubmit() } }
                Text {
                    attr {
                        text(if (busy()) "正在创建…" else "开始任务")
                        fontSize(15f)
                        fontWeightBold()
                        color(
                            if (canSubmit() && !busy() && agents.isNotEmpty()) primaryButtonText(colors)
                            else colors.labelTertiary,
                        )
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.NewTaskModelList(
    agentName: String,
    models: List<BusHomeModelOption>,
    selectedId: String,
    selectedEffort: String,
    colors: DshColorTokens,
    onSelect: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
) {
    View {
        attr {
            marginTop(10f)
            flexDirectionColumn()
            padding(10f)
            borderRadius(12f)
            backgroundColor(colors.bgLayer1)
            border(Border(1f, BorderStyle.SOLID, colors.borderL1))
        }
        Text {
            attr {
                text("${agentName.ifBlank { "Agent" }} 的模型")
                fontSize(11f)
                fontWeightMedium()
                color(colors.labelTertiary)
                marginBottom(4f)
            }
        }
        if (models.isEmpty()) {
            Text {
                attr {
                    text("这台 Agent 没有上报模型列表")
                    fontSize(12f)
                    color(colors.labelTertiary)
                    marginTop(6f)
                    marginBottom(4f)
                }
            }
        } else {
            models.forEach { model ->
                View {
                    attr {
                        height(42f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(8f)
                        paddingRight(8f)
                        borderRadius(8f)
                        backgroundColor(
                            if (model.id == selectedId) colors.specificSelector else Color(0x00000000),
                        )
                    }
                    event { click { onSelect(model.id) } }
                    Text {
                        attr {
                            text(model.displayName.ifBlank { model.id })
                            flex(1f)
                            fontSize(14f)
                            color(colors.labelPrimary)
                        }
                    }
                    if (model.id == selectedId) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets("check.svg"))
                                size(16f, 16f)
                                tintColor(colors.stateBusinessPrimary)
                            }
                        }
                    }
                }
            }
            val selected = models.firstOrNull { it.id == selectedId }
            if (selected != null && selected.efforts.size > 1) {
                Text {
                    attr {
                        text("强度")
                        fontSize(11f)
                        fontWeightMedium()
                        color(colors.labelTertiary)
                        marginTop(10f)
                        marginBottom(6f)
                    }
                }
                View {
                    attr {
                        flexDirectionRow()
                        flexWrap(FlexWrap.WRAP)
                    }
                    selected.efforts.forEach { effort ->
                        View {
                            attr {
                                height(28f)
                                marginRight(8f)
                                marginBottom(6f)
                                paddingLeft(10f)
                                paddingRight(10f)
                                borderRadius(14f)
                                backgroundColor(
                                    if (effort == selectedEffort) primaryButtonColor(colors) else colors.specificSelector,
                                )
                                alignItemsCenter()
                                justifyContentCenter()
                            }
                            event { click { onSelectEffort(effort) } }
                            Text {
                                attr {
                                    text(effortLabel(effort))
                                    fontSize(11f)
                                    fontWeightMedium()
                                    color(
                                        if (effort == selectedEffort) primaryButtonText(colors) else colors.labelSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.NewTaskLabel(
    text: String,
    colors: DshColorTokens,
    marginTop: Float = 2f,
) {
    Text {
        attr {
            this.text(text)
            fontSize(12f)
            fontWeightMedium()
            color(colors.labelSecondary)
            this.marginTop(marginTop)
            marginBottom(8f)
        }
    }
}

private fun ViewContainer<*, *>.NewTaskChip(
    label: String,
    selected: () -> Boolean,
    colors: DshColorTokens,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(34f)
            marginRight(8f)
            marginBottom(8f)
            paddingLeft(13f)
            paddingRight(13f)
            borderRadius(17f)
            backgroundColor(if (selected()) primaryButtonColor(colors) else colors.specificSelector)
            alignItemsCenter()
            justifyContentCenter()
        }
        event { click { onClick() } }
        Text {
            attr {
                text(label)
                fontSize(12f)
                fontWeightMedium()
                color(if (selected()) primaryButtonText(colors) else colors.labelSecondary)
            }
        }
    }
}

/** 协议只约定了 Agent 自己的强度词，认识的翻成本地文案，其余原样显示。 */
internal fun effortLabel(effort: String): String = when (effort) {
    "none" -> "无"
    "minimal" -> "极低"
    "low" -> "低"
    "medium" -> "中"
    "high" -> "高"
    "xhigh" -> "极高"
    "max" -> "最高"
    "ultra" -> "超高"
    else -> effort
}

/** 与登录页 / SSH 页保持一致的主动作黑色。 */
private fun primaryButtonColor(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFFFFFFFF) else Color(0xFF1D1B20)

private fun primaryButtonText(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF1D1B20) else Color(0xFFFFFFFF)

private fun sheetSurface(c: DshColorTokens): Color =
    if (c.isDark) Color(0xFF2A2A31) else Color(0xFFF5F5F5)
