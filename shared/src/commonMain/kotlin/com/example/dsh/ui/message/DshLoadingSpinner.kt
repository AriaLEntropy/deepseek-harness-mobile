package com.example.dsh.ui.message

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.base.event.didAppear
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.View

// 加载圈：官方 Lucide loader-circle SVG（见 assets/THIRD_PARTY_NOTICES.txt），
// 可见后以线性动画无限旋转，用于附件上传中状态。
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
        ref { ref = it }
        event {
            didAppear {
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
