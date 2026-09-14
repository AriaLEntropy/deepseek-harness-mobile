package com.example.dsh.ui.rendering

import com.example.dsh.session.DshJobItem
import com.example.dsh.session.DshQueueItem
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.Input

class DshQueueDockView : ComposeView<DshQueueDockAttr, ComposeEvent>() {
    override fun createAttr(): DshQueueDockAttr = DshQueueDockAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val items = ctx.attr.items
        val expanded = ctx.attr.expanded || items.size <= 1 || ctx.attr.editingId.isNotEmpty() || ctx.attr.actionBusy
        return {
            vif({ items.isNotEmpty() }) {
                View {
                    attr {
                        marginBottom(8f)
                        flexDirectionColumn()
                        padding(8f)
                        borderRadius(10f)
                        backgroundColor(ctx.attr.colors.bgBase)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    }
                    vif({ items.size > 1 }) {
                        View {
                            attr {
                                height(30f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            event { click { if (!ctx.attr.actionBusy && ctx.attr.editingId.isEmpty()) ctx.attr.onToggle() } }
                            Text {
                                attr {
                                    text("队列 · ${items.size}")
                                    flex(1f)
                                    fontSize(13f)
                                    color(ctx.attr.colors.labelPrimary)
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("chevron-down.svg"))
                                    size(14f, 14f)
                                    tintColor(ctx.attr.colors.labelTertiary)
                                    transform(Rotate(if (expanded) 0f else -90f))
                                }
                            }
                        }
                    }
                    vif({ expanded }) {
                        vfor({ ctx.attr.items }) { item ->
                            View {
                                attr {
                                    minHeight(40f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                }
                                vif({ ctx.attr.editingId != item.id }) {
                                    Text {
                                        attr {
                                            text(item.preview)
                                            flex(1f)
                                            lines(1)
                                            fontSize(13f)
                                            color(ctx.attr.colors.labelPrimary)
                                        }
                                    }
                                    Text {
                                        attr {
                                            text("编辑")
                                            marginLeft(8f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.stateBusinessPrimary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onEdit(item.id) } }
                                    }
                                    Text {
                                        attr {
                                            text("删除")
                                            marginLeft(10f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.stateErrorPrimary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onRemove(item.id) } }
                                    }
                                    Text {
                                        attr {
                                            text("转向")
                                            marginLeft(10f)
                                            fontSize(12f)
                                            color(if (ctx.attr.running) ctx.attr.colors.stateBusinessPrimary else ctx.attr.colors.labelCaption)
                                        }
                                        event { click { if (ctx.attr.running && !ctx.attr.actionBusy) ctx.attr.onSteer(item.id) } }
                                    }
                                }
                                vif({ ctx.attr.editingId == item.id }) {
                                    Input {
                                        ref { it.view?.setText(ctx.attr.editingText) }
                                        attr {
                                            flex(1f)
                                            height(32f)
                                            fontSize(13f)
                                            placeholder("编辑队列消息")
                                            placeholderColor(ctx.attr.colors.labelTertiary)
                                        }
                                        event { textDidChange { ctx.attr.onEditingTextChange(it.text) } }
                                    }
                                    Text {
                                        attr {
                                            text("保存")
                                            marginLeft(8f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.stateSuccessPrimary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onSaveEdit(item.id) } }
                                    }
                                    Text {
                                        attr {
                                            text("取消")
                                            marginLeft(10f)
                                            fontSize(12f)
                                            color(ctx.attr.colors.labelTertiary)
                                        }
                                        event { click { if (!ctx.attr.actionBusy) ctx.attr.onCancelEdit() } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class DshQueueDockAttr : ComposeAttr() {
    var items: com.tencent.kuikly.core.reactive.collection.ObservableList<DshQueueItem> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var expanded: Boolean by observable(false)
    var running: Boolean by observable(false)
    var editingId: String by observable("")
    var editingText: String by observable("")
    var actionBusy: Boolean by observable(false)
    var onToggle: () -> Unit by observable({})
    var onEdit: (String) -> Unit by observable({})
    var onEditingTextChange: (String) -> Unit by observable({})
    var onSaveEdit: (String) -> Unit by observable({})
    var onCancelEdit: () -> Unit by observable({})
    var onRemove: (String) -> Unit by observable({})
    var onSteer: (String) -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

fun ViewContainer<*, *>.DshQueueDock(init: DshQueueDockView.() -> Unit) {
    addChild(DshQueueDockView(), init)
}

class DshJobsPanelView : ComposeView<DshJobsPanelAttr, ComposeEvent>() {
    override fun createAttr(): DshJobsPanelAttr = DshJobsPanelAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val snapshot = ctx.attr.jobs.toList()
        val ordered = dshOrderedJobs(snapshot)
        val liveCount = ordered.count { it.status == "running" || it.status == "stopping" }
        return {
            vif({ ctx.attr.jobs.isNotEmpty() }) {
                View {
                    attr {
                        marginBottom(8f)
                        flexDirectionColumn()
                        padding(8f)
                        borderRadius(10f)
                        backgroundColor(ctx.attr.colors.bgBase)
                        border(Border(1f, BorderStyle.SOLID, ctx.attr.colors.borderL2))
                    }
                    View {
                        attr { height(30f); flexDirectionRow(); alignItemsCenter() }
                        event { click { ctx.attr.onToggle() } }
                        Text {
                            attr {
                                text(if (liveCount > 0) "后台任务 · $liveCount 运行中" else "后台任务 · ${snapshot.size}")
                                flex(1f)
                                fontSize(13f)
                                color(ctx.attr.colors.labelPrimary)
                            }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("chevron-down.svg"))
                                size(14f, 14f)
                                tintColor(ctx.attr.colors.labelTertiary)
                                transform(Rotate(if (ctx.attr.expanded) 0f else -90f))
                            }
                        }
                    }
                    vif({ ctx.attr.expanded }) {
                    vfor({ ctx.attr.jobs }) { job ->
                        View {
                            attr {
                                minHeight(46f)
                                marginTop(6f)
                                flexDirectionColumn()
                                padding(6f)
                                borderRadius(8f)
                                backgroundColor(ctx.attr.colors.bgBase)
                            }
                            View {
                                attr { flexDirectionRow(); alignItemsCenter() }
                                View {
                                    attr {
                                        size(7f, 7f)
                                        borderRadius(4f)
                                        backgroundColor(
                                            when (job.status) {
                                                "running" -> ctx.attr.colors.stateSuccessPrimary
                                                "stopping" -> ctx.attr.colors.stateWarnPrimary
                                                "completed" -> ctx.attr.colors.labelTertiary
                                                "failed" -> ctx.attr.colors.stateErrorPrimary
                                                else -> ctx.attr.colors.labelTertiary
                                            },
                                        )
                                    }
                                }
                                Text {
                                    attr {
                                        text(job.kind)
                                        marginLeft(7f)
                                        fontSize(12f)
                                        fontWeightMedium()
                                        color(ctx.attr.colors.labelPrimary)
                                    }
                                }
                                Text {
                                    attr {
                                        text(job.detail.ifEmpty { dshJobStatusLabel(job.status) })
                                        marginLeft(8f)
                                        fontSize(11f)
                                        color(ctx.attr.colors.labelSecondary)
                                    }
                                }
                                Text {
                                    attr {
                                        text(dshJobDuration(job, ctx.attr.now))
                                        marginLeft(8f)
                                        fontSize(11f)
                                        color(ctx.attr.colors.labelTertiary)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text(job.label)
                                    marginTop(3f)
                                    lines(1)
                                    fontSize(12f)
                                    color(ctx.attr.colors.labelPrimary)
                                }
                            }
                            vif({ job.detail.isNotEmpty() }) {
                                Text {
                                    attr {
                                        text(job.detail)
                                        marginTop(2f)
                                        lines(1)
                                        fontSize(11f)
                                        color(ctx.attr.colors.labelTertiary)
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

fun dshOrderedJobs(jobs: List<DshJobItem>): List<DshJobItem> {
    return jobs.sortedWith(
        compareByDescending<DshJobItem> { it.status == "running" || it.status == "stopping" }
            .thenBy { if (it.status == "running" || it.status == "stopping") it.startedAt else Long.MAX_VALUE }
            .thenByDescending { it.finishedAt ?: it.startedAt },
    )
}

/** Bottom jobs UI only represents work that can still change. */
fun dshLiveJobs(jobs: List<DshJobItem>): List<DshJobItem> =
    dshOrderedJobs(jobs.filter { it.status == "running" || it.status == "stopping" })

private fun dshJobStatusLabel(status: String): String = when (status) {
    "running" -> "运行中"
    "stopping" -> "正在停止"
    "completed" -> "已完成"
    "killed" -> "已取消"
    "failed" -> "已失败"
    else -> status
}

fun dshJobDuration(job: DshJobItem, now: Long): String {
    val end = job.finishedAt ?: now.takeIf { it > 0L } ?: return "进行中"
    val seconds = ((end - job.startedAt).coerceAtLeast(0L) / 1000L).toInt()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        minutes > 0 -> "${minutes}分${rest}秒"
        else -> "${rest}秒"
    }
}

class DshJobsPanelAttr : ComposeAttr() {
    var jobs: com.tencent.kuikly.core.reactive.collection.ObservableList<DshJobItem> by observable(
        com.tencent.kuikly.core.reactive.collection.ObservableList(),
    )
    var expanded: Boolean by observable(false)
    var now: Long by observable(0L)
    var onToggle: () -> Unit by observable({})
    var colors: com.example.dsh.theme.DshColorTokens by observable(com.example.dsh.theme.DshDefaultTheme.light)
}

fun ViewContainer<*, *>.DshJobsPanel(init: DshJobsPanelView.() -> Unit) {
    addChild(DshJobsPanelView(), init)
}
