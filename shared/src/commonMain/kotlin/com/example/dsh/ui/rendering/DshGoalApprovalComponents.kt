package com.example.dsh.ui.rendering

import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.interaction.DshPendingApproval
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Input

internal class DshGoalBarView : ComposeView<DshGoalBarAttr, ComposeEvent>() {
    private var editing by observable(false)
    private var draft by observable("")

    override fun createAttr(): DshGoalBarAttr = DshGoalBarAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vif({ ctx.attr.snapshot != null }) {
                val goal = ctx.attr.snapshot ?: return@vif
                View {
                    attr {
                        marginBottom(8f)
                        minHeight(38f)
                        flexDirectionRow()
                        alignItemsCenter()
                        padding(8f, 10f, 8f, 10f)
                        borderRadius(8f)
                        backgroundColor(ctx.attr.colors.bgBase)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    }
                    Image {
                        attr { src(ImageUri.commonAssets("goal.svg")); size(14f, 14f); tintColor(ctx.attr.colors.labelTertiary) }
                    }
                    Text {
                        attr {
                            text(when (goal.phase) {
                                "active" -> "目标进行中"
                                "paused" -> "目标已暂停"
                                "blocked" -> "目标受阻"
                                else -> goal.phase
                            })
                            marginLeft(6f)
                            fontSize(12f)
                            fontWeightMedium()
                            color(if (goal.phase == "blocked") ctx.attr.colors.stateErrorPrimary else ctx.attr.colors.labelSecondary)
                        }
                    }
                    vif({ !ctx.editing }) {
                        Text {
                            attr {
                                text(goal.objective)
                                flex(1f)
                                marginLeft(8f)
                                lines(2)
                                fontSize(12f)
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                    }
                    vif({ ctx.editing }) {
                        Input {
                            ref { it.view?.setText(ctx.draft) }
                            attr {
                                flex(1f)
                                height(30f)
                                marginLeft(8f)
                                fontSize(12f)
                                text(ctx.draft)
                            }
                            event { textDidChange { ctx.draft = it.text } }
                        }
                        Text {
                            attr {
                                text(if (ctx.attr.busy) "处理中" else "保存")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.stateSuccessPrimary)
                            }
                            event {
                                click {
                                    if (!ctx.attr.busy && ctx.draft.trim().isNotEmpty()) {
                                        ctx.attr.onEdit(ctx.draft.trim()) { success -> if (success) ctx.editing = false }
                                    }
                                }
                            }
                        }
                        Text {
                            attr {
                                text("取消")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.labelTertiary)
                            }
                            event { click { if (!ctx.attr.busy) ctx.editing = false } }
                        }
                    }
                    vif({ goal.blockedReason.isNotEmpty() }) {
                        Text {
                            attr {
                                text(goal.blockedReason)
                                marginLeft(6f)
                                lines(1)
                                fontSize(11f)
                                color(ctx.attr.colors.stateErrorPrimary)
                            }
                        }
                    }
                    vif({ ctx.attr.error.isNotEmpty() }) {
                        Text {
                            attr {
                                text(ctx.attr.error)
                                marginLeft(6f)
                                fontSize(11f)
                                color(ctx.attr.colors.stateErrorPrimary)
                            }
                        }
                    }
                    vif({ !ctx.editing && goal.phase == "active" }) {
                        Text {
                            attr {
                                text(if (ctx.attr.busy) "处理中" else "暂停")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.labelSecondary)
                            }
                            event { click { if (!ctx.attr.busy) ctx.attr.onPause() } }
                        }
                    }
                    vif({ !ctx.editing && goal.phase == "paused" }) {
                        Text {
                            attr {
                                text(if (ctx.attr.busy) "处理中" else "恢复")
                                marginLeft(8f)
                                fontSize(12f)
                                color(ctx.attr.colors.stateSuccessPrimary)
                            }
                            event { click { if (!ctx.attr.busy) ctx.attr.onResume() } }
                        }
                    }
                    vif({ !ctx.editing }) {
                    Text {
                        attr {
                            text("清除")
                            marginLeft(8f)
                            fontSize(12f)
                            color(ctx.attr.colors.stateErrorPrimary)
                        }
                        event { click { if (!ctx.attr.busy) ctx.attr.onClear() } }
                    }
                    }
                    vif({ !ctx.editing }) {
                    Text {
                        attr {
                            text("编辑")
                            marginLeft(8f)
                            fontSize(12f)
                            color(ctx.attr.colors.stateBusinessPrimary)
                        }
                        event { click { if (!ctx.attr.busy) { ctx.draft = goal.objective; ctx.editing = true } } }
                    }
                    }
                }
            }
        }
    }
}

