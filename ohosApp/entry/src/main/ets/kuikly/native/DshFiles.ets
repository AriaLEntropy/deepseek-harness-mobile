import { picker, fileUri } from '@kit.CoreFileKit';
import fs from '@ohos.file.fs';
import { encodeBase64Std } from './DshBytes';
import { mapOf } from './DshJson';
import { DshOhosContext, dshError } from './DshOhosContext';

// Limit bridge allocations before Base64 expansion; host-plugin enforces the same cap.
const MAX_FILE_BYTES = 50 * 1024 * 1024;
const CHUNK_BYTES = 64 * 1024;

/** Native generic-file selection; Host persistence stays in host-plugin's attachment endpoint. */
export class DshFiles {
  private static busy: boolean = false;

  static async pick(): Promise<Record<string, Object>> {
    if (DshFiles.busy) return failure('请先完成当前文件操作');
    DshFiles.busy = true;
    try {
      const documentPicker = new picker.DocumentViewPicker(DshOhosContext.require());
      const uris = await documentPicker.select({ maxSelectNumber: 10 });
      if (uris.length === 0) return cancelled();
      const files: Record<string, Object>[] = [];
      for (const uri of uris) {
        try {
          const entry = await readEntry(uri);
          if (entry) files.push(entry);
        } catch (error) {
          dshError('read picked file failed', error as Object);
        }
      }
      if (files.length === 0) return failure('无法读取文件数据');
      return mapOf(['ok', 'files'], [true, files]);
    } catch (error) {
      dshError('pick file failed', error as Object);
      return failure(errorMessage(error as Object, '无法打开文件选择器'));
    } finally {
      DshFiles.busy = false;
    }
  }
}

async function readEntry(uri: string): Promise<Record<string, Object> | null> {
  const bytes = await readAll(uri);
  if (bytes.length === 0) return null;
  let name = uri.substring(uri.lastIndexOf('/') + 1);
  try {
    name = decodeURIComponent(name);
  } catch (error) {
    // Keep the URI filename.
  }
  if (name.length === 0) name = 'attachment';
  const mime = mimeFromName(name);
  return mapOf(
    ['dataUrl', 'mediaType', 'name', 'bytes'],
    [`data:${mime};base64,${encodeBase64Std(bytes)}`, mime, name, `${bytes.length}`],
  );
}

async function readAll(uri: string): Promise<Uint8Array> {
  let file: fs.File;
  try {
    file = await fs.open(uri, fs.OpenMode.READ_ONLY);
  } catch (error) {
    const path = uri.startsWith('file://') ? new fileUri.FileUri(uri).path : uri;
    file = await fs.open(path, fs.OpenMode.READ_ONLY);
  }
  try {
    const size = (await fs.stat(file.fd)).size;
    if (size <= 0) throw new Error('文件为空');
    if (size > MAX_FILE_BYTES) throw new Error('文件超过 50MB 上限');
    const bytes = new Uint8Array(size);
    const buffer = new ArrayBuffer(Math.min(size, CHUNK_BYTES));
    let offset = 0;
    while (offset < size) {
      const count = await fs.read(file.fd, buffer, {
        offset: offset,
        length: Math.min(buffer.byteLength, size - offset),
      });
      if (count <= 0) throw new Error('文件读取不完整');
      bytes.set(new Uint8Array(buffer, 0, count), offset);
      offset += count;
    }
    return bytes;
  } finally {
    await fs.close(file);
  }
}

function mimeFromName(name: string): string {
  const lower = name.toLowerCase();
  const dot = lower.lastIndexOf('.');
  const ext = dot >= 0 ? lower.substring(dot + 1) : '';
  switch (ext) {
    case 'pdf': return 'application/pdf';
    case 'txt': return 'text/plain';
    case 'md': return 'text/markdown';
    case 'json': return 'application/json';
    case 'csv': return 'text/csv';
    case 'doc': return 'application/msword';
    case 'docx': return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    case 'xls': return 'application/vnd.ms-excel';
    case 'xlsx': return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    case 'ppt': return 'application/vnd.ms-powerpoint';
    case 'pptx': return 'application/vnd.openxmlformats-officedocument.presentationml.presentation';
    case 'zip': return 'application/zip';
    case 'png': return 'image/png';
    case 'jpg':
    case 'jpeg': return 'image/jpeg';
    case 'gif': return 'image/gif';
    case 'webp': return 'image/webp';
    case 'svg': return 'image/svg+xml';
    case 'mp3': return 'audio/mpeg';
    case 'mp4': return 'video/mp4';
    default: return 'application/octet-stream';
  }
}

function cancelled(): Record<string, Object> {
  return mapOf(['ok', 'cancelled'], [false, true]);
}

function failure(message: string): Record<string, Object> {
  return mapOf(['ok', 'error'], [false, message]);
}

function errorMessage(error: Object, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
