package com.example.dsh.home

import com.example.dsh.base.*
import com.example.dsh.diagnostics.DshLogPageContract
import com.example.dsh.diagnostics.DshLogQuery
import com.example.dsh.diagnostics.DshLogPageResult
import com.example.dsh.diagnostics.DshLogWork
import com.example.dsh.infrastructure.*
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NotifyModule
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.ScrollPicker
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 日志查看页（独立路由 Page，替代原先堆叠在主页上的全屏 Modal）。
 *
 * 路由参数：
 * - sessionId：非空为「会话日志」模式（只看该会话）；空为「诊断日志」全局模式
 * - exportDir / connectionMode：导出文件目录、反馈包抬头用的连接模式名
 *
 * 结构：列表态 ↔ 详情态在同一 Page 内切换（vif），时间/级别等筛选全部收
 * 底部 Sheet，页面层级恒为 1。数据源为全局 [DshStreamLog.writeBehind]，
 * 只展示脱敏元数据；会话标题通过路由快照传入，跳回会话通过 NotifyModule 解耦。
 */
@Page("dsh_log")
internal class DshLogPage : BasePager() {

    // ---- 路由参数 ----
    private var sessionId = ""
    private var exportDir = ""
    private var connectionMode = ""
    private val sessionTitles = mutableMapOf<String, String>()
    private val allMode: Boolean get() = sessionId.isEmpty()

    // ---- 数据 ----
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var queryWork: DshLogWork<DshLogPageResult>? = null
    private var exportWork: DshLogWork<String>? = null
    private var clearWork: DshLogWork<Unit>? = null
    private var queryVersion = -1L
    private var queryGeneration = 0
    private var pageOffset by observable(0)
    private var loading by observable(false)
    private var loadError by observable("")
    private var lastExportPath by observable("")
    private var alive = true
    private var filterRevision = 0
    private var jumpRef: CallbackRef? = null
    private var jumpRequest = ""
    private var jumping by observable(false)
    private val logView by observableList<LogEvent>()
    private var logTotal by observable(0)
    private val sessionOptions by observableList<String>()

    // ---- 筛选（级别为多选集合：空集 = 不过滤） ----
    private var selectedLevels by observable<Set<LogLevel>>(emptySet())
    // 级别计数（时间/会话/搜索词过滤后、级别过滤前的口径），供级别 Sheet 行展示
    private var levelCounts by observable<Map<LogLevel, Int>>(emptyMap())
    // 列表时间范围始终由 customStartMs/customEndMs 决定，默认当天 00:00 - 23:59
    private var customStartMs by observable<Long?>(null)
    private var customEndMs by observable<Long?>(null)
    private var selectedSessions by observable<Set<String>>(emptySet())
    private var keyword by observable("")

    // ---- UI 状态 ----
    private var detailEntry by observable<LogEvent?>(null)
    private var sheet by observable(SheetKind.NONE)
    private var clearVisible by observable(false)
    private var clearing by observable(false)
    private var exporting by observable(false)
    private var feedbackExporting by observable(false)

    // ---- 自定义时间 picker ----
    private var pickerTargetStart by observable(true)
    // 滚轮重建令牌：ScrollPicker 的 defaultIndex 仅在创建时生效，
    // tab 切换 / 快捷 chip 改值后 ++，配合奇偶 vif 强制滚轮按新值重建
    private var pickerNonce by observable(0)
    // 时间 Sheet 草稿：打开时从 custom* 拷贝，确定时一次性提交到生效条件；取消即作废
    private var draftStartMs by observable<Long?>(null)
    private var draftEndMs by observable<Long?>(null)
    private var pkYear by observable(0)
    private var pkMonth by observable(1)
    private var pkDay by observable(1)
    private var pkHour by observable(0)
    private var pkMinute by observable(0)

    // ---- 跟随底部 ----
    private var scrollerRef: ViewRef<ListView<*, *>>? = null
    private var followBottom by observable(true)
    private var newCount by observable(0)
    private var lastMaxSeq = 0L
    private var polling = false

    private enum class SheetKind { NONE, TIME, LEVEL, SESSION, EXPORT }

