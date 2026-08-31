import { KuiklyNativeManager } from '@kuikly-open/render';
import Napi from 'libentry.so';
import { DshOhosContext } from './native/DshOhosContext';

class MyNativeManager extends KuiklyNativeManager {
  protected loadNative(): number {
    const handler = Napi.initKuikly();
    DshOhosContext.installMainHandler();
    return handler;
  }
}

// 导出一个全局的 KuiklyNativeManager 实例给 Kuikly 页面共用
const globalNativeManager = new MyNativeManager();

export default globalNativeManager;