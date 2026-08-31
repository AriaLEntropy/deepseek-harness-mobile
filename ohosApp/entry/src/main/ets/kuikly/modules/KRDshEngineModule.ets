import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { mapOf } from '../native/DshJson';

export class KRDshEngineModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'DshEngineModule';

  syncMode(): boolean {
    return true;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    switch (method) {
      case 'start':
      case 'status':
        return this.unsupported(callback);
      case 'startSsh':
        callback?.(mapOf(
          ['phase', 'message', 'localPort', 'generation'],
          ['ERROR', 'HarmonyOS 暂不支持 SSH 隧道，请使用扫码连接', 0, 0],
        ));
        return null;
      case 'trustSshFingerprint':
      case 'stopSsh':
      case 'stop':
        return null;
      case 'sshEndpoint':
        return '';
      default:
        void params;
        return null;
    }
  }

  private unsupported(callback: KuiklyRenderCallback | null): string {
    const payload = mapOf(
      ['phase', 'progress', 'message'],
      ['UNSUPPORTED', 0, '本地模式已移至 DSH Local'],
    );
    callback?.(payload);
    return JSON.stringify(payload);
  }
}
