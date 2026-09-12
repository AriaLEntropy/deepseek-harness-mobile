package com.example.dsh.home

import com.example.dsh.base.*
import com.example.dsh.diagnostics.DshLogPageContract
import com.example.dsh.diagnostics.DshLogQuery
import com.example.dsh.diagnostics.DshLogFilters
import com.example.dsh.diagnostics.dshLogPickerEpoch
import com.example.dsh.diagnostics.DshLogPageResult
import com.example.dsh.diagnostics.DshLogWork
import com.example.dsh.diagnostics.DshCrashMarker
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
 * - sessionId：仅初始化可修改、可清除的会话筛选；所有入口共享「日志」页面
 * - exportDir / connectionMode：导出文件目录、反馈包抬头用的连接模式名
 *
 * 结构：列表保留在同一 Page，详情使用覆盖面板，时间/级别等筛选收进
 * 底部 Sheet。数据源为全局 [DshStreamLog.writeBehind]，
 * 只展示脱敏元数据；会话标题通过路由快照传入，跳回会话通过 NotifyModule 解耦。
 */
@Page("dsh_log")
internal class DshLogPage : BasePager() {

    // ---- 路由参数 ----
    private var exportDir = ""
    private var connectionMode = ""
    private val sessionTitles = mutableMapOf<String, String>()

    // ---- 数据 ----
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // 全量刷新（统计口径 + 第一页）与游标增量读取（懒加载 / 轮询）分别持有工作句柄。
    private var queryWork: DshLogWork<DshLogPageResult>? = null
    private var sliceWork: DshLogWork<List<LogEvent>>? = null
    private var exportWork: DshLogWork<String>? = null
    private var clearWork: DshLogWork<Unit>? = null
    private var queryVersion = -1L
    private var queryGeneration = 0
    // 已完成一次全量统计（total/级别计数/目录），增量轮询的前置条件。
    private var statsReady = false
    private var lastFullRefreshMs = 0L
    private var loadedCount by observable(0)
    private var loading by observable(false)
    private var loadError by observable("")
    private var storageWarning by observable("")
    private var lastExportPath by observable("")
    private var alive = true
    private var filterRevision = 0
    private var jumpRef: CallbackRef? = null
    private var jumpRequest = ""
    private var jumping by observable(false)
    private val logView by observableList<LogEvent>()
    private var logTotal by observable(0)
    private val sessionOptions by observableList<String>()
    private val typeOptions by observableList<String>()
    private var catalogVersion = -1L
    private var retainedTotal by observable(0)
    private var sessionSearch by observable("")
    private var typeSearch by observable("")

    // ---- 筛选（级别为多选集合：空集 = 不过滤） ----
    private var filters by observable(DshLogFilters())
    private var selectedLevels: Set<LogLevel>
        get() = filters.levels
        set(value) { filters = filters.copy(levels = value) }
    // 级别计数（时间/会话/搜索词过滤后、级别过滤前的口径），供级别 Sheet 行展示
    private var levelCounts by observable<Map<LogLevel, Int>>(emptyMap())
    // 默认全部保留日志，避免旧会话被隐含的“今天”条件隐藏。
    private var customStartMs: Long?
        get() = filters.fromTime
        set(value) { filters = filters.copy(fromTime = value) }
    private var customEndMs: Long?
        get() = filters.toTime
        set(value) { filters = filters.copy(toTime = value) }
    private var selectedSessions: Set<String>
        get() = filters.sessions
        set(value) { filters = filters.copy(sessions = value) }
    private var selectedTypes: Set<String>
        get() = filters.types
        set(value) { filters = filters.copy(types = value) }
    private var keyword: String
        get() = filters.keyword
        set(value) { filters = filters.copy(keyword = value) }

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
    private var draftAllTime by observable(true)
    private var pickerDirty = false
    private var pickerError by observable("")
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
    private var pollGeneration = 0

