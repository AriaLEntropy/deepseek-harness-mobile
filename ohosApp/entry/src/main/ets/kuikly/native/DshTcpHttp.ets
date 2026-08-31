import { BusinessError } from '@kit.BasicServicesKit';
import { connection, http, socket } from '@kit.NetworkKit';
import { concatBytes, fromArrayBuffer, toArrayBuffer, utf8Decode, utf8Encode } from './DshBytes';
import { parseObject } from './DshJson';
import { dshError, dshLog } from './DshOhosContext';

export class HttpJsonResult {
  code: number = 0;
  json: Record<string, Object> = {};
}

class HttpTarget {
  host: string = '';
  port: number = 80;
  path: string = '/';
  hostHeader: string = '';
}

export async function postJsonOverTcp(
  url: string,
  body: string,
  token: string,
  timeoutMs: number,
): Promise<HttpJsonResult> {
  const session = new TcpJsonPost(parseHttpUrl(url), body, token, timeoutMs);
  return await session.execute();
}

export async function postJsonOverSystemHttp(
  url: string,
  body: string,
  token: string,
  timeoutMs: number,
): Promise<HttpJsonResult> {
  const started = Date.now();
  const request = http.createHttp();
  try {
    const header: Record<string, string> = {};
    header['Content-Type'] = 'application/json; charset=utf-8';
    header['Accept'] = 'application/json, */*';
    if (token.length > 0) {
      header['Authorization'] = `Bearer ${token}`;
    }
    const options: http.HttpRequestOptions = {};
    options.method = http.RequestMethod.POST;
    options.extraData = body;
    options.expectDataType = http.HttpDataType.STRING;
    options.usingCache = false;
    options.usingProxy = false;
    options.connectTimeout = Math.min(15000, timeoutMs);
    options.readTimeout = timeoutMs;
    options.usingProtocol = http.HttpProtocol.HTTP1_1;
    options.header = header;
    const host = hostOfHttpUrl(url);
    if (isDotIpv4(host)) {
      options.addressFamily = http.AddressFamily.ONLY_V4;
    }
    dshLog(`http POST via netstack url=${url} host=${host} ipv4=${isDotIpv4(host)} bodyLen=${body.length} auth=${token.length > 0 ? 'yes' : 'no'} timeout=${timeoutMs}`);
    const response = await request.request(url, options);
    const text = httpBodyText(response.result);
    dshLog(`http POST netstack elapsed=${Date.now() - started}ms code=${response.responseCode} bodyLen=${text.length}`);
    const result = new HttpJsonResult();
    result.code = Number(response.responseCode);
    result.json = parseObject(text.length > 0 ? text : '{}');
    return result;
  } catch (error) {
    dshError(`http POST netstack fail elapsed=${Date.now() - started}ms url=${url}`, error as Object);
    throw new Error(httpErrorText(error as Object));
  } finally {
    try {
      request.destroy();
    } catch (error) {
    }
  }
}

class TcpJsonPost {
  private readonly target: HttpTarget;
  private readonly body: string;
  private readonly token: string;
  private readonly timeoutMs: number;
  private tcp: socket.TCPSocket | null = null;
  private buffer: Uint8Array = new Uint8Array(0);
  private settled: boolean = false;
  private sawBytes: boolean = false;
  private started: number = 0;
  private timer: number = -1;
  private result: HttpJsonResult | null = null;
  private error: Error | null = null;
  private resolveFn: ((value: HttpJsonResult) => void) | null = null;
  private rejectFn: ((reason: Error) => void) | null = null;

  constructor(target: HttpTarget, body: string, token: string, timeoutMs: number) {
    this.target = target;
    this.body = body;
    this.token = token;
    this.timeoutMs = timeoutMs;
  }

