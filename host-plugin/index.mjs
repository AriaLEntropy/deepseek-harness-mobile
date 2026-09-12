/**
 * DSH mobile Host bridge. Read-only inventory + file-level session management,
 * built only on public Cordis services (no core patches).
 *
 * - `/api/mobile-plugin-inventory/v1/list`  Loader inventory plus redacted config
 *                                           detail for the App's plugin detail page.
 * - `/api/mobile-plugin-inventory/v1/action` enable/disable/reload one Loader entry
 *                                           through the public `Entry.update()` API.
 * - `/api/session-manager/meta`             session createdAt/cwd metadata for the
 *                                           mobile archive page's sort and project
 *                                           grouping, read from `sessionPersistence.list()`.
 * - `/api/session-manager/unarchive`        restore one archived session to the
 *                                           registry-global archive set.
 * - `/api/session-manager/delete`           delete one session's backend artifact.
 * - `/api/session-manager/deleteMany`       delete a batch (all / per project).
 *
 * Deletion is file-level: it removes the backend-owned artifact directory and
 * refuses live sessions. It does NOT touch the workspace archive set or the
 * projection cache — those are private service state. Plugin enable/disable/reload
 * uses only the vendored Cordis loader's public `Entry.update()`, never private
 * state; the bridge refuses to touch itself or expression-gated entries.
 * Loopback-only, same trust boundary as the mobile app's Host.
 */
import { rm } from 'node:fs/promises'
import { dirname } from 'node:path'

export const name = 'dsh-mobile-plugin-inventory'
export const inject = ['webServer', 'loader', 'sessionPersistence']
export const PATH = '/api/mobile-plugin-inventory/v1/list'
export const ACTION_PATH = '/api/mobile-plugin-inventory/v1/action'
export const SESSION_META_PATH = '/api/session-manager/meta'
export const SESSION_DELETE_PATH = '/api/session-manager/delete'
export const SESSION_DELETE_MANY_PATH = '/api/session-manager/deleteMany'
export const SESSION_UNARCHIVE_PATH = '/api/session-manager/unarchive'
const BRIDGE_NAME = 'dsh-mobile-plugin-inventory'
const phases = ['pending', 'loading', 'active', 'failed', null, 'unloading']
const secretKey = /token|password|passphrase|secret|authorization|api.?key|access.?ticket|private.?key|credential/i

export function redact(value, depth = 0, seen = new WeakSet()) {
  if (depth > 4) return '[nested value]'
  if (typeof value === 'string') return value
    .replace(/\bsk-[\w-]{8,}/g, '***')
    .replace(/\b(Bearer|Basic)\s+[^\s"',}]+/gi, '$1 ***')
    .replace(/((?:api.?key|access.?ticket|clientToken|hostToken|token|secret|password)\s*[=:]\s*)(?:"(?:\\.|[^"\\])*"|'[^']*'|[^&\s,}]+)/gi, '$1***')
    .replace(/data:[^\s"']*;base64,[a-z\d+/=_-]*/gi, '[attachment omitted]').slice(0, 2000)
  if (value === null || typeof value === 'boolean' || typeof value === 'number') return value
  if (typeof value !== 'object') return String(value ?? '')
  if (seen.has(value)) return '[circular]'
  seen.add(value)
  if (Array.isArray(value)) return value.slice(0, 20).map(item => redact(item, depth + 1, seen))
  return Object.fromEntries(Object.entries(value).slice(0, 40).map(([key, item]) =>
    [key, secretKey.test(key) ? '***' : redact(item, depth + 1, seen)]))
}

async function failureOf(fiber) {
  if (fiber?.state !== 3) return null
  // Public Cordis Fiber.await() rethrows the actual startup/configuration failure.
  // Only inspect already-failed fibers and bound a concurrent restart's wait.
  let timer
  try {
    await Promise.race([
      fiber.await(),
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('生命周期正在变化，请刷新')), 500) }),
    ])
    return null
  } catch (error) {
    return String(redact(error instanceof Error ? error.message : String(error)))
  } finally { clearTimeout(timer) }
}

/** Whether the App may toggle this entry, plus a readable reason when it may not. */
function toggleState(entry) {
  const own = entry.options.disabled
  if (entry.options.name === BRIDGE_NAME) {
    return { canToggle: false, toggleHint: '桥接插件自身不可在 App 停用' }
  }
  if (own && typeof own === 'object') {
    return { canToggle: false, toggleHint: '启用状态由条件表达式控制' }
  }
  if (entry.disabled && !own) {
    return { canToggle: false, toggleHint: '由上级分组或条件禁用，无法单独启用' }
  }
  return { canToggle: true, toggleHint: null }
}

