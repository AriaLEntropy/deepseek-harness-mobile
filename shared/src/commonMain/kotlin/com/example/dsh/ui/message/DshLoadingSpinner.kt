package com.example.dsh.ui.message

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.View

// 加载圈：官方 Lucide loader-circle SVG（见 assets/THIRD_PARTY_NOTICES.txt），
// 以线性动画无限旋转，用于附件上传中状态。
internal fun ViewContainer<*, *>.DshLoadingSpinner(
    diameter: Float,
    tint: Color,
    durationS: Float = 0.9f,
) {
    var ref: ViewRef<DivView>? = null
    View {
        attr {
            width(diameter)
            height(diameter)
        }
        ref { viewRef ->
            ref = viewRef
            // 旋转默认绕视图中心，但必须等布局拿到真实尺寸后再启动动画：
            // 尺寸仍为 0 时启动，旋转轴会落在左上角，表现为绕圈 / 与图片中心错位。
            getPager().addTaskWhenPagerUpdateLayoutFinish {
                ref?.view?.animateToAttr(Animation.linear(durationS).repeatForever(true)) {
                    transform(Rotate(360f))
                }
            }
        }
        Image {
            attr {
                src(ImageUri.commonAssets("loader-circle.svg"))
                width(diameter)
                height(diameter)
                tintColor(tint)
            }
        }
    }
}
