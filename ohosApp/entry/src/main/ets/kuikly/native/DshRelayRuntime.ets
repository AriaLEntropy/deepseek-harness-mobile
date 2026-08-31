import { connection, webSocket } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { util } from '@kit.ArkTS';
import { callbackHasError, encodeBase64Url, webSocketText } from './DshBytes';
import { jsonObject, jsonString, parseObject } from './DshJson';
import { DshRelayLoopbackServer } from './DshRelayLoopback';
import { DshRelaySecrets } from './DshRelaySecrets';
import { DshSealedPayload, DshSealedTunnelCrypto, DshSecureCipher } from './DshSealedTunnelCrypto';
import { HttpJsonResult, postJsonOverSystemHttp, postJsonOverTcp } from './DshTcpHttp';
import { dshError, dshLog } from './DshOhosContext';

export class DshRelayNativeState {
  phase: string = 'IDLE';
  message: string = '';
  localPort: number = 0;
  localToken: string = '';
  hostId: string = '';
  hostName: string = '';
  relayOrigin: string = '';
  paired: boolean = false;
  generation: number = 0;

  toMap(): Record<string, Object> {
    const map: Record<string, Object> = {};
    map['phase'] = this.phase;
    map['message'] = this.message;
    map['localPort'] = this.localPort;
    map['localToken'] = this.localToken;
    map['hostId'] = this.hostId;
    map['hostName'] = this.hostName;
    map['relayOrigin'] = this.relayOrigin;
    map['paired'] = this.paired;
    map['generation'] = this.generation;
    return map;
  }

  copy(patch: Record<string, Object>): DshRelayNativeState {
    const next = new DshRelayNativeState();
    next.phase = typeof patch['phase'] === 'string' ? patch['phase'] as string : this.phase;
    next.message = typeof patch['message'] === 'string' ? patch['message'] as string : this.message;
    next.localPort = typeof patch['localPort'] === 'number' ? patch['localPort'] as number : this.localPort;
    next.localToken = typeof patch['localToken'] === 'string' ? patch['localToken'] as string : this.localToken;
    next.hostId = typeof patch['hostId'] === 'string' ? patch['hostId'] as string : this.hostId;
    next.hostName = typeof patch['hostName'] === 'string' ? patch['hostName'] as string : this.hostName;
    next.relayOrigin = typeof patch['relayOrigin'] === 'string' ? patch['relayOrigin'] as string : this.relayOrigin;
    next.paired = typeof patch['paired'] === 'boolean' ? patch['paired'] as boolean : this.paired;
    next.generation = typeof patch['generation'] === 'number' ? patch['generation'] as number : this.generation;
    return next;
  }
}

type StateListener = (state: DshRelayNativeState) => void;

class PairLink {
  origin: string;
  pairId: string;
  masterKey: string;

  constructor(origin: string, pairId: string, masterKey: string) {
    this.origin = origin;
    this.pairId = pairId;
    this.masterKey = masterKey;
  }
}

export class DshRelayRuntime {
  static readonly shared: DshRelayRuntime = new DshRelayRuntime();

  private readonly listeners: StateListener[] = [];
  private secrets: DshRelaySecrets | null = null;
  private loopback: DshRelayLoopbackServer | null = null;
  private socket: webSocket.WebSocket | null = null;
  private cipher: DshSecureCipher | null = null;
  private accessSessionId: string = '';
  private localToken: string = '';
  private hostName: string = '';
  private relayOrigin: string = '';
  private hostId: string = '';
  private generation: number = 0;
  private connecting: boolean = false;
  private stopped: boolean = true;
  private state: DshRelayNativeState = new DshRelayNativeState();
  private reconnectTimer: number = -1;
  private netConnection: connection.NetConnection | null = null;
  private sendChain: Promise<void> = Promise.resolve();
  private recvChain: Promise<void> = Promise.resolve();

  addListener(listener: StateListener): void {
    this.listeners.push(listener);
    listener(this.state);
  }

  removeListener(listener: StateListener): void {
    const index = this.listeners.indexOf(listener);
    if (index >= 0) {
      this.listeners.splice(index, 1);
    }
  }

