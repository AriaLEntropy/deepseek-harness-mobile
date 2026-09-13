import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve, sep } from 'node:path'

import {
  ATTACHMENT_DIR,
  decodeBase64,
  deleteAttachment,
  formatHandle,
  sanitizeName,
  sanitizeSessionId,
  saveAttachment,
} from './attachment.mjs'

async function withTempDir(fn) {
  const dir = await mkdtemp(join(tmpdir(), 'dsh-attach-'))
  try {
    return await fn(dir)
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
}

function persistenceFor(dir) {
  return { list: async () => [{ id: 's1', cwd: dir }] }
}

test('sanitizeName strips paths and unsafe characters', () => {
  assert.equal(sanitizeName('../../etc/passwd'), 'passwd')
  assert.equal(sanitizeName('a/b\\c.txt'), 'c.txt')
  assert.equal(sanitizeName('..hidden'), 'hidden')
  assert.equal(sanitizeName('file:name.txt'), 'file_name.txt')
  assert.equal(sanitizeName('a*b?c"d'), 'a_b_c_d')
  assert.equal(sanitizeName(''), 'attachment')
  assert.equal(sanitizeName(null), 'attachment')
  assert.equal(sanitizeName('   '), 'attachment')
  assert.equal(sanitizeName('x'.repeat(500)).length, 120)
})

test('decodeBase64 accepts canonical input and rejects the rest', () => {
  assert.equal(decodeBase64('aGVsbG8=').toString(), 'hello')
  assert.equal(decodeBase64(Buffer.from('hi').toString('base64')).toString(), 'hi')

  for (const bad of ['aGVsbG8', '!!!!', '====']) {
    assert.throws(() => decodeBase64(bad), (err) => err.code === 'bad-encoded', `expected bad-encoded for ${bad}`)
  }
  for (const empty of ['', null, undefined, 42]) {
    assert.throws(() => decodeBase64(empty), (err) => err.code === 'bad-request')
  }
})

test('formatHandle is a stable model-facing line', () => {
  const handle = formatHandle({ name: 'a.txt', bytes: 5, sha256: 'abcdef0123456789', path: '/x/a.txt' })
  assert.equal(handle, '[file] a.txt (5 bytes) sha256:abcdef012345 path: /x/a.txt')
})

test('saveAttachment sanitizes name and writes content-addressed file', async () => {
  await withTempDir(async (dir) => {
    const persistence = persistenceFor(dir)
    const result = await saveAttachment({
      persistence,
      sessionId: 's1',
      name: '../evil.txt',
      data: Buffer.from('hello').toString('base64'),
    })

    const expectedDigest = createHash('sha256').update('hello').digest('hex')
    assert.equal(result.name, 'evil.txt')
    assert.equal(result.bytes, 5)
    assert.equal(result.sha256, expectedDigest)
    assert.equal(result.storedName, `${expectedDigest.slice(0, 8)}-evil.txt`)
    assert.ok(result.path.startsWith(resolve(dir, ATTACHMENT_DIR) + sep), result.path)
    assert.ok(result.handle.includes(result.path))
    assert.equal((await readFile(result.path)).toString(), 'hello')
  })
})

test('saveAttachment requires a sessionId', async () => {
  await withTempDir(async (dir) => {
    await assert.rejects(
      () => saveAttachment({ persistence: persistenceFor(dir), sessionId: '', name: 'a', data: 'aGk=' }),
      (err) => err.code === 'bad-request',
    )
  })
})

test('deleteAttachment only removes files inside the attachment directory', async () => {
  await withTempDir(async (dir) => {
    const persistence = persistenceFor(dir)
    const attachDir = join(dir, ATTACHMENT_DIR)
    await mkdir(attachDir, { recursive: true })

    const inside = join(attachDir, 'a.txt')
    await writeFile(inside, 'x')
    await deleteAttachment({ persistence, sessionId: 's1', path: inside })
    await assert.rejects(() => readFile(inside), { code: 'ENOENT' })

    const outside = join(dir, 'outside.txt')
    await writeFile(outside, 'x')
    await assert.rejects(
      () => deleteAttachment({ persistence, sessionId: 's1', path: outside }),
      (err) => err.code === 'forbidden',
    )
    await assert.rejects(
      () => deleteAttachment({ persistence, sessionId: 's1', path: join(attachDir, '..', 'outside.txt') }),
      (err) => err.code === 'forbidden',
    )
    assert.equal((await readFile(outside)).toString(), 'x')
  })
})

test('sanitizeSessionId rejects path traversal and separators', () => {
  assert.equal(sanitizeSessionId('s1'), 's1')
  assert.equal(sanitizeSessionId('abc-123_x.y'), 'abc-123_x.y')
  for (const bad of ['..', '.', 'a/b', 'a\\b', '', null, 'x'.repeat(121)]) {
    assert.throws(() => sanitizeSessionId(bad), (err) => err.code === 'bad-request', `expected reject: ${bad}`)
  }
})

test('fallback attachment directory cannot escape via sessionId', async () => {
  const persistence = { list: async () => [] }
  await assert.rejects(
    () => saveAttachment({ persistence, sessionId: '..', name: 'x.txt', data: 'aGk=' }),
    (err) => err.code === 'bad-request',
  )
  await assert.rejects(
    () => deleteAttachment({ persistence, sessionId: '..', path: join(tmpdir(), 'anything') }),
    (err) => err.code === 'bad-request',
  )
})

test('decodeBase64 validates large legal payloads without stack overflow', () => {
  const bytes = Buffer.alloc(5 * 1024 * 1024, 0x41) // 5 MiB
  const decoded = decodeBase64(bytes.toString('base64'))
  assert.equal(decoded.length, bytes.length)
  assert.equal(decoded[0], 0x41)
})
