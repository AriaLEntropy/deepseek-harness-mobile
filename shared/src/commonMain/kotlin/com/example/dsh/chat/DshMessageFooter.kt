package com.example.dsh.chat

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

// 回答下方横向操作容器：复制 / 好的回答 / 有问题的回答 / 在新对话中分支 / 分享（对齐 dsh 原版）
internal fun ViewContainer<*, *>.DshMessageFooter(
    copied: Boolean = false,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    onAction: (DshMessageFooterAction) -> Unit,
) {
    View {
        attr {
            height(28f)
            marginTop(2f)
            flexDirectionRow()
            alignItemsCenter()
        }
        DshFooterActionIcon(if (copied) "check.svg" else "copy.svg", DshMessageFooterAction.COPY, onAction, colors = colors, first = true)
        DshFooterActionIcon("like.svg", DshMessageFooterAction.GOOD, onAction, colors = colors)
        DshFooterActionIcon("dislike.svg", DshMessageFooterAction.BAD, onAction, colors = colors)
        DshFooterActionIcon("branch.svg", DshMessageFooterAction.BRANCH, onAction, colors = colors)
        DshFooterActionIcon("share.svg", DshMessageFooterAction.SHARE, onAction, colors = colors)
    }
}

// 单个操作图标按钮：28x28 圆形热区，16px 图标居中（对齐 dsh 原版 IconActions）
internal fun ViewContainer<*, *>.DshFooterActionIcon(
    asset: String,
    action: DshMessageFooterAction,
    onAction: (DshMessageFooterAction) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
    first: Boolean = false,
) {
    View {
        attr {
            width(28f)
            height(28f)
            allCenter()
            borderRadius(14f)
            if (!first) marginLeft(6f)
        }
        event { click { onAction(action) } }
        Image {
            attr {
                src(ImageUri.commonAssets(asset))
                size(16f, 16f)
                tintColor(colors().labelTertiary)
            }
        }
    }
}
