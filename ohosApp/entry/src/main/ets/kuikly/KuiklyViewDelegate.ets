import {
  IKuiklyViewDelegate,
  KRRenderModuleExportCreator,
  KRRenderViewExportCreator
} from '@kuikly-open/render';
import { KRMyView } from './components/KRMyView';
import { KRBridgeModule } from './modules/KRBridgeModule';
import { KRDshEngineModule } from './modules/KRDshEngineModule';
import { KRDshRelayModule } from './modules/KRDshRelayModule';
import { KRDshWebSocketModule } from './modules/KRDshWebSocketModule';
import { KRMyModule } from './modules/KRMyModule';

export class KuiklyViewDelegate extends IKuiklyViewDelegate {
  getCustomRenderViewCreatorRegisterMap(): Map<string, KRRenderViewExportCreator> {
    const map: Map<string, KRRenderViewExportCreator> = new Map();
    map.set(KRMyView.VIEW_NAME, () => new KRMyView());
    return map;
  }

  getCustomRenderModuleCreatorRegisterMap(): Map<string, KRRenderModuleExportCreator> {
    const map: Map<string, KRRenderModuleExportCreator> = new Map();
    map.set(KRBridgeModule.MODULE_NAME, () => new KRBridgeModule());
    map.set(KRDshRelayModule.MODULE_NAME, () => new KRDshRelayModule());
    map.set(KRDshWebSocketModule.MODULE_NAME, () => new KRDshWebSocketModule());
    map.set(KRDshEngineModule.MODULE_NAME, () => new KRDshEngineModule());
    map.set(KRMyModule.MODULE_NAME, () => new KRMyModule());
    return map;
  }
}