internal class DshGoalBarAttr : ComposeAttr() {
    var snapshot: DshGoalSnapshot? by observable(null)
    var busy: Boolean by observable(false)
    var error: String by observable("")
    var onEdit: (String, (Boolean) -> Unit) -> Unit by observable({ _, _ -> })
    var onPause: () -> Unit by observable({})
    var onResume: () -> Unit by observable({})
    var onClear: () -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

internal fun ViewContainer<*, *>.DshGoalBar(init: DshGoalBarView.() -> Unit) {
    addChild(DshGoalBarView(), init)
}

internal class DshApprovalPanelView : ComposeView<DshApprovalPanelAttr, ComposeEvent>() {
    override fun createAttr(): DshApprovalPanelAttr = DshApprovalPanelAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            vif({ ctx.attr.approval != null }) {
                val approval = ctx.attr.approval ?: return@vif
                View {
                    attr {
                        marginLeft(4f)
                        marginRight(4f)
                        flexDirectionColumn()
                        padding(14f, 14f, 14f, 14f)
                        borderRadius(16f)
                        backgroundColor(ctx.attr.colors.bgLayer1)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.stateWarnSecondary))
                    }
                    View {
                        attr {
                            alignSelfFlexStart()
                            padding(3f, 8f, 3f, 8f)
                            borderRadius(6f)
                            backgroundColor(ctx.attr.colors.stateWarnTertiary)
                        }
                        Text {
                            attr {
                                text("等待审批")
                                fontSize(11f)
                                fontWeightMedium()
                                color(ctx.attr.colors.stateWarnPrimary)
                            }
                        }
                    }
                    Text {
                        attr {
                            text(approval.reason ?: "需要使用 ${approval.toolName}")
                            marginTop(10f)
                            fontSize(16f)
                            fontWeightMedium()
                            lineHeight(23f)
                            color(ctx.attr.colors.labelPrimary)
                        }
                    }
                    vif({ approval.command != null }) {
                        View {
                            attr {
                                marginTop(8f)
                                padding(10f, 12f, 10f, 12f)
                                borderRadius(10f)
                                backgroundColor(ctx.attr.colors.bgBase)
                            }
                            Text {
                                attr {
                                    text(approval.command ?: "")
                                    fontSize(12f)
                                    lineHeight(18f)
                                    fontFamily("monospace")
                                    color(ctx.attr.colors.labelSecondary)
                                }
                            }
                        }
                    }
                    View {
                        attr {
                            height(40f)
                            marginTop(12f)
                            flexDirectionRow()
                            justifyContentFlexEnd()
                            alignItemsCenter()
                        }
                        View {
                            attr {
                                height(32f)
                                paddingLeft(14f)
                                paddingRight(14f)
                                borderRadius(8f)
                                justifyContentCenter()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    text(if (ctx.attr.busy) "处理中" else "拒绝")
                                    fontSize(13f)
                                    color(ctx.attr.colors.stateErrorPrimary)
                                }
                            }
                            DshTapTarget { if (!ctx.attr.busy) ctx.attr.onAnswer("rejected") }
                        }
                        View {
                            attr {
                                height(32f)
                                marginLeft(8f)
                                paddingLeft(14f)
                                paddingRight(14f)
                                borderRadius(8f)
                                backgroundColor(if (ctx.attr.busy) ctx.attr.colors.stateSuccessTertiary else ctx.attr.colors.stateSuccessPrimary)
                                justifyContentCenter()
                                alignItemsCenter()
                            }
                            Text {
                                attr {
                                    text(if (ctx.attr.busy) "处理中" else "允许一次")
                                    fontSize(13f)
                                    fontWeightMedium()
                                    color(Color.WHITE)
                                }
                            }
                            DshTapTarget { if (!ctx.attr.busy) ctx.attr.onAnswer("allowed-once") }
                        }
                    }
                }
            }
        }
    }
}

internal class DshApprovalPanelAttr : ComposeAttr() {
    var approval: DshPendingApproval? by observable(null)
    var busy: Boolean by observable(false)
    var onAnswer: (String) -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}