  current(): DshRelayNativeState {
    return this.state;
  }

  async attach(): Promise<void> {
    if (!this.secrets) {
      this.secrets = new DshRelaySecrets();
    }
    if (await this.secrets.hasPairing()) {
      this.hostId = await this.secrets.hostId();
      this.hostName = await this.secrets.hostName();
      this.relayOrigin = await this.secrets.relayOrigin();
      this.publish(this.state.copy(putAll(
        ['paired', 'hostId', 'hostName', 'relayOrigin', 'phase'],
        [true, this.hostId, this.hostName, this.relayOrigin, this.state.phase === 'READY' ? 'READY' : 'IDLE'],
      )));
    }
  }

  async pairFromQr(qr: string): Promise<Record<string, Object>> {
    await this.attach();
    this.publish(this.idleState('PAIRING', '正在配对'));
    let origin = '';
    try {
      dshLog(`pairFromQr qr=${summarizeQr(qr)}`);
      dshLog('pair transport=netstack');
      const link = parsePairQr(qr);
      origin = link.origin;
      dshLog(`pairFromQr origin=${origin} host=${hostOf(origin)} ipv4=${isIpv4Literal(hostOf(origin))} private=${isPrivateIpv4(hostOf(origin))} pairIdLen=${link.pairId.length} keyLen=${link.masterKey.length}`);
      await logNetworkSnapshot('pair-before-bind');
      const claimToken = await DshSealedTunnelCrypto.deriveClaimToken(link.masterKey);
      const claim: Record<string, Object> = {};
      claim['pairId'] = link.pairId;
      claim['claimToken'] = claimToken;
      claim['deviceLabel'] = 'HarmonyOS';
      claim['platform'] = 'ohos';
      const body = JSON.stringify(claim);
      const claimUrl = `${link.origin}/pair/claim-device`;
      dshLog(`pair POST ${claimUrl} bodyLen=${body.length} claimTokenLen=${claimToken.length}`);
      await bindNetForIpv4(claimUrl);
      await logNetworkSnapshot('pair-after-bind');
      const response = await postJson(claimUrl, body);
      dshLog(`pair claim httpCode=${response.code} jsonKeys=${recordKeys(response.json)}`);
      if (response.code < 200 || response.code >= 300) {
        const reason = jsonString(response.json, 'error', `claim failed (${response.code})`);
        dshLog(`pair claim rejected ${reason}`);
        this.publish(this.idleState('ERROR', reason, await this.secrets?.hasPairing() === true));
        return failResult(reason);
      }
      const newHostId = jsonString(response.json, 'hostId');
      const token = jsonString(response.json, 'clientToken');
      const name = jsonString(response.json, 'hostName', '电脑');
      if (newHostId.length === 0 || token.length === 0) {
        throw new Error('pairing response missing credentials');
      }
      await this.secrets?.save(link.masterKey, token, newHostId, name, link.origin);
      this.hostId = newHostId;
      this.hostName = name;
      this.relayOrigin = link.origin;
      this.publish(this.idleState('IDLE', '已配对', true));
      dshLog(`pair success hostId=${newHostId} hostName=${name} origin=${link.origin}`);
      const paired: Record<string, Object> = {};
      paired['ok'] = true;
      paired['hostId'] = newHostId;
      paired['hostName'] = name;
      paired['relayOrigin'] = link.origin;
      paired['pairedAt'] = Date.now();
      return paired;
    } catch (error) {
      dshError(`pair failed origin=${origin} ${describeError(error)}`, error as Object);
      const message = isTimeoutError(error)
        ? timeoutMessage(origin)
        : errorMessage(error, '配对失败');
      this.publish(this.idleState('ERROR', message, await this.secrets?.hasPairing() === true));
      return failResult(message);
    }
  }

  async connect(): Promise<void> {
    await this.attach();
    this.registerNetwork();
    if (this.loopbackListening()) {
      this.stopped = false;
      dshLog(`reusing loopback port=${this.loopback?.port ?? 0}`);
      return;
    }
    if (this.connecting) {
      return;
    }
    this.stopped = false;
    this.dropLoopback('new-connect');
    const paired = this.secrets ? await this.secrets.hasPairing() : false;
    this.publish(this.idleState('CONNECTING', '正在申请访问票', paired));
    this.connectInternal();
  }

