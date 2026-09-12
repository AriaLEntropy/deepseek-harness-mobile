import { common } from '@kit.AbilityKit';
import { hiAppEvent } from '@kit.PerformanceAnalysisKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { utf8Encode } from './DshBytes';
import { dshError } from './DshOhosContext';

const MAX_REPORT_CHARS = 16000;
const MAX_LOG_BYTES = 32 * 1024;

class CrashRecord {
  id: string = '';
  time: number = 0;
  text: string = '';
}

/** Retains the latest crash across launches; reads never consume the feedback evidence. */
export class DshCrashStore {
  static readonly shared: DshCrashStore = new DshCrashStore();
  private path: string = '';
  private latest: CrashRecord = new CrashRecord();
  private queue: Promise<void> = Promise.resolve();
  private watcher: hiAppEvent.Watcher | null = null;

  attach(context: common.UIAbilityContext): void {
    if (this.watcher) return;
    this.path = `${context.filesDir}/last_crash.json`;
    this.enqueue(async () => {
      if (!await fs.access(this.path)) return;
      const stat = await fs.stat(this.path);
      if (stat.size > 128 * 1024) throw new Error('Stored crash record is too large');
      const saved = JSON.parse(await fs.readText(this.path)) as CrashRecord;
      if (typeof saved.id === 'string' && typeof saved.time === 'number' && typeof saved.text === 'string') {
        this.latest = saved;
      }
    });
    const watcher: hiAppEvent.Watcher = {
      name: 'DshCrashWatcher',
      appEventFilters: [{ domain: hiAppEvent.domain.OS, names: [hiAppEvent.event.APP_CRASH] }],
      onReceive: (domain: string, groups: Array<hiAppEvent.AppEventGroup>): void => {
        if (domain !== hiAppEvent.domain.OS) return;
        for (const group of groups) {
          if (group.name !== hiAppEvent.event.APP_CRASH) continue;
          for (const event of group.appEventInfos) {
            this.enqueue(() => this.saveEvent(event));
          }
        }
      },
    };
    try {
      hiAppEvent.addWatcher(watcher);
      this.watcher = watcher;
    } catch (error) {
      dshError('subscribe crash events failed', error as Object);
    }
  }

  detach(): void {
    if (!this.watcher) return;
    try {
      hiAppEvent.removeWatcher(this.watcher);
    } catch (error) {
      dshError('unsubscribe crash events failed', error as Object);
    }
    this.watcher = null;
  }

  async read(): Promise<string> {
    await this.queue;
    return this.latest.text;
  }

  async clear(): Promise<void> {
    const operation = this.queue.then(async () => {
        if (this.path && await fs.access(this.path)) await fs.unlink(this.path);
        this.latest = new CrashRecord();
    });
    this.queue = operation.catch((error: Object) => { dshError('clear crash record failed', error); });
    await operation;
  }

  private enqueue(work: () => Promise<void>): void {
    this.queue = this.queue.then(work).catch((error: Object) => {
      // A failed write must not poison later reads or crash delivery.
      dshError('crash record operation failed', error);
    });
  }

  private async saveEvent(event: hiAppEvent.AppEventInfo): Promise<void> {
    const params = event.params;
    const record = new CrashRecord();
    record.time = typeof params['time'] === 'number' ? params['time'] as number : 0;
    record.id = typeof params['uuid'] === 'string' ? params['uuid'] as string : `${record.time}-${params['pid']}`;
    if (record.id === this.latest.id || record.time < this.latest.time) return;
    record.text = `time=${record.time}\nid=${record.id}\ntype=${params['crash_type']}\n` +
      `version=${params['bundle_version']}\nexception=${JSON.stringify(params['exception'])}`;
    record.text = record.text.substring(0, 6000);
    const logs = params['external_log'];
    const paths: string[] = [];
    const copiedPaths: string[] = [];
    if (Array.isArray(logs)) {
      for (const path of logs as string[]) {
        if (typeof path !== 'string' || paths.indexOf(path) >= 0) continue;
        paths.push(path);
        if (record.text.length >= MAX_REPORT_CHARS) continue;
        try {
          const size = Math.min((await fs.stat(path)).size, MAX_LOG_BYTES);
          if (size > 0) {
            const text = await fs.readText(path, { length: size });
            record.text = `${record.text}\n\n${text}`.substring(0, MAX_REPORT_CHARS);
            copiedPaths.push(path);
          }
        } catch (error) {
          // The system may omit/expire its file. Keep the structured crash summary.
          dshError('read system crash log failed', error as Object);
        }
      }
    }
    if (params['log_over_limit'] === true) {
      record.text = `[系统故障日志目录已达容量上限]\n${record.text}`.substring(0, MAX_REPORT_CHARS);
    }
    const temporaryPath = `${this.path}.tmp`;
    try {
      const file = await fs.open(temporaryPath, fs.OpenMode.CREATE | fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
      try {
        const bytes = utf8Encode(JSON.stringify(record));
        let offset = 0;
        while (offset < bytes.length) {
          const count = await fs.write(file.fd, bytes.buffer.slice(offset), { offset: offset });
          if (count <= 0) throw new Error('Crash record write was incomplete');
          offset += count;
        }
      } finally {
        await fs.close(file);
      }
      await fs.rename(temporaryPath, this.path);
      this.latest = record;
    } finally {
      if (await fs.access(temporaryPath)) await fs.unlink(temporaryPath);
    }
    // Only release system files after our bounded snapshot has been persisted successfully.
    for (const path of copiedPaths) {
      try { await fs.unlink(path); } catch (error) { dshError('remove system crash log failed', error as Object); }
    }
  }
}
