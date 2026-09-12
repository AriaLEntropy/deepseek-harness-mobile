import { camera, cameraPicker } from '@kit.CameraKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { image } from '@kit.ImageKit';
import { fileIo as fs, fileUri } from '@kit.CoreFileKit';
import { decodeBase64Std, encodeBase64Std } from './DshBytes';
import { mapOf } from './DshJson';
import { DshOhosContext, dshError } from './DshOhosContext';

// Limit bridge allocations before Base64 expansion; Host imageLimits still apply in shared.
const MAX_IMAGE_BYTES = 32 * 1024 * 1024;
const CHUNK_BYTES = 64 * 1024;

/** Native image operations run on the UI thread via HRBridgeModule (syncMode=false). */
export class DshImages {
  private static busy: boolean = false;

  static async pick(source: string): Promise<Record<string, Object>> {
    if (DshImages.busy) return failure('请先完成当前图片操作');
    DshImages.busy = true;
    try {
      if (source === 'camera') {
        const result = await cameraPicker.pick(DshOhosContext.require(), [cameraPicker.PickerMediaType.PHOTO], {
          cameraPosition: camera.CameraPosition.CAMERA_POSITION_BACK,
        });
        if (result.resultCode !== 0) return cancelled();
        const uri = result.resultUri;
        if (!uri) return failure('相机未返回图片');
        const entry = await readEntry(uri);
        if (!entry) return failure('不支持的图片格式');
        return mapOf(['ok', 'images'], [true, [entry]]);
      }
      if (source === 'album') {
        const picker = new photoAccessHelper.PhotoViewPicker();
        const result = await picker.select({
          MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
          maxSelectNumber: 10,
        });
        if (result.photoUris.length === 0) return cancelled();
        const images: Record<string, Object>[] = [];
        for (const selected of result.photoUris) {
          const entry = await readEntry(selected);
          if (entry) images.push(entry);
        }
        if (images.length === 0) return failure('不支持的图片格式');
        return mapOf(['ok', 'images'], [true, images]);
      }
      return failure('不支持的图片来源');
    } catch (error) {
      dshError('pick image failed', error as Object);
      return failure(errorMessage(error as Object, '无法获取图片，请检查相册或相机是否可用'));
    } finally {
      DshImages.busy = false;
    }
  }

