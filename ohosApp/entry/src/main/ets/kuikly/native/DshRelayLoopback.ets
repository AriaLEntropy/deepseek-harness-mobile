import { socket } from '@kit.NetworkKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { decodeBase64Std, encodeBase64Std, utf8Encode, toArrayBuffer, fromArrayBuffer } from './DshBytes';
import { jsonBool, jsonNumber, jsonObject, jsonString, mapOf } from './DshJson';
import { dshError, dshLog } from './DshOhosContext';
import {
  FrameReader,
  OPCODE_PONG,
  encodeUnmasked,
  forwardOpcode,
  isClose,
  isData,
  isPing,
  isPong,
} from './DshWebSocketFrames';

type SendInner = (type: string, payload: Record<string, Object>, channel: string) => void;

class Waiter {
  private readonly queued: Record<string, Object>[] = [];
  private pending: ((value: Record<string, Object> | null) => void) | null = null;
  private timer: number = -1;

  offer(value: Record<string, Object>): void {
    if (this.pending) {
      const resolve = this.pending;
      this.pending = null;
      if (this.timer >= 0) {
        clearTimeout(this.timer);
        this.timer = -1;
      }
      resolve(value);
      return;
    }
    this.queued.push(value);
  }

  poll(timeoutMs: number): Promise<Record<string, Object> | null> {
    if (this.queued.length > 0) {
      return Promise.resolve(this.queued.shift() as Record<string, Object>);
    }
    return new Promise<Record<string, Object> | null>((resolve) => {
      this.pending = resolve;
      this.timer = setTimeout(() => {
        this.timer = -1;
        this.pending = null;
        resolve(null);
      }, timeoutMs);
    });
  }

  cancel(): void {
    if (this.timer >= 0) {
      clearTimeout(this.timer);
      this.timer = -1;
    }
    if (this.pending) {
      const resolve = this.pending;
      this.pending = null;
      resolve(null);
    }
  }
}

class ClientConn {
  client: socket.TCPSocketConnection;
  buffer: Uint8Array = new Uint8Array(0);
  upgraded: boolean = false;
  channel: string = '';
  reader: FrameReader = new FrameReader();
  sending: boolean = false;

  constructor(client: socket.TCPSocketConnection) {
    this.client = client;
  }
}

export class DshRelayLoopbackServer {
  private readonly token: string;
  private readonly sendInner: SendInner;
  private server: socket.TCPSocketServer | null = null;
  port: number = 0;
  private stopped: boolean = false;
  private httpCount: number = 0;
  private wsCount: number = 0;
  private readonly waiters: Map<string, Waiter> = new Map();
  private readonly sockets: Map<string, ClientConn> = new Map();

  constructor(token: string, sendInner: SendInner) {
    this.token = token;
    this.sendInner = sendInner;
  }

  async start(): Promise<number> {
    let lastError: Object | null = null;
    for (let attempt = 0; attempt < 8; attempt++) {
      const candidate = 18780 + Math.floor(Math.random() * 400);
      const server = socket.constructTCPSocketServerInstance();
      try {
        await server.listen({ address: '127.0.0.1', port: candidate, family: 1 });
        this.server = server;
        this.port = candidate;
        lastError = null;
        break;
      } catch (error) {
        lastError = error as Object;
      }
    }
    if (this.port <= 0 || !this.server) {
      throw new Error(`loopback bind failed ${JSON.stringify(lastError)}`);
    }
    const bound = this.server;
    bound.on('connect', (client: socket.TCPSocketConnection) => {
      this.accept(client);
    });
    dshLog(`loopback listening port=${this.port}`);
    return this.port;
  }

  isListening(): boolean {
    return !this.stopped && this.port > 0 && this.server !== null;
  }

  stop(): void {
    this.stopped = true;
    this.waiters.forEach((waiter) => waiter.cancel());
    this.waiters.clear();
    this.sockets.forEach((conn) => {
      try {
        conn.client.close();
      } catch (error) {
      }
    });
    this.sockets.clear();
    if (this.server) {
      try {
        this.server.off('connect');
      } catch (error) {
      }
      this.server = null;
    }
  }

  onInner(type: string, payload: Record<string, Object>, channel: string): void {
    dshLog(`inner recv type=${type} channel=${channel}`);
    if (type === 'http_res') {
      this.waiters.get(channel)?.offer(payload);
      return;
    }
    if (type === 'ws_open_ok') {
      this.waiters.get(channel)?.offer(mapOf(['ok'], [true]));
      return;
    }
    if (type === 'ws_frame') {
      this.writeWsFrame(channel, payload);
      return;
    }
    if (type === 'ws_close') {
      const waiter = this.waiters.get(channel);
      if (waiter) {
        const fail = mapOf(
          ['ok', 'reason'],
          [false, jsonString(payload, 'reason', 'host websocket closed')],
        );
        waiter.offer(fail);
      } else {
        const conn = this.sockets.get(channel);
        this.sockets.delete(channel);
        try {
          conn?.client.close();
        } catch (error) {
        }
      }
    }
  }

