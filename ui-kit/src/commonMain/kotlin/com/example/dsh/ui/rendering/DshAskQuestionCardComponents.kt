package com.example.dsh.ui.rendering

import com.example.dsh.tool.AnsweredItem
import com.example.dsh.tool.DshAskQuestionCard
import com.example.dsh.tool.UnansweredItem
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

class DshAskQuestionCardAttr : ComposeAttr() {
    var card: DshAskQuestionCard? by observable(null)
    var colors: DshColorTokens by observable(DshDefaultTheme.light)
}

class DshAskQuestionCardView : ComposeView<DshAskQuestionCardAttr, ComposeEvent>() {
    override fun createAttr(): DshAskQuestionCardAttr = DshAskQuestionCardAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        val card = ctx.attr.card ?: return { View { } }
        return when (card) {
            is DshAskQuestionCard.Answered -> {
                val questions = ObservableList<AnsweredItem>().also { it.addAll(card.questions) }
                val skippedLabel = card.skippedLabel
                {
                    View {
                        attr {
                            flexDirectionColumn()
                            marginTop(4f)
                            padding(0f, 4f, 4f, 4f)
                        }
                        vfor({ questions }) { item ->
                            val answers = ObservableList<String>().also { it.addAll(item.answers) }
                            View {
                                attr {
                                    flexDirectionColumn()
                                    marginTop(12f)
                                }
                                Text {
                                    attr {
                                        text(item.question)
                                        fontSize(14f)
                                        lineHeight(22f)
                                        color(ctx.attr.colors.labelTertiary)
                                    }
                                }
                                vif({ item.answers.isEmpty() }) {
                                    Text {
                                        attr {
                                            text(skippedLabel)
                                            fontSize(14f)
                                            lineHeight(22f)
                                            color(ctx.attr.colors.labelTertiary)
                                            marginTop(2f)
                                        }
                                    }
                                }
                                vif({ item.answers.isNotEmpty() }) {
                                    vfor({ answers }) { answer ->
                                        Text {
                                            attr {
                                                text(answer)
                                                fontSize(14f)
                                                lineHeight(22f)
                                                color(ctx.attr.colors.labelPrimary)
                                                marginTop(2f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is DshAskQuestionCard.Unanswered -> {
                val questions = ObservableList<UnansweredItem>().also { it.addAll(card.questions) }
                val verdict = card.verdict
                {
                    View {
                        attr {
                            flexDirectionColumn()
                            marginTop(4f)
                            padding(0f, 4f, 4f, 4f)
                        }
                        Text {
                            attr {
                                text(verdict)
                                fontSize(14f)
                                lineHeight(22f)
                                color(ctx.attr.colors.labelPrimary)
                                marginTop(12f)
                                marginBottom(8f)
                            }
                        }
                        vfor({ questions }) { item ->
                            Text {
                                attr {
                                    text(item.question)
                                    fontSize(14f)
                                    lineHeight(22f)
                                    color(ctx.attr.colors.labelTertiary)
                                    marginTop(6f)
                                    marginLeft(16f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun ViewContainer<*, *>.DshAskQuestionCard(init: DshAskQuestionCardView.() -> Unit) {
    addChild(DshAskQuestionCardView(), init)
}