  static async save(dataUrl: string): Promise<Record<string, Object>> {
    if (DshImages.busy) return failure('请先完成当前图片操作');
    DshImages.busy = true;
    let temporaryPath = '';
    let helper: photoAccessHelper.PhotoAccessHelper | null = null;
    try {
      const comma = dataUrl.indexOf(',');
      const header = dataUrl.substring(0, comma);
      if (comma < 0 || !/^data:image\/[a-z0-9.+-]+;base64$/.test(header)) {
        return failure('无效的图片数据');
      }
      const encoded = dataUrl.substring(comma + 1);
      if (encoded.length > Math.ceil(MAX_IMAGE_BYTES / 3) * 4) return failure('图片超过 32 MB，无法保存');
      const bytes = decodeBase64Std(encoded);
      if (bytes.length === 0 || bytes.length > MAX_IMAGE_BYTES || encodeBase64Std(bytes) !== encoded) {
        return failure('图片数据为空、过大或 Base64 格式错误');
      }
      const info = await imageInfo(bytes.buffer);
      const mime = info.mimeType.toLowerCase();
      if (header !== `data:${mime};base64`) return failure('图片格式与内容不一致');
      const extension = imageExtension(mime);
      if (!extension) return failure('不支持保存此图片格式');

      const context = DshOhosContext.require();
      temporaryPath = `${context.cacheDir}/dsh-image-save.${extension}`;
      const sourceFile = await fs.open(temporaryPath, fs.OpenMode.CREATE | fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
      try {
        // A short write is not success. Advance both the file and buffer positions explicitly.
        let offset = 0;
        while (offset < bytes.length) {
          const chunk = bytes.buffer.slice(offset, Math.min(bytes.length, offset + CHUNK_BYTES));
          const written = await fs.write(sourceFile.fd, chunk, { offset: offset });
          if (written <= 0) throw new Error('图片写入失败');
          offset += written;
        }
        helper = photoAccessHelper.getPhotoAccessHelper(context);
        const targets = await helper.showAssetsCreationDialog([fileUri.getUriFromPath(temporaryPath)], [{
          title: `DSH_${Date.now()}`,
          fileNameExtension: extension,
          photoType: photoAccessHelper.PhotoType.IMAGE,
        }]);
        if (targets.length === 0) return cancelled();
        const target = await fs.open(targets[0], fs.OpenMode.READ_WRITE);
        try {
          await fs.copyFile(sourceFile.fd, target.fd);
          if ((await fs.stat(target.fd)).size !== bytes.length) throw new Error('图片写入不完整');
        } finally {
          await fs.close(target);
        }
      } finally {
        await fs.close(sourceFile);
      }
      return mapOf(['ok'], [true]);
    } catch (error) {
      dshError('save image failed', error as Object);
      return failure(errorMessage(error as Object, '无法保存图片'));
    } finally {
      if (helper) {
        try { await helper.release(); } catch (error) { dshError('release photo helper failed', error as Object); }
      }
      if (temporaryPath) {
        try { await fs.unlink(temporaryPath); } catch (error) { dshError('remove temporary image failed', error as Object); }
      }
      DshImages.busy = false;
    }
  }
}

async function readEntry(uri: string): Promise<Record<string, Object> | null> {
  const bytes = await readImage(uri);
  const info = await imageInfo(bytes.buffer);
  const mime = info.mimeType.toLowerCase();
  if (!mime.startsWith('image/')) return null;
  let name = uri.substring(uri.lastIndexOf('/') + 1);
  try { name = decodeURIComponent(name); } catch (error) { /* Keep the URI filename. */ }
  return mapOf(
    ['dataUrl', 'mediaType', 'name', 'bytes', 'width', 'height'],
    [`data:${mime};base64,${encodeBase64Std(bytes)}`, mime, name || 'image',
      `${bytes.length}`, `${info.size.width}`, `${info.size.height}`],
  );
}

async function readImage(uri: string): Promise<Uint8Array> {
  const file = await fs.open(uri, fs.OpenMode.READ_ONLY);
  try {
    const size = (await fs.stat(file.fd)).size;
    if (size <= 0) throw new Error('图片为空');
    if (size > MAX_IMAGE_BYTES) throw new Error('图片超过 32 MB，请选择较小的图片');
    const bytes = new Uint8Array(size);
    const buffer = new ArrayBuffer(Math.min(size, CHUNK_BYTES));
    let offset = 0;
    while (offset < size) {
      const count = await fs.read(file.fd, buffer, { offset: offset, length: Math.min(buffer.byteLength, size - offset) });
      if (count <= 0) throw new Error('图片读取不完整');
      bytes.set(new Uint8Array(buffer, 0, count), offset);
      offset += count;
    }
    return bytes;
  } finally {
    await fs.close(file);
  }
}

async function imageInfo(buffer: ArrayBuffer): Promise<image.ImageInfo> {
  const source = image.createImageSource(buffer);
  if (!source) throw new Error('无法解码图片');
  try {
    const info = await source.getImageInfo();
    if (info.size.width <= 0 || info.size.height <= 0) throw new Error('图片尺寸无效');
    return info;
  } finally {
    await source.release();
  }
}

function imageExtension(mime: string): string {
  switch (mime) {
    case 'image/jpeg': return 'jpg';
    case 'image/png': return 'png';
    case 'image/webp': return 'webp';
    case 'image/gif': return 'gif';
    case 'image/bmp': return 'bmp';
    case 'image/heic': return 'heic';
    case 'image/heif': return 'heif';
    default: return '';
  }
}

function cancelled(): Record<string, Object> {
  return mapOf(['ok', 'cancelled'], [false, true]);
}

function failure(message: string): Record<string, Object> {
  return mapOf(['ok', 'error'], [false, message]);
}

function errorMessage(error: Object, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
