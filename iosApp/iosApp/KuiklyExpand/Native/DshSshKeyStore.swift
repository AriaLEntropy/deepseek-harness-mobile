import Foundation
import Security
import UniformTypeIdentifiers
import UIKit

@objc(DshSshKeyStore)
final class DshSshKeyStore: NSObject, UIDocumentPickerDelegate {
    @objc static let shared = DshSshKeyStore()

    private let service = "com.example.dsh.ssh-keys"
    private var pickerCompletion: ((String) -> Void)?
    private var picker: UIDocumentPickerViewController?

    @objc func pickKey(from presenter: UIViewController, completion: @escaping (String) -> Void) {
        pickerCompletion = completion
        let controller = UIDocumentPickerViewController(forOpeningContentTypes: [.item, .data], asCopy: true)
        controller.delegate = self
        controller.allowsMultipleSelection = false
        picker = controller
        presenter.present(controller, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        defer {
            picker = nil
            pickerCompletion = nil
        }
        guard let url = urls.first else {
            pickerCompletion?("")
            return
        }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            let dest = FileManager.default.temporaryDirectory.appendingPathComponent("ssh-pick-\(UUID().uuidString).key")
            try data.write(to: dest)
            pickerCompletion?(dest.absoluteString)
        } catch {
            pickerCompletion?("")
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        pickerCompletion?("")
        picker = nil
        pickerCompletion = nil
    }

    @objc func importBytes(_ bytes: Data, name: String) -> String {
        let digest = bytes.hashValue.magnitude
        let keyId = "key-\(Int(Date().timeIntervalSince1970 * 1000))-\(String(digest, radix: 16))"
        write(keyId, bytes)
        return keyId
    }

    @objc func importUri(_ uri: String) -> String? {
        guard let url = URL(string: uri) else { return nil }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url), !data.isEmpty else { return nil }
        return importBytes(data, name: url.lastPathComponent)
    }

    @objc func exists(_ keyId: String) -> Bool {
        !keyId.isEmpty && read(keyId) != nil
    }

    @objc func readKeyBytes(_ keyId: String) -> Data? {
        read(keyId)
    }

    @objc func deleteKey(_ keyId: String) {
        delete(keyId)
    }

    @objc func validateKey(_ keyId: String) -> Bool {
        guard let data = read(keyId), let text = String(data: data, encoding: .utf8) else { return false }
        let markers = [
            "BEGIN OPENSSH PRIVATE KEY",
            "BEGIN RSA PRIVATE KEY",
            "BEGIN EC PRIVATE KEY",
            "BEGIN DSA PRIVATE KEY",
            "BEGIN PRIVATE KEY",
            "BEGIN ENCRYPTED PRIVATE KEY",
        ]
        return markers.contains { text.contains($0) }
    }

    private func write(_ account: String, _ value: Data) {
        delete(account)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: value,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private func read(_ account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? Data
    }

    private func delete(_ account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
