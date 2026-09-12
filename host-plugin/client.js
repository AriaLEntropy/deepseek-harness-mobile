/**
 * Browser half of the DSH mobile bridge, emitted as the client module system's
 * lazy-CJS factory artifact (the shared tsdown preset is not published, so the
 * format is reproduced here by hand).
 *
 * It contributes one extra "插件启停" tab to the official Plugins settings
 * section. The official inventory tab is read-only and intentionally not
 * modified; this tab reuses the Host bridge endpoints that already power the
 * mobile app, so the computer and phone share one toggle path.
 *
 * Runtime requires stay inside the platform module table: only `react` is
 * requested. Copy is resolved from the document language instead of the locale
 * service to keep the bundle dependency-free.
 */
window.__ModuleLoader__.load({
  id: 'dsh-mobile-plugin-inventory',
  factory: (require) => {
    var module = { exports: {} }
    var exports = module.exports

    const React = require('react')
    const { useCallback, useEffect, useMemo, useRef, useState } = React
    const h = React.createElement

    const LIST_URL = '/api/mobile-plugin-inventory/v1/list'
    const ACTION_URL = '/api/mobile-plugin-inventory/v1/action'
    const TAB_ID = 'mobile-plugin-switch'

    const COPY = {
      zh: {
        tab: '插件启停',
        heading: '插件列表',
        search: '搜索插件名称或 ID',
        loading: '正在读取插件…',
        error: '暂时无法读取插件。',
        retry: '重试',
        empty: 'Host 暂无插件。',
        emptySearch: '没有匹配的插件。',
        enabledTag: '已启用',
        disabledTag: '已停用',
        enabled: '启用',
        disabled: '停用',
        reload: '重载',
        reloadHint: '停用后重新启动该插件',
        reloading: '正在重载…',
        id: 'Loader 条目',
        phase: 'Cordis 状态',
        failure: '失败原因',
        gate: '不可启停',
        expression: '禁用表达式',
        config: '脱敏配置',
        inject: 'Injects',
        busy: '处理中…',
        done: '操作已提交，正在同步状态',
        unobserved: '未挂载',
        pending: '等待依赖',
        loadingPhase: '加载中',
        active: '已挂载',
        failed: '挂载失败',
        unloading: '卸载中',
      },
      en: {
        tab: 'Plugin switches',
        heading: 'Plugin list',
        search: 'Search by name or ID',
        loading: 'Reading plugins…',
        error: 'Plugins are temporarily unavailable.',
        retry: 'Retry',
        empty: 'No plugins are available.',
        emptySearch: 'No matching plugins.',
        enabledTag: 'Enabled',
        disabledTag: 'Disabled',
        enabled: 'Enable',
        disabled: 'Disable',
        reload: 'Reload',
        reloadHint: 'Restart after unloading',
        reloading: 'Reloading…',
        id: 'Loader entry',
        phase: 'Cordis status',
        failure: 'Failure',
        gate: 'Not switchable',
        expression: 'Disabled expression',
        config: 'Redacted config',
        inject: 'Injects',
        busy: 'Working…',
        done: 'Request submitted, syncing state',
        unobserved: 'Not mounted',
        pending: 'Waiting for dependencies',
        loadingPhase: 'Loading',
        active: 'Mounted',
        failed: 'Mount failed',
        unloading: 'Unloading',
      },
    }

    const PHASE_COLORS = {
      active: '#22c55e',
      failed: '#ef4444',
      pending: '#f59e0b',
      loading: '#f59e0b',
      unloading: '#f59e0b',
    }

    function dictionary() {
      const documentLang = typeof document !== 'undefined' && document.documentElement
        ? document.documentElement.lang
        : ''
      const source = documentLang || (typeof navigator !== 'undefined' ? navigator.language : '')
      return /^en/i.test(source || '') ? COPY.en : COPY.zh
    }

    function shortName(moduleName) {
      const unscoped = moduleName.startsWith('@')
        ? moduleName.slice(moduleName.indexOf('/') + 1)
        : moduleName
      return unscoped
        .replace(/^cordis:/, '')
        .replace(/^cordis-plugin-/, '')
        .replace(/^dsh-(?:host-|client-)?/, '')
    }

    function phaseLabel(entry, t) {
      if (entry.fiberPhase === null || entry.fiberPhase === undefined) return t.unobserved
      return t[entry.fiberPhase] || entry.fiberPhase
    }

    function actionLabel(action, t) {
      if (action === 'enable') return t.enabled
      if (action === 'disable') return t.disabled
      return t.reload
    }

    function StatusDot(props) {
      const color = PHASE_COLORS[props.phase] || 'rgba(128,128,128,.6)'
      return h('span', {
        role: 'img',
        'aria-label': props.label,
        title: props.label,
        style: {
          flex: '0 0 auto', width: 8, height: 8, borderRadius: '50%', background: color,
        },
      })
    }

    function Toggle(props) {
      const on = props.enabled === true
      const blocked = props.disabled === true || props.busy === true
      return h('button', {
        type: 'button',
        role: 'switch',
        'aria-checked': on ? 'true' : 'false',
        'aria-label': props.label,
        title: props.label,
        disabled: blocked,
        onClick: (event) => {
          event.stopPropagation()
          if (!blocked) props.onToggle(!on)
        },
        style: {
          position: 'relative', flex: '0 0 auto', width: 40, height: 22, padding: 0,
          borderRadius: 11, border: '1px solid ' + (on ? 'transparent' : 'rgba(128,128,128,.45)'),
          background: on ? '#4176e6' : 'rgba(128,128,128,.22)',
          cursor: blocked ? 'not-allowed' : 'pointer',
          opacity: props.disabled === true ? 0.4 : 1,
          transition: 'background .15s ease',
        },
      }, h('span', {
        style: {
          position: 'absolute', top: 2, left: on ? 20 : 2, width: 16, height: 16,
          borderRadius: '50%', background: '#fff', boxShadow: '0 1px 2px rgba(0,0,0,.3)',
          transition: 'left .15s ease', display: 'block',
        },
      }))
    }

    function DetailRow(props) {
      return h('div', { style: { display: 'flex', gap: 8, fontSize: 12, lineHeight: '18px' } },
        h('span', { style: { flex: '0 0 auto', opacity: 0.55, minWidth: 84 } }, props.label),
        h('span', { style: { flex: 1, minWidth: 0, wordBreak: 'break-all' } }, props.value),
      )
    }

    function PreBlock(props) {
      if (!props.value) return null
      return h('div', { style: { marginTop: 8 } },
        h('div', { style: { fontSize: 12, opacity: 0.55, marginBottom: 4 } }, props.label),
        h('pre', {
          style: {
            margin: 0, padding: 8, maxHeight: 200, overflow: 'auto', fontSize: 11,
            lineHeight: '16px', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            background: 'rgba(128,128,128,.08)', borderRadius: 6, whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          },
        }, props.value),
      )
    }

    function PluginCard(props) {
      const entry = props.entry
      const t = props.t
      const status = phaseLabel(entry, t)
      const canToggle = entry.canToggle === true
      const busy = props.busy
      const details = []
      details.push(h(DetailRow, { key: 'id', label: t.id, value: entry.entryId }))
      if (entry.enabled) details.push(h(DetailRow, { key: 'phase', label: t.phase, value: status }))
      if (entry.failureSummary) details.push(h(DetailRow, { key: 'failure', label: t.failure, value: entry.failureSummary }))
      if (entry.disabledExpr) details.push(h(DetailRow, { key: 'expr', label: t.expression, value: entry.disabledExpr }))
      if (entry.toggleHint) details.push(h(DetailRow, { key: 'gate', label: t.gate, value: entry.toggleHint }))
      details.push(h(PreBlock, { key: 'config', label: t.config, value: entry.configDetail }))
      details.push(h(PreBlock, { key: 'inject', label: t.inject, value: entry.injectDetail }))

      return h('li', {
        'data-plugin-entry': entry.entryId,
        'data-open': props.expanded ? 'true' : undefined,
        style: {
          border: '1px solid rgba(128,128,128,.2)', borderRadius: 10,
          background: 'rgba(128,128,128,.04)', overflow: 'hidden',
        },
      },
        h('div', { style: { display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px' } },
          h('button', {
            type: 'button',
            'aria-expanded': props.expanded ? 'true' : 'false',
            onClick: () => { if (!busy) props.onExpand(entry.entryId) },
            style: {
              display: 'flex', flex: 1, alignItems: 'center', gap: 8, minWidth: 0,
              background: 'none', border: 0, padding: 0, margin: 0, cursor: 'pointer',
              color: 'inherit', font: 'inherit', textAlign: 'left',
            },
          },
            h('span', {
              title: entry.moduleName,
              style: { overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 14, fontWeight: 600 },
            }, shortName(entry.moduleName)),
            entry.enabled ? h(StatusDot, { phase: entry.fiberPhase, label: status }) : null,
          ),
          h('span', {
            style: { flex: '0 0 auto', fontSize: 11, opacity: 0.6, whiteSpace: 'nowrap' },
          }, entry.enabled ? t.enabledTag : t.disabledTag),
          h(Toggle, {
            enabled: entry.enabled,
            disabled: !canToggle,
            busy,
            label: canToggle
              ? (entry.enabled ? t.disabled : t.enabled)
              : (entry.toggleHint || t.gate),
            onToggle: (next) => props.onToggle(entry, next ? 'enable' : 'disable'),
          }),
          h('span', { 'aria-hidden': true, style: { flex: '0 0 auto', fontSize: 10, opacity: 0.5 } }, props.expanded ? '▾' : '▸'),
        ),
        props.expanded ? h('div', { style: { padding: '0 12px 12px', display: 'flex', flexDirection: 'column', gap: 6 } },
          ...details,
          entry.enabled && canToggle ? h('div', { style: { marginTop: 8, display: 'flex', justifyContent: 'flex-end' } },
            h('button', {
              type: 'button',
              disabled: busy,
              title: t.reloadHint,
              onClick: () => { if (!busy) props.onReload(entry) },
              style: {
                padding: '6px 14px', borderRadius: 8, border: '1px solid rgba(128,128,128,.35)',
                background: 'transparent', color: 'inherit', cursor: busy ? 'wait' : 'pointer',
                fontSize: 12, font: 'inherit',
              },
            }, t.reload),
          ) : null,
        ) : null,
      )
    }

    function PluginSwitchTab() {
      const t = dictionary()
      const [entries, setEntries] = useState(null)
      const [error, setError] = useState('')
      const [notice, setNotice] = useState('')
      const [busyId, setBusyId] = useState('')
      const [expandedId, setExpandedId] = useState('')
      const [query, setQuery] = useState('')
      const requestRef = useRef(0)

      const load = useCallback(() => {
        const token = ++requestRef.current
        setError('')
        void fetch(LIST_URL, { credentials: 'same-origin', headers: { accept: 'application/json' } })
          .then(async (response) => {
            const body = await response.json().catch(() => null)
            if (!response.ok || !body || body.ok !== true) {
              throw new Error((body && body.error && body.error.message) || ('HTTP ' + response.status))
            }
            return Array.isArray(body.entries) ? body.entries : []
          })
          .then((list) => { if (token === requestRef.current) setEntries(list) })
          .catch((cause) => {
            if (token !== requestRef.current) return
            setEntries([])
            setError(cause && cause.message ? cause.message : String(cause))
          })
      }, [])

      useEffect(() => { load() }, [load])

      const runAction = useCallback((entry, action) => {
        if (busyId) return
        setBusyId(entry.entryId)
        setError('')
        setNotice('')
        void fetch(ACTION_URL, {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ entryId: entry.entryId, action }),
        })
          .then(async (response) => {
            const body = await response.json().catch(() => null)
            if (!response.ok || !body || body.ok !== true) {
              throw new Error((body && body.error && body.error.message) || ('HTTP ' + response.status))
            }
            return body
          })
          .then((body) => {
            setBusyId('')
            setEntries((current) => (current || []).map((item) => item.entryId === entry.entryId
              ? Object.assign({}, item, {
                enabled: body.enabled === true,
                fiberPhase: body.fiberPhase === undefined ? null : body.fiberPhase,
              })
              : item))
            setNotice(actionLabel(action, t) + ' · ' + t.done)
            load()
          })
          .catch((cause) => {
            setBusyId('')
            setError(cause && cause.message ? cause.message : String(cause))
          })
      }, [busyId, load, t])

      const normalized = query.trim().toLowerCase()
      const filtered = useMemo(() => (entries || []).filter((entry) => {
        if (!normalized) return true
        return entry.moduleName.toLowerCase().includes(normalized)
          || entry.entryId.toLowerCase().includes(normalized)
      }), [entries, normalized])

      const banner = (text, color) => h('div', {
        role: 'status',
        style: {
          padding: '8px 10px', borderRadius: 8, fontSize: 12, marginBottom: 8,
          background: 'rgba(128,128,128,.1)', color,
        },
      }, text)

      return h('div', { 'data-plugin-switch-tab': '' },
        h('p', { style: { margin: '0 0 8px', fontSize: 12, opacity: 0.6 } },
          t.heading + '：' + (entries === null ? '…' : filtered.length)),
        h('label', { style: { display: 'flex', alignItems: 'center', marginBottom: 10 } },
          h('input', {
            type: 'search',
            value: query,
            placeholder: t.search,
            'aria-label': t.search,
            onChange: (event) => setQuery(event.currentTarget.value),
            style: {
              width: '100%', boxSizing: 'border-box', padding: '8px 10px', fontSize: 13,
              border: '1px solid rgba(128,128,128,.3)', borderRadius: 8,
              background: 'transparent', color: 'inherit', font: 'inherit',
            },
          }),
        ),
        notice ? banner(notice, 'inherit') : null,
        error ? banner(error, '#ef4444') : null,
        error ? h('button', {
          type: 'button',
          onClick: load,
          style: {
            marginBottom: 10, padding: '6px 14px', borderRadius: 8, fontSize: 12,
            border: '1px solid rgba(128,128,128,.35)', background: 'transparent',
            color: 'inherit', cursor: 'pointer', font: 'inherit',
          },
        }, t.retry) : null,
        entries === null ? h('p', { style: { fontSize: 13, opacity: 0.6 } }, t.loading) : null,
        entries !== null && entries.length === 0 && !error
          ? h('p', { style: { fontSize: 13, opacity: 0.6 } }, t.empty) : null,
        entries !== null && entries.length > 0 && filtered.length === 0
          ? h('p', { style: { fontSize: 13, opacity: 0.6 } }, t.emptySearch) : null,
        h('ul', {
          style: { listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 },
        }, filtered.map((entry) => h(PluginCard, {
          key: entry.entryId,
          entry,
          t,
          expanded: expandedId === entry.entryId,
          busy: busyId === entry.entryId,
          onExpand: (id) => setExpandedId((current) => (current === id ? '' : id)),
          onToggle: runAction,
          onReload: (item) => runAction(item, 'reload'),
        }))),
      )
    }

    // ===================== 已归档的聊天（电脑端 DSH Web） =====================
    const ARCHIVE_META_URL = '/api/session-manager/meta'
    const ARCHIVE_UNARCHIVE_URL = '/api/session-manager/unarchive'
    const ARCHIVE_DELETE_MANY_URL = '/api/session-manager/deleteMany'

    const ARCHIVE_COPY = {
      zh: {
        entry: '已归档的聊天', title: '已归档的聊天', deleteAll: '全部删除', close: '关闭',
        search: '搜索已归档聊天', typeAll: '全部聊天', typeLabel: '类型',
        projectAll: '所有项目', unprojected: '无项目', sortLabel: '排序方式',
        sortUpdated: '更新时间', sortCreated: '创建时间', sortName: '按字母顺序',
        chats: '个聊天', restore: '取消归档', deleteOne: '删除',
        deleteProject: '删除项目中的全部内容',
        loading: '正在同步 Host 归档列表…', empty: '暂无匹配的已归档聊天',
        loadError: '读取归档列表失败', restored: '已取消归档',
        deletedCount: '已删除 {n} 条会话', deletedSome: '已删除 {d} 条，{f} 条失败',
        deleteFail: '删除失败', restoreFail: '取消归档失败', readError: '读取历史失败',
        confirmSession: '删除这条聊天？', confirmProject: '删除「{name}」中的全部内容？',
        confirmAll: '删除全部已归档聊天？',
        confirmBody: '将永久删除该会话及其本地日志，此操作不可恢复。',
        confirmBodyProject: '将永久删除该项目下的 {n} 条会话，此操作不可恢复。',
        confirmBodyAll: '将永久删除全部 {n} 条已归档会话，此操作不可恢复。',
        cancel: '取消', deleting: '删除中…', confirmDelete: '删除',
      },
      en: {
        entry: 'Archived chats', title: 'Archived chats', deleteAll: 'Delete all', close: 'Close',
        search: 'Search archived chats', typeAll: 'All chats', typeLabel: 'Type',
        projectAll: 'All projects', unprojected: 'No project', sortLabel: 'Sort',
        sortUpdated: 'Updated', sortCreated: 'Created', sortName: 'Alphabetical',
        chats: 'chats', restore: 'Unarchive', deleteOne: 'Delete',
        deleteProject: 'Delete everything in this project',
        loading: 'Syncing archived chats…', empty: 'No matching archived chats',
        loadError: 'Failed to read archived chats', restored: 'Unarchived',
        deletedCount: 'Deleted {n} chats', deletedSome: 'Deleted {d}, {f} failed',
        deleteFail: 'Delete failed', restoreFail: 'Unarchive failed', readError: 'Failed to open history',
        confirmSession: 'Delete this chat?', confirmProject: 'Delete everything in "{name}"?',
        confirmAll: 'Delete all archived chats?',
        confirmBody: 'This permanently deletes the chat and its local log; it cannot be undone.',
        confirmBodyProject: 'This permanently deletes {n} chats in this project; it cannot be undone.',
        confirmBodyAll: 'This permanently deletes all {n} archived chats; it cannot be undone.',
        cancel: 'Cancel', deleting: 'Deleting…', confirmDelete: 'Delete',
      },
    }

    function archiveDictionary() {
      const documentLang = typeof document !== 'undefined' && document.documentElement
        ? document.documentElement.lang
        : ''
      const source = documentLang || (typeof navigator !== 'undefined' ? navigator.language : '')
      return /^en/i.test(source || '') ? ARCHIVE_COPY.en : ARCHIVE_COPY.zh
    }

    function archiveTokens() {
      return {
        bg: 'var(--dsw-alias-bg-base)', layer1: 'var(--dsw-alias-bg-layer-1)',
        skeleton: 'var(--dsw-alias-bg-skeleton)', border: 'var(--dsw-alias-border-l1)',
        border2: 'var(--dsw-alias-border-l2)', label: 'var(--dsw-alias-label-primary)',
        label2: 'var(--dsw-alias-label-secondary)', label3: 'var(--dsw-alias-label-tertiary)',
        primary: 'var(--dsw-alias-state-business-primary)',
        danger: 'var(--dsw-alias-state-error-primary)',
        hover: 'var(--dsw-alias-interactive-bg-hover)',
      }
    }

    const ARCHIVE_ICONS = {
      archive: {
        box: '0 0 20 20',
        html: '<path fill="currentColor" fill-rule="evenodd" clip-rule="evenodd" d="M15.8659 2.05975C17.2603 2.05995 18.3913 3.19096 18.3914 4.58527V5.4874C18.3914 6.02747 18.2192 6.52672 17.9303 6.93735C17.9336 6.96524 17.9388 6.99318 17.9388 7.02195V12.8884C17.9388 13.6345 17.9395 14.2379 17.8996 14.7254C17.8642 15.1593 17.7936 15.5499 17.6373 15.9141L17.5654 16.0685C17.278 16.6328 16.8405 17.1046 16.3038 17.434L16.0679 17.5661C15.66 17.7739 15.2196 17.8598 14.7237 17.9003C14.2362 17.9401 13.6327 17.9405 12.8867 17.9405H7.11122C6.36511 17.9405 5.76171 17.9401 5.27418 17.9003C4.84051 17.8649 4.44949 17.7952 4.08545 17.6391L3.93104 17.5661C3.36673 17.2785 2.89392 16.8414 2.56465 16.3044L2.43245 16.0685C2.22473 15.6608 2.13878 15.2211 2.09825 14.7254C2.05841 14.2379 2.05912 13.6345 2.05912 12.8884V7.02195C2.05912 6.99284 2.06422 6.96449 2.06758 6.93629C1.77931 6.52592 1.60858 6.02687 1.60858 5.4874V4.58527C1.60876 3.19084 2.73962 2.05975 4.1341 2.05975H15.8659ZM16.4984 7.92936C16.296 7.98169 16.0847 8.01288 15.8659 8.01291H4.1341C3.91478 8.01291 3.70246 7.98194 3.49955 7.92936V12.8884C3.49955 13.6582 3.50053 14.1927 3.53445 14.608C3.56769 15.0146 3.62923 15.244 3.71635 15.415L3.7925 15.5514C3.98339 15.8627 4.25749 16.1165 4.58464 16.2833L4.72529 16.3435C4.88095 16.3993 5.08638 16.4402 5.39158 16.4651C5.80685 16.4991 6.34138 16.5001 7.11122 16.5001H12.8867C13.6564 16.5001 14.1911 16.499 14.6063 16.4651C15.0128 16.432 15.2423 16.3703 15.4133 16.2833L15.5508 16.2061C15.8618 16.0152 16.116 15.7419 16.2827 15.415L16.3429 15.2732C16.3985 15.1177 16.4396 14.9128 16.4645 14.608C16.4985 14.1927 16.4984 13.6583 16.4984 12.8884V7.92936ZM4.1341 3.50019C3.53511 3.50019 3.0492 3.98631 3.04902 4.58527V5.4874C3.04902 6.08649 3.535 6.57248 4.1341 6.57248H15.8659C16.4648 6.57228 16.951 6.08638 16.951 5.4874V4.58527C16.9509 3.98644 16.4647 3.50038 15.8659 3.50019H4.1341Z"/><path fill="currentColor" d="M12.7962 12.5661V11.0832H7.20548V12.5661L12.7962 12.5661Z"/>',
      },
      folder: {
        box: '0 0 16 16',
        html: '<path d="M5.19629 1.57104C5.81144 1.5711 6.38623 1.8786 6.72754 2.39038L7.19922 3.09839C7.28454 3.22635 7.42824 3.30344 7.58203 3.30347H12.1699C13.5039 3.30348 14.5859 4.38548 14.5859 5.71948V6.62671C15.2694 7.02689 15.6605 7.85012 15.4385 8.68726L14.3848 12.658C14.1037 13.7164 13.1449 14.4527 12.0498 14.4529H2.91699C1.51651 14.4529 0.451662 13.2814 0.501954 11.9519V3.98706C0.501954 2.65305 1.58396 1.57104 2.91797 1.57104H5.19629ZM3.7793 7.75562C3.30994 7.75562 2.89883 8.07153 2.77832 8.52515L1.91602 11.7722C1.74167 12.4291 2.23734 13.073 2.91699 13.073H12.0498C12.5191 13.0728 12.9304 12.757 13.0508 12.3035L14.1045 8.33374C14.1819 8.04202 13.9619 7.756 13.6602 7.75562H3.7793ZM2.91797 2.9519C2.34625 2.9519 1.88281 3.41534 1.88281 3.98706V7.2937C2.33068 6.7269 3.02249 6.37476 3.7793 6.37476H13.2051V5.71948C13.2051 5.14777 12.7416 4.68434 12.1699 4.68433H7.58203C6.96675 4.6843 6.39209 4.37595 6.05078 3.86401L5.5791 3.15601C5.49379 3.02821 5.34995 2.95196 5.19629 2.9519H2.91797Z" fill="currentColor"/>',
      },
      trash: {
        box: '0 0 1024 1024',
        html: '<path fill="currentColor" d="M576.416 736V383.871c0-17.814 14.521-32.256 32.434-32.256 17.912 0 32.433 14.442 32.433 32.256V736c0 17.815-14.52 32.256-32.433 32.256S576.416 753.814 576.416 736z m-193.7 0V383.871c0-17.814 14.522-32.256 32.434-32.256 17.913 0 32.434 14.442 32.434 32.256V736c0 17.815-14.521 32.256-32.434 32.256-17.912 0-32.433-14.441-32.433-32.256z m548.666-512.063H770.116v-64.064c0-52.774-42.885-95.625-95.949-95.872H350.734c-25.645-0.12-50.28 9.929-68.456 27.921-18.176 17.993-28.394 42.446-28.394 67.95v64.065H92.618C76.295 225.86 64 239.622 64 255.969c0 16.346 12.295 30.108 28.618 32.032h838.764C947.705 286.077 960 272.315 960 255.969c0-16.347-12.295-30.11-28.618-32.032zM318.3 159.873c0.482-17.539 14.794-31.574 32.434-31.808h323.433a31.17 31.17 0 0 1 22.597 9.206 30.82 30.82 0 0 1 8.936 22.602v64.064H318.3v-64.064z m418.932 800.126H286.768c-25.645 0.12-50.28-9.929-68.456-27.921-18.176-17.993-28.394-42.446-28.394-67.95V383.871a31.271 31.271 0 0 1 9.232-22.626 31.623 31.623 0 0 1 22.751-9.182 32.076 32.076 0 0 1 22.907 9.157 31.721 31.721 0 0 1 9.526 22.651v480.255c0.482 17.539 14.794 31.574 32.434 31.808h450.464c17.64-0.234 31.952-14.27 32.434-31.808v-478.91c1.933-16.234 15.771-28.462 32.208-28.462 16.436 0 30.274 12.228 32.208 28.461v478.911c0 25.505-10.218 49.958-28.394 67.95-18.176 17.993-42.811 28.041-68.456 27.922z"/>',
      },
      search: {
        box: '0 0 24 24',
        html: '<circle cx="11" cy="11" r="7" fill="none" stroke="currentColor" stroke-width="2"/><path d="m20 20-3.5-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
      },
      chevron: {
        box: '0 0 14 14',
        html: '<path d="M11.8486 5.5L11.4238 5.92383L8.69727 8.65137C8.44157 8.90706 8.21562 9.13382 8.01172 9.29785C7.79912 9.46883 7.55595 9.61756 7.25 9.66602C7.08435 9.69222 6.91565 9.69222 6.75 9.66602C6.44405 9.61756 6.20088 9.46883 5.98828 9.29785C5.78438 9.13382 5.55843 8.90706 5.30273 8.65137L2.57617 5.92383L2.15137 5.5L3 4.65137L3.42383 5.07617L6.15137 7.80273C6.42595 8.07732 6.59876 8.24849 6.74023 8.3623C6.87291 8.46904 6.92272 8.47813 6.9375 8.48047C6.97895 8.48703 7.02105 8.48703 7.0625 8.48047C7.07728 8.47813 7.12709 8.46904 7.25977 8.3623C7.40124 8.24849 7.57405 8.07732 7.84863 7.80273L10.5762 5.07617L11 4.65137L11.8486 5.5Z" fill="currentColor"/>',
      },
      check: {
        box: '0 0 16 16',
        html: '<path d="M15.0498 3.92579L8.49512 12.3818C8.25774 12.6881 8.04517 12.9645 7.84668 13.1689C7.63957 13.3823 7.38732 13.5841 7.04492 13.6719C6.86373 13.7183 6.6757 13.7346 6.48926 13.7197C6.13666 13.6915 5.8528 13.5355 5.6123 13.3604C5.38201 13.1926 5.12573 12.9567 4.83984 12.6953L1.03125 9.21289L1.96875 8.1875L5.77734 11.6699C6.08684 11.9529 6.27773 12.1249 6.43066 12.2363C6.50183 12.2882 6.54699 12.3135 6.57324 12.3252C6.58525 12.3305 6.59269 12.3322 6.5957 12.333C6.59802 12.3336 6.59961 12.334 6.59961 12.334C6.63317 12.3367 6.66758 12.3335 6.7002 12.3252C6.7002 12.3252 6.70211 12.3251 6.7041 12.3242C6.70698 12.3229 6.71348 12.319 6.72461 12.3115C6.74849 12.2956 6.78843 12.2642 6.84961 12.2012C6.98138 12.0654 7.13957 11.8628 7.39648 11.5313L13.9502 3.07422L15.0498 3.92579Z" fill="currentColor"/>',
      },
      more: {
        box: '0 0 24 24',
        html: '<path d="M12 6a2 2 0 1 1 0-4 2 2 0 0 1 0 4zm0 8a2 2 0 1 1 0-4 2 2 0 0 1 0 4zm0 8a2 2 0 1 1 0-4 2 2 0 0 1 0 4z" fill="currentColor"/>',
      },
    }

    function ArchiveIcon(props) {
      const icon = ARCHIVE_ICONS[props.name]
      if (!icon) return null
      return h('svg', {
        viewBox: icon.box,
        width: props.size || 16,
        height: props.size || 16,
        'aria-hidden': 'true',
        style: { display: 'block', flex: '0 0 auto', color: props.color || 'inherit' },
        dangerouslySetInnerHTML: { __html: icon.html },
      })
    }

    function archiveFormatDate(timestamp) {
      if (!timestamp) return ''
      const d = new Date(timestamp)
      const pad = (n) => String(n).padStart(2, '0')
      return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日, '
        + pad(d.getHours()) + ':' + pad(d.getMinutes())
    }

    function archivePostJson(url, payload) {
      return fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(payload),
      }).then(async (response) => {
        const body = await response.json().catch(() => null)
        if (!response.ok || !body || body.ok !== true) {
          throw new Error((body && body.error && body.error.message) || ('HTTP ' + response.status))
        }
        return body
      })
    }

    function fill(template, values) {
      return template.replace(/\{(\w+)\}/g, (_match, key) => String(values[key]))
    }

    function ArchiveMenu(props) {
      return h('div', {
        style: {
          position: 'absolute', top: 'calc(100% + 6px)', zIndex: 40, minWidth: 200,
          padding: '6px 0', borderRadius: 10, background: props.tokens.layer1,
          border: '1px solid ' + props.tokens.border,
          boxShadow: '0 8px 24px rgba(0,0,0,.12)',
        },
      }, ...React.Children.toArray(props.children))
    }

    function ArchiveMenuItem(props) {
      return h('button', {
        type: 'button',
        onClick: props.onClick,
        style: {
          display: 'flex', alignItems: 'center', width: '100%', gap: 8, padding: '8px 14px',
          background: 'transparent', border: 0, cursor: 'pointer', font: 'inherit',
          fontSize: 14, textAlign: 'left', color: props.danger ? props.tokens.danger : props.tokens.label,
        },
      },
        h('span', { style: { flex: 1, minWidth: 0 } }, props.label),
        props.selected ? h(ArchiveIcon, { name: 'check', size: 16, color: props.tokens.primary }) : null,
      )
    }

    function ArchiveEntry(props) {
      const t = archiveDictionary()
      const C = archiveTokens()
      const sessions = props.useSessions((s) => s)
      const workspaces = props.useWorkspaces((s) => s)
      const [open, setOpen] = useState(false)
      const [query, setQuery] = useState('')
      const [projectFilter, setProjectFilter] = useState('')
      const [sort, setSort] = useState('updated')
      const [filterMenu, setFilterMenu] = useState('')
      const [groupMenu, setGroupMenu] = useState('')
      const [meta, setMeta] = useState({})
      const [removed, setRemoved] = useState(() => new Set())
      const [busy, setBusy] = useState(false)
      const [notice, setNotice] = useState('')
      const [error, setError] = useState('')
      const [confirm, setConfirm] = useState(null)

      useEffect(() => {
        if (!open) return undefined
        let alive = true
        setNotice('')
        setError('')
        fetch(ARCHIVE_META_URL, { credentials: 'same-origin', headers: { accept: 'application/json' } })
          .then((response) => response.json().catch(() => null))
          .then((body) => {
            if (!alive || !body || body.ok !== true || !Array.isArray(body.sessions)) return
            const next = {}
            for (const item of body.sessions) next[item.sessionId] = Number(item.createdAt) || 0
            setMeta(next)
          })
          .catch(() => {})
        return () => { alive = false }
      }, [open])

      useEffect(() => {
        if (!open || (!filterMenu && !groupMenu)) return undefined
        const close = () => { setFilterMenu(''); setGroupMenu('') }
        window.addEventListener('click', close)
        return () => { window.removeEventListener('click', close) }
      }, [open, filterMenu, groupMenu])

      useEffect(() => {
        if (!open) return undefined
        const onKey = (event) => {
          if (event.key !== 'Escape') return
          if (confirm) setConfirm(null)
          else setOpen(false)
        }
        window.addEventListener('keydown', onKey)
        return () => { window.removeEventListener('keydown', onKey) }
      }, [open, confirm])

      const archivedIds = (workspaces && workspaces.archivedSessionIds) || []
      const workspaceItems = (workspaces && workspaces.items) || []
      const byId = (sessions && sessions.byId) || {}

      const rows = useMemo(() => archivedIds
        .filter((id) => !removed.has(id))
        .map((id) => byId[id])
        .filter(Boolean), [archivedIds, byId, removed])

      const groups = useMemo(() => {
        const memberOf = {}
        for (const ws of workspaceItems) {
          for (const sid of ws.sessionIds || []) memberOf[sid] = ws.workspaceId
        }
        const buckets = new Map()
        for (const session of rows) {
          const wsid = memberOf[session.id] || ''
          if (!buckets.has(wsid)) {
            const ws = wsid ? workspaceItems.find((item) => item.workspaceId === wsid) : undefined
            buckets.set(wsid, {
              workspaceId: wsid,
              title: ws ? ws.title : t.unprojected,
              sessions: [],
            })
          }
          buckets.get(wsid).sessions.push(session)
        }
        let list = [...buckets.values()]
        if (projectFilter) list = list.filter((group) => group.workspaceId === projectFilter)
        const needle = query.trim().toLowerCase()
        if (needle) {
          list = list
            .map((group) => Object.assign({}, group, {
              sessions: group.sessions.filter((session) => String(session.displayTitle || '')
                .toLowerCase().includes(needle)),
            }))
            .filter((group) => group.sessions.length > 0)
        }
        const createdAt = (session) => meta[session.id] || session.updatedAt || 0
        const compare = sort === 'created'
          ? (a, b) => createdAt(b) - createdAt(a)
          : sort === 'name'
            ? (a, b) => String(a.displayTitle || '').localeCompare(String(b.displayTitle || ''))
            : (a, b) => (b.updatedAt || 0) - (a.updatedAt || 0)
        return list.map((group) => Object.assign({}, group, {
          sessions: [...group.sessions].sort(compare),
        }))
      }, [rows, workspaceItems, projectFilter, query, sort, meta, t.unprojected])

      const projectOptions = useMemo(() => workspaceItems
        .filter((ws) => rows.some((session) => (ws.sessionIds || []).includes(session.id))), [rows, workspaceItems])

      const runUnarchive = (sessionId) => {
        if (busy) return
        setBusy(true)
        setError('')
        setNotice('')
        archivePostJson(ARCHIVE_UNARCHIVE_URL, { sessionId })
          .then(() => { setNotice(t.restored) })
          .catch((cause) => { setError(t.restoreFail + '：' + (cause && cause.message ? cause.message : cause)) })
          .finally(() => { setBusy(false) })
      }

      const runDelete = (ids) => {
        if (busy || !ids || ids.length === 0) return
        setBusy(true)
        setError('')
        setNotice('')
        archivePostJson(ARCHIVE_DELETE_MANY_URL, { sessionIds: ids })
          .then((body) => {
            const deleted = Array.isArray(body.deleted) ? body.deleted : []
            const failed = Array.isArray(body.failed) ? body.failed : []
            setRemoved((prev) => {
              const next = new Set(prev)
              for (const id of deleted) next.add(id)
              return next
            })
            setNotice(failed.length > 0
              ? fill(t.deletedSome, { d: deleted.length, f: failed.length })
              : fill(t.deletedCount, { n: deleted.length }))
          })
          .catch((cause) => { setError(t.deleteFail + '：' + (cause && cause.message ? cause.message : cause)) })
          .finally(() => { setBusy(false) })
      }

      const openSession = (sessionId) => {
        try {
          if (typeof props.openSession === 'function') props.openSession(sessionId)
          setOpen(false)
        } catch (cause) {
          setError(t.readError + '：' + (cause && cause.message ? cause.message : cause))
        }
      }

      const total = rows.length
      const sortLabel = sort === 'created' ? t.sortCreated : sort === 'name' ? t.sortName : t.sortUpdated
      const projectLabel = projectFilter
        ? (projectOptions.find((ws) => ws.workspaceId === projectFilter) || {}).title || t.projectAll
        : t.projectAll

      const filterButton = (key, label, width) => h('button', {
        type: 'button',
        onClick: (event) => { event.stopPropagation(); setGroupMenu(''); setFilterMenu(filterMenu === key ? '' : key) },
        style: {
          display: 'flex', alignItems: 'center', gap: 6, height: 36, boxSizing: 'border-box',
          width: width, padding: '0 12px', borderRadius: 8, cursor: 'pointer', font: 'inherit',
          fontSize: 13, color: C.label, background: C.bg, border: '1px solid ' + C.border2,
        },
      },
        key === 'project' ? h(ArchiveIcon, { name: 'folder', size: 15, color: C.label3 }) : null,
        h('span', { style: { flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', textAlign: 'left' } }, label),
        h(ArchiveIcon, { name: 'chevron', size: 12, color: C.label3 }),
      )

      const body = []

      body.push(h('div', {
        key: 'header',
        style: { display: 'flex', alignItems: 'center', padding: '8px 0 22px' },
      },
        h('h2', { style: { flex: 1, margin: 0, fontSize: 28, fontWeight: 600, color: C.label } }, t.title),
        h('button', {
          type: 'button',
          disabled: busy || total === 0,
          onClick: () => setConfirm({ kind: 'all', title: '', count: total, ids: rows.map((s) => s.id) }),
          style: {
            display: 'flex', alignItems: 'center', gap: 6, padding: '8px 14px', borderRadius: 10,
            border: 0, cursor: (busy || total === 0) ? 'not-allowed' : 'pointer', font: 'inherit',
            fontSize: 14, color: C.danger, background: 'rgba(242,90,90,.12)',
            opacity: (busy || total === 0) ? 0.5 : 1,
          },
        }, h(ArchiveIcon, { name: 'trash', size: 15, color: C.danger }), t.deleteAll),
        h('button', {
          type: 'button',
          'aria-label': t.close,
          onClick: () => setOpen(false),
          style: {
            marginLeft: 10, width: 34, height: 34, borderRadius: 9, border: '1px solid ' + C.border2,
            background: 'transparent', color: C.label2, cursor: 'pointer', fontSize: 18, lineHeight: 1,
          },
        }, '×'),
      ))

      body.push(h('div', {
        key: 'search',
        style: { display: 'flex', alignItems: 'center', gap: 8, height: 44, boxSizing: 'border-box', padding: '0 12px', borderRadius: 10, background: C.skeleton },
      },
        h(ArchiveIcon, { name: 'search', size: 16, color: C.label3 }),
        h('input', {
          type: 'search',
          value: query,
          placeholder: t.search,
          'aria-label': t.search,
          onChange: (event) => setQuery(event.currentTarget.value),
          style: { flex: 1, minWidth: 0, border: 0, outline: 'none', background: 'transparent', color: C.label, font: 'inherit', fontSize: 14 },
        }),
      ))

      body.push(h('div', {
        key: 'filters',
        style: { position: 'relative', display: 'flex', gap: 10, marginTop: 14 },
      },
        h('div', { style: { position: 'relative', flex: '0 0 150px' } },
          filterButton('type', t.typeAll, '100%'),
          filterMenu === 'type' ? h(ArchiveMenu, { tokens: C },
            h('div', { style: { padding: '4px 14px 6px', fontSize: 12, color: C.label3 } }, t.typeLabel),
            h(ArchiveMenuItem, { label: t.typeAll, selected: true, tokens: C, onClick: () => setFilterMenu('') }),
          ) : null,
        ),
        h('div', { style: { position: 'relative', flex: '0 0 190px' } },
          filterButton('project', projectLabel, '100%'),
          filterMenu === 'project' ? h(ArchiveMenu, { tokens: C },
            h(ArchiveMenuItem, { label: t.projectAll, selected: projectFilter === '', tokens: C, onClick: () => { setProjectFilter(''); setFilterMenu('') } }),
            ...projectOptions.map((ws) => h(ArchiveMenuItem, {
              key: ws.workspaceId,
              label: ws.title,
              selected: projectFilter === ws.workspaceId,
              tokens: C,
              onClick: () => { setProjectFilter(ws.workspaceId); setFilterMenu('') },
            })),
          ) : null,
        ),
        h('div', { style: { position: 'relative', flex: '0 0 170px' } },
          filterButton('sort', sortLabel, '100%'),
          filterMenu === 'sort' ? h(ArchiveMenu, { tokens: C },
            h('div', { style: { padding: '4px 14px 6px', fontSize: 12, color: C.label3 } }, t.sortLabel),
            h(ArchiveMenuItem, { label: t.sortUpdated, selected: sort === 'updated', tokens: C, onClick: () => { setSort('updated'); setFilterMenu('') } }),
            h(ArchiveMenuItem, { label: t.sortCreated, selected: sort === 'created', tokens: C, onClick: () => { setSort('created'); setFilterMenu('') } }),
            h(ArchiveMenuItem, { label: t.sortName, selected: sort === 'name', tokens: C, onClick: () => { setSort('name'); setFilterMenu('') } }),
          ) : null,
        ),
      ))

      body.push(h('div', { key: 'status', style: { marginTop: 14, minHeight: 18, fontSize: 13 } },
        error ? h('span', { style: { color: C.danger } }, error) : null,
        !error && notice ? h('span', { style: { color: C.label2 } }, notice) : null,
      ))

      body.push(h('div', { key: 'list', style: { flex: 1, marginTop: 10, overflow: 'auto', paddingBottom: 20 } },
        !workspaces || !sessions
          ? h('div', { style: { padding: '40px 0', textAlign: 'center', color: C.label2, fontSize: 14 } }, t.loading)
          : total === 0
            ? h('div', { style: { padding: '60px 0', textAlign: 'center', color: C.label2, fontSize: 15 } }, t.empty)
            : groups.map((group) => h('div', { key: group.workspaceId || 'none', style: { marginBottom: 18 } },
              h('div', { style: { display: 'flex', alignItems: 'center', height: 40, padding: '0 4px' } },
                h(ArchiveIcon, { name: 'folder', size: 16, color: C.label3 }),
                h('span', { style: { marginLeft: 8, flex: 1, fontSize: 14, fontWeight: 600, color: C.label, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, group.title),
                h('span', { style: { fontSize: 12, color: C.label3, marginRight: 6 } }, group.sessions.length + ' ' + t.chats),
                group.workspaceId ? h('div', { style: { position: 'relative' } },
                  h('button', {
                    type: 'button',
                    'aria-label': t.deleteProject,
                    onClick: (event) => { event.stopPropagation(); setFilterMenu(''); setGroupMenu(groupMenu === group.workspaceId ? '' : group.workspaceId) },
                    style: { width: 28, height: 28, border: 0, borderRadius: 7, background: 'transparent', color: C.label3, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' },
                  }, h(ArchiveIcon, { name: 'more', size: 15, color: C.label3 })),
                  groupMenu === group.workspaceId ? h(ArchiveMenu, { tokens: C },
                    h(ArchiveMenuItem, {
                      label: t.deleteProject,
                      danger: true,
                      tokens: C,
                      onClick: () => {
                        setGroupMenu('')
                        setConfirm({ kind: 'project', title: group.title, count: group.sessions.length, ids: group.sessions.map((s) => s.id) })
                      },
                    }),
                  ) : null,
                ) : null,
              ),
              h('div', {
                style: { borderRadius: 12, background: C.layer1, border: '1px solid ' + C.border, overflow: 'hidden' },
              }, group.sessions.map((session, index) => h('div', {
                key: session.id,
                style: {
                  display: 'flex', alignItems: 'center', gap: 10, minHeight: 62, padding: '10px 14px',
                  borderTop: index === 0 ? 'none' : '1px solid ' + C.border,
                },
              },
                h('button', {
                  type: 'button',
                  onClick: () => openSession(session.id),
                  style: { flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4, border: 0, background: 'transparent', padding: 0, cursor: 'pointer', textAlign: 'left', font: 'inherit' },
                },
                  h('span', { style: { fontSize: 15, color: C.label, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, session.displayTitle || session.id),
                  h('span', { style: { fontSize: 12, color: C.label2 } }, archiveFormatDate(meta[session.id] || session.updatedAt)),
                ),
                h('button', {
                  type: 'button',
                  disabled: busy,
                  onClick: () => runUnarchive(session.id),
                  style: {
                    flex: '0 0 auto', height: 32, padding: '0 12px', borderRadius: 9, cursor: busy ? 'wait' : 'pointer',
                    border: 0, background: C.skeleton, color: C.primary, font: 'inherit', fontSize: 13,
                  },
                }, t.restore),
                h('button', {
                  type: 'button',
                  'aria-label': t.deleteOne,
                  disabled: busy,
                  onClick: () => setConfirm({ kind: 'session', title: session.displayTitle || session.id, count: 1, ids: [session.id] }),
                  style: {
                    flex: '0 0 auto', width: 34, height: 34, border: 0, borderRadius: 8, background: 'transparent',
                    color: C.label3, cursor: busy ? 'wait' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  },
                }, h(ArchiveIcon, { name: 'trash', size: 17, color: C.label3 })),
              ))),
            )),
      ))

      const confirmNode = confirm ? h('div', {
        style: { position: 'fixed', inset: 0, zIndex: 80, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,.4)' },
        onClick: () => { if (!busy) setConfirm(null) },
      }, h('div', {
        onClick: (event) => event.stopPropagation(),
        style: { width: 360, maxWidth: '90vw', borderRadius: 14, background: C.layer1, padding: 18, boxShadow: '0 12px 40px rgba(0,0,0,.25)' },
      },
        h('div', { style: { fontSize: 16, fontWeight: 700, color: C.label } },
          confirm.kind === 'session' ? t.confirmSession
            : confirm.kind === 'project' ? fill(t.confirmProject, { name: confirm.title })
              : t.confirmAll),
        h('div', { style: { marginTop: 10, fontSize: 13, color: C.label2, lineHeight: '20px' } },
          confirm.kind === 'session' ? t.confirmBody
            : confirm.kind === 'project' ? fill(t.confirmBodyProject, { n: confirm.count })
              : fill(t.confirmBodyAll, { n: confirm.count })),
        h('div', { style: { marginTop: 18, display: 'flex', justifyContent: 'flex-end', gap: 10 } },
          h('button', {
            type: 'button',
            disabled: busy,
            onClick: () => setConfirm(null),
            style: { height: 38, padding: '0 16px', borderRadius: 9, border: '1px solid ' + C.border2, background: 'transparent', color: C.label, cursor: 'pointer', font: 'inherit', fontSize: 14 },
          }, t.cancel),
          h('button', {
            type: 'button',
            disabled: busy,
            onClick: () => { const target = confirm; setConfirm(null); runDelete(target.ids) },
            style: { height: 38, padding: '0 16px', borderRadius: 9, border: 0, background: C.danger, color: '#fff', cursor: busy ? 'wait' : 'pointer', font: 'inherit', fontSize: 14 },
          }, busy ? t.deleting : t.confirmDelete),
        ),
      )) : null

      const overlay = open ? h('div', {
        'data-mobile-archive': '',
        style: {
          position: 'fixed', inset: 0, zIndex: 60, background: C.bg,
          display: 'flex', flexDirection: 'column', overflow: 'hidden',
        },
      },
        h('div', {
          style: { flex: 1, minHeight: 0, width: '100%', maxWidth: 980, margin: '0 auto', padding: 'calc(24px + env(safe-area-inset-top, 0px)) 28px 0', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' },
        }, ...body),
        confirmNode,
      ) : null

      return h(React.Fragment, null,
        h('button', {
          type: 'button',
          title: t.entry,
          'aria-label': t.entry,
          onClick: () => { setOpen(true); setFilterMenu(''); setGroupMenu('') },
          style: props.wide === false
            ? { width: 36, height: 36, margin: '0 auto', border: 0, borderRadius: 9, background: 'transparent', color: C.label2, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }
            : { display: 'flex', alignItems: 'center', width: '100%', height: 40, gap: 10, padding: '0 12px', border: 0, borderRadius: 9, background: 'transparent', color: C.label2, cursor: 'pointer', font: 'inherit', fontSize: 14 },
        },
          h(ArchiveIcon, { name: 'archive', size: 20, color: C.label2 }),
          props.wide === false ? null : h('span', { style: { flex: 1, textAlign: 'left' } }, t.entry),
        ),
        overlay,
      )
    }

    function apply(ctx) {
      ctx.slots.inject('settings.plugins.tab', () => ctx.slots.register({
        name: 'settings.plugins.tab',
        id: TAB_ID,
        order: 11,
        label: () => dictionary().tab,
      }, PluginSwitchTab))
      ctx.slots.inject('sidebar.footer.action', () => ctx.slots.register({
        name: 'sidebar.footer.action',
        id: 'mobile-archive',
        order: 20,
        inject: () => ({
          openSession: (sessionId) => {
            const sessions = ctx.get('sessions')
            if (sessions && typeof sessions.open === 'function') sessions.open(sessionId)
          },
        }),
      }, ArchiveEntry))
    }

    module.exports = { apply, inject: ['slots'] }
    return module.exports
  },
})