  disconnect(): void {
    this.stopped = true;
    this.generation += 1;
    this.connecting = false;
    this.clearReconnect();
    this.unregisterNetwork();
    this.closeSocket();
    this.cipher = null;
    this.dropLoopback('disconnect');
    this.publish(this.idleState('STOPPED', '扫码连接已断开', this.state.paired));
  }

  async forget(): Promise<void> {
    this.disconnect();
    this.secrets?.clear();
    this.hostId = '';
    this.hostName = '';
    this.relayOrigin = '';
    this.publish(this.idleState('IDLE', '已移除配对', false));
  }

  private async connectInternal(): Promise<void> {
    this.connecting = true;
    const myGeneration = ++this.generation;
    try {
      const stored = this.secrets;
      if (!stored) {
        throw new Error('missing secrets');
      }
      const master = await stored.masterKey();
      const clientToken = await stored.clientToken();
      if (master.length === 0 || clientToken.length === 0) {
        throw new Error('not paired');
      }
      const origin = (await stored.relayOrigin()) || this.relayOrigin || 'http://127.0.0.1:8787';
      this.hostId = await stored.hostId();
      this.hostName = (await stored.hostName()) || this.hostName;
      this.relayOrigin = origin;
      this.publish(this.idleState('CONNECTING', '正在申请访问票', true, myGeneration));
      const ticketUrl = `${origin}/access-ticket`;
      dshLog(`connect ticket POST ${ticketUrl} origin=${origin}`);
      await logNetworkSnapshot('connect-before-bind');
      await bindNetForIpv4(ticketUrl);
      await logNetworkSnapshot('connect-after-bind');
      const ticketResponse = await postJson(ticketUrl, '{}', clientToken);
      if (ticketResponse.code < 200 || ticketResponse.code >= 300) {
        throw new Error(jsonString(ticketResponse.json, 'error', 'ticket failed'));
      }
      this.accessSessionId = jsonString(ticketResponse.json, 'accessSessionId');
      const ticket = jsonString(ticketResponse.json, 'ticket');
      const tunnelUrl = jsonString(ticketResponse.json, 'tunnelUrl', `${toWs(origin)}/client-tunnel`);
      this.hostId = jsonString(ticketResponse.json, 'hostId', this.hostId);
      if (ticket.length === 0 || this.accessSessionId.length === 0) {
        throw new Error('ticket response incomplete');
      }
      this.publish(this.state.copy(putAll(
        ['phase', 'message', 'generation', 'hostId', 'hostName', 'relayOrigin', 'paired'],
        ['HANDSHAKING', '正在建立加密隧道', myGeneration, this.hostId, this.hostName, origin, true],
      )));
      const clientRandomB64 = encodeBase64Url(await DshSealedTunnelCrypto.randomBytes());
      const helloPayload: Record<string, Object> = {};
      helloPayload['accessSessionId'] = this.accessSessionId;
      helloPayload['clientRandomB64'] = clientRandomB64;
      helloPayload['clientProofB64'] =
        await DshSealedTunnelCrypto.clientProof(master, this.accessSessionId, clientRandomB64);
      const hello = envelope('client_hello', helloPayload);
      this.openTunnel(tunnelUrl, ticket, master, clientRandomB64, myGeneration, origin, hello);
    } catch (error) {
      dshError(`connect failed origin=${this.relayOrigin} ${describeError(error)}`, error as Object);
      if (!this.stopped && myGeneration === this.generation) {
        const origin = this.relayOrigin;
        const message = isTimeoutError(error) ? timeoutMessage(origin) : errorMessage(error, '连接失败');
        this.publish(this.idleState('ERROR', message, true, myGeneration));
        this.scheduleReconnect();
      }
    } finally {
      this.connecting = false;
    }
  }