export async function snapshot(loader) {
  const entries = [...loader.entries()].filter(entry => !entry.options.group)
  return { version: 1, entries: await Promise.all(entries.map(async entry => {
    const fiber = entry.fiber
    const failureSummary = await failureOf(fiber)
    const state = fiber?.state
    const config = fiber?.config ?? entry.options.config ?? {}
    const own = entry.options.disabled
    const toggle = toggleState(entry)
    return {
      entryId: entry.id,
      moduleName: entry.options.name,
      enabled: !entry.disabled,
      fiberPhase: state === undefined ? null : phases[state] ?? (state === 4 ? null : `unknown:${state}`),
      configSummary: JSON.stringify(redact(config)),
      failureSummary: state === 3 ? failureSummary ?? 'Host 未提供失败原因' : null,
      configDetail: JSON.stringify(redact(config), null, 2),
      injectDetail: JSON.stringify(redact(entry.options.inject ?? null)),
      disabledExpr: own && typeof own === 'object' ? String(own.__jsExpr ?? JSON.stringify(own)) : null,
      canToggle: toggle.canToggle,
      toggleHint: toggle.toggleHint,
    }
  })) }
}

/**
 * Mutate one Loader entry through the public `Entry.update()` API. Serialized so
 * overlapping App requests cannot interleave a disable/enable pair.
 */
let actionChain = Promise.resolve()
function queueAction(task) {
  const run = actionChain.then(task, task)
  actionChain = run.then(() => {}, () => {})
  return run
}

export async function performPluginAction(loader, entryId, action) {
  const entry = [...loader.entries()].find(item => item.id === entryId && !item.options.group)
  if (!entry) throw new Error('未找到该插件条目')
  if (entry.options.name === BRIDGE_NAME) throw new Error('桥接插件自身不可启停')
  const own = entry.options.disabled
  if (own && typeof own === 'object') throw new Error('启用状态由条件表达式控制，无法在 App 修改')
  if (action === 'enable') {
    if (entry.disabled && !own) throw new Error('由上级分组或条件禁用，无法单独启用')
    await entry.update({ disabled: null })
  } else if (action === 'disable') {
    await entry.update({ disabled: true })
  } else if (action === 'reload') {
    if (entry.disabled) throw new Error('插件当前未启用，无法重载')
    // Entry.update has no direct restart; a disable→enable pair re-inits it and
    // leaves the persisted flag cleared (same effective state as before).
    await entry.update({ disabled: true })
    await entry.update({ disabled: null })
  } else {
    throw new Error('不支持的操作')
  }
  return entry
}

function send(res, status, value) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' })
  res.end(JSON.stringify(value))
}

