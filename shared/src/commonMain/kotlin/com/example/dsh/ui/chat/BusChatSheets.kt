package com.example.dsh.ui.chat

import com.example.dsh.ui.home.BusHomeModelOption
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 对话页的全部底部浮层
 *
 * 一个外壳装七种内容：附件、模型、技能、步骤、单步详情、工作区；遮罩必须压住顶栏，
 * 所以整层挂在 Modal(inWindow = true) 上，面板贴底、宽度铺满页宽，高度取「该浮层内容高」
 * 与「页高 × [BusChatTokens.sheetMaxRatio]」的较小者，放不下时只滚正文，且不出滚动条
 *
 * 高度是算出来的而不是量出来的：每种浮层按行高累加，宁松勿紧，多出的一两像素
 * 落在正文底部留白里，看不出来
 */

internal enum class BusChatSheetKind {
    NONE,
    ATTACH,
    MODEL,
    SKILLS,
    SKILL_DETAIL,
    STEPS,
    STEP_DETAIL,
    WORKSPACE,
    AGENT,
}

// ---------- 外壳尺寸 ----------

/** 面板顶部 1px 高光 */
private const val SHEET_EDGE = 1f

/** 高光左右各内缩 22（等于上圆角），否则 1px 线会戳出圆角 */
private val SHEET_EDGE_INSET: Float get() = busChatPx(44f)

/** 抓握条距顶 10，条高 4 */
private val SHEET_GRAB_TOP: Float get() = busChatPx(10f)
private val SHEET_GRAB_ZONE: Float get() = busChatPx(14f)

/** 标题行 36 */
private val SHEET_TITLE_ROW: Float get() = busChatPx(36f)

/** 标题行与正文之间 2，正文底部留白 22 */
private val SHEET_BODY_TOP: Float get() = busChatPx(2f)
private val SHEET_BODY_BOTTOM: Float get() = busChatPx(22f)

/** 面板固定开销：高光 + 抓握 + 标题 + 正文上下留白 */
private val SHEET_CHROME: Float get() =
    SHEET_EDGE + SHEET_GRAB_ZONE + SHEET_TITLE_ROW + SHEET_BODY_TOP + SHEET_BODY_BOTTOM

/** 上抛阴影 offsetY -12、blur 36 */
private val SHEET_SHADOW_OFFSET_Y: Float get() = busChatPx(-12f)
private val SHEET_SHADOW_RADIUS: Float get() = busChatPx(36f)

// ---------- 各浮层行高 ----------

/** 附件三块入口：上下 12/10 + 40 圆底 + 7 + 一行 11.5 字，留两像素余量 */
private val SHEET_TILE_HEIGHT: Float get() = busChatPx(88f)
private val SHEET_TILE_GAP: Float get() = busChatPx(10f)

/** 技能行 11 + 11 + 一行 13 字；四行正好落在 168 的限高内 */
private val SHEET_SKILL_ROW: Float get() = busChatPx(41f)

/** 模型面板：头部一行 36，努力档 chips 行 44，模型行 13 + 13 + 一行 13.5 字 */
private val SHEET_MODEL_HEAD: Float get() = busChatPx(36f)
private val SHEET_EFFORT_ROW: Float get() = busChatPx(44f)
private val SHEET_MODEL_ROW: Float get() = busChatPx(46f)

/** 步骤行 8 + 8 + 一行 13 字 */
private val SHEET_STEP_ROW: Float get() = busChatPx(35f)

/** 工作区行 12 + 12 + 一行 14 字；底部大按钮 44 + 上间距 14 */
private val SHEET_WORKSPACE_ROW: Float get() = busChatPx(44f)
private val SHEET_BIG_BUTTON: Float get() = busChatPx(58f)

/** 空态行高 56 */
private val SHEET_EMPTY_ROW: Float get() = busChatPx(56f)

/** 选中行底色向左右各探出 10、圆角 12 */
private val SHEET_SELECT_BLEED: Float get() = busChatPx(10f)
private val SHEET_SELECT_RADIUS: Float get() = busChatPx(12f)

/** 单步详情：正文 13.5 / 1.75，段间距 9，代码块行 20 */
private val SHEET_DETAIL_FONT: Float get() = busChatPx(13.5f)
private val SHEET_DETAIL_LINE: Float get() = busChatPx(23.6f)
private val SHEET_DETAIL_GAP: Float get() = busChatPx(9f)
private val SHEET_CODE_LINE: Float get() = busChatPx(20f)

/**
 * 对话页底部浮层宿主
 *
 * 页面只负责给出当前 [kind] 与数据，开关、选中与回退都走回调；面板内容为空时
 * 各自显示一句空态，不画骨架
 *
 * [onSelectStep] 是追加的默认参数：STEPS 行右侧画了 chevron，但本页还没有单步跳转，
 * 页面不传就是纯展示，传了就能进详情
 */
