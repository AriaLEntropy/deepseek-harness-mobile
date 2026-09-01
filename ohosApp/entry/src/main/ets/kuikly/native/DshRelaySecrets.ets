import fs from '@ohos.file.fs';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { toArrayBuffer, utf8Decode, utf8Encode } from './DshBytes';
import { aesGcmDecrypt, aesGcmEncrypt } from './DshSealedTunnelCrypto';
import { DshOhosContext, dshError, pathExists } from './DshOhosContext';

const TAG_BYTES = 16;

export class DshRelaySecrets {
  private readonly root: string;

  constructor() {
    this.root = `${DshOhosContext.filesDir()}/relay-secrets`;
    if (!pathExists(this.root)) {
      fs.mkdirSync(this.root, true);
    }
  }

  async save(
    masterKeyB64: string,
    clientToken: string,
    hostId: string,
    hostName: string,
    relayOrigin: string,
  ): Promise<void> {
    await this.write('master.bin', utf8Encode(masterKeyB64));
    await this.write('client.bin', utf8Encode(clientToken));
    await this.write('host.bin', utf8Encode(hostId));
    await this.write('name.bin', utf8Encode(hostName));
    await this.write('origin.bin', utf8Encode(relayOrigin));
  }

  async masterKey(): Promise<string> {
    return await this.readString('master.bin');
  }

  async clientToken(): Promise<string> {
    return await this.readString('client.bin');
  }

  async hostId(): Promise<string> {
    return await this.readString('host.bin');
  }

  async hostName(): Promise<string> {
    return await this.readString('name.bin');
  }

  async relayOrigin(): Promise<string> {
    return await this.readString('origin.bin');
  }

  async hasPairing(): Promise<boolean> {
    const master = await this.masterKey();
    const token = await this.clientToken();
    return master.length > 0 && token.length > 0;
  }

  clear(): void {
    const names = ['master.bin', 'client.bin', 'host.bin', 'name.bin', 'origin.bin'];
    for (let i = 0; i < names.length; i++) {
      const path = `${this.root}/${names[i]}`;
      try {
        if (pathExists(path)) {
          fs.unlinkSync(path);
        }
      } catch (error) {
        dshError(`clear ${names[i]} failed`, error as Object);
      }
    }
  }

  private async readString(name: string): Promise<string> {
    const bytes = await this.read(name);
    return bytes ? utf8Decode(bytes) : '';
  }

  private async write(name: string, bytes: Uint8Array): Promise<void> {
    const key = await this.secretKeyBytes();
    const iv = cryptoFramework.createRandom().generateRandomSync(12).data;
    const sealed = await aesGcmEncrypt(key, iv, new Uint8Array(0), bytes);
    const packed = new Uint8Array(1 + iv.length + sealed.length);
    packed[0] = iv.length;
    packed.set(iv, 1);
    packed.set(sealed, 1 + iv.length);
    const file = fs.openSync(`${this.root}/${name}`, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(file.fd, toArrayBuffer(packed));
    } finally {
      fs.closeSync(file);
    }
  }

  private async read(name: string): Promise<Uint8Array | null> {
    const path = `${this.root}/${name}`;
    if (!pathExists(path)) {
      return null;
    }
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    try {
      const stat = fs.statSync(file.fd);
      const buffer = new ArrayBuffer(stat.size);
      fs.readSync(file.fd, buffer);
      const packed = new Uint8Array(buffer);
      const ivLength = packed[0];
      if (ivLength < 12 || ivLength > 32 || packed.length < 1 + ivLength + TAG_BYTES) {
        throw new Error('invalid encrypted secret');
      }
      const iv = packed.subarray(1, 1 + ivLength);
      const rest = packed.subarray(1 + ivLength);
      const key = await this.secretKeyBytes();
      return await aesGcmDecrypt(key, iv, new Uint8Array(0), rest);
    } finally {
      fs.closeSync(file);
    }
  }

  private async secretKeyBytes(): Promise<Uint8Array> {
    const path = `${this.root}/kek.bin`;
    if (pathExists(path)) {
      const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
      try {
        const stat = fs.statSync(file.fd);
        const buffer = new ArrayBuffer(stat.size);
        fs.readSync(file.fd, buffer);
        return new Uint8Array(buffer);
      } finally {
        fs.closeSync(file);
      }
    }
    const raw = cryptoFramework.createRandom().generateRandomSync(32).data;
    const file = fs.openSync(path, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE);
    try {
      fs.writeSync(file.fd, toArrayBuffer(raw));
    } finally {
      fs.closeSync(file);
    }
    return raw;
  }
}
