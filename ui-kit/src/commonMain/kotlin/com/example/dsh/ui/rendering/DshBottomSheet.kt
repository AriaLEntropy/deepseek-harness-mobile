package com.example.dsh.ui.rendering

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BorderRectRadius
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.base.Utils

/** 面板吸附档位：medium 半屏 / large 全屏。 */
enum class DshSheetDetent { MEDIUM, LARGE }

/** iOS 风格底部面板的默认抓握条尺寸（dp）。 */
private const val SHEET_GRABBER_ZONE_HEIGHT = 32f
private const val SHEET_GRABBER_WIDTH = 40f
private const val SHEET_GRABBER_HEIGHT = 5f

/** 松手后的弹簧吸附参数。 */
private const val SHEET_SPRING_DURATION_S = 0.34f
private const val SHEET_SPRING_DAMPING = 0.82f

/** 惯性投射时长（ms）：松手时按当前速度把面板投射这么远再找最近档位。 */
private const val SHEET_PROJECT_MS = 140f

/** 关闭动画时长（ms），结束后才真正回调关闭。 */
private const val SHEET_DISMISS_ANIM_MS = 300

/** 读取不到设备屏幕圆角时的兜底半径（dp）。 */
private const val SHEET_FALLBACK_RADIUS = 16f

/**
 * 设备屏幕圆角半径（dp），进程内缓存一次。
 * 通过 [BridgeModule] 同步向原生查询；未实现或失败时回退 [SHEET_FALLBACK_RADIUS]。
 */
object DshDeviceCorner {
    private var cached: Float? = null

    fun screenRadiusDp(): Float {
        cached?.let { return it }
        val radius = runCatching { Utils.currentBridgeModule().screenCornerRadius() }
            .getOrDefault(0f)
        val resolved = if (radius > 0f) radius else SHEET_FALLBACK_RADIUS
        cached = resolved
        return resolved
    }
}

class DshBottomSheetAttr : ComposeAttr() {
    var colors: () -> DshColorTokens by observable({ DshDefaultTheme.light })
    var panelBackground: () -> Color by observable({ DshDefaultTheme.light.bgLayer1 })
    var onDismiss: () -> Unit by observable({})
    /** true（默认）为模态：全屏遮罩 + 点击关闭；false 为内联贴底面板，不拦截上方内容。 */
    var modal: Boolean by observable(true)
    var initialDetent: DshSheetDetent by observable(DshSheetDetent.MEDIUM)
    /** 面板完整高度（dp）；<= 0 时按 [largeHeightRatio] × 页面高度计算。 */
    var panelHeight: Float by observable(0f)
    /** medium 档可见高度占页面高度的比例。 */
    var mediumHeightRatio: Float by observable(0.5f)
    /** 未显式指定 [panelHeight] 时，large 档高度占页面高度的比例。 */
    var largeHeightRatio: Float by observable(0.92f)
    /** 面板高度上限（dp）；<= 0 不限制。 */
    var heightCap: Float by observable(0f)
    /** 非模态内联面板是否按内容自适应高度（默认 false：使用 [resolvedPanelHeight]）。 */
    var wrapContent: Boolean by observable(false)
    /** 面板内输入框上报的键盘遮挡高度；输入时面板展开并停在键盘上方。 */
    var keyboardHeight: Float by observable(0f)
    var content: ViewContainer<*, *>.() -> Unit by observable({})
}

/**
 * iOS 风格可拖拽底部面板：
 * - 顶部居中 Grabber 抓握条，点击循环切换 medium/large 档位；
 * - 拖拽实时跟手，松手按速度惯性投射并用弹簧动画吸附最近档位；
 * - 向下拖过阈值松手即关闭（带下滑动画与背景遮罩）；
 * - 面板顶部圆角动态读取设备屏幕圆角，Grabber 颜色随深/浅色主题切换。
 */