internal fun ViewContainer<*, *>.BusChatSheetHost(
    kind: BusChatSheetKind,
    palette: BusChatPalette,
    pageWidth: Float,
    pageHeight: Float,
    skills: List<BusChatSkill>,
    skillsLoading: Boolean,
    skillsError: String,
    skillContentTitle: String,
    skillContentBody: String,
    skillContentLoading: Boolean,
    skillContentError: String,
    models: List<BusHomeModelOption>,
    modelId: String,
    effort: String,
    steps: List<BusChatStep>,
    stepsTitle: String = "",
    stepIndex: Int,
    workspaces: List<BusChatWorkspace>,
    workspacePath: String,
    agents: List<BusChatAgentOption>,
    agentKey: String,
    onClose: () -> Unit,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFile: () -> Unit,
    onSelectSkill: (String) -> Unit,
    onReadSkill: (BusChatSkill) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onBackStep: () -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onAddWorkspace: () -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectStep: (Int) -> Unit = {},
) {
    if (kind == BusChatSheetKind.NONE) return
    val bodyWidth = (pageWidth - BusChatTokens.sheetSide * 2f).coerceAtLeast(0f)
    val step = steps.getOrNull(stepIndex)
    val panelHeight = busChatSheetPanelHeight(
        kind = kind,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        skills = skills,
        models = models,
        modelId = modelId,
        steps = steps,
        stepIndex = stepIndex,
        workspaces = workspaces,
        agents = agents,
    )

    Modal(inWindow = true) {
        attr {
            // 与确认弹窗同一规则：inWindow Modal 默认铺满 activity，不要再锚到父容器
            backgroundColor(palette.scrim)
        }
        // 整屏点击捕获层：点遮罩关闭；面板挂在它之后，点面板不会走到这里
        View {
            attr { absolutePositionAllZero() }
            event { click { onClose() } }
        }
        View {
            attr {
                positionType(FlexPositionType.ABSOLUTE)
                left(busChatPx(0f))
                bottom(busChatPx(0f))
                width(pageWidth)
                height(panelHeight)
                flexDirectionColumn()
                borderRadius(BusChatTokens.sheetRadius, BusChatTokens.sheetRadius, 0f, 0f)
                backgroundColor(palette.sheet)
                boxShadow(
                    BoxShadow(
                        0f,
                        SHEET_SHADOW_OFFSET_Y,
                        SHEET_SHADOW_RADIUS,
                        BusChatTokens.overlayShadow,
                    ),
                )
            }
            // 面板空白处的点击就地消费掉，避免穿透到遮罩引起误关闭
            event { click { } }
            // 顶部 1px 高光：宽度 pageWidth - 44，居中
            View {
                attr {
                    alignSelfCenter()
                    width((pageWidth - SHEET_EDGE_INSET).coerceAtLeast(0f))
                    height(SHEET_EDGE)
                    backgroundColor(palette.surfaceBorder)
                }
            }
            // 抓握条 34×4，圆角 2，顶部 10，水平居中
            View {
                attr {
                    height(SHEET_GRAB_ZONE)
                    paddingTop(SHEET_GRAB_TOP)
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(BusChatTokens.grabWidth, BusChatTokens.grabHeight)
                        borderRadius(busChatPx(2f))
                        backgroundColor(palette.faint)
                        opacity(0.6f)
                    }
                }
            }
            BusChatSheetTitleRow(
                title = busChatSheetTitle(kind, steps, step, stepsTitle, skillContentTitle),
                showBack = kind == BusChatSheetKind.STEP_DETAIL,
                palette = palette,
                onBackStep = onBackStep,
                onClose = onClose,
            )
            // 面板正文：左右 16、底部 22，滚动条必须隐藏
            Scroller {
                attr {
                    flex(1f)
                    showScrollerIndicator(false)
                }
                View {
                    attr {
                        width(bodyWidth)
                        marginLeft(BusChatTokens.sheetSide)
                        paddingTop(SHEET_BODY_TOP)
                        paddingBottom(SHEET_BODY_BOTTOM)
                        flexDirectionColumn()
                    }
                    when (kind) {
                        BusChatSheetKind.ATTACH -> {
                            BusChatAttachTiles(
                                palette = palette,
                                width = bodyWidth,
                                onPickCamera = onPickCamera,
                                onPickGallery = onPickGallery,
                                onPickFile = onPickFile,
                            )
                            View {
                                attr {
                                    width(bodyWidth)
                                    height(SHEET_TILE_GAP)
                                }
                            }
                            BusChatSkillList(
                                skills = skills,
                                palette = palette,
                                width = bodyWidth,
                                loading = skillsLoading,
                                error = skillsError,
                                onSelectSkill = onSelectSkill,
                                onReadSkill = onReadSkill,
                            )
                        }

                        BusChatSheetKind.MODEL -> BusChatModelBody(
                            models = models,
                            modelId = modelId,
                            effort = effort,
                            palette = palette,
                            onSelectModel = onSelectModel,
                            onSelectEffort = onSelectEffort,
                        )

                        BusChatSheetKind.SKILLS ->
                            BusChatSkillList(
                                skills = skills,
                                palette = palette,
                                width = bodyWidth,
                                loading = skillsLoading,
                                error = skillsError,
                                onSelectSkill = onSelectSkill,
                                onReadSkill = onReadSkill,
                            )

                        BusChatSheetKind.SKILL_DETAIL -> BusChatSkillContentBody(
                            body = skillContentBody,
                            loading = skillContentLoading,
                            error = skillContentError,
                            palette = palette,
                            width = bodyWidth,
                        )

                        BusChatSheetKind.STEPS ->
                            BusChatStepList(steps, palette, onSelectStep)

                        BusChatSheetKind.STEP_DETAIL ->
                            BusChatStepDetail(step, palette)

                        BusChatSheetKind.WORKSPACE -> BusChatWorkspaceBody(
                            workspaces = workspaces,
                            workspacePath = workspacePath,
                            palette = palette,
                            onSelectWorkspace = onSelectWorkspace,
                            onAddWorkspace = onAddWorkspace,
                        )

                        BusChatSheetKind.AGENT -> BusChatAgentBody(
                            agents = agents,
                            agentKey = agentKey,
                            palette = palette,
                            onSelectAgent = onSelectAgent,
                        )

                        BusChatSheetKind.NONE -> Unit
                    }
                }
            }
        }
    }
}

