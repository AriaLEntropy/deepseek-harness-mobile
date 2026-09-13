package com.example.dsh.home


import com.example.dsh.base.DshBottomSheet
import com.example.dsh.models.DshModelOption
import com.example.dsh.models.DshReasoningEffort
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

internal fun ViewContainer<*, *>.DshModelPicker(
    options: () -> ObservableList<DshModelOption>,
    busy: () -> Boolean,
    error: () -> String,
    showEfforts: () -> Boolean,
    onShowEfforts: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSelect: (DshModelOption) -> Unit,
    onSelectEffort: (String) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    val selectedOpt: () -> DshModelOption? = { options().firstOrNull { it.selected } }
    val selectedEfforts: () -> ObservableList<DshReasoningEffort> = {
        val sel = selectedOpt()
        ObservableList<DshReasoningEffort>().apply { addAll(sel?.reasoningEfforts.orEmpty()) }
    }
    val selectedEffortName: () -> String = {
        val sel = selectedOpt()
        sel?.reasoningEfforts?.firstOrNull { it.id == sel.reasoningEffort }?.name
            ?: sel?.reasoningEffort ?: ""
    }
    val selectedSupportsEfforts: () -> Boolean = {
        selectedOpt()?.reasoningEfforts?.isNotEmpty() == true
    }
    DshBottomSheet(
        colors = colors,
        onClose = onClose,
        largeHeightRatio = 0.62f,
        heightCap = 540f,
    ) {
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                padding(18f)
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(if (showEfforts()) "推理等级" else "选择模型")
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(6f)
                        marginBottom(6f)
                        fontSize(12f)
                        color(colors().stateErrorPrimary)
                    }
                }
            }
            vif({ showEfforts() }) {
                // 推理等级：顶部返回栏 + 当前模型名
                View {
                    attr {
                        height(36f)
                        marginTop(2f)
                        marginBottom(4f)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr { size(32f, 32f); allCenter() }
                        Image { attr { src(ImageUri.commonAssets("chevron-left.svg")); size(18f, 18f); tintColor(colors().labelSecondary) } }
                        event { click { onShowEfforts(false) } }
                    }
                    Text {
                        attr {
                            text(selectedOpt()?.name ?: "")
                            marginLeft(4f)
                            fontSize(13f)
                            color(colors().labelTertiary)
                        }
                    }
                }
                Scroller {
                    attr { flex(1f); marginTop(4f) }
                    vfor({ selectedEfforts() }) { effort ->
                        View {
                            attr {
                                minHeight(54f)
                                marginBottom(6f)
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f, 12f, 10f, 12f)
                                borderRadius(10f)
                                backgroundColor(if (effort.id == selectedOpt()?.reasoningEffort) colors().specificSelector else colors().bgModulePlatform)
                            }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text {
                                    attr {
                                        text(effort.name)
                                        fontSize(14f)
                                        fontWeightMedium()
                                        color(colors().labelPrimary)
                                    }
                                }
                                if (effort.description.isNotEmpty()) {
                                    Text {
                                        attr {
                                            text(effort.description)
                                            marginTop(3f)
                                            lines(1)
                                            fontSize(11f)
                                            color(colors().labelTertiary)
                                        }
                                    }
                                }
                            }
                            vif({ effort.id == selectedOpt()?.reasoningEffort }) {
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(18f, 18f)
                                        tintColor(colors().stateBusinessPrimary)
                                    }
                                }
                            }
                            event { click { if (!busy()) onSelectEffort(effort.id) } }
                        }
                    }
                }
            }
            velse {
                // 当前选中模型：若支持推理等级，显示"推理等级 ›"入口行
                vif({ selectedSupportsEfforts() }) {
                    View {
                        attr {
                            height(44f)
                            marginTop(6f)
                            marginBottom(2f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            flexDirectionRow()
                            alignItemsCenter()
                            borderRadius(10f)
                            backgroundColor(colors().specificSelector)
                        }
                        Text {
                            attr {
                                text("推理等级")
                                fontSize(14f)
                                color(colors().labelPrimary)
                            }
                        }
                        View { attr { flex(1f) } }
                        Text {
                            attr {
                                text(selectedEffortName())
                                marginRight(4f)
                                fontSize(13f)
                                color(colors().labelTertiary)
                            }
                        }
                        Image { attr { src(ImageUri.commonAssets("chevron-right.svg")); size(14f, 14f); tintColor(colors().labelTertiary) } }
                        event { click { if (!busy()) onShowEfforts(true) } }
                    }
                }
                vif({ busy() && options().isEmpty() }) {
                    Text {
                        attr {
                            text("正在加载模型...")
                            marginTop(24f)
                            fontSize(14f)
                            color(colors().labelTertiary)
                        }
                    }
                }
                Scroller {
                    attr { flex(1f); marginTop(8f) }
                    vfor({ options() }) { option ->
                        View {
                            attr {
                                minHeight(58f)
                                marginBottom(6f)
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f, 12f, 10f, 12f)
                                borderRadius(10f)
                                backgroundColor(if (option.selected) colors().stateBusinessTertiary else colors().bgBase)
                            }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text {
                                    attr {
                                        text(option.name)
                                        fontSize(14f)
                                        fontWeightMedium()
                                        color(colors().labelPrimary)
                                    }
                                }
                                Text {
                                    attr {
                                        text(option.providerName + if (option.description.isEmpty()) "" else " · ${option.description}")
                                        marginTop(3f)
                                        lines(1)
                                        fontSize(11f)
                                        color(colors().labelTertiary)
                                    }
                                }
                            }
                            if (option.selected) {
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(18f, 18f)
                                        tintColor(colors().stateBusinessPrimary)
                                    }
                                }
                            }
                            event { click { if (!busy()) onSelect(option) } }
                        }
                    }
                }
            }
        }
    }
}
