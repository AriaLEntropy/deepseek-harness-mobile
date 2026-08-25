import Foundation
import Network

@objc(DshRelayRuntime)
final class DshRelayRuntime: NSObject, URLSessionWebSocketDelegate {
    @objc static let shared = DshRelayRuntime()

    private let secrets = DshRelaySecrets()
    private let lock = NSLock()
    private var listeners: [UInt: (NSDictionary) -> Void] = [:]
    private var listenerSeq: UInt = 0
    private var generation: Int64 = 0
    private var connecting = false
    private var stopped = true
    private var loopback: DshRelayLoopbackServer?
    private var webSocket: URLSessionWebSocketTask?
    private var session: URLSession?
    private var cipher: DshSecureCipher?
    private var accessSessionId = ""
    private var localToken = ""
    private var hostName = ""
    private var relayOrigin = ""
    private var hostId = ""
    private var state: [String: Any] = ["phase": "IDLE", "message": "", "localPort": 0, "localToken": "", "hostId": "", "hostName": "", "relayOrigin": "", "paired": false, "generation": 0]
    private var pathMonitor: NWPathMonitor?
    private var pendingHello: (master: String, clientRandomB64: String, generation: Int64, origin: String)?

    private override init() {
        super.init()
        restorePairing()
    }

    @objc func addListener(_ block: @escaping (NSDictionary) -> Void) -> UInt {
        lock.lock()
        listenerSeq += 1
        let token = listenerSeq
        listeners[token] = block
        let current = state as NSDictionary
        lock.unlock()
        block(current)
        return token
    }

    @objc func removeListener(_ token: UInt) {
        lock.lock()
        listeners.removeValue(forKey: token)
        lock.unlock()
    }

    @objc func currentState() -> NSDictionary {
        lock.lock()
        let current = state as NSDictionary
        lock.unlock()
        return current
    }

    @objc func pairFromQr(_ qr: String) -> NSDictionary {
        publish(phase: "PAIRING", message: "正在配对")
        do {
            let link = try parsePairQr(qr)
            let claimToken = try DshSealedTunnelCrypto.deriveClaimToken(masterKeyB64: link.masterKey)
            let body: [String: Any] = [
                "pairId": link.pairId,
                "claimToken": claimToken,
                "deviceLabel": "iOS",
                "platform": "ios",
            ]
            let json = try httpPost(url: "\(link.origin)/pair/claim-device", body: body, authorization: nil)
            let http = json.http
            let object = json.object
            if !(200...299).contains(http) {
                let reason = (object["error"] as? String)?.nilIfEmpty ?? "claim failed (\(http))"
                publish(phase: "ERROR", message: reason, paired: secrets.hasPairing())
                return ["ok": false, "message": reason]
            }
            let newHostId = object["hostId"] as? String ?? ""
            let token = object["clientToken"] as? String ?? ""
            let name = (object["hostName"] as? String)?.nilIfEmpty ?? "电脑"
            guard !newHostId.isEmpty, !token.isEmpty else {
                throw NSError(domain: "DshRelay", code: 1, userInfo: [NSLocalizedDescriptionKey: "pairing response missing credentials"])
            }
            secrets.save(masterKeyB64: link.masterKey, clientToken: token, hostId: newHostId, hostName: name, relayOrigin: link.origin)
            hostId = newHostId
            hostName = name
            relayOrigin = link.origin
            publish(phase: "IDLE", message: "已配对", hostId: newHostId, hostName: name, relayOrigin: link.origin, paired: true)
            return [
                "ok": true,
                "hostId": newHostId,
                "hostName": name,
                "relayOrigin": link.origin,
                "pairedAt": Int64(Date().timeIntervalSince1970 * 1000),
            ]
        } catch {
            let message = relayNetworkMessage(error)
            publish(phase: "ERROR", message: message, paired: secrets.hasPairing())
            return ["ok": false, "message": message]
        }
    }