class DshBottomSheetView : ComposeView<DshBottomSheetAttr, ComposeEvent>() {
    override fun createAttr(): DshBottomSheetAttr = DshBottomSheetAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    /** 非拖拽态下显示的位移比例：0 = 完全展开，1 = 完全移出屏幕。 */
    private var offset by observable(0f)

    /** 拖拽态下显示的位移比例（跟手，不做动画）。 */
    private var dragOffset by observable(0f)

    private var dragging by observable(false)
    private var dismissing by observable(false)

    private var currentDetent by observable(DshSheetDetent.MEDIUM)

    private var dragStartOffset = 0f
    private var dragStartY = 0f
    private var lastY = 0f
    private var lastAt = 0L
    private var velocity = 0f

    /** 是否已播放过入场动画，避免重复触发。 */
    private var revealed = false

    override fun created() {
        currentDetent = attr.initialDetent
        // 先挂载在屏幕外，等首帧布局完成后再滑入，避免从默认（左上角）位置起跳。
        offset = 1f
        dragOffset = 1f
    }

    override fun viewDidLayout() {
        super.viewDidLayout()
        // 必须等到面板真正布局完成（bottom 定位/高度已生效）后再改 offset 触发动画，
        // 否则首次打开时位移百分比会基于未布局的 0 尺寸计算，表现为从左上角飞入。
        if (revealed || dismissing) return
        revealed = true
        offset = offsetForDetent(currentDetent)
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            if (ctx.attr.modal) {
                Modal(inWindow = true) {
                    attr {
                        absolutePositionAllZero()
                        // 背景遮罩随面板下滑同步淡出
                        backgroundColor(ctx.scrimColor())
                    }
                    // 整屏点击捕获层：面板下移后露出的区域同样可点击关闭
                    View {
                        attr { absolutePositionAllZero() }
                        event { click { ctx.dismiss() } }
                    }
                    // 面板容器：贴底全宽，顶部圆角动态取设备屏幕圆角
                    View {
                        attr {
                            positionType(FlexPositionType.ABSOLUTE)
                            left(0f)
                            bottom(ctx.attr.keyboardHeight)
                            width(pagerData.pageViewWidth)
                            height(ctx.resolvedPanelHeight())
                            flexDirectionColumn()
                            borderRadius(BorderRectRadius(ctx.cornerRadius(), ctx.cornerRadius(), 0f, 0f))
                            backgroundColor(ctx.attr.panelBackground())
                            boxShadow(BoxShadow(0f, -4f, 16f, Color(0x14000000)))
                            transform(Translate(0f, ctx.displayOffset()))
                            if (!ctx.dragging) {
                                animation(
                                    Animation.springEaseOut(SHEET_SPRING_DURATION_S, SHEET_SPRING_DAMPING, 0f),
                                    ctx.offset,
                                )
                            }
                        }
                        // 面板整体可拖拽：点按由子控件处理，竖向滑动把面板拉上/拉下；
                        // 空白点击在此消费，避免穿透到全屏遮罩导致误关闭。
                        event {
                            click { }
                            pan { ctx.onPan(it) }
                        }
                        this@DshBottomSheetView.buildSheetContent(this)
                    }
                }
            } else {
                // 非模态：内联贴底面板，不拦截上方内容（导出多选态需继续点选消息）。
                View {
                    attr {
                        width(pagerData.pageViewWidth)
                        if (!ctx.attr.wrapContent) height(ctx.resolvedPanelHeight())
                        flexDirectionColumn()
                        borderRadius(BorderRectRadius(ctx.cornerRadius(), ctx.cornerRadius(), 0f, 0f))
                        backgroundColor(ctx.attr.panelBackground())
                        boxShadow(BoxShadow(0f, -4f, 16f, Color(0x14000000)))
                    }
                    this@DshBottomSheetView.buildSheetContent(this)
                }
            }
        }
    }

    private fun buildSheetContent(parent: ViewContainer<*, *>) {
        val ctx = this@DshBottomSheetView
        // ===== Grabber：居中抓握指示器，点按循环切换档位（仅模态面板） =====
        if (ctx.attr.modal) {
            parent.View {
                attr {
                    width(pagerData.pageViewWidth)
                    height(SHEET_GRABBER_ZONE_HEIGHT)
                    allCenter()
                }
                View {
                    attr {
                        width(SHEET_GRABBER_WIDTH)
                        height(SHEET_GRABBER_HEIGHT)
                        borderRadius(SHEET_GRABBER_HEIGHT / 2f)
                        backgroundColor(ctx.attr.colors().labelCaption)
                    }
                }
                event { click { ctx.cycleDetent() } }
            }
        }
        // ===== 业务内容 =====
        parent.View {
            attr {
                if (!ctx.attr.wrapContent) flex(1f)
                flexDirectionColumn()
                if (ctx.attr.modal) paddingBottom(ctx.contentBottomInset())
            }
            ctx.attr.content.invoke(this)
        }
    }

    /**
     * Translate 只移动面板，不改变 Flex 布局高度；把屏幕外的高度扣出内容区，
     * 让列表与底部操作在 medium/large 两档都落在可见区域内。
     * 拖拽时跟随可见高度，低于 medium 后维持最小内容高度并随面板下移；
     * 入场/关闭按当前档位布局，避免 offset = 1 时把内容压成零高。
     */
    private fun contentBottomInset(): Float {
        if (attr.keyboardHeight > 0f) return 12f
        val layoutOffset = if (dragging) {
            dragOffset.coerceIn(0f, mediumOffsetFraction())
        } else {
            offsetForDetent(currentDetent)
        }
        return resolvedPanelHeight() * layoutOffset + maxOf(0f, pagerData.safeAreaInsets.bottom)
    }

    private fun cornerRadius(): Float = DshDeviceCorner.screenRadiusDp()

    private fun scrimColor(): Color {
        val progress = 1f - displayOffset().coerceIn(0f, 1f)
        return Color(0x000000, 0.5f * progress)
    }

    /** 关闭动画期间按 offset 下滑；键盘弹出时强制展开，避免位移与抬升叠加。 */
    private fun displayOffset(): Float = when {
        dismissing -> offset
        attr.keyboardHeight > 0f -> 0f
        dragging -> dragOffset
        else -> offset
    }

    private fun resolvedPanelHeight(): Float {
        val pageHeight = pagerData.pageViewHeight
        val base = if (attr.panelHeight > 0f) attr.panelHeight else pageHeight * attr.largeHeightRatio
        val capped = if (attr.heightCap > 0f) base.coerceAtMost(attr.heightCap) else base
        return capped.coerceAtMost((pageHeight * 0.96f - attr.keyboardHeight).coerceAtLeast(0f))
    }

    /** medium 档对应的位移比例；面板本身不足半屏时返回 0（两档重合）。 */
    private fun mediumOffsetFraction(): Float {
        val panel = resolvedPanelHeight()
        if (panel <= 0f) return 0f
        val mediumVisible = (pagerData.pageViewHeight * attr.mediumHeightRatio).coerceAtMost(panel)
        return ((panel - mediumVisible) / panel).coerceIn(0f, 1f)
    }

    private fun offsetForDetent(detent: DshSheetDetent): Float =
        if (detent == DshSheetDetent.LARGE) 0f else mediumOffsetFraction()

    private fun cycleDetent() {
        if (dismissing || attr.keyboardHeight > 0f) return
        val medium = mediumOffsetFraction()
        if (medium <= 0.001f) return
        currentDetent = if (currentDetent == DshSheetDetent.LARGE) DshSheetDetent.MEDIUM else DshSheetDetent.LARGE
        offset = offsetForDetent(currentDetent)
    }

    private fun onPan(params: PanGestureParams) {
        if (dismissing || attr.keyboardHeight > 0f) return
        val panel = resolvedPanelHeight()
        if (panel <= 0f) return
        when {
            params.isStart -> {
                dragging = true
                dragStartOffset = offset
                dragOffset = offset
                dragStartY = params.pageY
                lastY = params.pageY
                lastAt = DateTime.currentTimestamp()
                velocity = 0f
            }
            params.isMove -> {
                dragOffset = (dragStartOffset + (params.pageY - dragStartY) / panel).coerceIn(0f, 1f)
                val now = DateTime.currentTimestamp()
                val dt = (now - lastAt).toFloat()
                if (dt > 0f) velocity = (params.pageY - lastY) / dt
                lastY = params.pageY
                lastAt = now
            }
            else -> {
                val now = DateTime.currentTimestamp()
                val dt = (now - lastAt).toFloat()
                if (dt > 0f) velocity = (params.pageY - lastY) / dt
                finishDrag(panel)
            }
        }
    }

    private fun finishDrag(panel: Float) {
        val medium = mediumOffsetFraction()
        // 速度按当前位移量纲投射：向上（负速度）偏向展开，向下偏向收起/关闭。
        val projected = (dragOffset + velocity * SHEET_PROJECT_MS / panel).coerceIn(0f, 1.2f)
        val closeThreshold = (medium + 1f) / 2f
        if (projected > closeThreshold) {
            dismiss()
            return
        }
        val target = when {
            medium <= 0.001f -> 0f
            abs(projected - 0f) <= abs(projected - medium) -> 0f
            else -> medium
        }
        currentDetent = if (target <= 0.001f) DshSheetDetent.LARGE else DshSheetDetent.MEDIUM
        // 先写目标位移（拖拽态下 transform 仍读 dragOffset，不会跳变），再结束拖拽触发弹簧动画。
        offset = target
        dragging = false
    }

    /** 关闭面板：先播放下滑弹簧动画，动画结束再真正回调关闭。 */
    private fun dismiss() {
        if (dismissing) return
        dismissing = true
        offset = 1f
        dragging = false
        setTimeout(pagerId, SHEET_DISMISS_ANIM_MS) {
            attr.onDismiss()
        }
    }
}