  private openTunnel(
    tunnelUrl: string,
    ticket: string,
    master: string,
    clientRandomB64: string,
    myGeneration: number,
    origin: string,
    hello: Record<string, Object>,
  ): void {
    this.closeSocket();
    const ws = webSocket.createWebSocket();
    this.socket = ws;
    ws.on('open', (err: Object) => {
      if (callbackHasError(err)) {
        dshError(`relay socket open failed ${describeError(err)}`, err);
        if (!this.stopped && myGeneration === this.generation) {
          this.publish(this.state.copy(putAll(
            ['phase', 'message'],
            ['ERROR', errorMessage(err, '隧道失败')],
          )));
          this.scheduleReconnect();
        }
        return;
      }
      dshLog('relay socket open');
      ws.send(JSON.stringify(hello));
    });
    ws.on('message', (err: Object, value: Object) => {
      if (this.stopped || myGeneration !== this.generation) {
        return;
      }
      const text = webSocketText(err, value);
      if (text.length === 0) {
        return;
      }
      this.enqueueRecv(() => this.handleOuter(text, master, clientRandomB64, myGeneration, origin));
    });
    ws.on('close', () => {
      if (!this.stopped && myGeneration === this.generation) {
        this.scheduleReconnect();
      }
    });
    ws.on('error', (error: Object) => {
      dshError(`relay socket failed ${describeError(error)}`, error);
      if (!this.stopped && myGeneration === this.generation) {
        this.publish(this.state.copy(putAll(
          ['phase', 'message'],
          ['ERROR', errorMessage(error, '隧道失败')],
        )));
        this.scheduleReconnect();
      }
    });
    const header: Record<string, string> = {};
    header['Authorization'] = `Bearer ${ticket}`;
    const options: webSocket.WebSocketRequestOptions = {};
    options.header = header;
    dshLog(`tunnel connect ${tunnelUrl}`);
    ws.connect(tunnelUrl, options).catch((error: Object) => {
      dshError(`relay connect failed url=${tunnelUrl} ${describeError(error)}`, error);
      if (!this.stopped && myGeneration === this.generation) {
        this.publish(this.state.copy(putAll(
          ['phase', 'message'],
          ['ERROR', errorMessage(error, '隧道失败')],
        )));
        this.scheduleReconnect();
      }
    });
  }

  private async handleOuter(
    text: string,
    master: string,
    clientRandomB64: string,
    myGeneration: number,
    origin: string,
  ): Promise<void> {
    const msg = parseObject(text);
    const type = jsonString(msg, 'type');
    dshLog(`tunnel message type=${type}`);
    if (type === 'server_hello') {
      const payload = jsonObject(msg, 'payload');
      this.cipher = await DshSealedTunnelCrypto.createClientCipher(
        master,
        this.accessSessionId,
        clientRandomB64,
        jsonString(payload, 'serverRandomB64'),
        jsonString(payload, 'serverProofB64'),
      );
      await this.startLoopback(myGeneration, origin);
      return;
    }
    if (type === 'sealed') {
      const payload = jsonObject(msg, 'payload');
      const opened = await this.cipher?.open(new DshSealedPayload(
        jsonString(payload, 'seq'),
        jsonString(payload, 'ciphertextB64'),
      ));
      if (!opened) {
        return;
      }
      this.loopback?.onInner(
        jsonString(opened, 'type'),
        jsonObject(opened, 'payload'),
        jsonString(opened, 'channel'),
      );
      return;
    }
    if (type === 'device_close' || type === 'close') {
      if (!this.stopped) {
        this.scheduleReconnect();
      }
    }
  }

  private async startLoopback(myGeneration: number, origin: string): Promise<void> {
    this.dropLoopback('replace');
    this.localToken = encodeBase64Url(await DshSealedTunnelCrypto.randomBytes(24));
    const server = new DshRelayLoopbackServer(this.localToken, (type, payload, channel) => {
      this.enqueueSend(() => this.sendInner(type, payload, channel));
    });
    const port = await server.start();
    this.loopback = server;
    dshLog(`loopback listening port=${port}`);
    this.publish(newState(
      'READY',
      '扫码隧道已连接',
      port,
      this.localToken,
      this.hostId,
      this.hostName,
      origin,
      true,
      myGeneration,
    ));
  }