  async execute(): Promise<HttpJsonResult> {
    this.started = Date.now();
    this.timer = setTimeout(() => {
      this.fail(new Error('TCP timeout: Operation timeout'));
    }, this.timeoutMs);
    try {
      await this.openAndSend();
      return await this.waitDone();
    } catch (error) {
      const wrapped = this.error ? this.error : new Error(tcpErrorText(error as Object));
      this.fail(wrapped);
      throw wrapped;
    } finally {
      this.clearTimer();
      this.closeSocket();
    }
  }

  private async openAndSend(): Promise<void> {
    const tcp = socket.constructTCPSocketInstance();
    this.tcp = tcp;
    tcp.on('message', (value: socket.SocketMessageInfo) => {
      this.onChunk(fromArrayBuffer(value.message));
    });
    tcp.on('close', () => {
      this.onClosed();
    });
    tcp.on('error', (error: BusinessError) => {
      dshError(`tcp http socket error host=${this.target.host}:${this.target.port}`, error);
      this.fail(new Error(`tcp socket error ${tcpErrorText(error)}`));
    });
    const bindHost = await pickLocalIpv4();
    const local: socket.NetAddress = {
      address: bindHost,
      port: 0,
      family: 1,
    };
    dshLog(`tcp http bind local=${bindHost}:0 family=1`);
    try {
      await tcp.bind(local);
    } catch (error) {
      dshError(`tcp http bind ${bindHost} failed`, error as Object);
      if (bindHost === '0.0.0.0') {
        throw new Error(tcpErrorText(error as Object));
      }
      const fallback: socket.NetAddress = {
        address: '0.0.0.0',
        port: 0,
        family: 1,
      };
      await tcp.bind(fallback);
      dshLog('tcp http bind fallback 0.0.0.0 ok');
    }
    try {
      const bound = await tcp.getLocalAddress();
      dshLog(`tcp http bound ${bound.address}:${bound.port ?? 0}`);
    } catch (error) {
      dshLog('tcp http getLocalAddress skipped');
    }
    const extra: socket.TCPExtraOptions = {
      TCPNoDelay: true,
    };
    try {
      await tcp.setExtraOptions(extra);
    } catch (error) {
    }
    await bindTcpToAppNet(tcp);
    const remote: socket.NetAddress = {
      address: this.target.host,
      port: this.target.port,
      family: 1,
    };
    const connectOpt: socket.TCPConnectOptions = {
      address: remote,
      timeout: Math.min(15000, this.timeoutMs),
    };
    const connectAt = Date.now();
    dshLog(`tcp http connect begin host=${this.target.host} port=${this.target.port} family=1`);
    await tcp.connect(connectOpt);
    dshLog(`tcp http connect ok elapsed=${Date.now() - connectAt}ms`);
    if (this.settled) {
      throw this.error ?? new Error('tcp http aborted');
    }
    const request = this.buildRequest();
    const sendOpt: socket.TCPSendOptions = {
      data: toArrayBuffer(request),
    };
    await tcp.send(sendOpt);
    dshLog(`tcp http sent bytes=${request.length} path=${this.target.path}`);
  }

  private buildRequest(): Uint8Array {
    const bodyBytes = utf8Encode(this.body);
    let header =
      `POST ${this.target.path} HTTP/1.1\r\n` +
      `Host: ${this.target.hostHeader}\r\n` +
      'Content-Type: application/json; charset=utf-8\r\n' +
      'Accept: application/json, */*\r\n' +
      `Content-Length: ${bodyBytes.length}\r\n` +
      'Connection: close\r\n';
    if (this.token.length > 0) {
      header += `Authorization: Bearer ${this.token}\r\n`;
    }
    header += '\r\n';
    return concatBytes(utf8Encode(header), bodyBytes);
  }