    @objc func connect() {
        if loopbackListening() {
            stopped = false
            registerNetwork()
            NSLog("[DshRelay] reusing loopback port=%d", loopback?.port ?? 0)
            return
        }
        lock.lock()
        if connecting {
            lock.unlock()
            return
        }
        connecting = true
        stopped = false
        lock.unlock()
        registerNetwork()
        dropLoopback("new-connect")
        publish(
            phase: "CONNECTING",
            message: "正在申请访问票",
            hostId: hostId,
            hostName: hostName,
            relayOrigin: relayOrigin,
            paired: secrets.hasPairing(),
            generation: generation
        )
        Thread.detachNewThread { [weak self] in
            self?.connectInternal()
        }
    }

    @objc func disconnect() {
        stopped = true
        lock.lock()
        generation += 1
        connecting = false
        lock.unlock()
        unregisterNetwork()
        webSocket?.cancel(with: .goingAway, reason: nil)
        webSocket = nil
        session?.invalidateAndCancel()
        session = nil
        cipher = nil
        pendingHello = nil
        dropLoopback("disconnect")
        publish(phase: "STOPPED", message: "扫码连接已断开", hostId: hostId, hostName: hostName, relayOrigin: relayOrigin, paired: secrets.hasPairing())
    }

    @objc func forgetPairing() {
        disconnect()
        secrets.clear()
        hostId = ""
        hostName = ""
        relayOrigin = ""
        publish(phase: "IDLE", message: "已移除配对")
    }

    private func restorePairing() {
        guard secrets.hasPairing() else { return }
        hostId = secrets.hostId() ?? ""
        hostName = secrets.hostName() ?? ""
        relayOrigin = secrets.relayOrigin() ?? ""
        publish(phase: "IDLE", message: "", hostId: hostId, hostName: hostName, relayOrigin: relayOrigin, paired: true)
    }

    private func connectInternal() {
        lock.lock()
        generation += 1
        let myGeneration = generation
        lock.unlock()
        defer {
            lock.lock()
            connecting = false
            lock.unlock()
        }
        do {
            guard let master = secrets.masterKey(), let clientToken = secrets.clientToken() else {
                throw NSError(domain: "DshRelay", code: 2, userInfo: [NSLocalizedDescriptionKey: "not paired"])
            }
            let origin = (secrets.relayOrigin() ?? "").nilIfEmpty ?? relayOrigin.nilIfEmpty ?? "http://127.0.0.1:8787"
            hostId = secrets.hostId() ?? ""
            hostName = (secrets.hostName() ?? "").nilIfEmpty ?? hostName
            relayOrigin = origin
            publish(phase: "CONNECTING", message: "正在申请访问票", hostId: hostId, hostName: hostName, relayOrigin: origin, paired: true, generation: myGeneration)
            let ticket = try httpPost(
                url: "\(origin)/access-ticket",
                body: [:],
                authorization: "Bearer \(clientToken)"
            )
            if !(200...299).contains(ticket.http) {
                throw NSError(
                    domain: "DshRelay",
                    code: ticket.http,
                    userInfo: [NSLocalizedDescriptionKey: (ticket.object["error"] as? String)?.nilIfEmpty ?? "ticket failed"]
                )
            }
            accessSessionId = ticket.object["accessSessionId"] as? String ?? ""
            let ticketValue = ticket.object["ticket"] as? String ?? ""
            let tunnelUrl = (ticket.object["tunnelUrl"] as? String)?.nilIfEmpty ?? (toWs(origin) + "/client-tunnel")
            hostId = (ticket.object["hostId"] as? String)?.nilIfEmpty ?? hostId
            guard !ticketValue.isEmpty, !accessSessionId.isEmpty else {
                throw NSError(domain: "DshRelay", code: 3, userInfo: [NSLocalizedDescriptionKey: "ticket response incomplete"])
            }
            publish(phase: "HANDSHAKING", message: "正在建立加密隧道", hostId: hostId, hostName: hostName, relayOrigin: origin, paired: true, generation: myGeneration)
            let clientRandomB64 = DshSealedTunnelCrypto.encodeBase64Url(DshSealedTunnelCrypto.randomBytes())
            let hello = envelope(
                type: "client_hello",
                payload: [
                    "accessSessionId": accessSessionId,
                    "clientRandomB64": clientRandomB64,
                    "clientProofB64": try DshSealedTunnelCrypto.clientProof(
                        masterKeyB64: master,
                        accessSessionId: accessSessionId,
                        clientRandomB64: clientRandomB64
                    ),
                ]
            )
            pendingHello = (master, clientRandomB64, myGeneration, origin)
            var request = URLRequest(url: URL(string: tunnelUrl)!)
            request.setValue("Bearer \(ticketValue)", forHTTPHeaderField: "Authorization")
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = .infinity
            configuration.timeoutIntervalForResource = .infinity
            let session = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
            self.session = session
            let socket = session.webSocketTask(with: request)
            webSocket = socket
            socket.resume()
            send(hello, on: socket)
            receive(on: socket)
        } catch {
            NSLog("[DshRelay] connect failed: %@", error.localizedDescription)
            if !stopped && myGeneration == currentGeneration() {
                publish(phase: "ERROR", message: error.localizedDescription.nilIfEmpty ?? "连接失败", hostId: hostId, hostName: hostName, relayOrigin: relayOrigin, paired: true, generation: myGeneration)
                scheduleReconnect()
            }
        }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {}

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        let myGeneration = generation
        lock.unlock()
        if stopped || myGeneration != currentGeneration() { return }
        if let error {
            NSLog("[DshRelay] relay socket failed: %@", error.localizedDescription)
            publish(phase: "ERROR", message: error.localizedDescription.nilIfEmpty ?? "隧道失败")
        }
        scheduleReconnect()
    }

