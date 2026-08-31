import Darwin
import Foundation
import Network

/// iOS Local Network privacy has no request API. Connecting a UDP socket to a
/// local address raises the system prompt without sending packets (TN3179).
/// NSBonjourServices + a long-lived NWBrowser is required on iOS 18 so the app
/// appears under Settings > Local Network.
enum DshLocalNetworkAccess {
    private static let browserQueue = DispatchQueue(label: "dsh.local-network")
    private static var browser: NWBrowser?

    static func prepare(for origin: String) {
        startBonjourBrowser()
        triggerLinkLocalAlert()
        guard let url = URL(string: origin), let host = url.host, !host.isEmpty else { return }
        let port = UInt16(url.port ?? 9)
        connectUdp(host: host, port: port)
    }

    private static func startBonjourBrowser() {
        browserQueue.sync {
            guard browser == nil else { return }
            let parameters = NWParameters()
            parameters.includePeerToPeer = true
            let next = NWBrowser(for: .bonjour(type: "_dsh-relay._tcp", domain: "local."), using: parameters)
            next.stateUpdateHandler = { _ in }
            next.start(queue: browserQueue)
            browser = next
        }
    }

    /// Best-effort prompt: UDP connect to link-local IPv6 on every broadcast interface.
    private static func triggerLinkLocalAlert() {
        let randomA = (0..<8).map { _ in UInt8.random(in: 0...255) }
        let randomB = (0..<8).map { _ in UInt8.random(in: 0...255) }
        for var address in linkLocalIPv6Addresses() {
            address.sin6_port = UInt16(9).bigEndian
            connectUdp6(setHostPart(of: address, to: randomA))
            connectUdp6(setHostPart(of: address, to: randomB))
        }
    }

    private static func connectUdp(host: String, port: UInt16) {
        var hints = addrinfo()
        hints.ai_socktype = SOCK_DGRAM
        hints.ai_protocol = IPPROTO_UDP
        var info: UnsafeMutablePointer<addrinfo>?
        let lookup = getaddrinfo(host, String(port), &hints, &info)
        defer { if let info { freeaddrinfo(info) } }
        guard lookup == 0, let first = info else { return }
        let fd = socket(first.pointee.ai_family, SOCK_DGRAM, IPPROTO_UDP)
        guard fd >= 0 else { return }
        _ = Darwin.connect(fd, first.pointee.ai_addr, first.pointee.ai_addrlen)
        Darwin.close(fd)
    }

    private static func connectUdp6(_ address: sockaddr_in6) {
        let fd = socket(AF_INET6, SOCK_DGRAM, 0)
        guard fd >= 0 else { return }
        defer { Darwin.close(fd) }
        withUnsafePointer(to: address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sa in
                _ = Darwin.connect(fd, sa, socklen_t(sa.pointee.sa_len))
            }
        }
    }

    private static func setHostPart(of address: sockaddr_in6, to hostPart: [UInt8]) -> sockaddr_in6 {
        var result = address
        withUnsafeMutableBytes(of: &result.sin6_addr) { buffer in
            buffer[8...].copyBytes(from: hostPart)
        }
        return result
    }

    private static func linkLocalIPv6Addresses() -> [sockaddr_in6] {
        var addrList: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addrList) == 0, let start = addrList else { return [] }
        defer { freeifaddrs(start) }
        return sequence(first: start, next: { $0.pointee.ifa_next }).compactMap { entry -> sockaddr_in6? in
            guard (entry.pointee.ifa_flags & UInt32(bitPattern: IFF_BROADCAST)) != 0,
                  let sa = entry.pointee.ifa_addr,
                  sa.pointee.sa_family == AF_INET6 else { return nil }
            let address = UnsafeRawPointer(sa).load(as: sockaddr_in6.self)
            let bytes = address.sin6_addr.__u6_addr.__u6_addr8
            let linkLocal = bytes.0 == 0xfe && (bytes.1 & 0xc0) == 0x80
            return linkLocal ? address : nil
        }
    }
}