  private enqueueSend(work: () => Promise<void>): void {
    const run = (): Promise<void> => work().catch((error: Object) => {
      dshError('sendInner failed', error);
    });
    this.sendChain = this.sendChain.then(run, run);
  }

  private enqueueRecv(work: () => Promise<void>): void {
    const run = (): Promise<void> => work().catch((error: Object) => {
      dshError('handleOuter failed', error);
    });
    this.recvChain = this.recvChain.then(run, run);
  }

  private resetIoChains(): void {
    this.sendChain = Promise.resolve();
    this.recvChain = Promise.resolve();
  }

  private async sendInner(type: string, payload: Record<string, Object>, channel: string): Promise<void> {
    const current = this.cipher;
    const ws = this.socket;
    if (!current || !ws) {
      dshLog(`inner send dropped type=${type} channel=${channel} hasCipher=${current !== null} hasSocket=${ws !== null}`);
      return;
    }
    dshLog(`inner send type=${type} channel=${channel}`);
    const sealed = await current.seal(envelope(type, payload, channel));
    const sealedPayload: Record<string, Object> = {};
    sealedPayload['accessSessionId'] = this.accessSessionId;
    sealedPayload['seq'] = sealed.seq;
    sealedPayload['ciphertextB64'] = sealed.ciphertextB64;
    const outer = envelope('sealed', sealedPayload);
    await this.sendSocket(ws, JSON.stringify(outer));
  }

  private sendSocket(ws: webSocket.WebSocket, text: string): Promise<void> {
    return new Promise<void>((resolve) => {
      try {
        ws.send(text, (err: BusinessError, _ok: boolean) => {
          if (callbackHasError(err)) {
            dshError('relay send failed', err);
          }
          resolve();
        });
      } catch (error) {
        dshError('relay send threw', error as Object);
        resolve();
      }
    });
  }

  private loopbackListening(): boolean {
    const server = this.loopback;
    return this.state.phase === 'READY' &&
      this.state.localPort > 0 &&
      !!server &&
      server.port === this.state.localPort &&
      server.isListening();
  }

  private dropLoopback(reason: string): void {
    const port = this.loopback?.port ?? 0;
    this.loopback?.stop();
    this.loopback = null;
    this.localToken = '';
    if (port > 0) {
      dshLog(`loopback stopped port=${port} reason=${reason}`);
    }
  }

  private scheduleReconnect(): void {
    if (this.stopped) {
      return;
    }
    this.dropLoopback('reconnect');
    this.publish(this.state.copy(putAll(
      ['phase', 'message', 'localPort', 'localToken'],
      ['RECONNECTING', '扫码连接重试中', 0, ''],
    )));
    this.clearReconnect();
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = -1;
      if (!this.stopped && !this.connecting) {
        this.connectInternal();
      }
    }, 2000);
  }

  private clearReconnect(): void {
    if (this.reconnectTimer >= 0) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = -1;
    }
  }

  private registerNetwork(): void {
    if (this.netConnection) {
      return;
    }
    try {
      const net = connection.createNetConnection();
      this.netConnection = net;
      net.on('netAvailable', () => {
        if (!this.stopped &&
          this.state.phase !== 'READY' &&
          this.state.phase !== 'CONNECTING' &&
          this.state.phase !== 'HANDSHAKING' &&
          !this.connecting) {
          this.connectInternal();
        }
      });
      net.register((err: BusinessError) => {
        if (err) {
          dshError('register network failed', err);
          this.netConnection = null;
        }
      });
    } catch (error) {
      dshError('register network failed', error as Object);
      this.netConnection = null;
    }
  }

  private unregisterNetwork(): void {
    const net = this.netConnection;
    this.netConnection = null;
    if (!net) {
      return;
    }
    try {
      net.unregister((_err: BusinessError) => {
      });
    } catch (error) {
      dshError('unregister network failed', error as Object);
    }
  }

  private closeSocket(): void {
    if (this.socket) {
      try {
        this.socket.close();
      } catch (error) {
      }
      this.socket = null;
    }
    this.resetIoChains();
  }

  private idleState(phase: string, message: string, paired?: boolean, generation?: number): DshRelayNativeState {
    return newState(
      phase,
      message,
      0,
      '',
      this.hostId,
      this.hostName,
      this.relayOrigin,
      paired !== undefined ? paired : this.state.paired,
      generation !== undefined ? generation : this.generation,
    );
  }

  private publish(next: DshRelayNativeState): void {
    this.state = next;
    for (let i = 0; i < this.listeners.length; i++) {
      try {
        this.listeners[i](next);
      } catch (error) {
        dshError('relay listener failed', error as Object);
      }
    }
  }
}

