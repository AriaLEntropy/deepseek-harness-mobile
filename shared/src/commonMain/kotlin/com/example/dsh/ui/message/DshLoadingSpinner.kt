package com.example.dsh.ui.message

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.View
import kotlin.math.roundToInt

// 加载圈：官方 Lucide loader-circle SVG（见 assets/THIRD_PARTY_NOTICES.txt），
// 匀速转满一圈耗时 durationS，用于附件上传中、审批区首次同步等等待态。
//
// 为什么不用 animateToAttr(Animation.linear().repeatForever(true))：
// - Android 渲染端把 repeatForever 编成 ValueAnimator.setRepeatCount(-1) +
//   setRepeatMode(REVERSE)，0°→360° 会正转半圈再倒回去，是「摆」不是「转」；
// - 实测深色模拟器整屏连续截图 8 帧，这颗圈 0 像素变化，这条路在本工程里没有画面。
// 改成工程里已经跑通的那条路（会话抽屉、输入条键盘动画同一套）：
// observable 角度 + animation(...) 逐段线性补间，每段 60°、段时长 = 一圈 / 6，
// 段与段首尾相接，看起来就是匀速自转。
internal fun ViewContainer<*, *>.DshLoadingSpinner(
    diameter: Float,
    tint: Color,
    durationS: Float = 0.9f,
) {
    val state = DshSpinnerState(this)
    var spinnerRef: ViewRef<DivView>? = null
    val stepDegrees = 60f
    val stepMs = (durationS * 1000f / (360f / stepDegrees)).roundToInt().coerceAtLeast(16)
    fun spin() {
        // 节点已被回收（加载结束、列表把它滚走、页面销毁）时不再续期，免得留下空转的定时器
        if (spinnerRef?.view == null) return
        // 角度只增不减：回绕到 0 会让最后一段从 300° 倒着转回 0°
        state.angle += stepDegrees
        setTimeout(pagerId, stepMs) { spin() }
    }
    setTimeout(pagerId, stepMs) { spin() }
    View {
        attr {
            width(diameter)
            height(diameter)
            transform(Rotate(state.angle))
            animation(Animation.linear(stepMs / 1000f), state.angle)
        }
        ref { viewRef ->
            spinnerRef = viewRef
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

/**
 * 旋转角度所在的响应式载体
 *
 * 局部变量不能用 `by observable(...)`（委托的 thisRef 是 Nothing?），
 * 所以按本工程已有做法（DshHomeState）单独建一个持有 PagerScope 的小类
 */
private class DshSpinnerState(private val scope: PagerScope) {
    var angle by scope.observable(0f)
}
