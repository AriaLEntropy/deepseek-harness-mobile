import UIKit
import PhotosUI
import Photos
import AVFoundation
import UniformTypeIdentifiers
import ImageIO

/** Platform acquisition only; Host imageLimits and session.prompt stay in shared Kotlin. */
@objc(DshImages)
final class DshImages: NSObject, PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    @objc static let shared = DshImages()
    private static let maxBytes = 32 * 1024 * 1024
    private var callback: ((NSDictionary) -> Void)?
    private var operation = UUID()
    private var loading: Progress?

    @objc(pick:completion:)
    func pick(_ source: String, completion: @escaping (NSDictionary) -> Void) {
        DispatchQueue.main.async {
            guard self.begin(completion) else { return }
            let id = self.operation
            if source == "camera" {
                guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
                    self.finish(["ok": false, "error": "当前设备没有可用相机"], id: id); return
                }
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    DispatchQueue.main.async {
                        guard self.operation == id else { return }
                        guard granted else {
                            self.finish(["ok": false, "error": "相机权限未开启，请在系统设置中允许访问相机"], id: id); return
                        }
                        guard let presenter = DshNativeUi.topViewController() else {
                            self.finish(["ok": false, "error": "无法打开相机"], id: id); return
                        }
                        let picker = UIImagePickerController()
                        picker.sourceType = .camera
                        picker.mediaTypes = [UTType.image.identifier]
                        picker.delegate = self
                        picker.modalPresentationStyle = .fullScreen
                        presenter.present(picker, animated: true)
                    }
                }
            } else {
                guard let presenter = DshNativeUi.topViewController() else {
                    self.finish(["ok": false, "error": "无法打开相册"], id: id); return
                }
                var config = PHPickerConfiguration()
                config.filter = .images
                config.selectionLimit = 10
                config.preferredAssetRepresentationMode = .current
                let picker = PHPickerViewController(configuration: config)
                picker.delegate = self
                picker.modalPresentationStyle = .fullScreen
                presenter.present(picker, animated: true)
            }
        }
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        let id = operation
        picker.dismiss(animated: true)
        guard !results.isEmpty else {
            finish(["ok": false, "cancelled": true], id: id); return
        }
        let supported = [UTType.png, UTType.jpeg, UTType.webP, UTType.gif]
        let group = DispatchGroup()
        let lock = NSLock()
        var images: [NSDictionary] = []
        var firstError: String?
        func recordError(_ message: String) {
            lock.lock(); if firstError == nil { firstError = message }; lock.unlock()
        }
        for result in results {
            let provider = result.itemProvider
            guard let type = provider.registeredTypeIdentifiers.first(where: { identifier in
                guard let type = UTType(identifier) else { return false }
                return supported.contains { type.conforms(to: $0) }
            }) else {
                recordError("仅支持 PNG、JPEG、WebP、GIF；请先将 HEIC/RAW 转换为 JPEG")
                continue
            }
            let suggestedName = provider.suggestedName
            group.enter()
            provider.loadFileRepresentation(forTypeIdentifier: type) { url, error in
                defer { group.leave() }
                do {
                    // Apple's temporary URL is valid only inside this completion handler.
                    guard let url = url, error == nil else { throw ImageFailure("读取图片失败，请检查 iCloud 下载或重试") }
                    let attrs = try FileManager.default.attributesOfItem(atPath: url.path)
                    guard let size = attrs[.size] as? NSNumber, size.int64Value <= Int64(Self.maxBytes) else {
                        throw ImageFailure("图片超过手机侧 32 MB 限制")
                    }
                    let handle = try FileHandle(forReadingFrom: url)
                    defer { try? handle.close() }
                    let data = try handle.read(upToCount: Self.maxBytes + 1) ?? Data()
                    guard data.count == size.intValue else { throw ImageFailure("图片读取不完整，请重试") }
                    let entry = try Self.imageResult(data, name: suggestedName ?? url.lastPathComponent)
                    lock.lock(); images.append(entry); lock.unlock()
                } catch { recordError(error.localizedDescription) }
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 60) {
            if self.operation == id && self.callback != nil {
                self.finish(["ok": false, "error": "图片下载超时，请重试"], id: id)
            }
        }
        group.notify(queue: .main) {
            guard self.operation == id else { return }
            if images.isEmpty {
                self.finish(["ok": false, "error": firstError ?? "无法读取图片数据"], id: id)
            } else {
                self.finish(["ok": true, "images": images], id: id)
            }
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        finish(["ok": false, "cancelled": true], id: operation)
    }

    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        let id = operation
        picker.dismiss(animated: true)
        guard let image = info[.originalImage] as? UIImage else {
            finish(["ok": false, "error": "拍照未返回图片"], id: id); return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            let result: NSDictionary
            do {
                guard let data = image.jpegData(compressionQuality: 0.9) else { throw ImageFailure("无法编码相机图片") }
                result = ["ok": true, "images": [try Self.imageResult(data, name: "camera-\(Int(Date().timeIntervalSince1970)).jpg")]]
            } catch { result = ["ok": false, "error": error.localizedDescription] }
            DispatchQueue.main.async { self.finish(result, id: id) }
        }
    }

    @objc(save:completion:)
    func save(_ dataUrl: String, completion: @escaping (NSDictionary) -> Void) {
        DispatchQueue.main.async {
            guard self.begin(completion) else { return }
            let id = self.operation
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    guard dataUrl.utf8.count <= Self.maxBytes * 4 / 3 + 256,
                          let delimiter = dataUrl.range(of: ";base64,"), dataUrl.hasPrefix("data:image/"),
                          let data = Data(base64Encoded: String(dataUrl[delimiter.upperBound...])) else {
                        throw ImageFailure("图片数据无效或超过 32 MB")
                    }
                    let metadata = try Self.metadata(data)
                    let declared = String(dataUrl.dropFirst(5).prefix(upTo: delimiter.lowerBound))
                    guard declared == metadata.mime else { throw ImageFailure("图片类型与内容不一致") }
                    PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                        guard status == .authorized || status == .limited else {
                            DispatchQueue.main.async { self.finish(["ok": false, "error": "未获得相册写入权限"], id: id) }; return
                        }
                        PHPhotoLibrary.shared().performChanges({
                            let request = PHAssetCreationRequest.forAsset()
                            let options = PHAssetResourceCreationOptions()
                            options.originalFilename = "dsh-image.\(metadata.ext)"
                            request.addResource(with: .photo, data: data, options: options)
                        }) { success, error in
                            DispatchQueue.main.async {
                                self.finish(["ok": success, "error": success ? "" : (error?.localizedDescription ?? "保存图片失败")], id: id)
                            }
                        }
                    }
                } catch {
                    DispatchQueue.main.async { self.finish(["ok": false, "error": error.localizedDescription], id: id) }
                }
            }
        }
    }

    private func begin(_ completion: @escaping (NSDictionary) -> Void) -> Bool {
        guard callback == nil else { completion(["ok": false, "error": "正在处理另一张图片，请稍候"]); return false }
        operation = UUID()
        callback = completion
        return true
    }

    private func finish(_ result: NSDictionary, id: UUID) {
        guard operation == id else { return }
        let completion = callback
        callback = nil
        loading = nil
        completion?(result)
    }

    private static func metadata(_ data: Data) throws -> (mime: String, ext: String, width: Int, height: Int) {
        guard !data.isEmpty && data.count <= maxBytes else { throw ImageFailure("图片为空或超过 32 MB") }
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
              let identifier = CGImageSourceGetType(source), let type = UTType(identifier as String),
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int, width > 0, height > 0 else {
            throw ImageFailure("无法识别图片格式或尺寸")
        }
        for (supported, mime, ext) in [(UTType.png, "image/png", "png"), (UTType.jpeg, "image/jpeg", "jpg"),
                                       (UTType.webP, "image/webp", "webp"), (UTType.gif, "image/gif", "gif")] {
            if type.conforms(to: supported) { return (mime, ext, width, height) }
        }
        throw ImageFailure("仅支持 PNG、JPEG、WebP、GIF")
    }

    private static func imageResult(_ data: Data, name: String) throws -> NSDictionary {
        let info = try metadata(data)
        let fileName = (name as NSString).lastPathComponent
        return ["ok": true, "mediaType": info.mime, "name": fileName.isEmpty ? "image.\(info.ext)" : fileName,
                "bytes": String(data.count), "width": String(info.width), "height": String(info.height),
                "dataUrl": "data:\(info.mime);base64,\(data.base64EncodedString())"]
    }
}

private struct ImageFailure: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}
