import { common, bundleManager, abilityAccessCtrl, Permissions } from '@kit.AbilityKit';
import { deviceInfo } from '@kit.DeviceInfoKit';
import { BusinessError, pasteboard } from '@kit.BasicServicesKit';
import { promptAction } from '@kit.ArkUI';
import { speechRecognizer } from '@kit.CoreSpeechKit';
import audio from '@ohos.multimedia.audio';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import util from '@ohos.util';
import fs from '@ohos.file.fs';
import router from '@ohos.router';
import { fileUri } from '@kit.CoreFileKit';
import { systemShare } from '@kit.ShareKit';
import { unifiedDataChannel } from '@kit.ArkData';
import { jsonString, mapOf, moduleParams } from '../native/DshJson';
import { DshSshKeyStore } from '../native/DshSshKeyStore';
import { DshOhosContext, dshError } from '../native/DshOhosContext';
import { DshImages } from '../native/DshImages';
import { DshFiles } from '../native/DshFiles';
import { DshCrashStore } from '../native/DshCrashStore';

export class KRBridgeModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = "HRBridgeModule";
  static readonly SHOW_ALERT = "showAlert";
  static readonly CLOSE_PAGE = "closePage";
  static readonly OPEN_PAGE = "openPage";
  static readonly COPY_TO_PASTE_BOARD = "copyToPasteboard";
  static readonly TOAST = "toast";
  static readonly LOG = "log";
  static readonly REPORT_DT = "reportDT";
  static readonly REPORT_REAL_TIME = "reportRealtime";
  static readonly LOCAL_SERVE_TIME = "localServeTime";
  static readonly CURRENT_TIME_STAMP = "currentTimestamp";
  static readonly DATE_FORMATTER = "dateFormatter";
  static readonly READ_ASSET_FILE = "readAssetFile";

  // ===== 语音识别（按住说话）：CoreSpeechKit 语音转文字 + AudioCapturer 音量 =====
  private voiceEngine: speechRecognizer.SpeechRecognitionEngine | null = null;
  private voiceCapturer: audio.AudioCapturer | null = null;
  private voiceReadDataCallback: ((buffer: ArrayBuffer) => void) | null = null;
  private voiceSlotId: string = '';
  private voiceSessionId: string = '';
  private voiceRecording: boolean = false;
  private voiceLastLevelAt: number = 0;
  private voiceFinalText: string = '';
  private voiceAudioRemainder: Uint8Array = new Uint8Array(0);

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    switch (method) {
      case KRBridgeModule.SHOW_ALERT: {
        this.showAlert(params, callback);
        break;
      }
      case KRBridgeModule.CLOSE_PAGE: {
        this.closePage();
        break;
      }
      case KRBridgeModule.OPEN_PAGE: {
        this.openPage();
        break;
      }
      case KRBridgeModule.COPY_TO_PASTE_BOARD: {
        this.copyToPasteboard(params);
        break;
      }
      case KRBridgeModule.TOAST: {
        this.toast(params);
        break;
      }
      case KRBridgeModule.LOG: {
        this.log(params);
        break;
      }
      case KRBridgeModule.REPORT_DT: {
        this.reportDT(params);
        break;
      }
      case KRBridgeModule.REPORT_REAL_TIME: {
        this.reportRealtime(params);
        break;
      }
      case KRBridgeModule.LOCAL_SERVE_TIME: {
        this.localServeTime(params, callback);
        break;
      }
      case KRBridgeModule.CURRENT_TIME_STAMP: {
        return this.currentTimestamp();
      }
      case KRBridgeModule.DATE_FORMATTER: {
        return this.dateFormatter(params);
      }
      case 'getScreenCornerRadius': {
        return this.getScreenCornerRadius();
      }
      case KRBridgeModule.READ_ASSET_FILE: {
        this.readAssetFile(params, callback);
        break;
      }
      case 'pickSshKey':
        this.pickSshKey(callback);
        break;
      case 'pickImage':
        DshImages.pick(jsonString(moduleParams(params as Object), 'source', 'album'))
          .then((result: Record<string, Object>) => callback?.(result));
        break;
      case 'pickFile':
        DshFiles.pick().then((result: Record<string, Object>) => callback?.(result));
        break;
      case 'saveImage':
        DshImages.save(jsonString(moduleParams(params as Object), 'dataUrl'))
          .then((result: Record<string, Object>) => callback?.(result));
        break;
      case 'startVoiceRecognition':
        this.startVoiceRecognition(callback);
        break;
      case 'stopVoiceRecognition':
        this.stopVoiceRecognition();
        break;
      case 'cancelVoiceRecognition':
        this.cancelVoiceRecognition();
        break;
      case 'importSshKey':
        this.importSshKey(params, callback);
        break;
      case 'validateSshKey':
        this.validateSshKey(params, callback);
        break;
      case 'deleteSshKey':
        this.deleteSshKey(params);
        break;
      case 'shareExportFile':
        this.shareExportFile(params, callback);
        break;
      case 'readLastCrash':
        DshCrashStore.shared.read().then((raw: string) => callback?.(mapOf(['raw'], [raw])));
        break;
      case 'clearLastCrash':
        DshCrashStore.shared.clear().then(() => callback?.(mapOf(['ok'], [true])))
          .catch(() => callback?.(mapOf(['ok'], [false])));
        break;
      case 'getDeviceInfo':
        return this.getDeviceInfo();
      case 'closeKeyboard':
      case 'setSystemBarsDimmed':
      case 'startSshKeepAlive':
      case 'stopSshKeepAlive':
        break;
      default:
        break;
    }
    return null;
  }

  // ===== 语音识别（按住说话）=====

  private startVoiceRecognition(callback: KuiklyRenderCallback): void {
    const context = DshOhosContext.require();
    // 先绑定回调槽位：拒绝授权时也要能把错误回传给页面（否则槽位为空，错误被丢弃）。
    this.unbindVoiceSlot();
    this.voiceSlotId = DshOhosContext.bindSlot((value: Record<string, Object>) => {
      callback(value);
    });
    const permissions: Permissions[] = ['ohos.permission.MICROPHONE'];
    abilityAccessCtrl.createAtManager().requestPermissionsFromUser(context, permissions)
      .then((result: abilityAccessCtrl.PermissionRequestResult) => {
        const granted = result.authResults.length > 0 && result.authResults[0] === 0;
        if (!granted) {
          this.emitVoiceError('permission_denied', '需要麦克风权限才能使用语音输入');
          return;
        }
        this.voiceSessionId = `dsh_voice_${Date.now()}`;
        this.voiceFinalText = '';
        this.voiceAudioRemainder = new Uint8Array(0);
        this.voiceRecording = true;
        this.createEngineAndListen();
      })
      .catch((error: Object) => {
        dshError('request microphone failed', error);
        this.emitVoiceError('permission_denied', '需要麦克风权限才能使用语音输入');
      });
  }

  private async createEngineAndListen(): Promise<void> {
    try {
      const engine = await this.ensureVoiceEngine();
      engine.setListener(this.voiceListener());
      engine.startListening({
        sessionId: this.voiceSessionId,
        audioInfo: {
          audioType: 'pcm',
          sampleRate: 16000,
          soundChannel: 1,
          sampleBit: 16
        }
      });
      await this.startVoiceCapturer();
      this.emitVoice('ready');
    } catch (error) {
      dshError('start voice recognition failed', error as Object);
      this.voiceRecording = false;
      this.releaseVoiceCapturer();
      this.emitVoiceError('start_failed', '语音识别启动失败');
    }
  }

  private async ensureVoiceEngine(): Promise<speechRecognizer.SpeechRecognitionEngine> {
    const existing = this.voiceEngine;
    if (existing !== null) {
      return existing;
    }
    const engine = await speechRecognizer.createEngine({ language: 'zh-CN', online: 1 });
    this.voiceEngine = engine;
    return engine;
  }

  private voiceListener(): speechRecognizer.RecognitionListener {
    return {
      onStart: (_sessionId: string, _eventMessage: string): void => {
      },
      onEvent: (_sessionId: string, _eventCode: number, _eventMessage: string): void => {
      },
      onResult: (_sessionId: string, result: speechRecognizer.SpeechRecognitionResult): void => {
        const text = result.result.trim();
        if (text.length === 0) {
          return;
        }
        if (result.isFinal || result.isLast) {
          this.voiceFinalText = text;
          this.emitVoice('final', text);
        } else {
          this.emitVoice('partial', text);
        }
      },
      onComplete: (_sessionId: string, _eventMessage: string): void => {
        this.voiceRecording = false;
        this.releaseVoiceCapturer();
        this.emitVoice('end');
      },
      onError: (_sessionId: string, errorCode: number, errorMessage: string): void => {
        dshError(`voice recognition error code=${errorCode} message=${errorMessage}`);
        this.voiceRecording = false;
        this.releaseVoiceCapturer();
        this.emitVoiceError('failed', errorMessage.length > 0 ? errorMessage : '语音识别失败');
      }
    };
  }

  private async startVoiceCapturer(): Promise<void> {
    const options: audio.AudioCapturerOptions = {
      streamInfo: {
        samplingRate: audio.AudioSamplingRate.SAMPLE_RATE_16000,
        channels: audio.AudioChannel.CHANNEL_1,
        sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
        encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW
      },
      capturerInfo: {
        source: audio.SourceType.SOURCE_TYPE_VOICE_RECOGNITION,
        capturerFlags: 0
      }
    };
    const capturer = await audio.createAudioCapturer(options);
    this.voiceCapturer = capturer;
    const readCallback = (buffer: ArrayBuffer): void => {
      this.writeVoiceAudio(buffer);
    };
    this.voiceReadDataCallback = readCallback;
    capturer.on('readData', readCallback);
    await capturer.start();
  }

  private writeVoiceAudio(buffer: ArrayBuffer): void {
    const engine = this.voiceEngine;
    if (!this.voiceRecording || engine === null || this.voiceSessionId.length === 0 || buffer.byteLength === 0) {
      return;
    }
    this.emitVoiceLevel(this.computeVoiceLevel(buffer));
    const input = new Uint8Array(buffer);
    const merged = new Uint8Array(this.voiceAudioRemainder.length + input.length);
    merged.set(this.voiceAudioRemainder, 0);
    merged.set(input, this.voiceAudioRemainder.length);
    const chunkSize = 1280;
    let offset = 0;
    while (offset + chunkSize <= merged.length) {
      const chunk = merged.slice(offset, offset + chunkSize);
      try {
        engine.writeAudio(this.voiceSessionId, chunk);
      } catch (error) {
        dshError('writeAudio failed', error as Object);
        this.stopVoiceRecognition();
        return;
      }
      offset += chunkSize;
    }
    this.voiceAudioRemainder = merged.slice(offset);
  }

  private computeVoiceLevel(buffer: ArrayBuffer): number {
    const samples = new Int16Array(buffer);
    if (samples.length === 0) {
      return 0;
    }
    let sum = 0;
    for (let index = 0; index < samples.length; index++) {
      const value = samples[index] / 32768;
      sum += value * value;
    }
    const rms = Math.sqrt(sum / samples.length);
    const db = 20 * Math.log10(Math.max(rms, 0.000001));
    return Math.min(1, Math.max(0, (db + 60) / 60));
  }

  private stopVoiceRecognition(): void {
    if (!this.voiceRecording) {
      this.emitVoice('end');
      return;
    }
    this.voiceRecording = false;
    this.releaseVoiceCapturer();
    const engine = this.voiceEngine;
    if (engine === null) {
      this.emitVoice('end');
      return;
    }
    try {
      engine.finish(this.voiceSessionId);
    } catch (error) {
      dshError('finish voice recognition failed', error as Object);
      this.emitVoice('end');
    }
  }

  private cancelVoiceRecognition(): void {
    this.voiceRecording = false;
    this.releaseVoiceCapturer();
    const engine = this.voiceEngine;
    if (engine !== null) {
      try {
        engine.cancel(this.voiceSessionId);
      } catch (error) {
        dshError('cancel voice recognition failed', error as Object);
      }
    }
    this.unbindVoiceSlot();
  }

  private async releaseVoiceCapturer(): Promise<void> {
    const capturer = this.voiceCapturer;
    if (capturer === null) {
      return;
    }
    const readCallback = this.voiceReadDataCallback;
    if (readCallback !== null) {
      try {
        capturer.off('readData', readCallback);
      } catch (error) {
      }
    }
    try {
      await capturer.stop();
    } catch (error) {
    }
    try {
      await capturer.release();
    } catch (error) {
    }
    this.voiceCapturer = null;
    this.voiceReadDataCallback = null;
    this.voiceAudioRemainder = new Uint8Array(0);
  }

  private emitVoice(event: string, text: string = ''): void {
    if (this.voiceSlotId.length === 0) {
      return;
    }
    const includeText = text.length > 0 || event === 'partial' || event === 'final';
    DshOhosContext.postToMain(this.voiceSlotId, mapOf(['event', 'text'], [event, includeText ? text : '']));
  }

  private emitVoiceLevel(level: number): void {
    if (this.voiceSlotId.length === 0) {
      return;
    }
    const now = Date.now();
    if (now - this.voiceLastLevelAt < 80) {
      return;
    }
    this.voiceLastLevelAt = now;
    DshOhosContext.postToMain(this.voiceSlotId, mapOf(['event', 'level'], ['level', level]));
  }

  private emitVoiceError(code: string, message: string): void {
    if (this.voiceSlotId.length === 0) {
      return;
    }
    DshOhosContext.postToMain(this.voiceSlotId, mapOf(['event', 'code', 'message'], ['error', code, message]));
  }

  private unbindVoiceSlot(): void {
    if (this.voiceSlotId.length > 0) {
      DshOhosContext.unbindSlot(this.voiceSlotId);
      this.voiceSlotId = '';
    }
  }

  private showAlert(params: KRAny, callback: KuiklyRenderCallback) {
  }

  private closePage() {
    router.back();
  }

  // TODO open new page
  private openPage() {
  }

  private copyToPasteboard(params: KRAny): void {
    const content = jsonString(moduleParams(params as Object), 'content');
    const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, content);
    pasteboard.getSystemPasteboard().setData(data);
  }

  private toast(params: KRAny): void {
    const content = jsonString(moduleParams(params as Object), 'content');
    try {
      promptAction.showToast({ message: content, duration: 2000 });
    } catch (error) {
      dshError('toast failed', error as Object);
    }
  }

  private log(params: KRAny) {
    if (params == null) {
      return
    }
    console.log(`KuiklyRender ${JSON.stringify(params)}`,)
  }

  private reportDT(params: KRAny) {
  }

  private reportRealtime(params: KRAny) {
  }

  private localServeTime(params: KRAny, callback: KuiklyRenderCallback) {
    callback({ "time": Date.now() / 1000.0 });
  }

  private currentTimestamp(): string {
    return Date.now().toString()
  }

  // TODO format date
  private dateFormatter(params: KRAny): string {
    console.log(`========== ${params}`)
    return "";
  }

  private readAssetFile(params: KRAny, callback: KuiklyRenderCallback) {
    // let startTimeMills = Date.now();
    let records = JSON.parse(params as string) as Record<string, string>;
    let assetPath = records["assetPath"];

    let context = getContext(this) as common.UIAbilityContext;
    /*
    context.resourceManager.getRawFileContent(assetPath, (error: BusinessError, value: Uint8Array) => {
      let textDecoder: util.TextDecoder = util.TextDecoder.create();
      let res: string = textDecoder.decodeWithStream(value);
      callback({
        "result": JSON.parse(res)
      });
    });
    */
    let resDir = context.resourceDir
    let path = resDir + '/' + assetPath
    let assetURI = fileUri.getUriFromPath(path)
    const file = fs.openSync(assetURI, fs.OpenMode.READ_ONLY);
    const stat = fs.statSync(file.fd);
    const arrayBuffer = new ArrayBuffer(stat.size);
    const slot = DshOhosContext.bindOnce((value: Record<string, Object>) => {
      callback(value);
    });
    fs.read(file.fd, arrayBuffer, (err: BusinessError, readLen: number) => {
      if (err) {
        console.error("read failed with error message: " + err.message + ", error code: " + err.code);
      } else {
        let buf = new Uint8Array(arrayBuffer, 0, readLen);
        const textDecoder = new util.TextDecoder('utf-8');
        const res = textDecoder.decodeWithStream(new Uint8Array(buf));
        const parsed: Record<string, Object> = {};
        parsed['result'] = JSON.parse(res);
        DshOhosContext.postToMain(slot, parsed);
      }
      fs.closeSync(file);
    });
  }

  private pickSshKey(callback: KuiklyRenderCallback): void {
    const slot = DshOhosContext.bindOnce((value: Record<string, Object>) => {
      callback(value);
    });
    new DshSshKeyStore().pickKey().then((uri: string) => {
      DshOhosContext.postToMain(slot, mapOf(['uri'], [uri]));
    }).catch((error: Object) => {
      dshError('pickSshKey failed', error);
      DshOhosContext.postToMain(slot, mapOf(['uri'], ['']));
    });
  }

  private importSshKey(params: KRAny, callback: KuiklyRenderCallback): void {
    const uri = jsonString(moduleParams(params as Object), 'uri');
    const keyId = new DshSshKeyStore().importUri(uri);
    if (keyId.length === 0) {
      callback(mapOf(['ok', 'message'], [false, '无法导入 SSH 私钥']));
      return;
    }
    callback(mapOf(['ok', 'keyId'], [true, keyId]));
  }

  private validateSshKey(params: KRAny, callback: KuiklyRenderCallback): void {
    const keyId = jsonString(moduleParams(params as Object), 'keyId');
    callback(mapOf(['valid'], [new DshSshKeyStore().validateKey(keyId)]));
  }

  private deleteSshKey(params: KRAny): void {
    const keyId = jsonString(moduleParams(params as Object), 'keyId');
    new DshSshKeyStore().deleteKey(keyId);
  }
  private shareExportFile(params: KRAny, callback: KuiklyRenderCallback): void {
    const path = jsonString(moduleParams(params as Object), 'path');
    if (path.length === 0) { callback?.(mapOf(['ok', 'message'], [false, '缺少文件路径'])); return; }
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const uri = fileUri.getUriFromPath(path);
      const data = new unifiedDataChannel.UnifiedData();
      data.addRecord(new unifiedDataChannel.File(uri));
      const controller = new systemShare.ShareController(context);
      controller.show(data, (error: BusinessError) => {
        if (error) {
          dshError('shareExportFile failed', error);
        }
        callback?.(mapOf(['ok', 'message'], [!error, error ? '无法分享文件' : '分享操作完成']));
      });
    } catch (error) {
      dshError('shareExportFile failed', error as Object);
      callback?.(mapOf(['ok', 'message'], [false, '无法打开分享面板']));
    }
  }

  /**
   * 设备屏幕圆角半径（dp）。HarmonyOS 未公开该能力，返回 0 由 Kotlin 侧回退默认圆角。
   */
  private getScreenCornerRadius(): string {
    return "0";
  }

  private getDeviceInfo(): string {
    try {
      const bundleInfo = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT);
      const version = bundleInfo.versionName;
      const model = deviceInfo.marketName ?? deviceInfo.model ?? '';
      const os = `HarmonyOS ${deviceInfo.displayVersion}`;
      return JSON.stringify({ version: version, model: model, os: os });
    } catch (error) {
      dshError('getDeviceInfo failed', error as Object);
      return '';
    }
  }
}
