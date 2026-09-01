import { common } from '@kit.AbilityKit';
import { BusinessError, pasteboard } from '@kit.BasicServicesKit';
import { promptAction } from '@kit.ArkUI';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import util from '@ohos.util';
import fs from '@ohos.file.fs';
import router from '@ohos.router';
import { fileUri } from '@kit.CoreFileKit';
import { jsonString, mapOf, moduleParams } from '../native/DshJson';
import { DshSshKeyStore } from '../native/DshSshKeyStore';
import { DshOhosContext, dshError } from '../native/DshOhosContext';

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
      case KRBridgeModule.READ_ASSET_FILE: {
        this.readAssetFile(params, callback);
        break;
      }
      case 'pickSshKey':
        this.pickSshKey(callback);
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
}