/**
 * 创建一个 iOS 风格可拖拽底部面板。
 *
 * @param colors 主题色（Grabber 颜色随主题切换）。
 * @param onClose 关闭回调（点击遮罩或下滑超过阈值后触发）。
 * @param initialDetent 初始吸附档位，默认 medium。
 * @param panelHeight 面板完整高度（dp）；<= 0 时按 [largeHeightRatio] 计算。
 * @param mediumHeightRatio medium 档可见高度占页面高度比例，默认 0.5。
 * @param largeHeightRatio 未显式指定 [panelHeight] 时 large 档高度占比，默认 0.92。
 * @param heightCap 面板高度上限（dp），用于限制弹层最大高度。
 * @param panelBackground 面板底色。
 * @param wrapContent 非模态内联面板是否按内容自适应高度（默认 false）。
 * @param keyboardHeight 面板内输入框上报的键盘高度，默认不避让键盘。
 * @param content 面板业务内容（抓握条之下）。
 */
fun ViewContainer<*, *>.DshBottomSheet(
    colors: () -> DshColorTokens,
    onClose: () -> Unit,
    initialDetent: DshSheetDetent = DshSheetDetent.MEDIUM,
    panelHeight: Float = 0f,
    mediumHeightRatio: Float = 0.5f,
    largeHeightRatio: Float = 0.92f,
    heightCap: Float = 0f,
    panelBackground: () -> Color = { colors().bgLayer1 },
    modal: Boolean = true,
    wrapContent: Boolean = false,
    keyboardHeight: () -> Float = { 0f },
    content: ViewContainer<*, *>.() -> Unit,
) {
    addChild(DshBottomSheetView()) {
        attr {
            this.colors = colors
            this.panelBackground = panelBackground
            this.onDismiss = onClose
            this.modal = modal
            this.initialDetent = initialDetent
            this.panelHeight = panelHeight
            this.mediumHeightRatio = mediumHeightRatio
            this.largeHeightRatio = largeHeightRatio
            this.heightCap = heightCap
            this.wrapContent = wrapContent
            this.keyboardHeight = keyboardHeight().coerceAtLeast(0f)
            this.content = content
        }
    }
}