function guard(req, res) {
  const address = req.socket.remoteAddress
  if (!['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(address)) {
    send(res, 403, { ok: false, error: { code: 'loopback-required', message: '请通过 SSH 或已配对 Relay 访问' } })
    return false
  }
  if (req.headers.origin && !/^https?:\/\/(127\.0\.0\.1|localhost|\[::1\])(?::\d+)?$/.test(req.headers.origin)) {
    send(res, 403, { ok: false, error: { code: 'origin-denied', message: '不允许此来源' } })
    return false
  }
  return true
}

async function readJson(req) {
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const text = Buffer.concat(chunks).toString('utf8').trim()
  if (!text) return {}
  try { return JSON.parse(text) } catch { return null }
}

export function createHandler(loader) {
  return async (req, res) => {
    if (!guard(req, res)) return
    if (req.method !== 'GET') { send(res, 405, { ok: false, error: { code: 'read-only', message: '插件清单仅支持读取' } }); return }
    try { send(res, 200, { ok: true, ...await snapshot(loader) }) }
    catch (error) { send(res, 500, { ok: false, error: { code: 'inventory-failed', message: String(redact(error.message ?? String(error))) } }) }
  }
}

export function createActionHandler(ctx) {
  return async (req, res) => {
    if (!guard(req, res)) return
    if (req.method !== 'POST') { send(res, 405, { ok: false, error: { code: 'post-required', message: '插件操作仅支持 POST' } }); return }
    const body = await readJson(req)
    if (body === null) { send(res, 400, { ok: false, error: { code: 'bad-json', message: '请求体不是合法 JSON' } }); return }
    const entryId = typeof body.entryId === 'string' ? body.entryId : ''
    const action = typeof body.action === 'string' ? body.action : ''
    if (!entryId || !action) {
      send(res, 400, { ok: false, error: { code: 'bad-request', message: '缺少 entryId 或 action' } }); return
    }
    try {
      const entry = await queueAction(() => performPluginAction(ctx.loader, entryId, action))
      const state = entry.fiber?.state
      send(res, 200, {
        ok: true,
        action,
        entryId: entry.id,
        enabled: !entry.disabled,
        fiberPhase: state === undefined ? null : phases[state] ?? null,
      })
    } catch (error) {
      send(res, 500, { ok: false, error: { code: 'plugin-action-failed', message: String(redact(error?.message ?? String(error))) } })
    }
  }
}

export async function sessionMeta(persistence) {
  const headers = await persistence.list()
  return {
    version: 1,
    sessions: headers.map(header => ({
      sessionId: String(header.id),
      createdAt: Number(header.createdAt) || 0,
      cwd: header.cwd ?? '',
    })),
  }
}

export async function deleteSession(ctx, sessionId) {
  if (typeof sessionId !== 'string' || sessionId.length === 0) throw new Error('缺少 sessionId')
  if (ctx.get('sessions')?.get?.(sessionId) !== undefined) throw new Error('会话正在运行，无法删除')
  const headers = await ctx.sessionPersistence.list()
  const header = headers.find(item => String(item.id) === sessionId)
  if (!header) throw new Error('Host 存储中未找到该会话')
  const location = ctx.sessionPersistence.locate(header)
  if (!location?.path) throw new Error('当前 Host 存储后端不支持按会话删除')
  await rm(dirname(location.path), { recursive: true, force: true })
  return sessionId
}

/**
 * Restore one archived session. The archive set is private registry state with
 * no public unarchive method, so this reaches the registry's own serialized
 * write path and durable global. `setState` writes the `workspace` domain
 * global, which emits `domain/changed`; the Host apiproxy turns that into
 * `host/archived-sessions-changed`, so both the computer and the phone update
 * without a manual refresh. Storage/serialization internals are still TS
 * `private`, hence the runtime feature detection and readable refusal.
 */
export async function unarchiveSession(ctx, sessionId) {
  if (typeof sessionId !== 'string' || sessionId.length === 0) throw new Error('缺少 sessionId')
  const registry = ctx.get('workspaceRegistry')
  if (!registry || registry.state === undefined || typeof registry.setState !== 'function') {
    throw new Error('当前 Host 版本不支持取消归档（workspace registry 状态不可用）')
  }
  const task = async () => {
    const state = registry.state
    const current = Array.isArray(state.archivedSessionIds) ? state.archivedSessionIds : []
    if (!current.includes(sessionId)) return sessionId
    await registry.setState({
      ...state,
      archivedSessionIds: current.filter(id => id !== sessionId),
    })
    return sessionId
  }
  // Serialize against every other registry write (archive included) so a
  // check-then-write pair cannot interleave; fall back to a direct write when
  // the private queue is unavailable.
  return typeof registry.enqueueOperation === 'function'
    ? registry.enqueueOperation(task)
    : task()
}

function createSessionHandler(ctx, kind) {
  return async (req, res) => {
    if (!guard(req, res)) return
    if (kind === 'meta') {
      if (req.method !== 'GET' && req.method !== 'POST') {
        send(res, 405, { ok: false, error: { code: 'read-only', message: '元数据仅支持读取' } }); return
      }
      try { send(res, 200, { ok: true, ...await sessionMeta(ctx.sessionPersistence) }) }
      catch (error) { send(res, 500, { ok: false, error: { code: 'meta-failed', message: String(redact(error?.message ?? String(error))) } }) }
      return
    }
    if (req.method !== 'POST') {
      send(res, 405, { ok: false, error: { code: 'read-only', message: '删除仅支持 POST' } }); return
    }
    const body = await readJson(req)
    if (body === null) { send(res, 400, { ok: false, error: { code: 'bad-json', message: '请求体不是合法 JSON' } }); return }
    try {
      if (kind === 'delete') {
        const sessionId = await deleteSession(ctx, body.sessionId)
        send(res, 200, { ok: true, sessionId })
        return
      }
      if (kind === 'unarchive') {
        const sessionId = await unarchiveSession(ctx, body.sessionId)
        send(res, 200, { ok: true, sessionId })
        return
      }
      const ids = Array.isArray(body.sessionIds) ? body.sessionIds : []
      const deleted = []
      const failed = []
      for (const id of ids) {
        try { await deleteSession(ctx, id); deleted.push(id) }
        catch (error) { failed.push({ sessionId: id, error: String(redact(error?.message ?? String(error))) }) }
      }
      send(res, 200, { ok: true, deleted, failed })
    } catch (error) {
      send(res, 500, { ok: false, error: { code: 'session-op-failed', message: String(redact(error?.message ?? String(error))) } })
    }
  }
}

export function apply(ctx) {
  // Same trust boundary as the mobile app's Host: loopback only, SSH/Relay authenticates access.
  if (ctx.webServer.host !== '127.0.0.1') throw new Error('mobile bridge requires a loopback Host (127.0.0.1)')
  ctx.effect(() => ctx.webServer.register({ kind: 'exact', path: PATH, handler: createHandler(ctx.loader) }),
    'mobile-plugin-inventory.route')
  ctx.effect(() => ctx.webServer.register({ kind: 'exact', path: ACTION_PATH, handler: createActionHandler(ctx) }),
    'mobile-plugin-action.route')
  ctx.effect(() => ctx.webServer.register({ kind: 'exact', path: SESSION_META_PATH, handler: createSessionHandler(ctx, 'meta') }),
    'mobile-session-meta.route')
  ctx.effect(() => ctx.webServer.register({ kind: 'exact', path: SESSION_DELETE_PATH, handler: createSessionHandler(ctx, 'delete') }),
    'mobile-session-delete.route')
  ctx.effect(() => ctx.webServer.register({ kind: 'exact', path: SESSION_DELETE_MANY_PATH, handler: createSessionHandler(ctx, 'deleteMany') }),
    'mobile-session-delete-many.route')
  ctx.effect(() => ctx.webServer.register({ kind: 'exact', path: SESSION_UNARCHIVE_PATH, handler: createSessionHandler(ctx, 'unarchive') }),
    'mobile-session-unarchive.route')
}