// ---------- 标题行 ----------

/** 标题行：高 36、左右 16；STEP_DETAIL 左侧多一枚返回键 */
private fun ViewContainer<*, *>.BusChatSheetTitleRow(
    title: String,
    showBack: Boolean,
    palette: BusChatPalette,
    onBackStep: () -> Unit,
    onClose: () -> Unit,
) {
    View {
        attr {
            height(SHEET_TITLE_ROW)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(BusChatTokens.sheetSide)
            paddingRight(BusChatTokens.sheetSide)
        }
        if (showBack) {
            View {
                attr {
                    size(BusChatTokens.roundKey, BusChatTokens.roundKey)
                    alignItemsCenter()
                    justifyContentCenter()
                    marginRight(busChatPx(4f))
                }
                event { click { onBackStep() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("chevron-left.svg"))
                        size(BusChatTokens.glyph, BusChatTokens.glyph)
                        tintColor(palette.ink)
                    }
                }
            }
        }
        Text {
            attr {
                text(title)
                fontSize(BusChatTokens.sheetTitle)
                fontWeightSemiBold()
                color(palette.ink)
                flex(1f)
                lines(1)
                textOverFlowTail()
            }
        }
        View {
            attr {
                size(BusChatTokens.roundKey, BusChatTokens.roundKey)
                alignItemsCenter()
                justifyContentCenter()
                marginLeft(busChatPx(4f))
            }
            event { click { onClose() } }
            Image {
                attr {
                    src(ImageUri.commonAssets("x.svg"))
                    size(BusChatTokens.glyph, BusChatTokens.glyph)
                    tintColor(palette.mut)
                }
            }
        }
    }
}

/** 标题：附件借用设计稿原句，步骤按条数拼，详情用步骤标签 */
private fun busChatSheetTitle(
    kind: BusChatSheetKind,
    steps: List<BusChatStep>,
    step: BusChatStep?,
    stepsTitle: String,
    skillTitle: String,
): String = when (kind) {
    BusChatSheetKind.NONE -> ""
    BusChatSheetKind.ATTACH -> "添加到这条消息"
    BusChatSheetKind.MODEL -> "选择模型"
    BusChatSheetKind.SKILLS -> "技能"
    BusChatSheetKind.SKILL_DETAIL -> skillTitle.ifBlank { "技能正文" }
    BusChatSheetKind.STEPS -> stepsTitle.ifBlank {
        if (steps.isEmpty()) "步骤" else "查看 ${steps.size} 个步骤"
    }
    BusChatSheetKind.STEP_DETAIL -> step?.label?.ifBlank { "步骤详情" } ?: "步骤详情"
    BusChatSheetKind.WORKSPACE -> "最近的工作区"
    BusChatSheetKind.AGENT -> "选择 Agent"
}

// ---------- 附件 ----------

/** 拍照 / 照片 / 文件：三块等宽 tile，间距 10 */
private fun ViewContainer<*, *>.BusChatAttachTiles(
    palette: BusChatPalette,
    width: Float,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFile: () -> Unit,
) {
    View {
        attr {
            width(width)
            flexDirectionRow()
        }
        BusChatTile("camera.svg", "拍照", palette, 0f, onPickCamera)
        BusChatTile("gallery.svg", "照片", palette, SHEET_TILE_GAP, onPickGallery)
        BusChatTile("file.svg", "文件", palette, SHEET_TILE_GAP, onPickFile)
    }
}

private fun ViewContainer<*, *>.BusChatTile(
    icon: String,
    label: String,
    palette: BusChatPalette,
    gap: Float,
    onClick: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
            alignItemsCenter()
            paddingTop(busChatPx(12f))
            paddingBottom(busChatPx(10f))
            borderRadius(busChatPx(12f))
            backgroundColor(palette.field)
            border(Border(1f, BorderStyle.SOLID, palette.line))
            if (gap > 0f) marginLeft(gap)
        }
        event { click { onClick() } }
        View {
            attr {
                size(busChatPx(40f), busChatPx(40f))
                borderRadius(busChatPx(20f))
                backgroundColor(palette.surface)
                alignItemsCenter()
                justifyContentCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets(icon))
                    size(busChatPx(20f), busChatPx(20f))
                    tintColor(palette.deep)
                }
            }
        }
        Text {
            attr {
                text(label)
                fontSize(busChatPx(11.5f))
                fontWeightSemiBold()
                color(palette.ink2)
                marginTop(busChatPx(7f))
            }
        }
    }
}

