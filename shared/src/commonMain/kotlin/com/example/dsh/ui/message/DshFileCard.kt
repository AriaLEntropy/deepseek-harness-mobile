package com.example.dsh.ui.message

import com.example.dsh.attachment.DshFileAttachment
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.attachment.dshFileIconAsset
import com.example.dsh.attachment.dshFileIconTint
import com.example.dsh.attachment.dshFileTypeLabel
import com.example.dsh.attachment.dshFormatFileSize
import com.example.dsh.ui.home.DshHitButton
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

// 通用文件卡片（参考 TDesign FileCard）：左侧灰色方形图标容器 + 文件名/类型·大小。
// 草稿态右上角带移除按钮；失败态红框并可点按重试。
internal fun ViewContainer<*, *>.DshFileCard(
    name: String,
    bytes: Long,
    mediaType: String,
    failed: Boolean = false,
    uploading: Boolean = false,
    descOverride: String = "",
    onRemove: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    val palette = colors()
    val desc = when {
        descOverride.isNotEmpty() -> descOverride
        uploading -> "上传中…"
        else -> "${dshFileTypeLabel(name, mediaType)} ${dshFormatFileSize(bytes)}"
    }
    View {
        attr {
            width(184f)
            height(64f)
            marginRight(8f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(10f)
            paddingRight(12f)
            borderRadius(10f)
            backgroundColor(palette.bgLayer1)
            border(Border(1f, BorderStyle.SOLID, if (failed) palette.stateErrorPrimary else palette.borderL1))
        }
        event {
            val retry = onRetry
            if (failed && retry != null) click { retry() }
        }
        // 灰色方形图标容器：浅色主题深于背景，深色主题浅于背景（bgModulePlatform 两种主题下都满足）。
        View {
            attr {
                size(40f, 40f)
                borderRadius(8f)
                allCenter()
                backgroundColor(palette.bgModulePlatform)
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(dshFileIconAsset(name)))
                    size(22f, 22f)
                    tintColor(dshFileIconTint(name, palette))
                }
            }
        }
        View {
            attr { marginLeft(10f); flex(1f); flexDirectionColumn(); justifyContentCenter() }
            Text {
                attr {
                    text(name)
                    fontSize(13f)
                    lines(1)
                    textOverFlowTail()
                    color(palette.labelPrimary)
                }
            }
            Text {
                attr {
                    text(desc)
                    fontSize(11f)
                    lines(1)
                    textOverFlowTail()
                    marginTop(3f)
                    color(if (failed) palette.stateErrorPrimary else palette.labelTertiary)
                }
            }
        }
        val remove = onRemove
        if (remove != null) {
            // TDesign 原生 16px 圆形关闭图标（圆+X 镂空），普通流内顶对齐，避免绝对定位飞出卡片。
            View {
                attr {
                    size(24f, 24f)
                    marginLeft(2f)
                    alignSelf(FlexAlign.FLEX_START)
                    allCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("file-close.svg"))
                        size(16f, 16f)
                        tintColor(palette.labelSecondary)
                    }
                }
                DshHitButton { remove() }
            }
        }
    }
}

// 已发送用户气泡内的文件卡片：不可移除，横向滑动由外层 Scroller 负责。
internal fun ViewContainer<*, *>.DshUserFileCard(
    file: DshFileAttachment,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    DshFileCard(name = file.name, bytes = file.bytes, mediaType = "", colors = colors)
}

// 输入区文件草稿卡片：带移除与失败重试。
internal fun ViewContainer<*, *>.DshDraftFileCard(
    file: DshPendingFile,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    DshFileCard(
        name = file.name,
        bytes = file.bytes,
        mediaType = file.mediaType,
        failed = file.isFailed,
        uploading = file.isUploading,
        descOverride = if (file.isFailed) file.error.ifEmpty { "上传失败，点按重试" } else "",
        onRemove = onRemove,
        onRetry = onRetry,
        colors = colors,
    )
}