    private val backCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            when {
                clearVisible -> clearVisible = false
                sheet != SheetKind.NONE -> sheet = SheetKind.NONE
                detailEntry != null -> closeDetail()
                else -> acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            }
        }
    }

    override fun created() {
        super.created()
        sessionId = pageData.params.optString("sessionId")
        exportDir = pageData.params.optString("exportDir")
        connectionMode = pageData.params.optString("connectionMode")
        jumpRef = acquireModule<NotifyModule>(NotifyModule.MODULE_NAME).addNotify(DshLogPageContract.EVENT_JUMP_RESULT) { data ->
            if (!jumping || data?.optString(DshLogPageContract.KEY_REQUEST) != jumpRequest) return@addNotify
            jumping = false
            if (data.optBoolean("ok")) acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            else bridgeModule.toast(LogSanitizer.sanitize(data.optString("message")))
        }
        val titleItems = pageData.params.optJSONArray(DshLogPageContract.KEY_SESSION_TITLES)
        if (titleItems != null) {
            for (index in 0 until titleItems.length()) {
                val item = titleItems.optJSONObject(index) ?: continue
                val id = item.optString(DshLogPageContract.KEY_SESSION_ID)
                if (id.isNotEmpty()) {
                    sessionTitles[id] = item.optString(DshLogPageContract.KEY_SESSION_TITLE).ifEmpty { id }
                }
            }
        }
        getBackPressHandler().addCallback(backCallback)
        applyTodayPreset()
        refresh()
        startFollow()
    }

    override fun pageWillDestroy() {
        alive = false
        polling = false
        workerScope.cancel()
        jumpRef?.let { acquireModule<NotifyModule>(NotifyModule.MODULE_NAME).removeNotify(DshLogPageContract.EVENT_JUMP_RESULT, it) }
        super.pageWillDestroy()
    }

    // ===== 数据：刷新与筛选 =====

    private fun refresh() {
        if (queryWork != null || clearing) return
        val source = DshStreamLog.writeBehind ?: run { loadError = "日志存储尚未就绪"; return }
        val version = source.version()
        if (version == queryVersion) return
        queryVersion = version
        val query = currentQuery()
        val generation = queryGeneration
        val offset = pageOffset
        loading = true
        loadError = ""
        val work = DshLogWork(workerScope) { query.readPage(source, offset, PAGE_SIZE) }
        queryWork = work
        fun receive() {
            if (!alive || queryWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            queryWork = null
            loading = false
            if (generation != queryGeneration) { queryVersion = -1; refresh(); return }
            result.onSuccess { page ->
                logTotal = page.total
                levelCounts = page.levels
                logView.diffUpdate(page.rows) { old, new -> old == new }
                val options = (sessionTitles.keys + page.rows.map { it.sessionId ?: "__mobile__" } + "__mobile__").distinct().sorted()
                if (sessionOptions.toList() != options) { sessionOptions.clear(); sessionOptions.addAll(options) }
                val newest = page.rows.firstOrNull()?.seq ?: 0
                if (lastMaxSeq > 0 && newest > lastMaxSeq) newCount += page.rows.count { it.seq > lastMaxSeq }
                lastMaxSeq = newest
                if (followBottom && pageOffset == 0) newCount = 0
                if (pageOffset > 0 && pageOffset >= page.total) { pageOffset = 0; queryVersion = -1; refresh() }
            }.onFailure { loadError = "读取日志失败：${LogSanitizer.sanitize(it.message.orEmpty())}"; queryVersion = -1 }
        }
        setTimeout(50) { receive() }
    }

    /** 按筛选条件（时间/级别集合/会话/搜索词）重算日志视图；级别计数取级别过滤前的口径。 */
    private fun recompute() {
        pageOffset = 0
        queryGeneration++
        val revision = ++filterRevision
        setTimeout(200) { if (alive && filterRevision == revision) reloadPage() }
    }

    private fun reloadPage() {
        queryGeneration++
        queryVersion = -1
        refresh()
    }

    private fun currentQuery(all: Boolean = false): DshLogQuery = DshLogQuery(
        LogFilter(sessionId = sessionId.takeIf { !allMode },
            sessionIds = if (all) null else selectedSessions.toList(),
            levels = if (all) null else selectedLevels.toList(),
            fromTime = if (all) null else customStartMs, toTime = if (all) null else customEndMs),
        if (all) "" else keyword,
    )

    private fun clearFilters() {
        applyTodayPreset()
        selectedLevels = emptySet()
        selectedSessions = emptySet()
        keyword = ""
        recompute()
    }

    // ===== 时间筛选 =====

    /** 预设区间的时间戳计算：0=今天 00:00-23:59；1=最近 1 小时；2=最近 10 小时。 */
    private fun presetRange(kind: Int): Pair<Long, Long> {
        val now = currentTimeMillis()
        return when (kind) {
            1 -> now - 3_600_000L to now
            2 -> now - 36_000_000L to now
            else -> {
                val s = LogExporter.formatTimestamp(now)
                val y = s.substring(0, 4).toInt()
                val mo = s.substring(5, 7).toInt()
                val d = s.substring(8, 10).toInt()
                LogExporter.parseEpoch(y, mo, d, 0, 0) to LogExporter.parseEpoch(y, mo, d, 23, 59) + 59_999L
            }
        }
    }

    /** 将今天的区间直接写入生效条件（页面初始化 / 清除筛选用，不经过 Sheet）。 */
    private fun applyTodayPreset() {
        val (s, e) = presetRange(0)
        customStartMs = s
        customEndMs = e
    }

    /** Sheet 预设 chip：把区间填入草稿两端并同步滚轮，不立即筛选。 */
    private fun applyPresetToDraft(kind: Int) {
        val (s, e) = presetRange(kind)
        draftStartMs = s
        draftEndMs = e
        syncPickerPkValues(pickerTargetStart)
        pickerNonce++
    }

    /** 打开时间 Sheet：把生效区间拷贝进草稿，滚轮同步到开始端。 */
    private fun openTimeSheet() {
        if (customStartMs == null || customEndMs == null) applyTodayPreset()
        draftStartMs = customStartMs
        draftEndMs = customEndMs
        pickerTargetStart = true
        syncPickerPkValues(true)
        sheet = SheetKind.TIME
    }

    private fun syncPickerPkValues(targetStart: Boolean) {
        val target = if (targetStart) draftStartMs else draftEndMs
        val ms = target ?: currentTimeMillis()
        val s = LogExporter.formatTimestamp(ms)
        pkYear = s.substring(0, 4).toInt()
        pkMonth = s.substring(5, 7).toInt()
        pkDay = s.substring(8, 10).toInt()
        pkHour = s.substring(11, 13).toInt()
        pkMinute = s.substring(14, 16).toInt()
    }

    /** 把滚轮当前值写回正在编辑的草稿端，防止 tab 切换 / 确定时丢失。 */
    private fun commitPickerToDraft() {
        val v = LogExporter.parseEpoch(pkYear, pkMonth, pkDay, pkHour, pkMinute)
        if (pickerTargetStart) draftStartMs = v else draftEndMs = v
    }

    /** 切换开始/结束编辑端：先保存当前端，再把另一端装载进滚轮。 */
    private fun switchPickerTarget(start: Boolean) {
        if (pickerTargetStart == start) return
        commitPickerToDraft()
        pickerTargetStart = start
        syncPickerPkValues(start)
        pickerNonce++
    }

    /** 确定：草稿两端一次性提交到生效条件并重筛；起止选反时自动交换。取消/返回仅关闭 Sheet，草稿作废。 */
    private fun confirmTimeSheet() {
        commitPickerToDraft()
        val s = draftStartMs
        val e = draftEndMs
        if (s != null && e != null && s > e) {
            customStartMs = e
            customEndMs = s
        } else {
            customStartMs = s
            customEndMs = e
        }
        recompute()
        sheet = SheetKind.NONE
    }

    /** 顶部时间 chip 文案：始终展示具体日期时间段，而非“今天”等预设名。 */
    private fun timeChipLabel(): String {
        val s = customStartMs?.let { formatCustomTime(it) } ?: "不限"
        val e = customEndMs?.let { formatCustomTime(it) } ?: "不限"
        return "$s ~ $e"
    }

    /** 级别 chip 文案：空集/全选=全部级别；连续后缀保留阈值语义（仅 X / ≥ X）；单排除“除 X 外”；其余按级别序拼接。 */
    private fun levelChipLabel(): String {
        val sel = selectedLevels
        val ordered = LogLevel.entries
        if (sel.isEmpty() || sel.size == ordered.size) return "全部级别"
        val suffix = ordered.takeLast(sel.size)
        if (suffix.all { it in sel }) {
            return if (sel.size == 1) "仅 ${levelShort(suffix.first())}" else "≥ ${levelShort(suffix.first())}"
        }
        if (sel.size == ordered.size - 1) {
            return "除 ${levelShort(ordered.first { it !in sel })} 外"
        }
        return ordered.filter { it in sel }.joinToString("+") { levelShort(it) }
    }

    private fun levelShort(lv: LogLevel): String = when (lv) {
        LogLevel.DEBUG -> "Debug"
        LogLevel.INFO -> "Info"
        LogLevel.WARN -> "Warn"
        LogLevel.ERROR -> "Error"
    }

    // ===== 详情 =====

    private fun openDetail(entry: LogEvent) {
        detailEntry = LogSanitizer.sanitize(entry)
    }

    private fun closeDetail() {
        detailEntry = null
    }

    private fun copyText(text: String, toast: String) {
        bridgeModule.copyToPasteboard(LogSanitizer.sanitize(text))
        bridgeModule.toast(toast)
    }

    /** 复制单条日志详情（脱敏后的全部字段）到剪贴板。 */
    private fun copyDetailAll(entry: LogEvent) {
        val text = buildString {
            appendLine("DSH 日志详情")
            appendLine("时间：${LogExporter.formatTimestamp(entry.timestamp)}")
            appendLine("序号：${entry.seq}")
            appendLine("级别：${entry.level.name}")
            appendLine("类型：${entry.type}")
            appendLine("会话：${entry.sessionId ?: "-"}")
            appendLine("RPC：${entry.rpcId ?: "-"}")
            appendLine("大小：${entry.size} B")
            appendLine("消息：${entry.message}")
        }
        copyText(text, "已复制")
    }

    private fun jumpToSession(sid: String) {
        if (jumping) return
        jumping = true
        jumpRequest = "$pagerId-${currentTimeMillis()}"
        val requestId = jumpRequest
        acquireModule<NotifyModule>(NotifyModule.MODULE_NAME).postNotify(
            DshLogPageContract.EVENT_JUMP_TO_SESSION,
            JSONObject().apply {
                put(DshLogPageContract.KEY_SESSION_ID, sid)
                put(DshLogPageContract.KEY_OWNER, pageData.params.optString(DshLogPageContract.KEY_OWNER))
                put(DshLogPageContract.KEY_REQUEST, requestId)
            },
        )
        setTimeout(35_000) {
            if (alive && jumping && jumpRequest == requestId) { jumping = false; bridgeModule.toast("跳转超时，请确认连接后重试") }
        }
    }

    private fun sessionTitle(sessionId: String): String = sessionTitles[sessionId] ?: sessionId

    // ===== 跟随底部（轮询） =====

    private fun startFollow() {
        polling = false
        followBottom = true
        newCount = 0
        lastMaxSeq = logView.maxOfOrNull { it.seq } ?: 0L
        polling = true
        setTimeout(POLL_INTERVAL_MS) { poll() }
    }

    private fun poll() {
        if (!polling) return
        refresh()
        setTimeout(POLL_INTERVAL_MS) { poll() }
    }

    private fun scrollToBottom() {
        val scroller = scrollerRef?.view ?: return
        pageOffset = 0
        reloadPage()
        scroller.setContentOffset(0f, 0f, animated = false)
    }

    private fun onLogScroll(params: ScrollParams) {
        val atBottom = pageOffset == 0 && params.offsetY <= 8f
        followBottom = atBottom
        if (atBottom) newCount = 0
    }

    // ===== 清空 =====

    private fun confirmClear() {
        if (clearing) return
        val source = DshStreamLog.writeBehind ?: return
        clearing = true
        val work = DshLogWork(workerScope) { source.clear() }
        clearWork = work
        fun receive() {
            if (!alive || clearWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            clearWork = null
            clearing = false
            result.onSuccess {
                clearVisible = false; detailEntry = null; newCount = 0; lastMaxSeq = 0
                recompute()
                bridgeModule.toast("已清空本地诊断日志")
            }.onFailure { bridgeModule.toast("清空失败，请重试") }
        }
        setTimeout(50) { receive() }
    }

    // ===== 导出 =====

    /** 按当前时间范围或全部日志导出；内容为脱敏后的结构化日志。 */
    private fun exportLogs(exportAll: Boolean) {
        if (exporting || feedbackExporting) return
        val source = DshStreamLog.writeBehind ?: run { bridgeModule.toast("日志存储尚未就绪"); return }
        exporting = true
        val query = currentQuery(exportAll)
        val header = "DSH 日志导出\n会话：${if (allMode) "全部" else sessionId}\n范围：${if (exportAll) "全部保留日志" else "当前筛选 · ${timeChipLabel()} · $keyword"}\n内容已脱敏。"
        val safeName = if (allMode) "global" else sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48)
        val dir = exportDir
        val filename = "dsh-$safeName-${currentTimeMillis()}-log.txt"
        awaitExport(DshLogWork(workerScope) { query.export(source, dir, filename, header) })
    }

    /** 生成问题反馈包：全量日志（脱敏）+ 连接模式 + App 版本 + 设备型号，写入文件并分享。 */
    private fun exportFeedbackPackage() {
        if (feedbackExporting || exporting) return
        val source = DshStreamLog.writeBehind ?: run { bridgeModule.toast("日志存储尚未就绪"); return }
        feedbackExporting = true
        val device = bridgeModule.getDeviceInfo()
        val stamp = LogExporter.formatTimestamp(currentTimeMillis()).replace(Regex("[^0-9]"), "")
        val content = buildString {
            appendLine("DSH 问题反馈包")
            appendLine("生成时间：${LogExporter.formatTimestamp(currentTimeMillis())}")
            appendLine("连接模式：${connectionMode.ifEmpty { "未知" }}")
            appendLine("App 版本：${device?.optString("version").orEmpty().ifEmpty { "未知" }}")
            appendLine("设备：${device?.optString("model").orEmpty().ifEmpty { "未知" }}（${device?.optString("os").orEmpty()}）")
            appendLine("说明：包含全部本地诊断日志，内容已脱敏。")
            appendLine("")
        }
        val dir = exportDir
        awaitExport(DshLogWork(workerScope) { DshLogQuery().export(source, dir, "dsh-feedback-$stamp.txt", content) })
    }

    private fun awaitExport(work: DshLogWork<String>) {
        exportWork = work
        fun receive() {
            if (!alive || exportWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            exportWork = null; exporting = false; feedbackExporting = false
            result.onSuccess { path ->
                lastExportPath = path
                bridgeModule.shareExportFile(path) { ok, message ->
                    if (alive && !ok) bridgeModule.toast("文件已生成，分享失败：$message，可在导出菜单重试")
                }
            }.onFailure { bridgeModule.toast("导出失败：${LogSanitizer.sanitize(it.message.orEmpty())}") }
        }
        setTimeout(50) { receive() }
    }

    // ===== UI =====

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    backgroundColor(ctx.themeColors.bgBase)
                    paddingTop(pagerData.statusBarHeight)
                }
                ctx.renderNavBar(this)
                vif({ ctx.detailEntry == null }) {
                    View {
                        attr { flex(1f); flexDirectionColumn() }
                        ctx.renderFilterBar(this)
                        ctx.renderSearchBar(this)
                        ctx.renderListArea(this)
                    }
                }
                vif({ ctx.detailEntry != null }) {
                    View {
                        attr { flex(1f); flexDirectionColumn() }
                        ctx.renderDetail(this)
                    }
                }
            }
            ctx.renderSheets(this)
            ctx.renderClearDialog(this)
        }
    }

    /** 顶部导航栏：列表态（返回 / 标题+总数 / 导出 / 清空），详情态（返回 / 日志详情 / 复制）。 */
    private fun renderNavBar(container: ViewContainer<*, *>) = with(container) {
        View {
            attr {
                height(48f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(4f)
                paddingRight(12f)
                borderBottom(Border(0.5f, BorderStyle.SOLID, this@DshLogPage.themeColors.borderL1))
                backgroundColor(this@DshLogPage.themeColors.bgBase)
            }
            // 返回
            View {
                attr { width(64f); height(44f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter() }
                event {
                    click {
                        if (this@DshLogPage.detailEntry != null) this@DshLogPage.closeDetail()
                        else this@DshLogPage.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                    }
                }
                Image { attr { src(ImageUri.commonAssets("chevron-left.svg")); size(18f, 18f); tintColor(this@DshLogPage.themeColors.labelPrimary) } }
                vif({ this@DshLogPage.detailEntry != null }) {
                    Text { attr { text("返回"); fontSize(14f); color(this@DshLogPage.themeColors.labelPrimary); marginLeft(1f) } }
                }
            }
            // 标题
            View {
                attr { flex(1f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter() }
                Text {
                    attr {
                        text(if (this@DshLogPage.detailEntry != null) "日志详情" else if (this@DshLogPage.allMode) "诊断日志" else "会话日志")
                        fontSize(16f)
                        fontWeightBold()
                        color(this@DshLogPage.themeColors.labelPrimary)
                    }
                }
                vif({ this@DshLogPage.detailEntry == null }) {
                    Text { attr { text("  ${this@DshLogPage.logTotal} 条"); fontSize(11f); color(this@DshLogPage.themeColors.labelTertiary) } }
                }
            }
            // 右侧操作
            View {
                attr { width(64f); height(44f); flexDirectionRow(); alignItemsCenter(); justifyContentFlexEnd() }
                vif({ this@DshLogPage.detailEntry != null }) {
                    Text {
                        attr { text("复制"); fontSize(14f); color(this@DshLogPage.themeColors.stateBusinessPrimary) }
                        event { click { this@DshLogPage.detailEntry?.let { this@DshLogPage.copyDetailAll(it) } } }
                    }
                }
                vif({ this@DshLogPage.detailEntry == null }) {
                    View {
                        attr { width(32f); height(32f); allCenter() }
                        event { click { this@DshLogPage.sheet = SheetKind.EXPORT } }
                        Image { attr { src(ImageUri.commonAssets("share.svg")); size(17f, 17f); tintColor(this@DshLogPage.themeColors.labelSecondary) } }
                    }
                    View {
                        attr { width(32f); height(32f); allCenter(); marginLeft(2f) }
                        event { click { this@DshLogPage.clearVisible = true } }
                        Image { attr { src(ImageUri.commonAssets("delete.svg")); size(17f, 17f); tintColor(this@DshLogPage.themeColors.labelSecondary) } }
                    }
                }
            }
        }
    }

    /** 筛选 chips 行：时间 / 级别 / 会话（全局模式）+ 匹配数。 */
    private fun renderFilterBar(container: ViewContainer<*, *>) = with(container) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(12f)
                paddingRight(12f)
                paddingTop(8f)
                paddingBottom(8f)
            }
            this@DshLogPage.filterChip(this, this@DshLogPage.timeChipLabel(), true) { this@DshLogPage.openTimeSheet() }
            this@DshLogPage.filterChip(this, this@DshLogPage.levelChipLabel(), this@DshLogPage.selectedLevels.isNotEmpty()) { this@DshLogPage.sheet = SheetKind.LEVEL }
            vif({ this@DshLogPage.allMode }) {
                this@DshLogPage.filterChip(
                    this,
                    if (this@DshLogPage.selectedSessions.isEmpty()) "会话" else "会话·${this@DshLogPage.selectedSessions.size}",
                    this@DshLogPage.selectedSessions.isNotEmpty(),
                ) { this@DshLogPage.sheet = SheetKind.SESSION }
            }
            View { attr { flex(1f) } }
            Text {
                attr {
                    text("${this@DshLogPage.logView.size} 匹配")
                    fontSize(11f)
                    color(this@DshLogPage.themeColors.labelTertiary)
                }
            }
        }
    }

    private fun filterChip(container: ViewContainer<*, *>, label: String, active: Boolean, onClick: () -> Unit) = with(container) {
        View {
            attr {
                height(26f)
                borderRadius(13f)
                paddingLeft(10f)
                paddingRight(8f)
                marginRight(8f)
                flexDirectionRow()
                alignItemsCenter()
                border(Border(1f, BorderStyle.SOLID, if (active) this@DshLogPage.themeColors.stateBusinessPrimary else this@DshLogPage.themeColors.borderL2))
                backgroundColor(if (active) this@DshLogPage.themeColors.specificSelector else this@DshLogPage.themeColors.bgLayer2)
            }
            event { click { onClick() } }
            Text {
                attr {
                    text(label)
                    fontSize(11f)
                    color(if (active) this@DshLogPage.themeColors.stateBusinessPrimary else this@DshLogPage.themeColors.labelSecondary)
                }
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-down.svg"))
                    size(10f, 10f)
                    marginLeft(3f)
                    tintColor(if (active) this@DshLogPage.themeColors.stateBusinessPrimary else this@DshLogPage.themeColors.labelTertiary)
                }
            }
        }
    }

    /** 常驻搜索框：筛选行之下，搜索时筛选 chips 保持可见。 */
    private fun renderSearchBar(container: ViewContainer<*, *>) = with(container) {
        View {
            attr {
                height(32f)
                marginLeft(12f)
                marginRight(12f)
                marginBottom(6f)
                borderRadius(8f)
                backgroundColor(this@DshLogPage.themeColors.bgSkeleton)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(10f)
                paddingRight(6f)
            }
            Image { attr { src(ImageUri.commonAssets("tool-search.svg")); size(13f, 13f); tintColor(this@DshLogPage.themeColors.labelTertiary) } }
            Input {
                attr {
                    flex(1f)
                    height(32f)
                    marginLeft(6f)
                    fontSize(12f)
                    placeholder("搜索内容、type:类型 或 正则")
                    placeholderColor(this@DshLogPage.themeColors.labelTertiary)
                    color(this@DshLogPage.themeColors.labelPrimary)
                    text(this@DshLogPage.keyword)
                }
                event { textDidChange { this@DshLogPage.keyword = it.text; this@DshLogPage.recompute() } }
            }
            vif({ this@DshLogPage.keyword.isNotEmpty() }) {
                View {
                    attr { width(24f); height(24f); allCenter() }
                    event { click { this@DshLogPage.keyword = ""; this@DshLogPage.recompute() } }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(12f, 12f); tintColor(this@DshLogPage.themeColors.labelTertiary) } }
                }
            }
        }
    }

    /** 列表区：空状态 / 无匹配 / 日志行列表 + 新日志悬浮按钮。 */
    private fun renderListArea(container: ViewContainer<*, *>) = with(container) {
        View {
            attr { flexDirectionRow(); alignItemsCenter(); height(36f); paddingLeft(12f); paddingRight(12f) }
            Text {
                attr { text("上一页"); color(this@DshLogPage.themeColors.stateBusinessPrimary); marginRight(12f) }
                event { click { if (this@DshLogPage.pageOffset > 0) { this@DshLogPage.pageOffset = (this@DshLogPage.pageOffset - PAGE_SIZE).coerceAtLeast(0); this@DshLogPage.reloadPage() } } }
            }
            Text { attr { text(if (this@DshLogPage.loading) "读取中…" else "${this@DshLogPage.pageOffset / PAGE_SIZE + 1} 页 · ${this@DshLogPage.logTotal} 条匹配"); flex(1f); fontSize(12f); color(this@DshLogPage.themeColors.labelSecondary) } }
            Text {
                attr { text("下一页"); color(this@DshLogPage.themeColors.stateBusinessPrimary) }
                event { click { if (this@DshLogPage.pageOffset + PAGE_SIZE < this@DshLogPage.logTotal) { this@DshLogPage.pageOffset += PAGE_SIZE; this@DshLogPage.reloadPage() } } }
            }
        }
        vif({ this@DshLogPage.loadError.isNotEmpty() }) {
            Text { attr { text(this@DshLogPage.loadError); color(this@DshLogPage.themeColors.stateErrorPrimary); fontSize(12f) } }
        }
        vif({ this@DshLogPage.logTotal == 0 }) {
            Text { attr { text("暂无日志"); marginTop(90f); alignSelfCenter(); fontSize(14f); color(this@DshLogPage.themeColors.labelTertiary) } }
        }
        vif({ this@DshLogPage.logTotal > 0 && this@DshLogPage.logView.isEmpty() }) {
            View {
                attr { flexDirectionColumn(); alignItemsCenter(); marginTop(70f) }
                Text { attr { text("无匹配结果"); fontSize(14f); color(this@DshLogPage.themeColors.labelTertiary) } }
                View {
                    attr {
                        marginTop(12f)
                        padding(top = 8f, left = 18f, bottom = 8f, right = 18f)
                        borderRadius(16f)
                        border(Border(1f, BorderStyle.SOLID, this@DshLogPage.themeColors.stateBusinessPrimary))
                        highlightBackgroundColor(Color(0x0A000000))
                    }
                    event { click { this@DshLogPage.clearFilters() } }
                    Text { attr { text("清除筛选"); fontSize(13f); color(this@DshLogPage.themeColors.stateBusinessPrimary) } }
                }
            }
        }
        vif({ this@DshLogPage.logView.isNotEmpty() }) {
            View {
                attr { flex(1f); marginTop(2f) }
                List {
                    attr { absolutePositionAllZero(); firstContentLoadMaxIndex(LOG_INITIAL_RENDER_COUNT) }
                    ref { this@DshLogPage.scrollerRef = it }
                    event { scroll { params -> this@DshLogPage.onLogScroll(params) } }
                    vforLazy({ this@DshLogPage.logView }, maxLoadItem = LOG_MAX_RENDERED) { entry, _, _ ->
                        this@DshLogPage.logRow(this, entry)
                    }
                }
                vif({ !this@DshLogPage.followBottom && this@DshLogPage.newCount > 0 }) {
                    View {
                        attr {
                            positionAbsolute()
                            right(12f)
                            bottom(14f)
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(left = 12f, right = 12f)
                            height(32f)
                            borderRadius(16f)
                            backgroundColor(this@DshLogPage.themeColors.stateBusinessPrimary)
                            boxShadow(BoxShadow(0f, 2f, 8f, Color(0x33000000)))
                        }
                        event { click { this@DshLogPage.followBottom = true; this@DshLogPage.newCount = 0; this@DshLogPage.scrollToBottom() } }
                        Text { attr { text("↑ 新日志 ${this@DshLogPage.newCount}"); fontSize(12f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                    }
                }
            }
        }
    }

    /** 日志行：左 2dp 级别色条 + type/时间一行 + message 两行；seq/size/会话收进详情。 */
    private fun logRow(container: ViewContainer<*, *>, entry: LogEvent) = with(container) {
        View {
            attr {
                paddingLeft(14f)
                paddingRight(12f)
                paddingTop(9f)
                paddingBottom(9f)
                borderBottom(Border(0.5f, BorderStyle.SOLID, this@DshLogPage.themeColors.borderL1))
                highlightBackgroundColor(Color(0x0A000000))
            }
            event { click { this@DshLogPage.openDetail(entry) } }
            // 级别色条
            View {
                attr {
                    positionAbsolute()
                    left(6f)
                    top(11f)
                    bottom(11f)
                    width(2f)
                    borderRadius(1f)
                    backgroundColor(Color(dshLogLevelColor(entry.level)))
                }
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(entry.type)
                        fontSize(12f)
                        fontWeightBold()
                        color(this@DshLogPage.themeColors.labelPrimary)
                        flex(1f)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text(LogExporter.formatTimestamp(entry.timestamp).substring(11, 19))
                        fontSize(11f)
                        color(this@DshLogPage.themeColors.labelTertiary)
                        marginLeft(8f)
                    }
                }
            }
            Text {
                attr {
                    text(entry.message)
                    fontSize(12f)
                    lineHeight(17f)
                    color(if (entry.level == LogLevel.ERROR) this@DshLogPage.themeColors.stateErrorPrimary else this@DshLogPage.themeColors.labelSecondary)
                    marginTop(2f)
                    lines(2)
                }
            }
        }
    }

    /** 详情态：摘要卡 + 消息卡（独立复制）+ 底部跳转操作。 */
    private fun renderDetail(container: ViewContainer<*, *>) = with(container) {
        val e = this@DshLogPage.detailEntry ?: return@with
        Scroller {
            attr { flex(1f) }
            // 摘要卡
            View {
                attr {
                    margin(12f)
                    padding(12f)
                    borderRadius(10f)
                    backgroundColor(this@DshLogPage.themeColors.bgLayer1)
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); justifyContentSpaceBetween() }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            height(22f)
                            paddingLeft(8f)
                            paddingRight(8f)
                            borderRadius(11f)
                            border(Border(1f, BorderStyle.SOLID, Color(dshLogLevelColor(e.level))))
                        }
                        View {
                            attr {
                                size(6f, 6f)
                                borderRadius(3f)
                                backgroundColor(Color(dshLogLevelColor(e.level)))
                                marginRight(5f)
                            }
                        }
                        Text {
                            attr {
                                text(e.level.name)
                                fontSize(11f)
                                fontWeightBold()
                                color(Color(dshLogLevelColor(e.level)))
                            }
                        }
                    }
                    Text {
                        attr {
                            text(LogExporter.formatTimestamp(e.timestamp))
                            fontSize(12f)
                            color(this@DshLogPage.themeColors.labelTertiary)
                        }
                    }
                }
                this@DshLogPage.detailMetaRow(this, "类型", e.type)
                this@DshLogPage.detailMetaRow(this, "会话", if (e.sessionId.isNullOrEmpty()) "移动端" else this@DshLogPage.sessionTitle(e.sessionId))
                this@DshLogPage.detailMetaRow(this, "RPC", e.rpcId ?: "-")
                this@DshLogPage.detailMetaRow(this, "序号 / 大小", "#${e.seq} · ${e.size} B")
            }
            // 消息卡
            View {
                attr {
                    marginLeft(12f)
                    marginRight(12f)
                    marginBottom(12f)
                    padding(12f)
                    borderRadius(10f)
                    backgroundColor(this@DshLogPage.themeColors.bgLayer1)
                }
                View {
                    attr { flexDirectionRow(); alignItemsCenter(); justifyContentSpaceBetween() }
                    Text { attr { text("消息"); fontSize(12f); fontWeightMedium(); color(this@DshLogPage.themeColors.labelTertiary) } }
                    Text {
                        attr { text("复制"); fontSize(12f); color(this@DshLogPage.themeColors.stateBusinessPrimary) }
                        event { click { this@DshLogPage.copyText(e.message, "已复制消息") } }
                    }
                }
                Text {
                    attr {
                        text(e.message)
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(this@DshLogPage.themeColors.labelPrimary)
                    }
                }
            }
            // 底部操作
            vif({ !e.sessionId.isNullOrEmpty() }) {
                View {
                    attr {
                        margin(12f)
                        height(40f)
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                        borderRadius(8f)
                        backgroundColor(this@DshLogPage.themeColors.stateBusinessPrimary)
                    }
                    event { click { this@DshLogPage.jumpToSession(e.sessionId!!) } }
                    Text { attr { text(if (this@DshLogPage.jumping) "正在打开会话…" else "跳回该会话"); fontSize(14f); fontWeightBold(); color(Color(0xFFFFFFFF)) } }
                }
            }
        }
    }

    private fun detailMetaRow(container: ViewContainer<*, *>, label: String, value: String) = with(container) {
        View {
            attr { flexDirectionRow(); marginTop(8f) }
            Text { attr { text(label); width(72f); fontSize(12f); color(this@DshLogPage.themeColors.labelTertiary) } }
            Text { attr { text(value); flex(1f); fontSize(12f); color(this@DshLogPage.themeColors.labelPrimary) } }
        }
    }

    // ===== 底部 Sheet（时间 / 级别 / 会话 / 导出 / 自定义时间） =====

    private fun renderSheets(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetScaffold(container, SheetKind.TIME) { this@DshLogPage.renderTimeSheet(this) }
        this@DshLogPage.sheetScaffold(container, SheetKind.LEVEL) { this@DshLogPage.renderLevelSheet(this) }
        this@DshLogPage.sheetScaffold(container, SheetKind.SESSION) { this@DshLogPage.renderSessionSheet(this) }
        this@DshLogPage.sheetScaffold(container, SheetKind.EXPORT) { this@DshLogPage.renderExportSheet(this) }
    }

    /** 底部 Sheet 脚手架：遮罩点击关闭 + 圆角底栏容器。 */
    private fun sheetScaffold(container: ViewContainer<*, *>, kind: SheetKind, content: ViewContainer<*, *>.() -> Unit) = with(container) {
        vif({ this@DshLogPage.sheet == kind }) {
            Modal(inWindow = true) {
                attr {
                    absolutePositionAllZero()
                    flexDirectionColumn()
                    justifyContentFlexEnd()
                    backgroundColor(Color(0x66000000))
                }
                View {
                    attr { absolutePositionAllZero() }
                    event { click { this@DshLogPage.sheet = SheetKind.NONE } }
                }
                View {
                    attr {
                        borderRadius(BorderRectRadius(16f, 16f, 0f, 0f))
                        backgroundColor(this@DshLogPage.themeColors.bgLayer1)
                        paddingBottom(20f)
                    }
                    content(this)
                }
            }
        }
    }

    private fun sheetTitle(container: ViewContainer<*, *>, title: String) = with(container) {
        Text {
            attr {
                text(title)
                fontSize(13f)
                fontWeightMedium()
                color(this@DshLogPage.themeColors.labelTertiary)
                marginLeft(16f)
                marginTop(14f)
                marginBottom(4f)
            }
        }
    }

    private fun sheetRow(
        container: ViewContainer<*, *>,
        label: String,
        subtitle: String = "",
        checked: Boolean = false,
        dotColor: Long = 0L,
        onClick: () -> Unit,
    ) = with(container) {
        View {
            attr {
                height(if (subtitle.isEmpty()) 44f else 52f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(16f)
                paddingRight(16f)
                highlightBackgroundColor(Color(0x0A000000))
            }
            event { click { onClick() } }
            vif({ dotColor != 0L }) {
                View {
                    attr {
                        size(8f, 8f)
                        borderRadius(4f)
                        backgroundColor(Color(dotColor))
                        marginRight(8f)
                    }
                }
            }
            View {
                attr { flex(1f); flexDirectionColumn() }
                Text { attr { text(label); fontSize(14f); color(this@DshLogPage.themeColors.labelPrimary) } }
                vif({ subtitle.isNotEmpty() }) {
                    Text { attr { text(subtitle); fontSize(11f); color(this@DshLogPage.themeColors.labelTertiary); marginTop(2f) } }
                }
            }
            vif({ checked }) {
                Image { attr { src(ImageUri.commonAssets("check.svg")); size(16f, 16f); tintColor(this@DshLogPage.themeColors.stateBusinessPrimary) } }
            }
        }
    }

    /** 时间范围 Sheet：外层“今天”预设 + 开始/结束时间滚轮，确定后应用。 */
    private fun renderTimeSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "时间范围")
        this@DshLogPage.renderCustomRangeSection(this)
    }

    /** 级别多选 Sheet：空集 = 全部；点击行 toggle（不自动关闭），与“会话”Sheet 交互一致。 */
    private fun renderLevelSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "级别（多选）")
        this@DshLogPage.sheetRow(this, "全部", subtitle = "${this@DshLogPage.levelCounts.values.sum()} 条", checked = this@DshLogPage.selectedLevels.isEmpty()) {
            this@DshLogPage.selectedLevels = emptySet()
            this@DshLogPage.recompute()
        }
        for (lv in LogLevel.entries) {
            val checked = lv in this@DshLogPage.selectedLevels
            this@DshLogPage.sheetRow(this, this@DshLogPage.levelShort(lv), subtitle = "${this@DshLogPage.levelCounts[lv] ?: 0} 条", checked = checked, dotColor = dshLogLevelColor(lv)) {
                this@DshLogPage.selectedLevels = if (checked) this@DshLogPage.selectedLevels - lv else this@DshLogPage.selectedLevels + lv
                this@DshLogPage.recompute()
            }
        }
    }

    private fun renderSessionSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "会话")
        this@DshLogPage.sheetRow(this, "全部会话", checked = this@DshLogPage.selectedSessions.isEmpty()) {
            this@DshLogPage.selectedSessions = emptySet()
            this@DshLogPage.recompute()
        }
        Scroller {
            attr { flex(1f); maxHeight(320f); marginTop(4f) }
            vfor({ this@DshLogPage.sessionOptions }) { sid ->
                val label = if (sid == "__mobile__") "移动端" else this@DshLogPage.sessionTitle(sid).take(10)
                val checked = sid in this@DshLogPage.selectedSessions
                this@DshLogPage.sheetRow(this, label, checked = checked) {
                    this@DshLogPage.selectedSessions = if (checked) this@DshLogPage.selectedSessions - sid else this@DshLogPage.selectedSessions + sid
                    this@DshLogPage.recompute()
                }
            }
        }
    }

    private fun renderExportSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "导出")
        vif({ this@DshLogPage.lastExportPath.isNotEmpty() }) {
            View {
                attr { padding(12f) }
                Text { attr { text("上次导出文件：${this@DshLogPage.lastExportPath}"); fontSize(12f); color(this@DshLogPage.themeColors.labelSecondary) } }
                Text {
                    attr { text("复制路径"); marginTop(8f); color(this@DshLogPage.themeColors.stateBusinessPrimary) }
                    event { click { this@DshLogPage.copyText(this@DshLogPage.lastExportPath, "已复制文件路径") } }
                }
                Text {
                    attr { text("重新分享"); marginTop(8f); color(this@DshLogPage.themeColors.stateBusinessPrimary) }
                    event { click { this@DshLogPage.bridgeModule.shareExportFile(this@DshLogPage.lastExportPath) { ok, message -> if (!ok) this@DshLogPage.bridgeModule.toast(message) } } }
                }
            }
        }
        this@DshLogPage.sheetRow(this, if (this@DshLogPage.exporting) "导出中..." else "按当前时间范围导出", subtitle = "当前范围：${this@DshLogPage.timeChipLabel()}") {
            this@DshLogPage.sheet = SheetKind.NONE
            this@DshLogPage.exportLogs(false)
        }
        this@DshLogPage.sheetRow(this, if (this@DshLogPage.exporting) "导出中..." else "导出全部日志", subtitle = "包含所有本地日志，不受列表时间限制") {
            this@DshLogPage.sheet = SheetKind.NONE
            this@DshLogPage.exportLogs(true)
        }
        this@DshLogPage.sheetRow(this, if (this@DshLogPage.feedbackExporting) "生成中..." else "生成问题反馈包", subtitle = "全量日志 + 设备与连接信息，用于问题反馈") {
            this@DshLogPage.sheet = SheetKind.NONE
            this@DshLogPage.exportFeedbackPackage()
        }
    }

    /** 范围预设 tag：位于开始/结束时间外层，点击填充草稿起止时间，不立即应用。 */
    private fun rangePresetChip(container: ViewContainer<*, *>, label: String, kind: Int) = with(container) {
        View {
            attr {
                height(26f)
                borderRadius(13f)
                paddingLeft(12f)
                paddingRight(12f)
                marginRight(8f)
                alignItemsCenter()
                justifyContentCenter()
                border(Border(1f, BorderStyle.SOLID, this@DshLogPage.themeColors.stateBusinessPrimary))
                backgroundColor(this@DshLogPage.themeColors.specificSelector)
                highlightBackgroundColor(Color(0x0A000000))
            }
            event { click { this@DshLogPage.applyPresetToDraft(kind) } }
            Text { attr { text(label); fontSize(11f); color(this@DshLogPage.themeColors.stateBusinessPrimary) } }
        }
    }

    /** 自定义范围：外层预设 tags + 起/止 tabs + 5 列 ScrollPicker + 预览，确定后应用。 */
    private fun renderCustomRangeSection(container: ViewContainer<*, *>) = with(container) {
        View {
            attr { paddingLeft(16f); paddingRight(16f) }
            // 外层预设：点击自动填充开始/结束时间，不立即筛选
            View {
                attr { flexDirectionRow(); alignItemsCenter(); marginBottom(8f) }
                this@DshLogPage.rangePresetChip(this, "今天", 0)
                this@DshLogPage.rangePresetChip(this, "最近1小时", 1)
                this@DshLogPage.rangePresetChip(this, "最近10小时", 2)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                View {
                    attr { flex(1f); alignItemsCenter(); padding(top = 8f, bottom = 8f); borderRadius(8f); backgroundColor(if (this@DshLogPage.pickerTargetStart) this@DshLogPage.themeColors.specificSelector else Color(0x00000000)) }
                    event { click { this@DshLogPage.switchPickerTarget(true) } }
                    Text { attr { text("开始时间"); fontSize(14f); fontWeightBold(); color(if (this@DshLogPage.pickerTargetStart) this@DshLogPage.themeColors.labelPrimary else this@DshLogPage.themeColors.labelTertiary) } }
                }
                View {
                    attr { flex(1f); alignItemsCenter(); padding(top = 8f, bottom = 8f); borderRadius(8f); backgroundColor(if (!this@DshLogPage.pickerTargetStart) this@DshLogPage.themeColors.specificSelector else Color(0x00000000)) }
                    event { click { this@DshLogPage.switchPickerTarget(false) } }
                    Text { attr { text("结束时间"); fontSize(14f); fontWeightBold(); color(if (!this@DshLogPage.pickerTargetStart) this@DshLogPage.themeColors.labelPrimary else this@DshLogPage.themeColors.labelTertiary) } }
                }
            }
            View {
                attr { flexDirectionRow(); justifyContentCenter(); marginTop(8f); height(120f) }
                // nonce 奇偶双分支：pickerNonce++ 时强制重建滚轮，让 defaultIndex 按新值生效
                vif({ this@DshLogPage.pickerNonce % 2 == 0 }) {
                    View {
                        attr { flexDirectionRow(); height(120f) }
                        this@DshLogPage.renderPickerColumns(this)
                    }
                }
                vif({ this@DshLogPage.pickerNonce % 2 != 0 }) {
                    View {
                        attr { flexDirectionRow(); height(120f) }
                        this@DshLogPage.renderPickerColumns(this)
                    }
                }
            }
            Text {
                attr {
                    text((if (this@DshLogPage.pickerTargetStart) this@DshLogPage.draftStartMs else this@DshLogPage.draftEndMs)?.let { formatCustomTime(it) } ?: "不限")
                    fontSize(12f)
                    color(this@DshLogPage.themeColors.labelSecondary)
                    marginTop(6f)
                    textAlignCenter()
                }
            }
            View {
                attr { height(40f); marginTop(12f); flexDirectionRow(); justifyContentFlexEnd() }
                Text {
                    attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(this@DshLogPage.themeColors.labelTertiary) }
                    event { click { this@DshLogPage.sheet = SheetKind.NONE } }
                }
                Text {
                    attr { text("确定"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); fontWeightBold(); color(this@DshLogPage.themeColors.stateBusinessPrimary) }
                    event { click { this@DshLogPage.confirmTimeSheet() } }
                }
            }
        }
    }

    private fun renderPickerColumns(container: ViewContainer<*, *>) = with(container) {
        ScrollPicker(itemList = Array(8) { (this@DshLogPage.pkYear - 7 + it).toString() }, defaultIndex = 7) {
            attr { itemWidth = 48f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> this@DshLogPage.pkYear = v.toInt() } }
        }
        ScrollPicker(itemList = Array(12) { (it + 1).toString() }, defaultIndex = this@DshLogPage.pkMonth - 1) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> this@DshLogPage.pkMonth = v.toInt() } }
        }
        ScrollPicker(itemList = Array(31) { (it + 1).toString() }, defaultIndex = this@DshLogPage.pkDay - 1) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> this@DshLogPage.pkDay = v.toInt() } }
        }
        ScrollPicker(itemList = Array(24) { (if (it < 10) "0" else "") + it }, defaultIndex = this@DshLogPage.pkHour) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> this@DshLogPage.pkHour = v.toInt() } }
        }
        ScrollPicker(itemList = Array(60) { (if (it < 10) "0" else "") + it }, defaultIndex = this@DshLogPage.pkMinute) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> this@DshLogPage.pkMinute = v.toInt() } }
        }
        View {
            attr {
                absolutePosition(top = 0f, left = 0f, right = 0f)
                height(40f)
                backgroundLinearGradient(Direction.TO_BOTTOM, ColorStop(this@DshLogPage.themeColors.bgLayer1, 0f), ColorStop(Color.TRANSPARENT, 1f))
            }
        }
        View {
            attr {
                absolutePosition(bottom = 0f, left = 0f, right = 0f)
                height(40f)
                backgroundLinearGradient(Direction.TO_TOP, ColorStop(this@DshLogPage.themeColors.bgLayer1, 0f), ColorStop(Color.TRANSPARENT, 1f))
            }
        }
        View {
            attr {
                absolutePosition(top = 40f, left = 0f, right = 0f)
                height(38f)
                borderTop(Border(1f, BorderStyle.SOLID, this@DshLogPage.themeColors.borderL1))
                borderBottom(Border(1f, BorderStyle.SOLID, this@DshLogPage.themeColors.borderL1))
            }
        }
    }

    // ===== 清空确认弹窗 =====

    private fun renderClearDialog(container: ViewContainer<*, *>) = with(container) {
        vif({ this@DshLogPage.clearVisible }) {
            Modal(inWindow = true) {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    paddingLeft(20f)
                    paddingRight(20f)
                    backgroundColor(Color(0x66000000))
                }
                View {
                    attr {
                        width(pagerData.pageViewWidth - 40f)
                        maxWidth(420f)
                        padding(20f)
                        borderRadius(16f)
                        backgroundColor(this@DshLogPage.themeColors.bgLayer1)
                    }
                    Text { attr { text("清空本地诊断日志"); fontSize(18f); fontWeightBold(); color(this@DshLogPage.themeColors.labelPrimary) } }
                    Text {
                        attr {
                            text("将删除手机上的全部本地诊断日志（含所有会话与移动端日志），不影响会话消息、附件和 Host 侧历史。此操作不可恢复。")
                            marginTop(8f)
                            fontSize(13f)
                            lineHeight(20f)
                            color(this@DshLogPage.themeColors.labelSecondary)
                        }
                    }
                    View {
                        attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                        Text {
                            attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(this@DshLogPage.themeColors.labelTertiary) }
                            event { click { this@DshLogPage.clearVisible = false } }
                        }
                        Text {
                            attr { text(if (this@DshLogPage.clearing) "清空中..." else "清空日志"); width(104f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(this@DshLogPage.themeColors.stateErrorPrimary) }
                            event { click { if (!this@DshLogPage.clearing) this@DshLogPage.confirmClear() } }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000
        private const val PAGE_SIZE = 200
    }
}

private fun dshLogLevelColor(level: LogLevel): Long = when (level) {
    LogLevel.DEBUG -> 0xFF8B939A
    LogLevel.INFO -> 0xFF4176E6
    LogLevel.WARN -> 0xFFDD8629
    LogLevel.ERROR -> 0xFFD25A5A
}

private fun formatCustomTime(ms: Long): String =
    LogExporter.formatTimestamp(ms).substring(0, 16).replace('T', ' ')
