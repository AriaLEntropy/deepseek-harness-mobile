/**
 * Host-side generic-file attachment backport for pre-0.1.5 DSH Hosts.
 *
 * The old Host prompt protocol only accepts text and image parts and has no
 * durable generic-file store, so this module owns the bytes itself:
 *
 * - `POST /api/mobile-attachment/v1/upload` writes canonical Base64 bytes into
 *   `<session cwd>/.dsh-attachments/<sha8>-<name>` and returns the absolute path
 *   plus a deterministic handle line the client appends to the prompt text.
 *   The model then reads the saved path with its existing file tools.
 * - `POST /api/mobile-attachment/v1/delete` removes one file inside the same
 *   session's attachment directory.
 * - `GET  /api/mobile-attachment/v1/config` reports the client pre-check limit.
 *
 * Files never enter the Host session log as bytes: only the plain-text handle
 * line is durable, so nothing here depends on the later `FileAttachmentRef`
 * protocol that this Host version does not provide.
 */
import { createHash } from 'node:crypto'
import { mkdir, rename, rm, stat, writeFile } from 'node:fs/promises'
import { basename, join, resolve, sep } from 'node:path'
import { homedir } from 'node:os'

export const ATTACHMENT_DIR = '.dsh-attachments'
export const UPLOAD_PATH = '/api/mobile-attachment/v1/upload'
export const DELETE_PATH = '/api/mobile-attachment/v1/delete'
export const CONFIG_PATH = '/api/mobile-attachment/v1/config'

/** Decoded byte ceiling for one uploaded file. */
export const MAX_FILE_BYTES = 50 * 1024 * 1024
/** Base64 expands by 4/3; leave room for the JSON envelope. */
const MAX_BODY_BYTES = Math.ceil(MAX_FILE_BYTES / 3) * 4 + 64 * 1024
const MAX_NAME_LENGTH = 120
const SESSION_ID_PATTERN = /^[A-Za-z0-9._-]{1,120}$/

/**
 * Linear Base64 alphabet check. A repeated-group regex over a large (multi-MB)
 * payload overflows the V8 regex stack, so validate with a single scan instead.
 */
