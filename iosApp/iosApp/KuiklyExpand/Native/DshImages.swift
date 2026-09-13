import UIKit
import PhotosUI
import Photos
import AVFoundation
import Speech
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

/** 通用文件选择（文档选择器）：读原始字节并回传 Base64，Host 侧由 host-plugin 落盘。 */
@objc(DshFiles)
final class DshFiles: NSObject, UIDocumentPickerDelegate {
    @objc static let shared = DshFiles()
    private static let maxBytes = 50 * 1024 * 1024
    private var callback: ((NSDictionary) -> Void)?
    private var operation = UUID()

    @objc(pick:)
    func pick(_ completion: @escaping (NSDictionary) -> Void) {
        DispatchQueue.main.async {
            guard self.callback == nil else {
                completion(["ok": false, "error": "正在处理另一个文件，请稍候"]); return
            }
            guard let presenter = DshNativeUi.topViewController() else {
                completion(["ok": false, "error": "无法打开文件选择器"]); return
            }
            self.operation = UUID()
            self.callback = completion
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item, .data], asCopy: true)
            picker.delegate = self
            picker.allowsMultipleSelection = true
            picker.modalPresentationStyle = .fullScreen
            presenter.present(picker, animated: true)
        }
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        controller.dismiss(animated: true)
        let id = operation
        let group = DispatchGroup()
        let lock = NSLock()
        var files: [NSDictionary] = []
        var firstError: String?
        for url in urls {
            group.enter()
            DispatchQueue.global(qos: .userInitiated).async {
                defer { group.leave() }
                do {
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    // asCopy: true 已复制到临时目录，直接读取即可。
                    let data = try Data(contentsOf: url)
                    if data.isEmpty { throw FileFailure("文件内容为空") }
                    if data.count > Self.maxBytes { throw FileFailure("文件超过 50MB 上限") }
                    let name = url.lastPathComponent.isEmpty ? "attachment" : url.lastPathComponent
                    let contentType = try? url.resourceValues(forKeys: [.contentTypeKey]).contentType
                    let mime = contentType?.preferredMIMEType ?? "application/octet-stream"
                    let entry: NSDictionary = [
                        "ok": true,
                        "name": name,
                        "mediaType": mime,
                        "bytes": String(data.count),
                        "dataUrl": "data:\(mime);base64,\(data.base64EncodedString())"
                    ]
                    lock.lock(); files.append(entry); lock.unlock()
                } catch {
                    lock.lock(); if firstError == nil { firstError = error.localizedDescription }; lock.unlock()
                }
            }
        }
        group.notify(queue: .main) {
            guard self.operation == id else { return }
            if files.isEmpty {
                self.finish(["ok": false, "error": firstError ?? "无法读取文件数据"], id: id)
            } else {
                self.finish(["ok": true, "files": files], id: id)
            }
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        controller.dismiss(animated: true)
        finish(["ok": false, "cancelled": true], id: operation)
    }

    private func finish(_ result: NSDictionary, id: UUID) {
        guard operation == id else { return }
        let completion = callback
        callback = nil
        completion?(result)
    }
}

private struct FileFailure: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

/**
 语音识别（按住说话）：系统 Speech 框架做语音转文字，AVAudioEngine 采集并计算实时音量。
 通过 `start` 的回调持续回传事件字典：
 ready / level / partial / final / end / error。
 要求：Info.plist 配置 NSSpeechRecognitionUsageDescription 与 NSMicrophoneUsageDescription。
 */
@objc(DshVoiceRecognizer)
final class DshVoiceRecognizer: NSObject {
    @objc static let shared = DshVoiceRecognizer()

    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var onEvent: ((NSDictionary) -> Void)?
    private var tapInstalled = false
    private var finished = false
    private var lastLevelAt: TimeInterval = 0
    private var finalText = ""
    private var finalEmitted = false
    /// 每次录音的代次；停止/取消会使其失效，迟到的权限回调据此丢弃过期启动。
    private var generation = 0

    @objc(start:)
    func start(_ onEvent: @escaping (NSDictionary) -> Void) {
        DispatchQueue.main.async {
            self.generation += 1
            self.onEvent = onEvent
            self.finished = false
            self.finalText = ""
            self.finalEmitted = false
            self.authorizeAndStart()
        }
    }

