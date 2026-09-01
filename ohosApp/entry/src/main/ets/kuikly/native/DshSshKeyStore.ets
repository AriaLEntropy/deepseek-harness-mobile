import fs from '@ohos.file.fs';
import { picker } from '@kit.CoreFileKit';
import { fileUri } from '@kit.CoreFileKit';
import { DshOhosContext, dshError, pathExists } from './DshOhosContext';
import { toArrayBuffer } from './DshBytes';

export class DshSshKeyStore {
  private readonly root: string;

  constructor() {
    this.root = `${DshOhosContext.filesDir()}/ssh-keys`;
    if (!pathExists(this.root)) {
      fs.mkdirSync(this.root, true);
    }
  }

  async pickKey(): Promise<string> {
    const documentPicker = new picker.DocumentViewPicker(DshOhosContext.require());
    const uris = await documentPicker.select({
      maxSelectNumber: 1,
    });
    if (uris.length === 0) {
      return '';
    }
    const pending = `${this.root}/pending-${Date.now()}.bin`;
    if (!this.copyUri(uris[0], pending)) {
      return '';
    }
    return pending;
  }

  importUri(uri: string): string {
    if (uri.length === 0) {
      return '';
    }
    try {
      const bytes = this.readAll(uri);
      if (bytes.length === 0) {
        return '';
      }
      return this.importBytes(bytes);
    } catch (error) {
      dshError('import ssh key failed', error as Object);
      return '';
    }
  }

  private copyUri(uri: string, dest: string): boolean {
    try {
      const bytes = this.readAll(uri);
      if (bytes.length === 0) {
        return false;
      }
      const file = fs.openSync(dest, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
      try {
        fs.writeSync(file.fd, toArrayBuffer(bytes));
      } finally {
        fs.closeSync(file);
      }
      return true;
    } catch (error) {
      dshError('copy ssh key failed', error as Object);
      return false;
    }
  }

  private readAll(uri: string): Uint8Array {
    try {
      return this.readOpened(fs.openSync(uri, fs.OpenMode.READ_ONLY));
    } catch (error) {
      const path = uri.startsWith('file://') ? new fileUri.FileUri(uri).path : uri;
      return this.readOpened(fs.openSync(path, fs.OpenMode.READ_ONLY));
    }
  }

  private readOpened(file: fs.File): Uint8Array {
    try {
      const stat = fs.statSync(file.fd);
      const buffer = new ArrayBuffer(stat.size);
      fs.readSync(file.fd, buffer);
      return new Uint8Array(buffer);
    } finally {
      fs.closeSync(file);
    }
  }

  importBytes(bytes: Uint8Array): string {
    const keyId = `key-${Date.now()}-${bytes.length.toString(16)}`;
    const file = fs.openSync(`${this.root}/${keyId}.bin`, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE);
    try {
      fs.writeSync(file.fd, toArrayBuffer(bytes));
    } finally {
      fs.closeSync(file);
    }
    return keyId;
  }

  exists(keyId: string): boolean {
    return keyId.length > 0 && pathExists(`${this.root}/${keyId}.bin`);
  }

  readKeyText(keyId: string): string {
    const path = `${this.root}/${keyId}.bin`;
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    try {
      const stat = fs.statSync(file.fd);
      const buffer = new ArrayBuffer(stat.size);
      fs.readSync(file.fd, buffer);
      return bytesToText(new Uint8Array(buffer));
    } finally {
      fs.closeSync(file);
    }
  }

  validateKey(keyId: string): boolean {
    if (!this.exists(keyId)) {
      return false;
    }
    try {
      const text = this.readKeyText(keyId);
      return text.indexOf('BEGIN') >= 0 && text.indexOf('PRIVATE KEY') >= 0;
    } catch (error) {
      return false;
    }
  }

  deleteKey(keyId: string): void {
    const path = `${this.root}/${keyId}.bin`;
    try {
      if (pathExists(path)) {
        fs.unlinkSync(path);
      }
    } catch (error) {
      dshError('delete ssh key failed', error as Object);
    }
  }
}

function bytesToText(bytes: Uint8Array): string {
  let text = '';
  for (let i = 0; i < bytes.length; i++) {
    text += String.fromCharCode(bytes[i]);
  }
  return text;
}
