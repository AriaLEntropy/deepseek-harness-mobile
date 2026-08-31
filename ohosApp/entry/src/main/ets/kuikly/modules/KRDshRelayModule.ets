import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { mapOf, moduleParams } from '../native/DshJson';
import { scanRelayQr } from '../native/DshQrScan';
import { DshRelayNativeState, DshRelayRuntime } from '../native/DshRelayRuntime';
import { DshOhosContext, dshError, dshLog } from '../native/DshOhosContext';

export class KRDshRelayModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'DshRelayModule';
  private stateListener: ((state: DshRelayNativeState) => void) | null = null;
  private stateSlot: string = '';

  syncMode(): boolean {
    return true;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    switch (method) {
      case 'scanAndPair':
        this.scanAndPair(callback);
        return null;
      case 'connect':
        this.listen(callback);
        DshRelayRuntime.shared.connect();
        return null;
      case 'disconnect':
        DshRelayRuntime.shared.disconnect();
        return null;
      case 'forget':
        this.forget(callback);
        return null;
      case 'status':
        return JSON.stringify(DshRelayRuntime.shared.current().toMap());
      case 'subscribe':
        this.listen(callback);
        return null;
      default:
        void moduleParams(params as Object);
        return null;
    }
  }

  private scanAndPair(callback: KuiklyRenderCallback | null): void {
    dshLog('scanAndPair start');
    if (!callback) {
      return;
    }
    const slot = DshOhosContext.bindOnce((value: Record<string, Object>) => {
      callback(value);
    });
    scanRelayQr().then((qr: string) => {
      dshLog(`scanAndPair qrLength=${qr.length}`);
      if (qr.length === 0) {
        dshLog('scanAndPair cancelled empty qr');
        DshOhosContext.postToMain(slot, mapOf(['ok', 'message'], [false, '已取消扫码']));
        return;
      }
      return DshRelayRuntime.shared.pairFromQr(qr).then((result) => {
        const ok = result['ok'] === true;
        dshLog(`scanAndPair done ok=${ok} message=${result['message'] ?? ''}`);
        DshOhosContext.postToMain(slot, result);
      });
    }).catch((error: Object) => {
      dshError('scanAndPair failed', error);
      const message = error instanceof Error ? error.message : '已取消扫码';
      DshOhosContext.postToMain(slot, mapOf(['ok', 'message'], [false, message]));
    });
  }

  private forget(callback: KuiklyRenderCallback | null): void {
    const slot = callback ? DshOhosContext.bindOnce((value: Record<string, Object>) => {
      callback(value);
    }) : '';
    DshRelayRuntime.shared.forget().then(() => {
      if (slot.length > 0) {
        DshOhosContext.postToMain(slot, mapOf(['ok'], [true]));
      }
    });
  }

  private listen(callback: KuiklyRenderCallback | null): void {
    if (this.stateSlot.length > 0) {
      DshOhosContext.unbindSlot(this.stateSlot);
      this.stateSlot = '';
    }
    if (this.stateListener) {
      DshRelayRuntime.shared.removeListener(this.stateListener);
      this.stateListener = null;
    }
    if (!callback) {
      return;
    }
    const slot = DshOhosContext.bindSlot((value: Record<string, Object>) => {
      callback(value);
    });
    this.stateSlot = slot;
    const listener = (state: DshRelayNativeState) => {
      DshOhosContext.postToMain(slot, state.toMap());
    };
    this.stateListener = listener;
    DshRelayRuntime.shared.addListener(listener);
  }
}