  private onChunk(chunk: Uint8Array): void {
    if (this.settled || chunk.length === 0) {
      return;
    }
    try {
      if (!this.sawBytes) {
        this.sawBytes = true;
        dshLog(`tcp http first-byte elapsed=${Date.now() - this.started}ms chunk=${chunk.length}`);
      }
      this.buffer = concatBytes(this.buffer, chunk);
      if (this.buffer.length > 2 * 1024 * 1024) {
        this.fail(new Error('tcp http response too large'));
        return;
      }
      const parsed = parseHttpResponse(this.buffer, false);
      if (parsed) {
        this.finish(parsed);
      }
    } catch (error) {
      this.fail(new Error(tcpErrorText(error as Object)));
    }
  }

  private onClosed(): void {
    if (this.settled) {
      return;
    }
    try {
      const parsed = parseHttpResponse(this.buffer, true);
      if (parsed) {
        this.finish(parsed);
        return;
      }
      this.fail(new Error(`tcp http closed before response bytes=${this.buffer.length}`));
    } catch (error) {
      this.fail(new Error(tcpErrorText(error as Object)));
    }
  }

  private finish(parsed: ParsedHttp): void {
    dshLog(`tcp http response elapsed=${Date.now() - this.started}ms status=${parsed.status} bodyLen=${parsed.body.length}`);
    const result = new HttpJsonResult();
    result.code = parsed.status;
    result.json = parseObject(parsed.body.length > 0 ? parsed.body : '{}');
    this.succeed(result);
  }

  private waitDone(): Promise<HttpJsonResult> {
    if (this.result) {
      return Promise.resolve(this.result);
    }
    if (this.error) {
      return Promise.reject(this.error);
    }
    return new Promise<HttpJsonResult>((resolve: (value: HttpJsonResult) => void, reject: (reason: Error) => void) => {
      this.resolveFn = resolve;
      this.rejectFn = reject;
    });
  }

  private succeed(result: HttpJsonResult): void {
    if (this.settled) {
      return;
    }
    this.settled = true;
    this.result = result;
    this.clearTimer();
    this.closeSocket();
    if (this.resolveFn) {
      this.resolveFn(result);
    }
  }

  private fail(error: Error): void {
    if (this.settled) {
      return;
    }
    this.settled = true;
    this.error = error;
    this.clearTimer();
    this.closeSocket();
    if (this.rejectFn) {
      this.rejectFn(error);
    }
  }

  private clearTimer(): void {
    if (this.timer >= 0) {
      clearTimeout(this.timer);
      this.timer = -1;
    }
  }

  private closeSocket(): void {
    const tcp = this.tcp;
    this.tcp = null;
    if (!tcp) {
      return;
    }
    try {
      tcp.off('message');
      tcp.off('close');
      tcp.off('error');
    } catch (error) {
    }
    try {
      tcp.close();
    } catch (error) {
    }
  }
}

class ParsedHttp {
  status: number = 0;
  body: string = '';
}

function parseHttpUrl(url: string): HttpTarget {
  if (!url.startsWith('http://')) {
    throw new Error('tcp http requires http://');
  }
  const rest = url.substring(7);
  const cut = indexOfPath(rest);
  const authority = cut >= 0 ? rest.substring(0, cut) : rest;
  const path = cut >= 0 ? rest.substring(cut) : '/';
  if (authority.startsWith('[')) {
    throw new Error('tcp http IPv6 literal is not supported');
  }
  const target = new HttpTarget();
  target.path = path.length > 0 ? path : '/';
  const colon = authority.lastIndexOf(':');
  if (colon >= 0) {
    target.host = authority.substring(0, colon);
    const port = Number(authority.substring(colon + 1));
    if (port !== port || port <= 0 || port > 65535) {
      throw new Error('tcp http invalid port');
    }
    target.port = port;
    target.hostHeader = authority;
  } else {
    target.host = authority;
    target.port = 80;
    target.hostHeader = authority;
  }
  if (target.host.length === 0) {
    throw new Error('tcp http missing host');
  }
  return target;
}