    private func receive(on socket: URLSessionWebSocketTask) {
        socket.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .failure(let error):
                if !self.stopped {
                    NSLog("[DshRelay] relay socket failed: %@", error.localizedDescription)
                    self.publish(phase: "ERROR", message: error.localizedDescription.nilIfEmpty ?? "隧道失败")
                    self.scheduleReconnect()
                }
            case .success(let message):
                if self.stopped { return }
                if case .string(let text) = message {
                    self.handleOuter(text)
                }
                self.receive(on: socket)
            }
        }
    }

    private func handleOuter(_ text: String) {
        guard let hello = pendingHello ?? optionalHello(),
              currentGeneration() == hello.generation || pendingHello != nil else { return }
        let myGeneration = pendingHello?.generation ?? hello.generation
        if stopped || myGeneration != currentGeneration() { return }
        guard let msg = jsonObject(text) else { return }
        let type = msg["type"] as? String ?? ""
        switch type {
        case "server_hello":
            let payload = msg["payload"] as? [String: Any] ?? [:]
            do {
                cipher = try DshSealedTunnelCrypto.createClientCipher(
                    masterKeyB64: hello.master,
                    accessSessionId: accessSessionId,
                    clientRandomB64: hello.clientRandomB64,
                    serverRandomB64: payload["serverRandomB64"] as? String ?? "",
                    serverProofB64: payload["serverProofB64"] as? String ?? ""
                )
                startLoopback(myGeneration: myGeneration, origin: hello.origin)
            } catch {
                publish(phase: "ERROR", message: error.localizedDescription.nilIfEmpty ?? "握手失败")
                scheduleReconnect()
            }
        case "sealed":
            let payload = msg["payload"] as? [String: Any] ?? [:]
            let sealed = DshSealedPayload(seq: payload["seq"] as? String ?? "", ciphertextB64: payload["ciphertextB64"] as? String ?? "")
            guard let opened = try? cipher?.open(sealed) else { return }
            loopback?.onInner(
                type: opened["type"] as? String ?? "",
                payload: opened["payload"] as? [String: Any] ?? [:],
                channel: opened["channel"] as? String ?? ""
            )
        case "device_close", "close":
            if !stopped { scheduleReconnect() }
        default:
            break
        }
    }

    private func startLoopback(myGeneration: Int64, origin: String) {
        dropLoopback("replace")
        localToken = DshSealedTunnelCrypto.encodeBase64Url(DshSealedTunnelCrypto.randomBytes(24))
        let server = DshRelayLoopbackServer(token: localToken) { [weak self] type, payload, channel in
            self?.sendInner(type: type, payload: payload, channel: channel)
        }
        do {
            let port = try server.start()
            loopback = server
            NSLog("[DshRelay] loopback listening port=%d", port)
            publish(
                phase: "READY",
                message: "扫码隧道已连接",
                localPort: port,
                localToken: localToken,
                hostId: hostId,
                hostName: hostName,
                relayOrigin: origin,
                paired: true,
                generation: myGeneration
            )
        } catch {
            publish(phase: "ERROR", message: "无法启动本机网关")
            scheduleReconnect()
        }
    }

    private func sendInner(type: String, payload: [String: Any], channel: String) {
        guard let cipher else { return }
        guard let sealed = try? cipher.seal(envelope(type: type, payload: payload, channel: channel)) else { return }
        send(
            envelope(
                type: "sealed",
                payload: [
                    "accessSessionId": accessSessionId,
                    "seq": sealed.seq,
                    "ciphertextB64": sealed.ciphertextB64,
                ]
            )
        )
    }

    private func envelope(type: String, payload: [String: Any], channel: String? = nil) -> [String: Any] {
        var object: [String: Any] = [
            "v": 1,
            "type": type,
            "id": UUID().uuidString,
            "ts": NSNumber(value: Int64(Date().timeIntervalSince1970 * 1000)),
            "payload": payload,
        ]
        if let channel { object["channel"] = channel }
        return object
    }

    private func send(_ object: [String: Any], on socket: URLSessionWebSocketTask? = nil) {
        guard let data = try? JSONSerialization.data(withJSONObject: object),
              let text = String(data: data, encoding: .utf8) else { return }
        (socket ?? webSocket)?.send(.string(text)) { _ in }
    }

    private func loopbackListening() -> Bool {
        lock.lock()
        let phase = state["phase"] as? String
        let localPort = state["localPort"] as? Int ?? 0
        lock.unlock()
        return phase == "READY" && localPort > 0 && loopback?.port == localPort && loopback?.isListening() == true
    }

    private func dropLoopback(_ reason: String) {
        let port = loopback?.port ?? 0
        loopback?.stop()
        loopback = nil
        localToken = ""
        if port > 0 {
            NSLog("[DshRelay] loopback stopped port=%d reason=%@", port, reason)
        }
    }

    private func scheduleReconnect() {
        if stopped { return }
        dropLoopback("reconnect")
        publish(phase: "RECONNECTING", message: "扫码连接重试中", localPort: 0, localToken: "")
        Thread.detachNewThread { [weak self] in
            Thread.sleep(forTimeInterval: 2)
            guard let self, !self.stopped else { return }
            self.lock.lock()
            let already = self.connecting
            if !already { self.connecting = true }
            self.lock.unlock()
            if !already { self.connectInternal() }
        }
    }

    private func parsePairQr(_ raw: String) throws -> (origin: String, pairId: String, masterKey: String) {
        guard let uri = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            throw NSError(domain: "DshRelay", code: 4, userInfo: [NSLocalizedDescriptionKey: "二维码缺少配对参数"])
        }
        let fragment = uri.fragment ?? ""
        let fromFragment: String
        if fragment.isEmpty {
            fromFragment = ""
        } else if let idx = fragment.firstIndex(of: "?") {
            fromFragment = String(fragment[fragment.index(after: idx)...])
        } else {
            fromFragment = fragment
        }
        let query = fromFragment.isEmpty ? (uri.query ?? "") : fromFragment
        var params: [String: String] = [:]
        for part in query.split(separator: "&") {
            let pieces = part.split(separator: "=", maxSplits: 1).map(String.init)
            let key = pieces.first?.removingPercentEncoding ?? ""
            let value = pieces.count > 1 ? (pieces[1].removingPercentEncoding ?? pieces[1]) : ""
            params[key] = value
        }
        let id = params["id"] ?? ""
        let key = params["key"] ?? ""
        guard !id.isEmpty, !key.isEmpty else {
            throw NSError(domain: "DshRelay", code: 4, userInfo: [NSLocalizedDescriptionKey: "二维码缺少配对参数"])
        }
        guard let scheme = uri.scheme, let host = uri.host else {
            throw NSError(domain: "DshRelay", code: 4, userInfo: [NSLocalizedDescriptionKey: "二维码缺少配对参数"])
        }
        if host == "127.0.0.1" || host == "localhost" {
            throw NSError(
                domain: "DshRelay",
                code: 6,
                userInfo: [NSLocalizedDescriptionKey: "二维码指向电脑本机 \(host)，手机连不上。请在电脑上设置 PUBLIC_RELAY_URL 为局域网地址后重新生成二维码。"]
            )
        }
        let origin: String
        if let port = uri.port {
            origin = "\(scheme)://\(host):\(port)"
        } else {
            origin = "\(scheme)://\(host)"
        }
        return (origin, id, key)
    }

    private func toWs(_ origin: String) -> String {
        if origin.hasPrefix("https:") { return origin.replacingOccurrences(of: "https:", with: "wss:", options: .anchored) }
        return origin.replacingOccurrences(of: "http:", with: "ws:", options: .anchored)
    }

    private func httpPost(url: String, body: [String: Any], authorization: String?) throws -> (http: Int, object: [String: Any]) {
        guard let endpoint = URL(string: url) else {
            throw NSError(domain: "DshRelay", code: 5, userInfo: [NSLocalizedDescriptionKey: "invalid url"])
        }
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        if let authorization { request.setValue(authorization, forHTTPHeaderField: "Authorization") }
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.timeoutInterval = 20
        let semaphore = DispatchSemaphore(value: 0)
        var capturedData: Data?
        var capturedResponse: HTTPURLResponse?
        var capturedError: Error?
        URLSession.shared.dataTask(with: request) { data, response, error in
            capturedData = data
            capturedResponse = response as? HTTPURLResponse
            capturedError = error
            semaphore.signal()
        }.resume()
        semaphore.wait()
        if let capturedError { throw capturedError }
        let object = jsonObject(String(data: capturedData ?? Data(), encoding: .utf8) ?? "{}") ?? [:]
        return (capturedResponse?.statusCode ?? 0, object)
    }

    private func relayNetworkMessage(_ error: Error) -> String {
        let nsError = error as NSError
        return nsError.localizedDescription.isEmpty ? "配对失败" : nsError.localizedDescription
    }

    private func jsonObject(_ text: String) -> [String: Any]? {
        guard let data = text.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return object
    }

    private func currentGeneration() -> Int64 {
        lock.lock()
        let value = generation
        lock.unlock()
        return value
    }

    private func optionalHello() -> (master: String, clientRandomB64: String, generation: Int64, origin: String)? {
        pendingHello
    }

    private func registerNetwork() {
        unregisterNetwork()
        let monitor = NWPathMonitor()
        pathMonitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self, path.status == .satisfied, !self.stopped else { return }
            self.lock.lock()
            let phase = self.state["phase"] as? String ?? ""
            self.lock.unlock()
            if phase != "READY" && phase != "CONNECTING" && phase != "HANDSHAKING" {
                Thread.detachNewThread {
                    self.lock.lock()
                    let already = self.connecting
                    if !already { self.connecting = true }
                    self.lock.unlock()
                    if !already { self.connectInternal() }
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "dsh.relay.net"))
    }

    private func unregisterNetwork() {
        pathMonitor?.cancel()
        pathMonitor = nil
    }

    private func publish(
        phase: String,
        message: String,
        localPort: Int? = nil,
        localToken: String? = nil,
        hostId: String? = nil,
        hostName: String? = nil,
        relayOrigin: String? = nil,
        paired: Bool? = nil,
        generation: Int64? = nil
    ) {
        lock.lock()
        var next = state
        next["phase"] = phase
        next["message"] = message
        if let localPort { next["localPort"] = localPort }
        if let localToken { next["localToken"] = localToken }
        if let hostId { next["hostId"] = hostId }
        if let hostName { next["hostName"] = hostName }
        if let relayOrigin { next["relayOrigin"] = relayOrigin }
        if let paired { next["paired"] = paired }
        if let generation { next["generation"] = generation }
        state = next
        let blocks = Array(listeners.values)
        lock.unlock()
        let snapshot = next as NSDictionary
        DispatchQueue.main.async {
            blocks.forEach { $0(snapshot) }
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