// ---------- 技能 ----------

/**
 * 技能列表：限高 168、可滚动、不出滚动条；超过四行才挂内层滚动，
 * 短列表直接平铺，避免多余的可滚区域抢手势
 */
private fun ViewContainer<*, *>.BusChatSkillList(
    skills: List<BusChatSkill>,
    palette: BusChatPalette,
    width: Float,
    loading: Boolean,
    error: String,
    onSelectSkill: (String) -> Unit,
    onReadSkill: (BusChatSkill) -> Unit,
) {
    if (skills.isEmpty()) {
        View {
            attr {
                width(width)
                height(SHEET_EMPTY_ROW)
                justifyContentCenter()
            }
            Text {
                attr {
                    text(
                        when {
                            loading -> "正在读取技能…"
                            error.isNotBlank() -> error
                            else -> "这个 Agent 没有上报技能"
                        },
                    )
                    fontSize(busChatPx(12.5f))
                    color(palette.mut)
                }
            }
        }
        return
    }
    if (skills.size * SHEET_SKILL_ROW > BusChatTokens.skillListMax) {
        Scroller {
            attr {
                width(width)
                height(BusChatTokens.skillListMax)
                showScrollerIndicator(false)
            }
            View {
                attr {
                    width(width)
                    flexDirectionColumn()
                }
                skills.forEachIndexed { index, skill ->
                    BusChatSkillRow(
                        skill = skill,
                        palette = palette,
                        last = index == skills.lastIndex,
                        onSelectSkill = onSelectSkill,
                        onReadSkill = onReadSkill,
                    )
                }
            }
        }
    } else {
        View {
            attr {
                width(width)
                flexDirectionColumn()
            }
            skills.forEachIndexed { index, skill ->
                BusChatSkillRow(
                    skill = skill,
                    palette = palette,
                    last = index == skills.lastIndex,
                    onSelectSkill = onSelectSkill,
                    onReadSkill = onReadSkill,
                )
            }
        }
    }
}

/** readSkill 的正文：加载、错误、空正文各一句，正文按行铺开，由外层 Scroller 兜住 */
private fun ViewContainer<*, *>.BusChatSkillContentBody(
    body: String,
    loading: Boolean,
    error: String,
    palette: BusChatPalette,
    width: Float,
) {
    Text {
        attr {
            text(
                when {
                    loading -> "正在读取技能正文…"
                    error.isNotBlank() -> error
                    body.isBlank() -> "这个技能没有正文"
                    else -> body
                },
            )
            fontSize(busChatPx(13f))
            lineHeight(busChatPx(20f))
            color(if (error.isNotBlank()) palette.mut else palette.deep)
            width(width)
            lines(Int.MAX_VALUE)
        }
    }
}

/** 技能行：/命令名 13/600 + 说明 12 深灰，底部一条发丝线（最后一行无） */
private fun ViewContainer<*, *>.BusChatSkillRow(
    skill: BusChatSkill,
    palette: BusChatPalette,
    last: Boolean,
    onSelectSkill: (String) -> Unit,
    onReadSkill: (BusChatSkill) -> Unit,
) {
    View {
        attr { flexDirectionColumn() }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                paddingTop(busChatPx(11f))
                paddingBottom(busChatPx(11f))
            }
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    flex(1f)
                }
                event { click { onSelectSkill(skill.name) } }
                Text {
                    attr {
                        text("/${skill.name}")
                        fontSize(busChatPx(13f))
                        fontWeightSemiBold()
                        color(palette.ink)
                    }
                }
                Text {
                    attr {
                        text(skill.description)
                        fontSize(busChatPx(12f))
                        color(palette.deep)
                        marginLeft(busChatPx(8f))
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
            if (skill.hasBody) {
                Text {
                    attr {
                        text("查看")
                        fontSize(busChatPx(11f))
                        color(palette.mut)
                        marginLeft(busChatPx(8f))
                    }
                    event { click { onReadSkill(skill) } }
                }
            }
        }
        if (!last) {
            View {
                attr {
                    height(busChatPx(1f))
                    backgroundColor(palette.line)
                }
            }
        }
    }
}

// ---------- 模型 ----------

/**
 * 模型面板：推理等级一行、努力档 chips、模型列表
 *
 * [BusHomeModelOption] 不带 provider，副标题行先不画；选中态只换底色与右侧对勾
 */
