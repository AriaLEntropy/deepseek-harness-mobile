import AVFoundation
import UIKit

@objc(DshQrScanViewController)
final class DshQrScanViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    @objc var onResult: ((String?) -> Void)?

    private let session = AVCaptureSession()
    private let overlay = DshQrScanOverlayView()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var finished = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        overlay.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(overlay)
        NSLayoutConstraint.activate([
            overlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlay.topAnchor.constraint(equalTo: view.topAnchor),
            overlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        overlay.onCancel = { [weak self] in self?.finish(nil) }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.setupCamera() } else { self?.finish(nil) }
                }
            }
        default:
            finish(nil)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
        overlay.safeTop = view.safeAreaInsets.top
        overlay.safeBottom = view.safeAreaInsets.bottom
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        overlay.start()
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        overlay.stop()
        session.stopRunning()
        super.viewWillDisappear(animated)
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue, !value.isEmpty else { return }
        finish(value)
    }

    private func setupCamera() {
        session.beginConfiguration()
        session.sessionPreset = .high
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            session.commitConfiguration()
            finish(nil)
            return
        }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            session.commitConfiguration()
            finish(nil)
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
        output.metadataObjectTypes = [.qr]
        session.commitConfiguration()
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.bounds
        view.layer.insertSublayer(layer, at: 0)
        previewLayer = layer
    }

    private func finish(_ value: String?) {
        guard !finished else { return }
        finished = true
        session.stopRunning()
        let callback = onResult
        onResult = nil
        dismiss(animated: true) { callback?(value) }
    }
}

private final class DshQrScanOverlayView: UIView {
    var onCancel: (() -> Void)?
    var safeTop: CGFloat = 0 { didSet { setNeedsDisplay() } }
    var safeBottom: CGFloat = 0 { didSet { setNeedsDisplay() } }

    private var progress: CGFloat = 0
    private var displayLink: CADisplayLink?
    private let cancelButton = UIButton(type: .system)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        cancelButton.setTitle("取消", for: .normal)
        cancelButton.setTitleColor(.white, for: .normal)
        cancelButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(cancelButton)
        NSLayoutConstraint.activate([
            cancelButton.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            cancelButton.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor, constant: 8),
        ])
    }

    required init?(coder: NSCoder) { nil }

    func start() {
        displayLink?.invalidate()
        let link = CADisplayLink(target: self, selector: #selector(tick))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
    }

    @objc private func tick(_ link: CADisplayLink) {
        progress += CGFloat(link.duration) / 1.4
        if progress > 1 { progress = 0 }
        setNeedsDisplay()
    }

    @objc private func cancelTapped() { onCancel?() }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let maxSize = 240 as CGFloat
        let tipSpace: CGFloat = 52
        let availH = bounds.height - safeTop - safeBottom
        let size = min(maxSize, min(bounds.width, availH - tipSpace) * 0.72)
        let box = CGRect(
            x: (bounds.width - size) / 2,
            y: safeTop + (availH - size - tipSpace) / 2,
            width: size,
            height: size
        )
        context.setFillColor(UIColor(white: 0, alpha: 0.53).cgColor)
        context.fill(CGRect(x: 0, y: 0, width: bounds.width, height: box.minY))
        context.fill(CGRect(x: 0, y: box.minY, width: box.minX, height: box.height))
        context.fill(CGRect(x: box.maxX, y: box.minY, width: bounds.width - box.maxX, height: box.height))
        context.fill(CGRect(x: 0, y: box.maxY, width: bounds.width, height: bounds.height - box.maxY))
        context.setStrokeColor(UIColor.white.cgColor)
        context.setLineWidth(1)
        context.stroke(box)
        context.setLineWidth(3)
        let length: CGFloat = 20
        strokeCorner(context, box.minX, box.minY, length, length)
        strokeCorner(context, box.maxX, box.minY, -length, length)
        strokeCorner(context, box.minX, box.maxY, length, -length)
        strokeCorner(context, box.maxX, box.maxY, -length, -length)
        let inset: CGFloat = 8
        let lineHeight: CGFloat = 2
        let travel = box.height - inset * 2 - lineHeight
        let y = box.minY + inset + travel * pingPong(progress)
        let scan = CGRect(x: box.minX + inset, y: y, width: box.width - inset * 2, height: lineHeight)
        context.setFillColor(UIColor(red: 0, green: 0.9, blue: 0.46, alpha: 1).cgColor)
        context.fill(scan)
        let tip = "扫描电脑二维码" as NSString
        let attrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 14),
            .foregroundColor: UIColor.white,
        ]
        let tipSize = tip.size(withAttributes: attrs)
        tip.draw(at: CGPoint(x: box.midX - tipSize.width / 2, y: box.maxY + 16), withAttributes: attrs)
    }

    private func pingPong(_ value: CGFloat) -> CGFloat {
        value < 0.5 ? value * 2 : (1 - value) * 2
    }

    private func strokeCorner(_ context: CGContext, _ x: CGFloat, _ y: CGFloat, _ dx: CGFloat, _ dy: CGFloat) {
        context.move(to: CGPoint(x: x, y: y))
        context.addLine(to: CGPoint(x: x + dx, y: y))
        context.move(to: CGPoint(x: x, y: y))
        context.addLine(to: CGPoint(x: x, y: y + dy))
        context.strokePath()
    }
}
