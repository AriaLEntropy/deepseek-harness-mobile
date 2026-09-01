import common from '@ohos.app.ability.common';
import fs from '@ohos.file.fs';
import hilog from '@ohos.hilog';
import Napi from 'libentry.so';
import { parseObject } from './DshJson';

const TAG = 'DshOhos';
const DOMAIN = 0x0D51;

type SlotHandler = (value: Record<string, Object>) => void;

export class DshOhosContext {
  private static ability: common.UIAbilityContext | null = null;
  private static slots: Map<string, SlotHandler> = new Map();
  private static seq: number = 0;
  private static handlerReady: boolean = false;

  static attach(context: common.UIAbilityContext): void {
    DshOhosContext.ability = context;
    const databases = `${context.filesDir}/databases`;
    try {
      if (!pathExists(databases)) {
        fs.mkdirSync(databases, true);
      }
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'mkdir databases failed %{public}s', JSON.stringify(error));
    }
  }

  static installMainHandler(): void {
    if (DshOhosContext.handlerReady) {
      return;
    }
    DshOhosContext.handlerReady = true;
    Napi.setMainHandler((raw: string): void => {
      const split = raw.indexOf('\n');
      const id = split >= 0 ? raw.substring(0, split) : raw;
      const payload = split >= 0 ? raw.substring(split + 1) : '';
      const handler = DshOhosContext.slots.get(id);
      if (handler) {
        handler(payload.length > 0 ? parseObject(payload) : {});
      }
    });
  }

  static bindSlot(handler: SlotHandler): string {
    const id = `dsh${++DshOhosContext.seq}`;
    DshOhosContext.slots.set(id, handler);
    return id;
  }

  static bindOnce(handler: SlotHandler): string {
    const id = `dsh${++DshOhosContext.seq}`;
    DshOhosContext.slots.set(id, (value: Record<string, Object>): void => {
      DshOhosContext.slots.delete(id);
      handler(value);
    });
    return id;
  }

  static unbindSlot(id: string): void {
    DshOhosContext.slots.delete(id);
  }

  static postToMain(id: string, value: Record<string, Object>): void {
    Napi.postToMain(`${id}\n${JSON.stringify(value)}`);
  }

  static require(): common.UIAbilityContext {
    const context = DshOhosContext.ability;
    if (!context) {
      throw new Error('HarmonyOS 宿主上下文尚未就绪');
    }
    return context;
  }

  static filesDir(): string {
    return DshOhosContext.require().filesDir;
  }

  static databaseDir(): string {
    return `${DshOhosContext.filesDir()}/databases`;
  }
}

export function pathExists(path: string): boolean {
  try {
    return fs.accessSync(path);
  } catch (error) {
    return false;
  }
}

export function dshLog(message: string): void {
  hilog.info(0x0000, TAG, '%{public}s', message);
}

export function dshError(message: string, error?: Object): void {
  hilog.error(0x0000, TAG, '%{public}s %{public}s', message, error ? JSON.stringify(error) : '');
}