private fun ViewContainer<*, *>.BusChatModelBody(
    models: List<BusHomeModelOption>,
    modelId: String,
    effort: String,
    palette: BusChatPalette,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
) {
    View {
        attr {
            height(SHEET_MODEL_HEAD)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text("推理等级")
                fontSize(busChatPx(13.5f))
                fontWeightSemiBold()
                color(palette.ink)
            }
        }
        View {
            attr { flex(1f) }
        }
        Text {
            attr {
                text(busChatEffortLabel(effort).ifBlank { "默认" })
                fontSize(busChatPx(12.5f))
                color(palette.deep)
            }
        }
    }
    val efforts = busChatEfforts(models, modelId)
    if (efforts.isNotEmpty()) {
        View {
            attr {
                height(SHEET_EFFORT_ROW)
                flexDirectionRow()
                alignItemsCenter()
            }
            efforts.forEachIndexed { index, item ->
                val selected = item.equals(effort, ignoreCase = true)
                View {
                    attr {
                        height(busChatPx(28f))
                        paddingLeft(busChatPx(14f))
                        paddingRight(busChatPx(14f))
                        borderRadius(busChatPx(14f))
                        alignItemsCenter()
                        justifyContentCenter()
                        if (index > 0) marginLeft(busChatPx(8f))
                        if (selected) {
                            backgroundColor(palette.solid)
                            border(Border(1f, BorderStyle.SOLID, palette.solid))
                        } else {
                            backgroundColor(palette.surface)
                            border(Border(1f, BorderStyle.SOLID, palette.line))
                        }
                    }
                    event { click { onSelectEffort(item) } }
                    Text {
                        attr {
                            text(busChatEffortLabel(item))
                            fontSize(busChatPx(12.5f))
                            if (selected) fontWeightSemiBold()
                            color(if (selected) palette.onSolid else palette.ink2)
                        }
                    }
                }
            }
        }
    }
    models.forEachIndexed { index, model ->
        val selected = model.id == modelId
        View {
            attr { flexDirectionColumn() }
            View {
                attr {
                    positionType(FlexPositionType.RELATIVE)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingTop(busChatPx(13f))
                    paddingBottom(busChatPx(13f))
                }
                event { click { onSelectModel(model.id) } }
                // 选中底色左右各探出 10，压在文字下面
                if (selected) {
                    View {
                        attr {
                            positionType(FlexPositionType.ABSOLUTE)
                            left(-SHEET_SELECT_BLEED)
                            right(-SHEET_SELECT_BLEED)
                            top(busChatPx(0f))
                            bottom(busChatPx(0f))
                            borderRadius(SHEET_SELECT_RADIUS)
                            backgroundColor(palette.chip)
                        }
                    }
                }
                Text {
                    attr {
                        text(model.displayName.ifBlank { model.id })
                        fontSize(busChatPx(13.5f))
                        fontWeightSemiBold()
                        color(palette.ink)
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
                if (selected) {
                    Image {
                        attr {
                            src(ImageUri.commonAssets("check.svg"))
                            size(busChatPx(16f), busChatPx(16f))
                            tintColor(palette.ink)
                            marginLeft(busChatPx(8f))
                        }
                    }
                }
            }
            if (!selected && index != models.lastIndex) {
                View {
                    attr {
                        height(busChatPx(1f))
                        backgroundColor(palette.line)
                    }
                }
            }
        }
    }
}

/** 当前模型上报的努力档；模型 id 对不上时不猜，宁可空着 */
private fun busChatEfforts(models: List<BusHomeModelOption>, modelId: String): List<String> {
    val current = models.firstOrNull { it.id == modelId } ?: models.firstOrNull { modelId.isBlank() }
    return current?.efforts.orEmpty()
}

// ---------- 步骤 ----------

/**
 * 步骤列表：纯列表，没有分割线也没有底色
 *
 * 右侧 chevron 表示可以进详情，走 [onSelectStep]；页面没接线时点到也无副作用
 */
private fun ViewContainer<*, *>.BusChatStepList(
    steps: List<BusChatStep>,
    palette: BusChatPalette,
    onSelectStep: (Int) -> Unit,
) {
    if (steps.isEmpty()) {
        View {
            attr { height(SHEET_EMPTY_ROW) }
            Text {
                attr {
                    text("这一轮还没有工具步骤")
                    fontSize(busChatPx(12.5f))
                    color(palette.mut)
                }
            }
        }
        return
    }
    steps.forEachIndexed { index, step ->
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                paddingTop(busChatPx(8f))
                paddingBottom(busChatPx(8f))
            }
            event { click { onSelectStep(index) } }
            Text {
                attr {
                    text(step.label)
                    fontSize(busChatPx(13f))
                    fontWeightSemiBold()
                    color(palette.ink)
                }
            }
            Text {
                attr {
                    text(step.summary)
                    fontSize(busChatPx(13f))
                    color(palette.deep)
                    marginLeft(busChatPx(8f))
                    flex(1f)
                    lines(1)
                    textOverFlowTail()
                }
            }
            // 失败只给一枚红叹号，不写「失败」二字
            if (step.failed) {
                Image {
                    attr {
                        src(ImageUri.commonAssets("warning-outline.svg"))
                        size(busChatPx(16f), busChatPx(16f))
                        tintColor(palette.bad)
                        marginLeft(busChatPx(6f))
                    }
                }
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-right.svg"))
                    size(busChatPx(16f), busChatPx(16f))
                    tintColor(palette.mut)
                    marginLeft(busChatPx(6f))
                }
            }
        }
    }
}

