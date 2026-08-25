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
}