function newState(
  phase: string,
  message: string,
  localPort: number,
  localToken: string,
  hostId: string,
  hostName: string,
  relayOrigin: string,
  paired: boolean,
  generation: number,
): DshRelayNativeState {
  const state = new DshRelayNativeState();
  state.phase = phase;
  state.message = message;
  state.localPort = localPort;
  state.localToken = localToken;
  state.hostId = hostId;
  state.hostName = hostName;
  state.relayOrigin = relayOrigin;
  state.paired = paired;
  state.generation = generation;
  return state;
}

function envelope(type: string, payload: Record<string, Object>, channel?: string): Record<string, Object> {
  const msg: Record<string, Object> = {};
  msg['v'] = 1;
  msg['type'] = type;
  msg['id'] = randomId();
  msg['ts'] = Date.now();
  msg['payload'] = payload;
  if (channel) {
    msg['channel'] = channel;
  }
  return msg;
}

function parsePairQr(raw: string): PairLink {
  const trimmed = raw.trim();
  const schemeEnd = trimmed.indexOf('://');
  if (schemeEnd < 0) {
    throw new Error('二维码缺少配对参数');
  }
  const scheme = trimmed.substring(0, schemeEnd);
  const afterScheme = trimmed.substring(schemeEnd + 3);
  const slash = indexOfPath(afterScheme);
  const authority = slash >= 0 ? afterScheme.substring(0, slash) : afterScheme;
  const origin = `${scheme}://${authority}`;
  const hash = trimmed.indexOf('#');
  const queryIndex = trimmed.indexOf('?');
  let query = '';
  if (hash >= 0) {
    const fragment = trimmed.substring(hash + 1);
    const inner = fragment.indexOf('?');
    query = inner >= 0 ? fragment.substring(inner + 1) : fragment;
  } else if (queryIndex >= 0) {
    query = trimmed.substring(queryIndex + 1);
  }
  const params: Record<string, string> = {};
  const parts = query.split('&');
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];
    const eq = part.indexOf('=');
    if (eq < 0) {
      params[decodeURIComponent(part)] = '';
    } else {
      params[decodeURIComponent(part.substring(0, eq))] = decodeURIComponent(part.substring(eq + 1));
    }
  }
  const id = params['id'] ?? '';
  const key = params['key'] ?? '';
  if (id.length === 0 || key.length === 0) {
    throw new Error('二维码缺少配对参数');
  }
  const host = hostOf(origin);
  if (host === '127.0.0.1' || host === 'localhost' || host === '::1') {
    throw new Error('二维码里是电脑本机地址，手机访问不到。请让电脑用局域网 IP 生成二维码。');
  }
  return new PairLink(origin, id, key);
}