// ---------- 单步详情 ----------

/** 单步详情：内容全落在白卡里，正文按 [busChatBlocks] 分段渲染 */
private fun ViewContainer<*, *>.BusChatStepDetail(
    step: BusChatStep?,
    palette: BusChatPalette,
) {
    val blocks = step?.let { busChatBlocks(it.summary) }.orEmpty()
    if (blocks.isEmpty()) {
        View {
            attr { height(SHEET_EMPTY_ROW) }
            Text {
                attr {
                    text("这一步没有更多细节")
                    fontSize(busChatPx(12.5f))
                    color(palette.mut)
                }
            }
        }
        return
    }
    View {
        attr {
            flexDirectionColumn()
            borderRadius(busChatPx(14f))
            backgroundColor(palette.surface)
            border(Border(1f, BorderStyle.SOLID, palette.line))
            padding(busChatPx(12f), busChatPx(13f), busChatPx(12f), busChatPx(13f))
        }
        blocks.forEach { block -> BusChatDetailBlock(block, palette) }
    }
}

private fun ViewContainer<*, *>.BusChatDetailBlock(
    block: BusChatBlock,
    palette: BusChatPalette,
) {
    when (block) {
        is BusChatBlock.Paragraph -> Text {
            attr {
                text(block.text)
                fontSize(SHEET_DETAIL_FONT)
                lineHeight(SHEET_DETAIL_LINE)
                color(palette.ink2)
                marginBottom(SHEET_DETAIL_GAP)
            }
        }

        is BusChatBlock.Heading -> Text {
            attr {
                text(block.text)
                fontSize(SHEET_DETAIL_FONT)
                fontWeightSemiBold()
                lineHeight(SHEET_DETAIL_LINE)
                color(palette.ink)
                marginBottom(SHEET_DETAIL_GAP)
            }
        }

        is BusChatBlock.Bullets -> block.items.forEach { item ->
            View {
                attr {
                    flexDirectionRow()
                    marginBottom(busChatPx(5f))
                }
                View {
                    attr {
                        size(BusChatTokens.bulletDot, BusChatTokens.bulletDot)
                        borderRadius(busChatPx(1f))
                        backgroundColor(palette.faint)
                        marginTop(busChatPx(10f))
                    }
                }
                Text {
                    attr {
                        text(item)
                        fontSize(SHEET_DETAIL_FONT)
                        lineHeight(SHEET_DETAIL_LINE)
                        color(palette.ink2)
                        marginLeft(busChatPx(8f))
                        flex(1f)
                    }
                }
            }
        }

        is BusChatBlock.Numbered -> block.items.forEachIndexed { index, item ->
            View {
                attr {
                    flexDirectionRow()
                    marginBottom(busChatPx(5f))
                }
                Text {
                    attr {
                        text("${index + 1}.")
                        fontSize(SHEET_DETAIL_FONT)
                        lineHeight(SHEET_DETAIL_LINE)
                        color(palette.mut)
                        marginRight(busChatPx(6f))
                    }
                }
                Text {
                    attr {
                        text(item)
                        fontSize(SHEET_DETAIL_FONT)
                        lineHeight(SHEET_DETAIL_LINE)
                        color(palette.ink2)
                        flex(1f)
                    }
                }
            }
        }

        is BusChatBlock.Code -> View {
            attr {
                flexDirectionColumn()
                borderRadius(BusChatTokens.codeRadius)
                backgroundColor(palette.field)
                padding(busChatPx(10f), busChatPx(12f), busChatPx(10f), busChatPx(12f))
                marginBottom(SHEET_DETAIL_GAP)
            }
            Text {
                attr {
                    text(block.text)
                    fontSize(BusChatTokens.codeSize)
                    lineHeight(SHEET_CODE_LINE)
                    color(palette.deep)
                }
            }
        }
    }
}

// ---------- 工作区 ----------

