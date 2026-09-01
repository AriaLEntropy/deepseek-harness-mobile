export const OPCODE_CONTINUATION = 0;
export const OPCODE_TEXT = 1;
export const OPCODE_BINARY = 2;
export const OPCODE_CLOSE = 8;
export const OPCODE_PING = 9;
export const OPCODE_PONG = 10;

export class WsFrame {
  opcode: number;
  payload: Uint8Array;

  constructor(opcode: number, payload: Uint8Array) {
    this.opcode = opcode;
    this.payload = payload;
  }
}

export function isClose(opcode: number): boolean {
  return opcode === OPCODE_CLOSE;
}

export function isPing(opcode: number): boolean {
  return opcode === OPCODE_PING;
}

export function isPong(opcode: number): boolean {
  return opcode === OPCODE_PONG;
}

export function isData(opcode: number): boolean {
  return opcode === OPCODE_CONTINUATION || opcode === OPCODE_TEXT || opcode === OPCODE_BINARY;
}

export function forwardOpcode(opcode: number): number {
  return opcode === OPCODE_BINARY ? OPCODE_BINARY : OPCODE_TEXT;
}

export function encodeUnmasked(opcode: number, payload: Uint8Array): Uint8Array {
  const headerSize = payload.length < 126 ? 2 : (payload.length <= 0xffff ? 4 : 10);
  const out = new Uint8Array(headerSize + payload.length);
  out[0] = 0x80 | (opcode & 0x0f);
  writeLength(out, payload.length, false);
  out.set(payload, headerSize);
  return out;
}

export class FrameReader {
  private buffer: Uint8Array = new Uint8Array(0);

  push(chunk: Uint8Array): void {
    const next = new Uint8Array(this.buffer.length + chunk.length);
    next.set(this.buffer, 0);
    next.set(chunk, this.buffer.length);
    this.buffer = next;
  }

  takeFrame(): WsFrame | null {
    if (this.buffer.length < 2) {
      return null;
    }
    const b1 = this.buffer[0];
    const b2 = this.buffer[1];
    const opcode = b1 & 0x0f;
    const masked = (b2 & 0x80) !== 0;
    let len = b2 & 0x7f;
    let offset = 2;
    if (len === 126) {
      if (this.buffer.length < 4) {
        return null;
      }
      len = ((this.buffer[2] & 0xff) << 8) | (this.buffer[3] & 0xff);
      offset = 4;
    } else if (len === 127) {
      if (this.buffer.length < 10) {
        return null;
      }
      len = 0;
      for (let i = 2; i < 10; i++) {
        len = len * 256 + (this.buffer[i] & 0xff);
      }
      offset = 10;
    }
    const maskLen = masked ? 4 : 0;
    if (this.buffer.length < offset + maskLen + len) {
      return null;
    }
    const mask = masked ? this.buffer.subarray(offset, offset + 4) : new Uint8Array(0);
    const dataStart = offset + maskLen;
    const data = this.buffer.subarray(dataStart, dataStart + len);
    const payload = new Uint8Array(len);
    payload.set(data);
    if (masked) {
      for (let i = 0; i < payload.length; i++) {
        payload[i] = payload[i] ^ mask[i % 4];
      }
    }
    this.buffer = this.buffer.subarray(dataStart + len);
    return new WsFrame(opcode, payload);
  }
}

function writeLength(out: Uint8Array, size: number, masked: boolean): void {
  const maskBit = masked ? 0x80 : 0;
  if (size < 126) {
    out[1] = maskBit | size;
    return;
  }
  if (size <= 0xffff) {
    out[1] = maskBit | 126;
    out[2] = (size >> 8) & 0xff;
    out[3] = size & 0xff;
    return;
  }
  out[1] = maskBit | 127;
  let n = size;
  for (let i = 9; i >= 2; i--) {
    out[i] = n & 0xff;
    n = Math.floor(n / 256);
  }
}