    @objc(stop)
    func stop() {
        DispatchQueue.main.async {
            guard !self.finished else { return }
            self.generation += 1
            // 停止采集并结束音频输入，等待识别任务回传最终结果
            self.stopCaptureAndEndAudio()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                if !self.finished { self.finish() }
            }
        }
    }

    @objc(cancel)
    func cancel() {
        DispatchQueue.main.async {
            guard !self.finished else { return }
            self.generation += 1
            self.finished = true
            self.task?.cancel()
            self.task = nil
            self.request = nil
            self.stopCaptureAndEndAudio()
            self.onEvent = nil
        }
    }

    private func authorizeAndStart() {
        let gen = generation
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                // 等待授权期间录音已被停止/取消：丢弃这次迟到的授权，避免重新启动麦克风。
                guard gen == self.generation else { return }
                guard status == .authorized else {
                    self.emitError("permission_denied", "未获得语音识别权限，请在系统设置中开启")
                    return
                }
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    DispatchQueue.main.async {
                        guard gen == self.generation else { return }
                        guard granted else {
                            self.emitError("permission_denied", "需要麦克风权限才能使用语音输入")
                            return
                        }
                        self.beginSession()
                    }
                }
            }
        }
    }

    private func beginSession() {
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-CN")) ?? SFSpeechRecognizer(),
              recognizer.isAvailable else {
            emitError("unsupported", "当前设备暂不支持语音识别")
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            self.request = request

            let input = audioEngine.inputNode
            let format = input.outputFormat(forBus: 0)
            if tapInstalled {
                input.removeTap(onBus: 0)
                tapInstalled = false
            }
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                request.append(buffer)
                guard let self = self else { return }
                self.emitLevel(DshVoiceRecognizer.level(from: buffer))
            }
            tapInstalled = true

            audioEngine.prepare()
            try audioEngine.start()

            task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                DispatchQueue.main.async {
                    guard let self = self, !self.finished else { return }
                    if let result = result {
                        let text = result.bestTranscription.formattedString
                        if result.isFinal {
                            self.finalText = text
                            if !text.isEmpty {
                                self.finalEmitted = true
                                self.emit(["event": "final", "text": text])
                            }
                            self.finish()
                        } else if !text.isEmpty {
                            self.emit(["event": "partial", "text": text])
                        }
                    }
                    if error != nil {
                        self.finish()
                    }
                }
            }
            emit(["event": "ready"])
        } catch {
            emitError("start_failed", "语音识别启动失败：\(error.localizedDescription)")
        }
    }

    private func stopCaptureAndEndAudio() {
        if audioEngine.isRunning {
            audioEngine.stop()
        }
        if tapInstalled {
            audioEngine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        request?.endAudio()
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        stopCaptureAndEndAudio()
        task?.cancel()
        task = nil
        request = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        if !finalText.isEmpty && !finalEmitted {
            finalEmitted = true
            emit(["event": "final", "text": finalText])
        }
        emit(["event": "end"])
        onEvent = nil
    }

    private func emitError(_ code: String, _ message: String) {
        guard !finished else { return }
        finished = true
        emit(["event": "error", "code": code, "message": message])
        stopCaptureAndEndAudio()
        task?.cancel()
        task = nil
        request = nil
        onEvent = nil
    }

    private func emitLevel(_ level: Float) {
        let now = Date().timeIntervalSince1970
        if now - lastLevelAt < 0.08 { return }
        lastLevelAt = now
        emit(["event": "level", "level": level])
    }

    private func emit(_ event: [String: Any]) {
        onEvent?(event as NSDictionary)
    }

    private static func level(from buffer: AVAudioPCMBuffer) -> Float {
        guard let channel = buffer.floatChannelData?[0] else { return 0 }
        let length = Int(buffer.frameLength)
        guard length > 0 else { return 0 }
        var sum: Float = 0
        for index in 0..<length {
            let sample = channel[index]
            sum += sample * sample
        }
        let rms = sqrt(sum / Float(length))
        // -60..0 dB 归一到 0..1
        let db = 20 * log10(max(rms, 1e-6))
        return min(1, max(0, (db + 60) / 60))
    }
}