/** 工作区：单选行 + 底部「添加工作区」大按钮 */
private fun ViewContainer<*, *>.BusChatWorkspaceBody(
    workspaces: List<BusChatWorkspace>,
    workspacePath: String,
    palette: BusChatPalette,
    onSelectWorkspace: (String) -> Unit,
    onAddWorkspace: () -> Unit,
) {
    if (workspaces.isEmpty()) {
        View {
            attr {
                height(SHEET_EMPTY_ROW)
                justifyContentCenter()
            }
            Text {
                attr {
                    text("电脑上还没有项目")
                    fontSize(busChatPx(12.5f))
                    color(palette.mut)
                }
            }
        }
    } else {
        workspaces.forEachIndexed { index, workspace ->
            val selected = workspace.path == workspacePath
            View {
                attr { flexDirectionColumn() }
                View {
                    attr {
                        positionType(FlexPositionType.RELATIVE)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingTop(busChatPx(12f))
                        paddingBottom(busChatPx(12f))
                    }
                    event { click { onSelectWorkspace(workspace.path) } }
                    if (selected) {
                        View {
                            attr {
                                positionType(FlexPositionType.ABSOLUTE)
                                left(-SHEET_SELECT_BLEED)
                                right(-SHEET_SELECT_BLEED)
                                top(busChatPx(0f))
                                bottom(busChatPx(0f))
                                borderRadius(SHEET_SELECT_RADIUS)
                                backgroundColor(palette.chip)
                            }
                        }
                    }
                    // 18 圆 radio：未选 1.5px 灰描边，选中用 5px 粗边留出小孔
                    View {
                        attr {
                            size(busChatPx(18f), busChatPx(18f))
                            borderRadius(busChatPx(9f))
                            if (selected) {
                                border(Border(5f, BorderStyle.SOLID, palette.solid))
                            } else {
                                border(Border(1.5f, BorderStyle.SOLID, palette.faint))
                            }
                        }
                    }
                    Text {
                        attr {
                            text(workspace.name.ifBlank { workspace.path })
                            fontSize(busChatPx(14f))
                            color(palette.ink)
                            marginLeft(busChatPx(10f))
                            flex(1f)
                            lines(1)
                            textOverFlowTail()
                        }
                    }
                }
                if (!selected && index != workspaces.lastIndex) {
                    View {
                        attr {
                            height(busChatPx(1f))
                            backgroundColor(palette.line)
                        }
                    }
                }
            }
        }
    }
    View {
        attr {
            height(busChatPx(44f))
            marginTop(busChatPx(14f))
            borderRadius(busChatPx(14f))
            backgroundColor(palette.field)
            alignItemsCenter()
            justifyContentCenter()
        }
        event { click { onAddWorkspace() } }
        Text {
            attr {
                text("添加工作区")
                fontSize(busChatPx(13.5f))
                fontWeightSemiBold()
                color(palette.ink2)
            }
        }
    }
}

// ---------- Agent 选择 ----------

/** Agent 行高 58，底部说明 44 */
private val SHEET_AGENT_ROW: Float get() = busChatPx(58f)
private val SHEET_AGENT_NOTE: Float get() = busChatPx(44f)

private fun busChatAgentBlockHeight(agents: List<BusChatAgentOption>): Float =
    (if (agents.isEmpty()) SHEET_EMPTY_ROW else agents.size * SHEET_AGENT_ROW) + SHEET_AGENT_NOTE

/** 选择 Agent：单选列表，头像 20、名字 13.5/600、副标题 11.5 灰 */
private fun ViewContainer<*, *>.BusChatAgentBody(
    agents: List<BusChatAgentOption>,
    agentKey: String,
    palette: BusChatPalette,
    onSelectAgent: (String) -> Unit,
) {
    if (agents.isEmpty()) {
        View {
            attr {
                height(SHEET_EMPTY_ROW)
                justifyContentCenter()
            }
            Text {
                attr {
                    text("电脑上没有可启动的 Agent")
                    fontSize(busChatPx(12.5f))
                    color(palette.mut)
                }
            }
        }
        return
    }
    agents.forEach { agent ->
        val selected = agent.key == agentKey
        View {
            attr {
                height(SHEET_AGENT_ROW)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(busChatPx(10f))
                paddingRight(busChatPx(10f))
                if (selected) {
                    backgroundColor(palette.chip)
                    borderRadius(busChatPx(12f))
                }
            }
            event { click { onSelectAgent(agent.key) } }
            View {
                attr {
                    size(busChatPx(18f), busChatPx(18f))
                    borderRadius(busChatPx(9f))
                    border(
                        Border(
                            if (selected) busChatPx(5f) else busChatPx(1.5f),
                            BorderStyle.SOLID,
                            if (selected) palette.solid else palette.faint,
                        ),
                    )
                }
            }
            View {
                attr {
                    size(busChatPx(20f), busChatPx(20f))
                    borderRadius(busChatPx(10f))
                    backgroundColor(palette.field)
                    marginLeft(busChatPx(10f))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets(agent.avatarAsset))
                        size(busChatPx(20f), busChatPx(20f))
                        if (com.example.dsh.ui.home.busAgentIconIsPhoto(agent.avatarAsset)) {
                            resizeCover()
                        } else {
                            resizeContain()
                        }
                    }
                }
            }
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    marginLeft(busChatPx(10f))
                }
                Text {
                    attr {
                        text(agent.name)
                        fontSize(busChatPx(13.5f))
                        fontWeightSemiBold()
                        color(palette.ink)
                        lines(1)
                        textOverFlowTail()
                    }
                }
                Text {
                    attr {
                        text(agent.subtitle + if (selected) " · 上次在这台电脑上使用" else "")
                        fontSize(busChatPx(11.5f))
                        color(palette.mut)
                        marginTop(busChatPx(2f))
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
        }
    }
    Text {
        attr {
            text("只列出已启用、可用、且允许从手机启动的 Agent；未安装或已停用的请在电脑端处理。第一次从手机对某个 Agent 发起任务前会先说明授权范围。")
            fontSize(busChatPx(11f))
            lineHeight(busChatPx(11f) * 1.6f)
            color(palette.mut)
            marginTop(busChatPx(10f))
            lines(Int.MAX_VALUE)
        }
    }
}