function parseHttpResponse(buffer: Uint8Array, eof: boolean): ParsedHttp | null {
  const split = indexOfHeaderEnd(buffer);
  if (split < 0) {
    return null;
  }
  const headerText = bytesToAscii(buffer.subarray(0, split));
  const lines = headerText.split('\r\n');
  const statusLine = lines[0] ?? '';
  const parts = statusLine.split(' ');
  const status = parts.length >= 2 ? Number(parts[1]) : 0;
  if (status !== status || status <= 0) {
    throw new Error(`tcp http bad status line ${statusLine}`);
  }
  let contentLength = -1;
  let chunked = false;
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    const idx = line.indexOf(':');
    if (idx <= 0) {
      continue;
    }
    const name = line.substring(0, idx).trim().toLowerCase();
    const value = line.substring(idx + 1).trim();
    if (name === 'content-length') {
      contentLength = Number(value);
    } else if (name === 'transfer-encoding' && value.toLowerCase().indexOf('chunked') >= 0) {
      chunked = true;
    }
  }
  const bodyStart = split + 4;
  const rawBody = buffer.subarray(bodyStart);
  if (chunked) {
    const decoded = decodeChunked(rawBody);
    if (!decoded) {
      if (eof) {
        throw new Error(`tcp http incomplete chunked response bytes=${buffer.length}`);
      }
      return null;
    }
    return httpBody(status, decoded);
  }
  if (contentLength >= 0) {
    if (rawBody.length < contentLength) {
      if (eof) {
        throw new Error(`tcp http incomplete body bytes=${buffer.length} want=${contentLength}`);
      }
      return null;
    }
    return httpBody(status, rawBody.subarray(0, contentLength));
  }
  if (!eof) {
    return null;
  }
  return httpBody(status, rawBody);
}

function httpBody(status: number, bytes: Uint8Array): ParsedHttp {
  const parsed = new ParsedHttp();
  parsed.status = status;
  parsed.body = bytes.length > 0 ? utf8Decode(bytes) : '';
  return parsed;
}

function decodeChunked(body: Uint8Array): Uint8Array | null {
  let offset = 0;
  let out = new Uint8Array(0);
  while (offset < body.length) {
    const lineEnd = indexOfCrlf(body, offset);
    if (lineEnd < 0) {
      return null;
    }
    let sizeHex = bytesToAscii(body.subarray(offset, lineEnd)).trim();
    const semi = sizeHex.indexOf(';');
    if (semi >= 0) {
      sizeHex = sizeHex.substring(0, semi).trim();
    }
    const size = parseHex(sizeHex);
    if (size !== size || size < 0) {
      throw new Error('tcp http bad chunk size');
    }
    offset = lineEnd + 2;
    if (size === 0) {
      return out;
    }
    if (offset + size + 2 > body.length) {
      return null;
    }
    out = concatBytes(out, body.subarray(offset, offset + size));
    offset += size;
    if (body[offset] !== 13 || body[offset + 1] !== 10) {
      throw new Error('tcp http bad chunk trailer');
    }
    offset += 2;
  }
  return null;
}

function indexOfHeaderEnd(buffer: Uint8Array): number {
  for (let i = 0; i + 3 < buffer.length; i++) {
    if (buffer[i] === 13 && buffer[i + 1] === 10 && buffer[i + 2] === 13 && buffer[i + 3] === 10) {
      return i;
    }
  }
  return -1;
}

function indexOfCrlf(buffer: Uint8Array, start: number): number {
  for (let i = start; i + 1 < buffer.length; i++) {
    if (buffer[i] === 13 && buffer[i + 1] === 10) {
      return i;
    }
  }
  return -1;
}