  private accept(client: socket.TCPSocketConnection): void {
    const conn = new ClientConn(client);
    client.on('message', (value: socket.SocketMessageInfo) => {
      const chunk = fromArrayBuffer(value.message);
      if (conn.upgraded) {
        conn.reader.push(chunk);
        this.drainWs(conn);
        return;
      }
      const next = new Uint8Array(conn.buffer.length + chunk.length);
      next.set(conn.buffer, 0);
      next.set(chunk, conn.buffer.length);
      conn.buffer = next;
      this.tryHttp(conn);
    });
    client.on('close', () => {
      if (conn.channel.length > 0) {
        this.sockets.delete(conn.channel);
        if (conn.upgraded) {
          const payload = mapOf(['code', 'reason'], [1000, '']);
          this.sendInner('ws_close', payload, conn.channel);
          this.wsCount = Math.max(0, this.wsCount - 1);
        }
      }
    });
    client.on('error', (error: Object) => {
      dshError('loopback client error', error);
    });
  }

  private tryHttp(conn: ClientConn): void {
    const split = indexOfHeaderEnd(conn.buffer);
    if (split < 0) {
      if (conn.buffer.length > 64 * 1024) {
        this.writeHttp(conn, 400, 'bad request');
      }
      return;
    }
    const headerBytes = conn.buffer.subarray(0, split);
    const headerText = bytesToAscii(headerBytes);
    const lines = headerText.split('\r\n');
    const request = (lines[0] ?? '').split(' ');
    if (request.length < 2) {
      this.writeHttp(conn, 400, 'bad request');
      return;
    }
    const method = request[0];
    const path = request[1];
    const headers: Record<string, string> = {};
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      const idx = line.indexOf(':');
      if (idx > 0) {
        headers[line.substring(0, idx).trim().toLowerCase()] = line.substring(idx + 1).trim();
      }
    }
    const length = Number(headers['content-length'] ?? '0');
    const bodyStart = split + 4;
    if (Number.isNaN(length) || length < 0) {
      this.writeHttp(conn, 400, 'bad request');
      return;
    }
    if (conn.buffer.length < bodyStart + length) {
      return;
    }
    if (headers['authorization'] !== `Bearer ${this.token}`) {
      this.writeHttp(conn, 401, 'unauthorized');
      return;
    }
    const body = conn.buffer.subarray(bodyStart, bodyStart + length);
    conn.buffer = conn.buffer.subarray(bodyStart + length);
    if ((headers['upgrade'] ?? '').toLowerCase() === 'websocket') {
      this.acceptWebSocket(conn, path, headers);
      return;
    }
    if (length > 2 * 1024 * 1024) {
      this.writeHttp(conn, 413, 'request too large');
      return;
    }
    this.proxyHttp(conn, method, path, headers, body);
  }

  private async proxyHttp(
    conn: ClientConn,
    method: string,
    path: string,
    headers: Record<string, string>,
    body: Uint8Array,
  ): Promise<void> {
    if (this.httpCount + 1 > 32) {
      this.writeHttp(conn, 429, 'too many tunnels');
      return;
    }
    this.httpCount += 1;
    const channel = `http-${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
    const waiter = new Waiter();
    this.waiters.set(channel, waiter);
    const forwarded: Record<string, Object> = {};
    const keys = Object.keys(headers);
    for (let i = 0; i < keys.length; i++) {
      const key = keys[i];
      if (key === 'authorization' || key === 'host' || key === 'content-length') {
        continue;
      }
      forwarded[key] = headers[key];
    }
    const payload = mapOf(
      ['method', 'path', 'headers', 'bodyB64'],
      [method, path, forwarded, encodeBase64Std(body)],
    );
    this.sendInner('http_req', payload, channel);
    try {
      let headerWritten = false;
      while (true) {
        const part = await waiter.poll(120000);
        if (!part) {
          break;
        }
        if (!headerWritten) {
          const status = jsonNumber(part, 'status', 200);
          await this.sendRaw(conn, `HTTP/1.1 ${status} OK\r\n`);
          await this.sendRaw(conn, 'Transfer-Encoding: chunked\r\nConnection: close\r\n');
          const responseHeaders = jsonObject(part, 'headers');
          const headerKeys = Object.keys(responseHeaders);
          for (let i = 0; i < headerKeys.length; i++) {
            const name = headerKeys[i];
            if (name.toLowerCase() === 'content-length' || name.toLowerCase() === 'transfer-encoding') {
              continue;
            }
            await this.sendRaw(conn, `${name}: ${jsonString(responseHeaders, name)}\r\n`);
          }
          await this.sendRaw(conn, '\r\n');
          headerWritten = true;
        }
        const chunkB64 = jsonString(part, 'bodyB64');
        const chunk = chunkB64.length > 0 ? decodeBase64Std(chunkB64) : new Uint8Array(0);
        if (chunk.length > 0) {
          await this.sendRaw(conn, `${chunk.length.toString(16)}\r\n`);
          await this.sendBytes(conn, chunk);
          await this.sendRaw(conn, '\r\n');
        }
        if (jsonBool(part, 'final')) {
          await this.sendRaw(conn, '0\r\n\r\n');
          break;
        }
      }
    } finally {
      this.waiters.delete(channel);
      this.httpCount = Math.max(0, this.httpCount - 1);
      try {
        conn.client.close();
      } catch (error) {
      }
    }
  }

  private async acceptWebSocket(
    conn: ClientConn,
    path: string,
    headers: Record<string, string>,
  ): Promise<void> {
    if (this.wsCount + 1 > 16) {
      this.writeHttp(conn, 503, 'too many tunnels');
      return;
    }
    this.wsCount += 1;
    const channel = `ws-${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
    const waiter = new Waiter();
    this.waiters.set(channel, waiter);
    const forwarded: Record<string, Object> = {};
    const keys = Object.keys(headers);
    for (let i = 0; i < keys.length; i++) {
      const key = keys[i];
      if (key === 'authorization' || key === 'host') {
        continue;
      }
      forwarded[key] = headers[key];
    }
    const openPayload = mapOf(['path', 'headers'], [path, forwarded]);
    dshLog(`ws_open send path=${path} channel=${channel}`);
    this.sendInner('ws_open', openPayload, channel);
    const opened = await waiter.poll(10000);
    this.waiters.delete(channel);
    if (!opened || !jsonBool(opened, 'ok')) {
      this.wsCount = Math.max(0, this.wsCount - 1);
      const reason = !opened ? 'host websocket timeout' : jsonString(opened, 'reason', 'host websocket rejected');
      dshLog(`ws_open failed path=${path} reason=${reason}`);
      this.writeHttp(conn, 502, reason);
      return;
    }
    const accept = await websocketAccept(headers['sec-websocket-key'] ?? '');
    await this.sendRaw(conn, 'HTTP/1.1 101 Switching Protocols\r\n');
    await this.sendRaw(conn, 'Upgrade: websocket\r\nConnection: Upgrade\r\n');
    await this.sendRaw(conn, `Sec-WebSocket-Accept: ${accept}\r\n\r\n`);
    conn.upgraded = true;
    conn.channel = channel;
    this.sockets.set(channel, conn);
  }

  private drainWs(conn: ClientConn): void {
    let frame = conn.reader.takeFrame();
    while (frame) {
      if (isClose(frame.opcode)) {
        const payload = mapOf(['code', 'reason'], [1000, '']);
        this.sendInner('ws_close', payload, conn.channel);
        try {
          conn.client.close();
        } catch (error) {
        }
        return;
      }
      if (isPing(frame.opcode)) {
        this.sendBytes(conn, encodeUnmasked(OPCODE_PONG, frame.payload));
      } else if (isPong(frame.opcode)) {
        // ignore
      } else if (isData(frame.opcode)) {
        const payload = mapOf(
          ['dataB64', 'opcode'],
          [encodeBase64Std(frame.payload), forwardOpcode(frame.opcode)],
        );
        this.sendInner('ws_frame', payload, conn.channel);
      }
      frame = conn.reader.takeFrame();
    }
  }

  private writeWsFrame(channel: string, payload: Record<string, Object>): void {
    const conn = this.sockets.get(channel);
    if (!conn) {
      return;
    }
    const data = jsonString(payload, 'dataB64').length > 0 ?
      decodeBase64Std(jsonString(payload, 'dataB64')) : new Uint8Array(0);
    const opcode = forwardOpcode(jsonNumber(payload, 'opcode'));
    this.sendBytes(conn, encodeUnmasked(opcode, data));
  }

  private writeHttp(conn: ClientConn, status: number, body: string): void {
    const bytes = utf8Encode(body);
    this.sendRaw(conn, `HTTP/1.1 ${status} ERROR\r\nContent-Length: ${bytes.length}\r\nConnection: close\r\n\r\n`)
      .then(() => this.sendBytes(conn, bytes))
      .then(() => {
        try {
          conn.client.close();
        } catch (error) {
        }
      });
  }

  private async sendRaw(conn: ClientConn, text: string): Promise<void> {
    await this.sendBytes(conn, utf8Encode(text));
  }

  private async sendBytes(conn: ClientConn, bytes: Uint8Array): Promise<void> {
    await conn.client.send({ data: toArrayBuffer(bytes) });
  }
}

function indexOfHeaderEnd(buffer: Uint8Array): number {
  for (let i = 0; i + 3 < buffer.length; i++) {
    if (buffer[i] === 13 && buffer[i + 1] === 10 && buffer[i + 2] === 13 && buffer[i + 3] === 10) {
      return i;
    }
  }
  return -1;
}

function bytesToAscii(bytes: Uint8Array): string {
  let text = '';
  for (let i = 0; i < bytes.length; i++) {
    text += String.fromCharCode(bytes[i]);
  }
  return text;
}

async function websocketAccept(key: string): Promise<string> {
  const md = cryptoFramework.createMd('SHA1');
  await md.update({ data: utf8Encode(`${key}258EAFA5-E914-47DA-95CA-C5AB0DC85B11`) });
  return encodeBase64Std((await md.digest()).data);
}