// ---------- 高度 ----------

/** 面板总高：内容高与「页高 × 0.74」取小，正文放不下时由外层 Scroller 兜住 */
private fun busChatSheetPanelHeight(
    kind: BusChatSheetKind,
    pageWidth: Float,
    pageHeight: Float,
    skills: List<BusChatSkill>,
    models: List<BusHomeModelOption>,
    modelId: String,
    steps: List<BusChatStep>,
    stepsTitle: String = "",
    stepIndex: Int,
    workspaces: List<BusChatWorkspace>,
    agents: List<BusChatAgentOption>,
): Float {
    val bodyWidth = (pageWidth - BusChatTokens.sheetSide * 2f).coerceAtLeast(0f)
    val body = when (kind) {
        BusChatSheetKind.ATTACH ->
            SHEET_TILE_HEIGHT + SHEET_TILE_GAP + busChatSkillBlockHeight(skills)

        BusChatSheetKind.MODEL -> busChatModelBlockHeight(models, modelId)
        BusChatSheetKind.SKILLS -> busChatSkillBlockHeight(skills)
        BusChatSheetKind.SKILL_DETAIL -> busChatPx(260f)
        BusChatSheetKind.STEPS -> steps.size * SHEET_STEP_ROW
        BusChatSheetKind.STEP_DETAIL ->
            busChatDetailBlockHeight(steps.getOrNull(stepIndex), bodyWidth)

        BusChatSheetKind.WORKSPACE -> busChatWorkspaceBlockHeight(workspaces)
        BusChatSheetKind.AGENT -> busChatAgentBlockHeight(agents)
        BusChatSheetKind.NONE -> 0f
    }
    val full = (SHEET_CHROME + body).coerceAtLeast(SHEET_CHROME + SHEET_EMPTY_ROW / 2f)
    return full
        .coerceAtMost(pageHeight * BusChatTokens.sheetMaxRatio)
        .coerceAtLeast(SHEET_CHROME)
}

/** 技能块：空态 56，否则按行累加并限高 168 */
private fun busChatSkillBlockHeight(skills: List<BusChatSkill>): Float =
    if (skills.isEmpty()) {
        SHEET_EMPTY_ROW
    } else {
        (skills.size * SHEET_SKILL_ROW).coerceAtMost(BusChatTokens.skillListMax)
    }

/** 模型块：头部 + 努力档行（无档位就不占）+ 每行 44 */
private fun busChatModelBlockHeight(
    models: List<BusHomeModelOption>,
    modelId: String,
): Float {
    val efforts = busChatEfforts(models, modelId)
    return SHEET_MODEL_HEAD +
        (if (efforts.isEmpty()) 0f else SHEET_EFFORT_ROW) +
        models.size * SHEET_MODEL_ROW
}

/** 工作区块：空态 56 或每行 42，再加底部大按钮 */
private fun busChatWorkspaceBlockHeight(workspaces: List<BusChatWorkspace>): Float =
    (if (workspaces.isEmpty()) SHEET_EMPTY_ROW else workspaces.size * SHEET_WORKSPACE_ROW) +
        SHEET_BIG_BUTTON

/**
 * 单步详情高度：按 13.5 号字估每行字数再累加行高
 *
 * 只是给面板定高用，估多一两行只会让正文底部多留一点白，不会截断内容
 */
private fun busChatDetailBlockHeight(step: BusChatStep?, bodyWidth: Float): Float {
    val blocks = step?.let { busChatBlocks(it.summary) }.orEmpty()
    if (blocks.isEmpty()) return SHEET_EMPTY_ROW
    val perLine = ((bodyWidth - busChatPx(26f)) / SHEET_DETAIL_FONT).toInt().coerceAtLeast(6)
    var height = busChatPx(24f)
    for (block in blocks) {
        height += when (block) {
            is BusChatBlock.Paragraph ->
                busChatSheetLines(block.text, perLine) * SHEET_DETAIL_LINE + SHEET_DETAIL_GAP

            is BusChatBlock.Heading -> SHEET_DETAIL_LINE + busChatPx(3f) + SHEET_DETAIL_GAP

            is BusChatBlock.Bullets -> block.items.fold(busChatPx(4f)) { acc, item ->
                acc + busChatSheetLines(item, perLine - 2) * SHEET_DETAIL_LINE + busChatPx(5f)
            }

            is BusChatBlock.Numbered -> block.items.fold(busChatPx(4f)) { acc, item ->
                acc + busChatSheetLines(item, perLine - 2) * SHEET_DETAIL_LINE + busChatPx(5f)
            }

            is BusChatBlock.Code ->
                block.text.split('\n').size * SHEET_CODE_LINE + busChatPx(32f)
        }
    }
    return height
}

/** 一段文字按每行字数折成行数，至少一行 */
private fun busChatSheetLines(text: String, perLine: Int): Int =
    text.split('\n').sumOf { line ->
        ((line.length + perLine - 1) / perLine).coerceAtLeast(1)
    }
