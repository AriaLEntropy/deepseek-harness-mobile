import UIKit

@objc(DshNativeUi)
final class DshNativeUi: NSObject {
    @objc static func topViewController() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
        let window = windows.first(where: \.isKeyWindow) ?? windows.first
        var controller = window?.rootViewController
        while let presented = controller?.presentedViewController {
            controller = presented
        }
        if let nav = controller as? UINavigationController {
            controller = nav.visibleViewController ?? nav
        }
        return controller
    }

    @objc static func toast(_ content: String) {
        DispatchQueue.main.async {
            let windows = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
            guard let window = windows.first(where: \.isKeyWindow) ?? windows.first else { return }
            let label = PaddingLabel()
            label.text = content
            label.textColor = .white
            label.backgroundColor = UIColor(white: 0.12, alpha: 0.92)
            label.font = .systemFont(ofSize: 14)
            label.textAlignment = .center
            label.numberOfLines = 0
            label.layer.cornerRadius = 8
            label.clipsToBounds = true
            label.translatesAutoresizingMaskIntoConstraints = false
            window.addSubview(label)
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: window.centerXAnchor),
                label.bottomAnchor.constraint(equalTo: window.safeAreaLayoutGuide.bottomAnchor, constant: -48),
                label.leadingAnchor.constraint(greaterThanOrEqualTo: window.leadingAnchor, constant: 24),
                label.trailingAnchor.constraint(lessThanOrEqualTo: window.trailingAnchor, constant: -24),
            ])
            label.alpha = 0
            UIView.animate(withDuration: 0.18, animations: { label.alpha = 1 }) { _ in
                UIView.animate(withDuration: 0.25, delay: 1.6, options: [], animations: { label.alpha = 0 }) { _ in
                    label.removeFromSuperview()
                }
            }
        }
    }
}

private final class PaddingLabel: UILabel {
    override func drawText(in rect: CGRect) {
        super.drawText(in: rect.inset(by: UIEdgeInsets(top: 10, left: 16, bottom: 10, right: 16)))
    }

    override var intrinsicContentSize: CGSize {
        let size = super.intrinsicContentSize
        return CGSize(width: size.width + 32, height: size.height + 20)
    }
}