function randomId(): string {
  try {
    return util.generateRandomUUID();
  } catch (error) {
    return `${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
  }
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

function toWs(origin: string): string {
  if (origin.startsWith('https:')) {
    return origin.replace('https:', 'wss:');
  }
  return origin.replace('http:', 'ws:');
}

async function postJson(
  url: string,
  body: string,
  token?: string,
): Promise<HttpJsonResult> {
  const auth = token && token.length > 0 ? token : '';
  try {
    return await postJsonOverSystemHttp(url, body, auth, 20000);
  } catch (error) {
    dshError(`http POST netstack fail url=${url} ${describeError(error as Object)}`, error as Object);
    if (url.startsWith('http://')) {
      dshLog('http POST fallback tcp after netstack error');
      return await postJsonOverTcp(url, body, auth, 20000);
    }
    throw new Error(describeError(error as Object));
  }
}

function putAll(keys: string[], values: Object[]): Record<string, Object> {
  const map: Record<string, Object> = {};
  const count = Math.min(keys.length, values.length);
  for (let i = 0; i < count; i++) {
    map[keys[i]] = values[i];
  }
  return map;
}

function failResult(message: string): Record<string, Object> {
  const result: Record<string, Object> = {};
  result['ok'] = false;
  result['message'] = message;
  return result;
}

function errorMessage(error: Object, fallback: string): string {
  if (error instanceof Error && error.message.length > 0) {
    return error.message;
  }
  const text = JSON.stringify(error);
  return text.length > 0 && text !== '{}' ? text : fallback;
}

function describeError(error: Object): string {
  if (error instanceof Error) {
    return `Error(${error.name}): ${error.message}`;
  }
  return JSON.stringify(error);
}

function recordKeys(record: Record<string, Object>): string {
  const keys = Object.keys(record);
  let out = '';
  for (let i = 0; i < keys.length; i++) {
    if (i > 0) {
      out += ',';
    }
    out += keys[i];
  }
  return out;
}

function redactQueryParam(text: string, name: string): string {
  const needle = `${name}=`;
  let cursor = 0;
  let out = '';
  while (true) {
    const found = text.indexOf(needle, cursor);
    if (found < 0) {
      out += text.substring(cursor);
      break;
    }
    out += text.substring(cursor, found + needle.length);
    const valueStart = found + needle.length;
    let valueEnd = text.length;
    const amp = text.indexOf('&', valueStart);
    if (amp >= 0) {
      valueEnd = amp;
    }
    out += `[len=${valueEnd - valueStart}]`;
    cursor = valueEnd;
  }
  return out;
}

function summarizeQr(qr: string): string {
  const trimmed = qr.trim();
  return `len=${trimmed.length} http=${trimmed.indexOf('http') === 0} hash=${trimmed.indexOf('#') >= 0} text=${redactQueryParam(trimmed, 'key')}`;
}

function bearersText(types: Array<connection.NetBearType>): string {
  let out = '';
  for (let i = 0; i < types.length; i++) {
    if (i > 0) {
      out += ',';
    }
    if (types[i] === connection.NetBearType.BEARER_WIFI) {
      out += 'wifi';
    } else if (types[i] === connection.NetBearType.BEARER_CELLULAR) {
      out += 'cellular';
    } else if (types[i] === connection.NetBearType.BEARER_ETHERNET) {
      out += 'ethernet';
    } else {
      out += `${types[i]}`;
    }
  }
  return out;
}

async function logNetworkSnapshot(tag: string): Promise<void> {
  try {
    const def = await connection.getDefaultNet();
    dshLog(`${tag} defaultNet=${def.netId}`);
  } catch (error) {
    dshError(`${tag} defaultNet failed`, error as Object);
  }
  try {
    const appNet = connection.getAppNetSync();
    dshLog(`${tag} appNet=${appNet.netId}`);
  } catch (error) {
    dshLog(`${tag} appNet unset`);
  }
  try {
    const nets = await connection.getAllNets();
    dshLog(`${tag} netCount=${nets.length}`);
    for (let i = 0; i < nets.length; i++) {
      const cap = await connection.getNetCapabilities(nets[i]);
      dshLog(`${tag} net[${i}] id=${nets[i].netId} bearers=${bearersText(cap.bearerTypes)} internet=${netHasInternet(cap)}`);
      await logNetAddresses(`${tag} net[${i}]`, nets[i]);
    }
  } catch (error) {
    dshError(`${tag} getAllNets failed`, error as Object);
  }
}

function isTimeoutError(error: Object): boolean {
  const text = (error instanceof Error ? error.message : JSON.stringify(error)).toLowerCase();
  if (text.indexOf('2300028') >= 0 || text.indexOf('timeout') >= 0 || text.indexOf('超时') >= 0 ||
    text.indexOf('-2001') >= 0) {
    return true;
  }
  const biz = error as BusinessError;
  return typeof biz.code === 'number' && biz.code === 2300028;
}

function timeoutMessage(origin: string): string {
  const target = origin.length > 0 ? origin : 'Relay';
  const host = hostOf(target);
  if (isPrivateIpv4(host)) {
    return `连不上 ${target}（超时）。局域网扫码需要手机和电脑在同一 Wi-Fi。`;
  }
  if (isIpv4Literal(host)) {
    return `连不上 ${target}（超时）。Wi-Fi 已连通，但访问该明文 IPv4 地址失败。请再扫一次；若仍失败，把 PUBLIC_RELAY_URL 换成 HTTPS 域名。`;
  }
  return `连不上 ${target}（超时）。请确认二维码 origin 可从手机访问。`;
}

async function logNetAddresses(tag: string, net: connection.NetHandle): Promise<void> {
  try {
    const props = await connection.getConnectionProperties(net);
    let addrs = '';
    const list = props.linkAddresses;
    for (let i = 0; i < list.length; i++) {
      if (i > 0) {
        addrs += ',';
      }
      addrs += `${list[i].address.address}/${list[i].prefixLength}`;
    }
    dshLog(`${tag} if=${props.interfaceName} addrs=${addrs}`);
  } catch (error) {
    dshError(`${tag} props failed`, error as Object);
  }
}

function hostOf(url: string): string {
  const schemeEnd = url.indexOf('://');
  const rest = schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
  const slash = indexOfPath(rest);
  const authority = slash >= 0 ? rest.substring(0, slash) : rest;
  if (authority.startsWith('[')) {
    const close = authority.indexOf(']');
    return close >= 0 ? authority.substring(1, close) : authority;
  }
  const colon = authority.lastIndexOf(':');
  return colon >= 0 ? authority.substring(0, colon) : authority;
}

function isPrivateIpv4(host: string): boolean {
  if (host === 'localhost') {
    return true;
  }
  const parts = host.split('.');
  if (parts.length !== 4) {
    return false;
  }
  const a = Number(parts[0]);
  const b = Number(parts[1]);
  if (a !== a || b !== b) {
    return false;
  }
  return a === 10 || a === 127 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
}

function isIpv4Literal(host: string): boolean {
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

function netHasInternet(cap: connection.NetCapabilities): boolean {
  const caps = cap.networkCap;
  if (!caps || caps.length === 0) {
    return true;
  }
  for (let i = 0; i < caps.length; i++) {
    if (caps[i] === connection.NetCap.NET_CAPABILITY_INTERNET) {
      return true;
    }
  }
  return false;
}

function isWifiOrEthernet(cap: connection.NetCapabilities): boolean {
  const types = cap.bearerTypes;
  for (let i = 0; i < types.length; i++) {
    if (types[i] === connection.NetBearType.BEARER_WIFI ||
      types[i] === connection.NetBearType.BEARER_ETHERNET) {
      return true;
    }
  }
  return false;
}

async function bindNetForIpv4(url: string): Promise<void> {
  const host = hostOf(url);
  dshLog(`bindNetForIpv4 host=${host} ipv4=${isIpv4Literal(host)} private=${isPrivateIpv4(host)}`);
  if (!isIpv4Literal(host)) {
    dshLog('bindNetForIpv4 skip, host is not an IPv4 literal');
    return;
  }
  const privateLan = isPrivateIpv4(host);
  try {
    const nets = await connection.getAllNets();
    for (let i = 0; i < nets.length; i++) {
      const cap = await connection.getNetCapabilities(nets[i]);
      const wifi = isWifiOrEthernet(cap);
      const internet = netHasInternet(cap);
      dshLog(`bindNetForIpv4 consider id=${nets[i].netId} wifi=${wifi} internet=${internet} bearers=${bearersText(cap.bearerTypes)}`);
      if (!wifi) {
        continue;
      }
      if (!privateLan && !internet) {
        continue;
      }
      await connection.setAppNet(nets[i]);
      dshLog(`bindNetForIpv4 bound netId=${nets[i].netId} host=${host}`);
      return;
    }
    dshLog(`bindNetForIpv4 no suitable wifi/ethernet net for host=${host}`);
  } catch (error) {
    dshError('bindNetForIpv4 failed', error as Object);
  }
}