function indexOfPath(value: string): number {
  for (let i = 0; i < value.length; i++) {
    const ch = value.charAt(i);
    if (ch === '/' || ch === '?' || ch === '#') {
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

function parseHex(text: string): number {
  if (text.length === 0) {
    return Number.NaN;
  }
  let n = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    let digit = -1;
    if (c >= 48 && c <= 57) {
      digit = c - 48;
    } else if (c >= 97 && c <= 102) {
      digit = c - 87;
    } else if (c >= 65 && c <= 70) {
      digit = c - 55;
    }
    if (digit < 0) {
      return Number.NaN;
    }
    n = n * 16 + digit;
  }
  return n;
}

async function bindTcpToAppNet(tcp: socket.TCPSocket): Promise<void> {
  const net = pickNetHandle();
  if (!net) {
    dshLog('tcp http bindSocket skipped, no netHandle');
    return;
  }
  try {
    await net.bindSocket(tcp);
    dshLog(`tcp http bindSocket netId=${net.netId}`);
  } catch (error) {
    dshError(`tcp http bindSocket failed netId=${net.netId}`, error as Object);
  }
}

function pickNetHandle(): connection.NetHandle | null {
  try {
    const app = connection.getAppNetSync();
    if (app && app.netId >= 100) {
      return app;
    }
  } catch (error) {
  }
  try {
    const def = connection.getDefaultNetSync();
    if (def && def.netId >= 100) {
      return def;
    }
  } catch (error) {
  }
  return null;
}

function hostOfHttpUrl(url: string): string {
  try {
    return parseHttpUrl(url).host;
  } catch (error) {
    return '';
  }
}

function httpBodyText(value: string | Object | ArrayBuffer): string {
  if (typeof value === 'string') {
    return value;
  }
  if (value instanceof ArrayBuffer) {
    return utf8Decode(fromArrayBuffer(value));
  }
  if (value === null || value === undefined) {
    return '';
  }
  return JSON.stringify(value);
}

function httpErrorText(error: Object): string {
  if (error instanceof Error) {
    return `${error.name}: ${error.message}`;
  }
  try {
    const raw = JSON.stringify(error);
    if (raw.indexOf('2300028') >= 0 || raw.indexOf('timeout') >= 0 || raw.indexOf('Timeout') >= 0) {
      return `TCP timeout: Operation timeout netstack=${raw}`;
    }
    return raw;
  } catch (ignored) {
    return 'netstack http failed';
  }
}

function isDotIpv4(host: string): boolean {
  const parts = host.split('.');
  if (parts.length !== 4) {
    return false;
  }
  for (let i = 0; i < 4; i++) {
    const n = Number(parts[i]);
    if (parts[i].length === 0 || n !== n || n < 0 || n > 255) {
      return false;
    }
  }
  return true;
}

async function pickLocalIpv4(): Promise<string> {
  try {
    const net = connection.getAppNetSync();
    const picked = await ipv4OfNet(net);
    if (picked.length > 0) {
      return picked;
    }
  } catch (error) {
  }
  try {
    const def = await connection.getDefaultNet();
    const picked = await ipv4OfNet(def);
    if (picked.length > 0) {
      return picked;
    }
  } catch (error) {
  }
  dshLog('tcp http bind fallback 0.0.0.0');
  return '0.0.0.0';
}

async function ipv4OfNet(net: connection.NetHandle): Promise<string> {
  const props = await connection.getConnectionProperties(net);
  const list = props.linkAddresses;
  let addrs = '';
  for (let i = 0; i < list.length; i++) {
    const addr = list[i].address.address;
    if (i > 0) {
      addrs += ',';
    }
    addrs += `${addr}/${list[i].prefixLength}`;
    if (isDotIpv4(addr) && !addr.startsWith('127.')) {
      dshLog(`tcp http pick if=${props.interfaceName} local=${addr} netId=${net.netId} addrs=${addrs}`);
      return addr;
    }
  }
  dshLog(`tcp http no ipv4 if=${props.interfaceName} netId=${net.netId} addrs=${addrs}`);
  return '';
}

function tcpErrorText(error: Object): string {
  if (error instanceof Error) {
    return `${error.name}: ${error.message}`;
  }
  return JSON.stringify(error);
}
