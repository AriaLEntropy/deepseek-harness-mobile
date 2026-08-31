import { abilityAccessCtrl } from '@kit.AbilityKit';
import { scanBarcode, scanCore } from '@kit.ScanKit';
import { DshOhosContext, dshLog } from './DshOhosContext';

export async function scanRelayQr(): Promise<string> {
  const context = DshOhosContext.require();
  const atManager = abilityAccessCtrl.createAtManager();
  const granted = await atManager.requestPermissionsFromUser(context, ['ohos.permission.CAMERA']);
  dshLog('scanRelayQr camera permission requested');
  if (granted.authResults.length === 0 || granted.authResults[0] !== 0) {
    throw new Error('未授予相机权限');
  }
  dshLog('scanRelayQr opening scanner');
  const options: scanBarcode.ScanOptions = {
    scanTypes: [scanCore.ScanType.QR_CODE],
    enableMultiMode: true,
    enableAlbum: false,
  };
  const result = await scanBarcode.startScanForResult(context, options);
  const value = result.originalValue ?? '';
  dshLog(`scanRelayQr result length=${value.length} startsHttp=${value.indexOf('http') === 0} hasHash=${value.indexOf('#') >= 0}`);
  return value;
}