    private enum class SheetKind { NONE, TIME, LEVEL, SESSION, TYPE, EXPORT }

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
        filters = DshLogFilters.forSession(pageData.params.optString(DshLogPageContract.KEY_SESSION_ID))
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
                    sessionTitles[id] = LogSanitizer.sanitize(item.optString(DshLogPageContract.KEY_SESSION_TITLE).ifEmpty { id })
                }
            }
        }
        getBackPressHandler().addCallback(backCallback)
        sessionOptions.addAll((sessionTitles.keys + selectedSessions + DshLogFilters.UNASSOCIATED).distinct().sorted())
        refreshAll()
        startFollow()
    }

    override fun pageWillDestroy() {
        alive = false
        polling = false
        workerScope.cancel()
        jumpRef?.let { acquireModule<NotifyModule>(NotifyModule.MODULE_NAME).removeNotify(DshLogPageContract.EVENT_JUMP_RESULT, it) }
        super.pageWillDestroy()
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        polling = false
        pollGeneration++
        queryWork?.cancel(); queryWork = null
        sliceWork?.cancel(); sliceWork = null
        statsReady = false
        queryVersion = -1
        loading = false
        exportWork?.cancel(); exportWork = null; exporting = false; feedbackExporting = false
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        refreshAll()
        startFollow()
    }

    // ===== 数据：刷新与筛选 =====

    /**
     * 全量刷新：遍历一次匹配集合重算 total / 级别计数 / 会话类型目录，并重建第一页。
     * 只在打开、切换筛选、重新显示和低频对账时调用；懒加载与轮询走 [loadMore]/[refreshNewer]，
     * 不再为多看一页或看新日志而重复扫描整张表。
     */
    private fun refreshAll() {
        if (queryWork != null || sliceWork != null || clearing) return
        val source = DshStreamLog.writeBehind ?: run { loading = false; loadError = "日志存储尚未就绪"; return }
        storageWarning = if (source.isDegraded()) {
            "日志存储降级：${source.lastFailure() ?: "未知原因"}，部分日志可能未保存"
        } else {
            ""
        }
        val version = source.version()
        queryVersion = version
        val query = currentQuery()
        val generation = queryGeneration
        loading = true
        loadError = ""
        val includeCatalog = catalogVersion != version
        val work = DshLogWork(workerScope) { cancelled -> query.readPage(source, 0, PAGE_SIZE, includeCatalog, cancelled) }
        queryWork = work
        fun receive() {
            if (!alive || queryWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            queryWork = null
            loading = false
            if (generation != queryGeneration) { queryVersion = -1; refreshAll(); return }
            result.onSuccess { page ->
                logTotal = page.total
                loadedCount = page.rows.size
                levelCounts = page.levels
                logView.diffUpdate(page.rows) { old, new -> old == new }
                page.catalog?.let { catalog ->
                    catalogVersion = version
                    retainedTotal = catalog.retained
                    mergeCatalog(catalog.sessions, catalog.types)
                }
                lastMaxSeq = page.rows.firstOrNull()?.seq ?: 0L
                newCount = 0
                statsReady = true
                lastFullRefreshMs = currentTimeMillis()
            }.onFailure { loadError = "读取日志失败：${LogSanitizer.sanitize(it.message.orEmpty())}"; queryVersion = -1 }
        }
        setTimeout(50) { receive() }
    }

    /** 轮询增量：只拉取比 [lastMaxSeq] 更新的记录并叠加到统计与列表，避免整表扫描。 */
    private fun refreshNewer() {
        if (!statsReady || loading || queryWork != null || sliceWork != null || clearing) return
        val source = DshStreamLog.writeBehind ?: return
        val version = source.version()
        if (version == queryVersion) return
        queryVersion = version
        val query = currentQuery()
        val after = lastMaxSeq
        val generation = queryGeneration
        val work = DshLogWork(workerScope) { cancelled -> query.readNewer(source, after, NEWER_LIMIT, cancelled) }
        sliceWork = work
        fun receive() {
            if (!alive || sliceWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            sliceWork = null
            if (generation != queryGeneration) { queryVersion = -1; refreshAll(); return }
            result.onSuccess { rows ->
                // 空结果（清空/淘汰）或新日志过多时，退回全量对账，避免增量口径偏差。
                if (rows.isEmpty() || rows.size >= NEWER_LIMIT) { queryVersion = -1; refreshAll(); return }
                applyNewer(rows)
            }.onFailure { queryVersion = -1 }
        }
        setTimeout(50) { receive() }
    }

    /** 把更新的日志叠加进视图：级别计数、总数、会话/类型目录和列表头部。 */
    private fun applyNewer(rows: List<LogEvent>) {
        val levels = filters.levels
        val displayed = if (levels.isEmpty()) rows else rows.filter { it.level in levels }
        val merged = levelCounts.toMutableMap()
        rows.forEach { merged[it.level] = (merged[it.level] ?: 0) + 1 }
        levelCounts = merged
        logTotal += displayed.size
        if (displayed.isNotEmpty()) logView.addAll(0, displayed)
        // 环形上限：持续生成时只保留最近 MAX_LOADED 条，超出部分从尾部淘汰。
        var excess = logView.size - MAX_LOADED
        while (excess-- > 0) logView.removeAt(logView.size - 1)
        loadedCount = logView.size
        lastMaxSeq = maxOf(lastMaxSeq, rows.first().seq)
        mergeCatalog(
            rows.map { it.sessionId?.takeIf(String::isNotEmpty) ?: DshLogFilters.UNASSOCIATED },
            rows.map { it.type },
        )
        if (followBottom) newCount = 0 else newCount += displayed.size
    }

    /** 合并会话/类型下拉选项并保持排序；全量与增量共用。 */
    private fun mergeCatalog(sessions: List<String>, types: List<String>) {
        val options = (sessionTitles.keys + sessionOptions + sessions + selectedSessions + DshLogFilters.UNASSOCIATED).distinct().sorted()
        sessionOptions.diffUpdate(options) { old, new -> old == new }
        typeOptions.diffUpdate((typeOptions + types).distinct().sorted()) { old, new -> old == new }
    }

    /** 按筛选条件（时间/级别集合/会话/搜索词）重算日志视图；级别计数取级别过滤前的口径。 */
    private fun recompute() {
        queryWork?.cancel(); queryWork = null
        sliceWork?.cancel(); sliceWork = null
        statsReady = false
        loadedCount = 0
        lastMaxSeq = 0
        newCount = 0
        loading = true
        logView.clear()
        queryGeneration++
        val revision = ++filterRevision
        setTimeout(200) { if (alive && filterRevision == revision) reloadPage() }
    }

    private fun reloadPage() {
        queryGeneration++
        queryVersion = -1
        refreshAll()
    }

    /** 触底懒加载：按游标只取比当前最旧一条更早的一页，不重算统计。 */
    private fun loadMore() {
        if (loading || queryWork != null || sliceWork != null || clearing) return
        if (loadedCount >= logTotal || loadedCount >= MAX_LOADED) return
        val source = DshStreamLog.writeBehind ?: return
        val oldest = logView.lastOrNull()?.seq ?: return
        val query = currentQuery()
        val generation = queryGeneration
        loading = true
        val work = DshLogWork(workerScope) { cancelled -> query.readOlder(source, oldest, PAGE_SIZE, cancelled) }
        sliceWork = work
        fun receive() {
            if (!alive || sliceWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            sliceWork = null
            loading = false
            if (generation != queryGeneration) { queryVersion = -1; refreshAll(); return }
            result.onSuccess { rows ->
                if (rows.isEmpty()) { queryVersion = -1; refreshAll(); return }
                logView.addAll(rows)
                loadedCount = logView.size
            }.onFailure { loadError = "读取日志失败：${LogSanitizer.sanitize(it.message.orEmpty())}" }
        }
        setTimeout(50) { receive() }
    }

    private fun currentQuery(all: Boolean = false): DshLogQuery = filters.query(all)

    private fun clearFilters() {
        filters = DshLogFilters()
        recompute()
    }

    // ===== 时间筛选 =====

    /** 预设区间：0=今天；1=最近 1 小时；2=最近 15 分钟。 */
    private fun presetRange(kind: Int): Pair<Long, Long> {
        val now = currentTimeMillis()
        return when (kind) {
            1 -> now - 3_600_000L to now
            2 -> now - 900_000L to now
            else -> {
                val s = LogExporter.formatTimestamp(now)
                val y = s.substring(0, 4).toInt()
                val mo = s.substring(5, 7).toInt()
                val d = s.substring(8, 10).toInt()
                LogExporter.parseEpoch(y, mo, d, 0, 0) to LogExporter.parseEpoch(y, mo, d, 23, 59) + 59_999L
            }
        }
    }

    /** Sheet 预设 chip：把区间填入草稿两端并同步滚轮，不立即筛选。 */
    private fun applyPresetToDraft(kind: Int) {
        draftAllTime = false
        val (s, e) = presetRange(kind)
        draftStartMs = s
        draftEndMs = e
        syncPickerPkValues(pickerTargetStart)
        pickerNonce++
    }

    /** 打开时间 Sheet：把生效区间拷贝进草稿，滚轮同步到开始端。 */
    private fun openTimeSheet() {
        draftAllTime = customStartMs == null && customEndMs == null
        val today = presetRange(0)
        draftStartMs = customStartMs ?: today.first
        draftEndMs = customEndMs ?: today.second
        pickerTargetStart = true
        syncPickerPkValues(true)
        sheet = SheetKind.TIME
    }

    private fun syncPickerPkValues(targetStart: Boolean) {
        pickerDirty = false
        pickerError = ""
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
    private fun commitPickerToDraft(): Boolean {
        if (!pickerDirty) return true
        val v = dshLogPickerEpoch(pkYear, pkMonth, pkDay, pkHour, pkMinute, end = !pickerTargetStart)
            ?: run { pickerError = "日期无效，请检查所选月份的天数"; return false }
        pickerError = ""
        if (pickerTargetStart) draftStartMs = v else draftEndMs = v
        pickerDirty = false
        return true
    }

    private fun pickerChanged() {
        pickerDirty = true
        draftAllTime = false
        commitPickerToDraft()
    }

    /** 切换开始/结束编辑端：先保存当前端，再把另一端装载进滚轮。 */
    private fun switchPickerTarget(start: Boolean) {
        if (pickerTargetStart == start) return
        if (!commitPickerToDraft()) return
        pickerTargetStart = start
        syncPickerPkValues(start)
        pickerNonce++
    }

    /** 确定：草稿两端一次性提交到生效条件并重筛；起止选反时自动交换。取消/返回仅关闭 Sheet，草稿作废。 */
    private fun confirmTimeSheet() {
        if (draftAllTime) {
            filters = filters.copy(fromTime = null, toTime = null)
            recompute()
            sheet = SheetKind.NONE
            return
        }
        if (!commitPickerToDraft()) return
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
        if (customStartMs == null && customEndMs == null) return "全部保留"
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

    private fun sessionLabel(sid: String): String =
        if (sid == DshLogFilters.UNASSOCIATED) "未关联会话" else sessionTitle(sid)

    private fun sessionChipLabel(): String = when (selectedSessions.size) {
        0 -> "会话：全部"
        1 -> "会话：${sessionLabel(selectedSessions.first())}"
        else -> "会话：已选 ${selectedSessions.size} 项"
    }

    private fun filterSummary(): String = buildString {
        appendLine("会话：${selectedSessions.takeIf { it.isNotEmpty() }?.joinToString { sessionLabel(it) + if (it == DshLogFilters.UNASSOCIATED) "" else " [$it]" } ?: "全部（含未关联会话）"}")
        appendLine("时间：${timeChipLabel()}")
        appendLine("级别：${levelChipLabel()}")
        appendLine("事件类型：${selectedTypes.takeIf { it.isNotEmpty() }?.joinToString() ?: "全部"}")
        appendLine("关键词（普通文本）：$keyword")
        appendLine("RPC：${filters.rpcId.ifEmpty { "不限" }}")
    }

    // ===== 跟随底部（轮询） =====

    private fun startFollow() {
        if (polling) return
        followBottom = true
        newCount = 0
        lastMaxSeq = logView.maxOfOrNull { it.seq } ?: 0L
        polling = true
        val generation = ++pollGeneration
        setTimeout(POLL_INTERVAL_MS) { poll(generation) }
    }

    private fun poll(generation: Int) {
        if (!polling || generation != pollGeneration) return
        // 常规轮询只增量拉新。仅在用户尚未向下翻页（列表仍是第一页）时，
        // 才允许低频全量对账，避免把已加载的多页数据重置回第一页。
        val canReconcile = logView.size <= PAGE_SIZE
        if (canReconcile && currentTimeMillis() - lastFullRefreshMs >= FULL_REFRESH_INTERVAL_MS) {
            refreshAll()
        } else {
            refreshNewer()
        }
        setTimeout(POLL_INTERVAL_MS) { poll(generation) }
    }

    private fun scrollToBottom() {
        val scroller = scrollerRef?.view ?: return
        scroller.setContentOffset(0f, 0f, animated = false)
    }

    private fun onLogScroll(params: ScrollParams) {
        val atTop = params.offsetY <= 8f
        followBottom = atTop
        if (atTop) newCount = 0
        val maxOffset = (params.contentHeight - params.viewHeight).coerceAtLeast(0f)
        if (params.offsetY >= maxOffset - LOAD_MORE_SLACK_PX) loadMore()
    }

    // ===== 清空 =====

    private fun confirmClear() {
        if (clearing) return
        clearing = true
        if (pagerData.platform == "ohos") {
            bridgeModule.readLastCrashAsync { raw -> if (alive) clearLogsAndCrash(raw) }
        } else {
            runCatching { bridgeModule.readLastCrash() }.onSuccess { clearLogsAndCrash(it) }
                .onFailure { clearing = false; bridgeModule.toast("无法确认崩溃快照状态，请重试清空") }
        }
    }

    private fun clearLogsAndCrash(raw: String) {
        val source = DshStreamLog.writeBehind ?: run { clearing = false; return }
        val id = raw.takeIf { it.isNotEmpty() }?.let(DshCrashMarker::idOf)
        val work = DshLogWork(workerScope) { _ -> source.clear(id) }
        clearWork = work
        fun receive() {
            if (!alive || clearWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            clearWork = null
            clearing = false
            result.onSuccess {
                // 清空本地日志同时清除原生最近一次崩溃快照；已导入 ID 保留，避免清除失败时旧崩溃重现。
                bridgeModule.clearLastCrash { ok ->
                    if (alive) bridgeModule.toast(if (ok) "已清空全部本地日志和崩溃快照"
                        else "日志已清空，原生崩溃快照清理失败；旧快照已标记，不会重新导入")
                }
                clearVisible = false; detailEntry = null; newCount = 0; lastMaxSeq = 0
                recompute()
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
        val header = "DSH 日志导出\n范围：${if (exportAll) "全部本地日志（忽略所有筛选）" else "当前筛选结果（含全部分页）\n${filterSummary()}"}\n内容已脱敏。"
        val safeName = if (exportAll) "all" else "filtered"
        val dir = exportDir
        val filename = "dsh-$safeName-${currentTimeMillis()}-log.txt"
        awaitExport(DshLogWork(workerScope) { cancelled -> query.export(source, dir, filename, header, cancelled) })
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
        awaitExport(DshLogWork(workerScope) { cancelled -> DshLogQuery().export(source, dir, "dsh-feedback-$stamp.txt", content, cancelled) })
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
                    paddingBottom(pagerData.safeAreaInsets.bottom)
                }
                ctx.renderNavBar(this)
                View {
                    attr { flex(1f); flexDirectionColumn() }
                    ctx.renderSearchBar(this)
                    ctx.renderFilterBar(this)
                    ctx.renderListArea(this)
                }
            }
            // Keep the list mounted behind details so returning preserves its scroll position.
            vif({ ctx.detailEntry != null }) {
                Modal(inWindow = true) {
                    attr {
                        absolutePositionAllZero(); flexDirectionColumn()
                        paddingTop(pagerData.statusBarHeight)
                        paddingBottom(pagerData.safeAreaInsets.bottom)
                        backgroundColor(ctx.themeColors.bgBase)
                    }
                    ctx.renderNavBar(this)
                    ctx.renderDetail(this)
                }
            }
            ctx.renderSheets(this)
            ctx.renderClearDialog(this)
        }
    }

    /** 顶部标题始终为「日志」；详情面板显示返回与复制操作。 */
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
                        text("日志")
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

    /** All filter values are read inside attr/vif; never capture an observable as a static label. */
    private fun renderFilterBar(container: ViewContainer<*, *>) = with(container) {
        View {
            attr { paddingLeft(12f); paddingRight(12f) }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                this@DshLogPage.filterChip(this, { this@DshLogPage.sessionChipLabel() },
                    { this@DshLogPage.selectedSessions.isNotEmpty() },
                    { this@DshLogPage.selectedSessions = emptySet(); this@DshLogPage.recompute() }) {
                    this@DshLogPage.sessionSearch = ""; this@DshLogPage.sheet = SheetKind.SESSION
                }
                this@DshLogPage.filterChip(this, { "时间：${this@DshLogPage.timeChipLabel()}" },
                    { this@DshLogPage.customStartMs != null || this@DshLogPage.customEndMs != null },
                    { this@DshLogPage.filters = this@DshLogPage.filters.copy(fromTime = null, toTime = null); this@DshLogPage.recompute() }) {
                    this@DshLogPage.openTimeSheet()
                }
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                this@DshLogPage.filterChip(this, { this@DshLogPage.levelChipLabel() },
                    { this@DshLogPage.selectedLevels.isNotEmpty() },
                    { this@DshLogPage.selectedLevels = emptySet(); this@DshLogPage.recompute() }) {
                    this@DshLogPage.sheet = SheetKind.LEVEL
                }
                this@DshLogPage.filterChip(this, {
                    when (this@DshLogPage.selectedTypes.size) {
                        0 -> "事件类型：全部"
                        1 -> this@DshLogPage.selectedTypes.first()
                        else -> "事件类型：${this@DshLogPage.selectedTypes.size} 项"
                    }
                }, { this@DshLogPage.selectedTypes.isNotEmpty() },
                    { this@DshLogPage.selectedTypes = emptySet(); this@DshLogPage.recompute() }) {
                    this@DshLogPage.typeSearch = ""; this@DshLogPage.sheet = SheetKind.TYPE
                }
            }
            vif({ this@DshLogPage.filters.rpcId.isNotEmpty() }) {
                View {
                    attr { flexDirectionRow() }
                    this@DshLogPage.filterChip(this, { "RPC：${this@DshLogPage.filters.rpcId}" }, { true },
                        { this@DshLogPage.filters = this@DshLogPage.filters.copy(rpcId = ""); this@DshLogPage.recompute() }) {
                        this@DshLogPage.copyText(this@DshLogPage.filters.rpcId, "已复制 RPC ID")
                    }
                }
            }
            vif({ this@DshLogPage.customStartMs != null || this@DshLogPage.customEndMs != null }) {
                Text { attr { text(this@DshLogPage.timeChipLabel()); fontSize(11f); marginTop(4f); color(this@DshLogPage.themeColors.labelSecondary) } }
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter(); minHeight(36f) }
                Text { attr { text("本机已保留日志 · ${this@DshLogPage.retainedTotal} 条"); flex(1f); fontSize(11f); color(this@DshLogPage.themeColors.labelTertiary) } }
                vif({ this@DshLogPage.filters.active }) {
                    Text {
                        attr { text("重置筛选"); fontSize(12f); color(this@DshLogPage.themeColors.stateBusinessPrimary) }
                        event { click { this@DshLogPage.clearFilters() } }
                    }
                }
            }
        }
    }

    private fun filterChip(container: ViewContainer<*, *>, label: () -> String, active: () -> Boolean,
        onClear: () -> Unit, onClick: () -> Unit) = with(container) {
        View {
            attr {
                flex(1f)
                height(40f)
                borderRadius(10f)
                marginRight(4f)
                marginBottom(4f)
                flexDirectionRow()
                alignItemsCenter()
                border(Border(1f, BorderStyle.SOLID, if (active()) this@DshLogPage.themeColors.stateBusinessPrimary else this@DshLogPage.themeColors.borderL2))
                backgroundColor(if (active()) this@DshLogPage.themeColors.specificSelector else this@DshLogPage.themeColors.bgLayer2)
            }
            View {
                attr { flex(1f); height(40f); paddingLeft(8f); paddingRight(4f); flexDirectionRow(); alignItemsCenter() }
                event { click { onClick() } }
                Text {
                    attr {
                        text(label()); flex(1f); lines(1); fontSize(11f)
                        color(if (active()) this@DshLogPage.themeColors.stateBusinessPrimary else this@DshLogPage.themeColors.labelSecondary)
                    }
                }
                Image {
                    attr { src(ImageUri.commonAssets("chevron-down.svg")); size(10f, 10f); marginLeft(3f); tintColor(this@DshLogPage.themeColors.labelTertiary) }
                }
            }
            vif({ active() }) {
                View {
                    attr { width(28f); height(40f); allCenter() }
                    event { click { onClear() } }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(12f, 12f); tintColor(this@DshLogPage.themeColors.stateBusinessPrimary) } }
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
                    placeholder("搜索脱敏日志关键词（普通文本）")
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
        vif({ this@DshLogPage.loading || this@DshLogPage.logTotal > 0 }) {
            View {
                attr { flexDirectionRow(); alignItemsCenter(); height(28f); paddingLeft(12f); paddingRight(12f) }
                Text {
                    attr {
                        text(
                            when {
                                this@DshLogPage.loading -> "读取中…"
                                this@DshLogPage.loadedCount >= this@DshLogPage.logTotal ->
                                    "已显示全部 ${this@DshLogPage.logTotal} 条"
                                this@DshLogPage.loadedCount >= MAX_LOADED ->
                                    "已显示最近 ${this@DshLogPage.loadedCount} 条"
                                else ->
                                    "上滑加载更多 · 已显示 ${this@DshLogPage.loadedCount}/${this@DshLogPage.logTotal} 条"
                            }
                        )
                        flex(1f); fontSize(12f); color(this@DshLogPage.themeColors.labelSecondary)
                    }
                }
            }
        }
        vif({ this@DshLogPage.loadError.isNotEmpty() }) {
            Text { attr { text(this@DshLogPage.loadError); color(this@DshLogPage.themeColors.stateErrorPrimary); fontSize(12f) } }
        }
        vif({ this@DshLogPage.storageWarning.isNotEmpty() && this@DshLogPage.loadError.isEmpty() }) {
            Text { attr { text(this@DshLogPage.storageWarning); color(this@DshLogPage.themeColors.stateWarnPrimary); fontSize(12f) } }
        }
        vif({ !this@DshLogPage.loading && this@DshLogPage.loadError.isEmpty() && this@DshLogPage.retainedTotal == 0 }) {
            Text { attr { text("本机暂无保留日志"); marginTop(60f); alignSelfCenter(); fontSize(14f); color(this@DshLogPage.themeColors.labelTertiary) } }
        }
        vif({ !this@DshLogPage.loading && this@DshLogPage.loadError.isEmpty() && this@DshLogPage.retainedTotal > 0 && this@DshLogPage.logTotal == 0 }) {
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
                        text("${this@DshLogPage.levelShort(entry.level)} · ${entry.type}")
                        fontSize(12f)
                        fontWeightBold()
                        color(this@DshLogPage.themeColors.labelPrimary)
                        flex(1f)
                        lines(1)
                    }
                }
                Text {
                    attr {
                        text(LogExporter.formatTimestamp(entry.timestamp).substring(5, 19))
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
                this@DshLogPage.detailMetaRow(this, "会话", if (e.sessionId.isNullOrEmpty()) "未关联会话" else "${this@DshLogPage.sessionTitle(e.sessionId)}\n${e.sessionId}")
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
            vif({ !e.rpcId.isNullOrEmpty() }) {
                View {
                    attr { margin(12f); minHeight(40f); allCenter(); borderRadius(8f); backgroundColor(this@DshLogPage.themeColors.bgLayer2) }
                    event { click {
                        // A request drill-down intentionally replaces list restrictions with its exact ID.
                        this@DshLogPage.filters = DshLogFilters(rpcId = e.rpcId.orEmpty())
                        this@DshLogPage.closeDetail()
                        this@DshLogPage.recompute()
                    } }
                    Text { attr { text("按此 RPC ID 筛选全部本地日志"); fontSize(14f); color(this@DshLogPage.themeColors.stateBusinessPrimary) } }
                }
            }
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
        this@DshLogPage.sheetScaffold(container, SheetKind.TYPE) { this@DshLogPage.renderTypeSheet(this) }
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
                        paddingBottom(maxOf(20f, pagerData.safeAreaInsets.bottom))
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
        label: () -> String,
        subtitle: () -> String = { "" },
        checked: () -> Boolean = { false },
        dotColor: Long = 0L,
        onClick: () -> Unit,
    ) = with(container) {
        View {
            attr {
                minHeight(if (subtitle().isEmpty()) 44f else 52f)
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
                Text { attr { text(label()); fontSize(14f); lines(1); color(this@DshLogPage.themeColors.labelPrimary) } }
                vif({ subtitle().isNotEmpty() }) {
                    Text { attr { text(subtitle()); fontSize(11f); color(this@DshLogPage.themeColors.labelTertiary); marginTop(2f); marginBottom(4f) } }
                }
            }
            vif({ checked() }) {
                Image { attr { src(ImageUri.commonAssets("check.svg")); size(16f, 16f); tintColor(this@DshLogPage.themeColors.stateBusinessPrimary) } }
            }
        }
    }

    /** 时间范围 Sheet：外层“今天”预设 + 开始/结束时间滚轮，确定后应用。 */
    private fun renderTimeSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "时间范围")
        this@DshLogPage.sheetRow(this, { "全部保留日志" }, checked = { this@DshLogPage.draftAllTime }) {
            this@DshLogPage.draftAllTime = true
        }
        this@DshLogPage.sheetRow(this, { "指定时间范围" }, checked = { !this@DshLogPage.draftAllTime }) {
            this@DshLogPage.draftAllTime = false
        }
        this@DshLogPage.renderCustomRangeSection(this)
    }

    /** 级别多选 Sheet：空集 = 全部；点击行 toggle（不自动关闭），与“会话”Sheet 交互一致。 */
    private fun renderLevelSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "级别（多选）")
        this@DshLogPage.sheetRow(this, { "全部" }, subtitle = { "${this@DshLogPage.levelCounts.values.sum()} 条" }, checked = { this@DshLogPage.selectedLevels.isEmpty() }) {
            this@DshLogPage.selectedLevels = emptySet()
            this@DshLogPage.recompute()
        }
        for (lv in LogLevel.entries) {
            this@DshLogPage.sheetRow(this, { this@DshLogPage.levelShort(lv) }, subtitle = { "${this@DshLogPage.levelCounts[lv] ?: 0} 条" }, checked = { lv in this@DshLogPage.selectedLevels }, dotColor = dshLogLevelColor(lv)) {
                this@DshLogPage.selectedLevels = if (lv in this@DshLogPage.selectedLevels) this@DshLogPage.selectedLevels - lv else this@DshLogPage.selectedLevels + lv
                this@DshLogPage.recompute()
            }
        }
        this@DshLogPage.sheetDone(this)
    }

    private fun renderSessionSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "会话（多选，按标题或 ID 搜索）")
        this@DshLogPage.sheetSearch(this, "输入会话标题或完整 ID", { this@DshLogPage.sessionSearch }) { this@DshLogPage.sessionSearch = it }
        this@DshLogPage.sheetRow(this, { "全部（含未关联会话）" }, checked = { this@DshLogPage.selectedSessions.isEmpty() }) {
            this@DshLogPage.selectedSessions = emptySet()
            this@DshLogPage.recompute()
        }
        Scroller {
            attr { height((pagerData.pageViewHeight * 0.35f).coerceAtMost(300f)); marginTop(4f) }
            vfor({ this@DshLogPage.sessionOptions }) { sid ->
                View {
                    vif({ this@DshLogPage.sessionMatches(sid) }) {
                        this@DshLogPage.sheetRow(this, { this@DshLogPage.sessionLabel(sid) },
                            subtitle = { if (sid == DshLogFilters.UNASSOCIATED) "连接、崩溃等没有 sessionId 的日志" else sid },
                            checked = { sid in this@DshLogPage.selectedSessions }) {
                            this@DshLogPage.selectedSessions = if (sid in this@DshLogPage.selectedSessions) this@DshLogPage.selectedSessions - sid else this@DshLogPage.selectedSessions + sid
                            this@DshLogPage.recompute()
                        }
                    }
                }
            }
            vif({ this@DshLogPage.sessionOptions.none { this@DshLogPage.sessionMatches(it) } }) {
                Text { attr { text("没有匹配的会话"); margin(16f); fontSize(13f); color(this@DshLogPage.themeColors.labelTertiary) } }
            }
        }
        this@DshLogPage.sheetDone(this)
    }

    private fun sessionMatches(sid: String): Boolean = sessionSearch.trim().let {
        sid.contains(it, ignoreCase = true) || sessionLabel(sid).contains(it, ignoreCase = true)
    }

    private fun sheetSearch(container: ViewContainer<*, *>, hint: String, value: () -> String, onChange: (String) -> Unit) = with(container) {
        View {
            attr { margin(12f); height(38f); borderRadius(8f); backgroundColor(this@DshLogPage.themeColors.bgSkeleton) }
            Input {
                attr { height(38f); marginLeft(10f); marginRight(10f); fontSize(13f); text(value()); placeholder(hint); color(this@DshLogPage.themeColors.labelPrimary); placeholderColor(this@DshLogPage.themeColors.labelTertiary) }
                event { textDidChange { onChange(it.text) } }
            }
        }
    }

    private fun sheetDone(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetRow(this, { "完成" }) { this@DshLogPage.sheet = SheetKind.NONE }
    }

    private fun renderTypeSheet(container: ViewContainer<*, *>) = with(container) {
        this@DshLogPage.sheetTitle(this, "事件类型（多选，同一维度取并集）")
        this@DshLogPage.sheetSearch(this, "搜索类型或常用分组", { this@DshLogPage.typeSearch }) { this@DshLogPage.typeSearch = it }
        this@DshLogPage.sheetRow(this, { "全部事件类型" }, checked = { this@DshLogPage.selectedTypes.isEmpty() }) {
            this@DshLogPage.selectedTypes = emptySet(); this@DshLogPage.recompute()
        }
        Scroller {
            attr { height((pagerData.pageViewHeight * 0.4f).coerceAtMost(340f)) }
            for ((name, types) in DshLogFilters.typePresets) {
                vif({ name.contains(this@DshLogPage.typeSearch.trim(), true) || types.any { it.contains(this@DshLogPage.typeSearch.trim(), true) } }) {
                    this@DshLogPage.sheetRow(this, { name }, subtitle = { types.joinToString(" / ") },
                        checked = { this@DshLogPage.selectedTypes.containsAll(types) }) {
                        this@DshLogPage.selectedTypes = if (this@DshLogPage.selectedTypes.containsAll(types)) this@DshLogPage.selectedTypes - types.toSet() else this@DshLogPage.selectedTypes + types
                        this@DshLogPage.recompute()
                    }
                }
            }
            this@DshLogPage.sheetTitle(this, "已记录类型（包含其他 / 新增类型）")
            vfor({ this@DshLogPage.typeOptions }) { type ->
                View {
                    vif({ type.contains(this@DshLogPage.typeSearch.trim(), true) }) {
                        this@DshLogPage.sheetRow(this, { type }, checked = { type in this@DshLogPage.selectedTypes }) {
                            this@DshLogPage.selectedTypes = if (type in this@DshLogPage.selectedTypes) this@DshLogPage.selectedTypes - type else this@DshLogPage.selectedTypes + type
                            this@DshLogPage.recompute()
                        }
                    }
                }
            }
        }
        this@DshLogPage.sheetDone(this)
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
        this@DshLogPage.sheetRow(this, { if (this@DshLogPage.exporting) "导出中..." else "导出当前筛选结果" }, subtitle = { "应用全部筛选条件，包含所有匹配分页" }) {
            this@DshLogPage.sheet = SheetKind.NONE
            this@DshLogPage.exportLogs(false)
        }
        this@DshLogPage.sheetRow(this, { if (this@DshLogPage.exporting) "导出中..." else "导出全部本地日志" }, subtitle = { "包含所有会话及未关联日志，忽略全部筛选" }) {
            this@DshLogPage.sheet = SheetKind.NONE
            this@DshLogPage.exportLogs(true)
        }
        this@DshLogPage.sheetRow(this, { if (this@DshLogPage.feedbackExporting) "生成中..." else "生成问题反馈包" }, subtitle = { "全部本地日志 + 设备与连接信息（忽略筛选）" }) {
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
                this@DshLogPage.rangePresetChip(this, "最近15分钟", 2)
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
                    text(if (this@DshLogPage.draftAllTime) "全部保留日志（确定后生效）" else "${this@DshLogPage.draftStartMs?.let { formatCustomTime(it) }} ~ ${this@DshLogPage.draftEndMs?.let { formatCustomTime(it) }}")
                    fontSize(12f)
                    color(this@DshLogPage.themeColors.labelSecondary)
                    marginTop(6f)
                    textAlignCenter()
                }
            }
            vif({ !this@DshLogPage.draftAllTime && this@DshLogPage.pickerError.isNotEmpty() }) {
                Text { attr { text(this@DshLogPage.pickerError); fontSize(12f); marginTop(6f); color(this@DshLogPage.themeColors.stateErrorPrimary) } }
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
            event { scrollEndEvent { v, _ -> if (v.toInt() != this@DshLogPage.pkYear) { this@DshLogPage.pkYear = v.toInt(); this@DshLogPage.pickerChanged() } } }
        }
        ScrollPicker(itemList = Array(12) { (it + 1).toString() }, defaultIndex = this@DshLogPage.pkMonth - 1) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> if (v.toInt() != this@DshLogPage.pkMonth) { this@DshLogPage.pkMonth = v.toInt(); this@DshLogPage.pickerChanged() } } }
        }
        ScrollPicker(itemList = Array(31) { (it + 1).toString() }, defaultIndex = this@DshLogPage.pkDay - 1) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> if (v.toInt() != this@DshLogPage.pkDay) { this@DshLogPage.pkDay = v.toInt(); this@DshLogPage.pickerChanged() } } }
        }
        ScrollPicker(itemList = Array(24) { (if (it < 10) "0" else "") + it }, defaultIndex = this@DshLogPage.pkHour) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> if (v.toInt() != this@DshLogPage.pkHour) { this@DshLogPage.pkHour = v.toInt(); this@DshLogPage.pickerChanged() } } }
        }
        ScrollPicker(itemList = Array(60) { (if (it < 10) "0" else "") + it }, defaultIndex = this@DshLogPage.pkMinute) {
            attr { itemWidth = 42f; itemHeight = 40f; countPerScreen = 3; itemTextColor = this@DshLogPage.themeColors.labelPrimary }
            event { scrollEndEvent { v, _ -> if (v.toInt() != this@DshLogPage.pkMinute) { this@DshLogPage.pkMinute = v.toInt(); this@DshLogPage.pickerChanged() } } }
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
                    Text { attr { text("清空全部本地日志"); fontSize(18f); fontWeightBold(); color(this@DshLogPage.themeColors.labelPrimary) } }
                    Text {
                        attr {
                            text("将忽略当前筛选，删除手机上的全部本地日志（含所有会话与未关联日志），不影响会话消息、附件和 Host 侧历史。此操作不可恢复。")
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
        // 懒加载单页条数：首屏只加载一页，触底再按此游标增量追加。
        private const val PAGE_SIZE = 100
        // 距列表底部该像素内即视为触底，提前触发下一页加载。
        private const val LOAD_MORE_SLACK_PX = 300f
        // 单次增量轮询最多拉取的新日志数；超过则退回全量刷新。
        private const val NEWER_LIMIT = 500
        // 已加载列表的环形上限，避免长时间开启日志页时内存无界增长。
        private const val MAX_LOADED = 2000
        // 增量轮询下定期做一次全量对账的间隔。
        private const val FULL_REFRESH_INTERVAL_MS = 30_000L
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