function isCanonicalBase64(data) {
  const len = data.length
  if (len === 0 || len % 4 !== 0) return false
  let padding = 0
  if (data.charCodeAt(len - 1) === 61) padding++ // '='
  if (len >= 2 && data.charCodeAt(len - 2) === 61) padding++
  const bodyEnd = len - padding
  for (let i = 0; i < bodyEnd; i++) {
    const c = data.charCodeAt(i)
    const ok =
      (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || (c >= 48 && c <= 57) || c === 43 || c === 47
    if (!ok) return false
  }
  for (let i = bodyEnd; i < len; i++) {
    if (data.charCodeAt(i) !== 61) return false
  }
  return true
}

export class AttachmentError extends Error {
  constructor(code, message) {
    super(message)
    this.name = 'AttachmentError'
    this.code = code
  }
}

function attachmentError(code, message) {
  return new AttachmentError(code, message)
}

/** Strip any path component and unsafe characters from a client-supplied name. */
export function sanitizeName(raw) {
  const leaf = basename(String(raw ?? '').replace(/\\/g, '/'))
  const cleaned = leaf
    .replace(/[\u0000-\u001f\u007f/\\:*?"<>|]/g, '_')
    .replace(/^\.+/, '')
    .trim()
    .slice(0, MAX_NAME_LENGTH)
  return cleaned.length > 0 ? cleaned : 'attachment'
}

/**
 * The fallback attachment directory uses the session id as a single path segment,
 * so it must not contain separators or traversal (`.`, `..`).
 */
export function sanitizeSessionId(raw) {
  const value = String(raw ?? '')
  if (!SESSION_ID_PATTERN.test(value) || value === '.' || value === '..') {
    throw attachmentError('bad-request', '缺少或非法 sessionId')
  }
  return value
}

/** Decode strict canonical Base64 and enforce the byte ceiling. */
export function decodeBase64(data) {
  if (typeof data !== 'string' || data.length === 0) {
    throw attachmentError('bad-request', '缺少文件内容')
  }
  if (data.length > MAX_BODY_BYTES) {
    throw attachmentError('file-too-large', `文件超过上限（${MAX_FILE_BYTES} 字节）`)
  }
  if (!isCanonicalBase64(data)) {
    throw attachmentError('bad-encoded', '文件内容不是规范 Base64')
  }
  const bytes = Buffer.from(data, 'base64')
  if (bytes.length === 0) throw attachmentError('bad-request', '文件内容为空')
  if (bytes.length > MAX_FILE_BYTES) {
    throw attachmentError('file-too-large', `文件超过上限（${MAX_FILE_BYTES} 字节）`)
  }
  return bytes
}

/**
 * Deterministic model-facing handle. Kept stable so the client and future
 * readers can parse the same line; the model only needs the trailing path.
 */
export function formatHandle({ name, bytes, sha256, path }) {
  return `[file] ${name} (${bytes} bytes) sha256:${sha256.slice(0, 12)} path: ${path}`
}

async function sessionCwd(persistence, sessionId) {
  if (typeof sessionId !== 'string' || sessionId.length === 0) {
    throw attachmentError('bad-request', '缺少 sessionId')
  }
  const headers = await persistence.list()
  const header = headers.find((item) => String(item.id) === sessionId)
  return typeof header?.cwd === 'string' ? header.cwd : ''
}

/**
 * Workspace-local directory when the session has a cwd; otherwise a
 * plugin-managed fallback under the Host home. The fallback keeps blank
 * sessions usable; the model still receives an absolute readable path.
 */
function attachmentDirectory(cwd, sessionId) {
  if (cwd !== '') return join(cwd, ATTACHMENT_DIR)
  const segment = sanitizeSessionId(sessionId)
  return join(homedir(), '.dsh-mobile-attachments', segment)
}

/**
 * Persist one file verbatim inside its session's attachment directory.
 * Content-addressed prefix prevents collisions while keeping the leaf name
 * recognizable to the model.
 */
export async function saveAttachment({ persistence, sessionId, name, data, mediaType }) {
  const bytes = decodeBase64(data)
  const safeName = sanitizeName(name)
  const cwd = await sessionCwd(persistence, sessionId)
  const digest = createHash('sha256').update(bytes).digest('hex')
  const dir = attachmentDirectory(cwd, sessionId)
  await mkdir(dir, { recursive: true })
  const storedName = `${digest.slice(0, 8)}-${safeName}`
  const target = join(dir, storedName)
  let exists = true
  try {
    await stat(target)
  } catch {
    exists = false
  }
  if (!exists) {
    const temp = `${target}.${process.pid}.tmp`
    await writeFile(temp, bytes)
    await rename(temp, target)
  }
  return {
    attachmentId: `sha256:${digest}`,
    sessionId,
    name: safeName,
    storedName,
    bytes: bytes.length,
    mediaType: typeof mediaType === 'string' && mediaType.length > 0 ? mediaType : 'application/octet-stream',
    sha256: digest,
    path: target,
    handle: formatHandle({ name: safeName, bytes: bytes.length, sha256: digest, path: target }),
  }
}

/** Delete one file, refusing any path outside the session's attachment directory. */
export async function deleteAttachment({ persistence, sessionId, path }) {
  const cwd = await sessionCwd(persistence, sessionId)
  const dir = resolve(attachmentDirectory(cwd, sessionId))
  const target = resolve(String(path ?? ''))
  if (target !== dir && !target.startsWith(dir + sep)) {
    throw attachmentError('forbidden', '只能删除本会话附件目录内的文件')
  }
  await rm(target, { force: true })
  return { path: target }
}

function readJsonLimited(req, readJson) {
  const declared = Number(req.headers['content-length'])
  if (Number.isFinite(declared) && declared > MAX_BODY_BYTES) {
    throw attachmentError('file-too-large', `请求体超过上限（${MAX_BODY_BYTES} 字节）`)
  }
  return readJson(req)
}

function attachmentFailure(error, redact) {
  if (error instanceof AttachmentError) {
    return { code: error.code, message: error.message }
  }
  return { code: 'attachment-failed', message: String(redact(error?.message ?? String(error))) }
}

export function createConfigHandler({ guard, send }) {
  return (req, res) => {
    if (!guard(req, res)) return
    if (req.method !== 'GET' && req.method !== 'POST') {
      send(res, 405, { ok: false, error: { code: 'read-only', message: '配置仅支持读取' } })
      return
    }
    send(res, 200, {
      ok: true,
      version: 1,
      maxBytes: MAX_FILE_BYTES,
      directory: ATTACHMENT_DIR,
      handlePrefix: '[file]',
    })
  }
}

export function createUploadHandler(ctx, { guard, send, readJson, redact }) {
  return async (req, res) => {
    if (!guard(req, res)) return
    if (req.method !== 'POST') {
      send(res, 405, { ok: false, error: { code: 'post-required', message: '上传仅支持 POST' } })
      return
    }
    let body
    try {
      body = await readJsonLimited(req, readJson)
    } catch (error) {
      // Business failures keep HTTP 200 so the App can read {ok:false,error}.
      send(res, 200, { ok: false, error: attachmentFailure(error, redact) })
      return
    }
    if (body === null) {
      send(res, 200, { ok: false, error: { code: 'bad-json', message: '请求体不是合法 JSON' } })
      return
    }
    try {
      const attachment = await saveAttachment({
        persistence: ctx.sessionPersistence,
        sessionId: body.sessionId,
        name: body.name,
        data: body.data,
        mediaType: body.mediaType,
      })
      send(res, 200, { ok: true, version: 1, attachment })
    } catch (error) {
      send(res, 200, { ok: false, error: attachmentFailure(error, redact) })
    }
  }
}

export function createDeleteHandler(ctx, { guard, send, readJson, redact }) {
  return async (req, res) => {
    if (!guard(req, res)) return
    if (req.method !== 'POST') {
      send(res, 405, { ok: false, error: { code: 'post-required', message: '删除仅支持 POST' } })
      return
    }
    const body = await readJson(req)
    if (body === null) {
      send(res, 400, { ok: false, error: { code: 'bad-json', message: '请求体不是合法 JSON' } })
      return
    }
    try {
      const result = await deleteAttachment({
        persistence: ctx.sessionPersistence,
        sessionId: body.sessionId,
        path: body.path,
      })
      send(res, 200, { ok: true, version: 1, ...result })
    } catch (error) {
      send(res, 200, { ok: false, error: attachmentFailure(error, redact) })
    }
  }
}

/** Register every attachment route on the bridge's web server. */
export function registerAttachmentRoutes(ctx, helpers) {
  ctx.effect(
    () => ctx.webServer.register({ kind: 'exact', path: UPLOAD_PATH, handler: createUploadHandler(ctx, helpers) }),
    'mobile-attachment.upload.route',
  )
  ctx.effect(
    () => ctx.webServer.register({ kind: 'exact', path: DELETE_PATH, handler: createDeleteHandler(ctx, helpers) }),
    'mobile-attachment.delete.route',
  )
  ctx.effect(
    () => ctx.webServer.register({ kind: 'exact', path: CONFIG_PATH, handler: createConfigHandler(helpers) }),
    'mobile-attachment.config.route',
  )
}
