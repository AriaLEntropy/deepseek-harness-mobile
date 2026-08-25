import Foundation
import Security

@objc(DshRelaySecrets)
final class DshRelaySecrets: NSObject {
    private let service = "com.example.dsh.relay-secrets"

    @objc func save(
        masterKeyB64: String,
        clientToken: String,
        hostId: String,
        hostName: String,
        relayOrigin: String
    ) {
        write("master", masterKeyB64)
        write("client", clientToken)
        write("host", hostId)
        write("name", hostName)
        write("origin", relayOrigin)
    }

    @objc func masterKey() -> String? { read("master") }
    @objc func clientToken() -> String? { read("client") }
    @objc func hostId() -> String? { read("host") }
    @objc func hostName() -> String? { read("name") }
    @objc func relayOrigin() -> String? { read("origin") }

    @objc func hasPairing() -> Bool {
        let master = masterKey() ?? ""
        let token = clientToken() ?? ""
        return !master.isEmpty && !token.isEmpty
    }

    @objc func clear() {
        ["master", "client", "host", "name", "origin"].forEach { delete($0) }
    }

    private func write(_ account: String, _ value: String) {
        delete(account)
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private func read(_ account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
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
