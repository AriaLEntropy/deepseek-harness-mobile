package com.example.dsh.ssh

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.common.NamedResource
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.common.config.keys.KeyUtils
import java.io.File
import java.net.SocketAddress
import java.nio.file.Paths
import java.security.PublicKey
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Owns one long-lived MINA client and one replaceable authenticated session. */
internal object SshTunnelManager {
    private const val TAG = "DshSshTunnel"
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val KEEP_ALIVE_MS = 15_000L
    private const val KEEP_ALIVE_MAX_MISSES = 3

    private val listeners = CopyOnWriteArrayList<(SshState) -> Unit>()
    private val generation = AtomicLong(0)
    private var client: SshClient? = null
    private var session: ClientSession? = null
    private var forwarding: SshdSocketAddress? = null
    private var config: DshSshConfig? = null
    private var acceptedFingerprint = ""
    private var lastKeyBytes: ByteArray? = null
    private var passphrase = ""
    private var localPort = 0
    private var stopped = true
    private var monitorThread: Thread? = null
    private var reconnectThread: Thread? = null
    @Volatile private var connecting = false
    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var appContext: Context

    fun addListener(listener: (SshState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (SshState) -> Unit) {
        listeners -= listener
    }

    @Volatile private var state = SshState(SshPhase.IDLE)

    @Synchronized
    fun connect(context: Context, next: DshSshConfig, keyBytes: ByteArray, keyPassphrase: String = "") {
        if (connecting) return
        appContext = context.applicationContext
        stopped = false
        config = next
        acceptedFingerprint = next.hostFingerprint
        lastKeyBytes?.fill(0)
        lastKeyBytes = keyBytes.copyOf()
        connecting = true
        passphrase = keyPassphrase
        ensureClient()
        registerNetworkCallback()
        thread(name = "dsh-ssh-connect") { connectInternal(keyBytes.copyOf()) }
    }

    @Synchronized
    fun disconnect() {
        stopped = true
        generation.incrementAndGet()
        connecting = false
        unregisterNetworkCallback()
        monitorThread?.interrupt()
        monitorThread = null
        reconnectThread?.interrupt()
        reconnectThread = null
        lastKeyBytes?.fill(0)
        lastKeyBytes = null
        closeSession()
        client?.let { runCatching { it.stop() } }
        client = null
        localPort = 0
        publish(SshState(SshPhase.STOPPED, "SSH 已断开"))
    }

    fun acceptFingerprint(fingerprint: String) {
        if (fingerprint.isBlank() || stopped) return
        if (connecting) return
        acceptedFingerprint = fingerprint
        val keyBytes = lastKeyBytes?.copyOf() ?: return
        connecting = true
        thread(name = "dsh-ssh-fingerprint-retry") { connectInternal(keyBytes) }
    }

    fun endpoint(): String? = localPort.takeIf { it > 0 }?.let { "http://127.0.0.1:$it" }

    private fun connectInternal(keyBytes: ByteArray) {
        val current = config ?: return
        val myGeneration = generation.incrementAndGet()
        try {
            publish(SshState(SshPhase.CONNECTING, "正在连接 SSH"))
            val ssh = ensureClient()
            val keys = loadKeys(keyBytes)
            ssh.setKeyIdentityProvider(KeyPairProvider.wrap(keys))
            publish(SshState(SshPhase.AUTHENTICATING, "正在验证 SSH 身份"))
            val connected = ssh.connect(current.username, current.host, current.port)
                .verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val authenticated = connected.session
            keys.forEach(authenticated::addPublicKeyIdentity)
            authenticated.auth().verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (stopped || myGeneration != generation.get()) {
                authenticated.close()
                return
            }
            session = authenticated
            publish(SshState(SshPhase.FORWARDING, "正在建立 DSH 转发"))
            val endpoint = startForwarding(authenticated, current.remoteDshPort)
            publish(SshState(SshPhase.READY, "SSH 已连接", endpoint.getPort(), myGeneration))
            startMonitor(myGeneration)
        } catch (error: Throwable) {
            Log.e(TAG, "SSH connection failed", error)
            if (!stopped && myGeneration == generation.get()) {
                closeSession()
                if (state.phase != SshPhase.FINGERPRINT_REQUIRED) {
                    publish(SshState(SshPhase.ERROR, friendlyMessage(error), localPort, myGeneration))
                }
            }
        } finally {
            connecting = false
        }
    }

    private fun startForwarding(active: ClientSession, remotePort: Int): SshdSocketAddress {
        val requested = SshdSocketAddress("127.0.0.1", 0)
        val destination = SshdSocketAddress("127.0.0.1", remotePort)
        val actual = runCatching { active.startLocalPortForwarding(requested, destination) }
            .getOrElse { throw PortForwardingException(it) }
        forwarding = actual
        localPort = actual.getPort()
        return actual
    }

    private fun startMonitor(myGeneration: Long) {
        monitorThread?.interrupt()
        monitorThread = thread(name = "dsh-ssh-monitor") {
            var misses = 0
            while (!stopped && myGeneration == generation.get()) {
                try {
                    Thread.sleep(KEEP_ALIVE_MS)
                    val active = session
                    if (active == null || !active.isOpen) {
                        misses++
                    } else {
                        misses = 0
                    }
                    if (misses >= KEEP_ALIVE_MAX_MISSES) {
                        publish(SshState(SshPhase.RECONNECTING, "SSH 连接已断开，正在重连", localPort, myGeneration))
                        closeSession()
                        scheduleReconnect()
                        return@thread
                    }
                } catch (_: InterruptedException) {
                    return@thread
                }
            }
        }
    }

    private fun ensureClient(): SshClient {
        client?.let { return it }
        // Android does not provide a usable JVM user.home. SSHD lazily resolves
        // ~/.ssh during ClientBuilder initialization, so give it an app-owned
        // directory before touching any SSHD client classes that need it.
        PathUtils.setUserHomeFolderResolver {
            Paths.get(appContext.filesDir.absolutePath).toAbsolutePath().normalize()
        }
        return SshClient.setUpDefaultClient().also { ssh ->
            ssh.serverKeyVerifier = FingerprintVerifier { host, fingerprint ->
                val saved = acceptedFingerprint.ifEmpty { config?.hostFingerprint.orEmpty() }
                if (saved.isNotEmpty() && saved == fingerprint) {
                    true
                } else {
                    // Treat both the first connection and a changed host key as
                    // an explicit trust decision. Returning false still aborts
                    // the handshake, but the state lets the UI show the new
                    // fingerprint instead of reporting a generic SSH failure.
                    publish(SshState(SshPhase.FINGERPRINT_REQUIRED, fingerprint))
                    false
                }
            }
            org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_INTERVAL.set(
                ssh, java.time.Duration.ofMillis(KEEP_ALIVE_MS),
            )
            org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_REQUEST.set(
                ssh, "keepalive@openssh.com",
            )
            org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(
                ssh, java.time.Duration.ofMillis(KEEP_ALIVE_MS),
            )
            org.apache.sshd.core.CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(
                ssh, KEEP_ALIVE_MAX_MISSES,
            )
            ssh.start()
            client = ssh
        }
    }

    private fun loadKeys(bytes: ByteArray): List<java.security.KeyPair> {
        val provider = if (passphrase.isEmpty()) FilePasswordProvider.EMPTY else FilePasswordProvider.of(passphrase)
        return try {
            SecurityUtils.loadKeyPairIdentities(
            null,
            NamedResource.ofName("imported-ssh-key"),
            bytes.inputStream(),
            provider,
            )?.toList()?.ifEmpty { error("SSH 私钥中没有可用的 KeyPair") }
                ?: error("SSH 私钥中没有可用的 KeyPair")
        } finally {
            bytes.fill(0)
        }
    }

    private fun closeSession() {
        forwarding?.let { address -> session?.let { runCatching { it.stopLocalPortForwarding(address) } } }
        forwarding = null
        session?.let { runCatching { it.close() } }
        session = null
    }

    private fun registerNetworkCallback() {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivity = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (!stopped) {
                    publish(SshState(SshPhase.RECONNECTING, "网络已切换，正在重连", localPort, generation.get()))
                    closeSession()
                }
            }

            override fun onAvailable(network: Network) {
                if (!stopped && session == null) scheduleReconnect()
            }
        }
        networkCallback = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    private fun unregisterNetworkCallback() {
        val manager = connectivity
        val callback = networkCallback
        if (manager != null && callback != null) runCatching { manager.unregisterNetworkCallback(callback) }
        connectivity = null
        networkCallback = null
    }

    private fun friendlyMessage(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            error is PortForwardingException -> "SSH_PORT_IN_USE：本地转发端口分配失败"
            text.contains("auth", true) -> "SSH 身份验证失败，请检查用户名和私钥"
            text.contains("password", true) -> "SSH 私钥口令错误"
            text.contains("connect", true) || text.contains("refused", true) -> "无法连接 SSH 主机：$text"
            else -> text.ifEmpty { "SSH 连接失败" }
        }
    }

    private class PortForwardingException(cause: Throwable) : RuntimeException(cause)

    private fun scheduleReconnect() {
        if (stopped || reconnectThread?.isAlive == true) return
        val key = lastKeyBytes?.copyOf() ?: return
        reconnectThread = thread(name = "dsh-ssh-reconnect") {
            val delays = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
            for (delay in delays) {
                if (stopped || session != null) return@thread
                try {
                    Thread.sleep(delay)
                    connectInternal(key.copyOf())
                    if (session != null) return@thread
                } catch (_: InterruptedException) {
                    return@thread
                }
            }
        }
    }

    private fun publish(next: SshState) {
        state = next
        listeners.forEach { listener -> runCatching { listener(next) } }
    }

    private fun interface FingerprintCallback {
        fun verify(host: String, fingerprint: String): Boolean
    }

    private class FingerprintVerifier(private val callback: FingerprintCallback) : ServerKeyVerifier {
        override fun verifyServerKey(session: ClientSession, remoteAddress: SocketAddress, serverKey: PublicKey): Boolean {
            val fingerprint = KeyUtils.getFingerPrint(serverKey)
            return callback.verify(remoteAddress.toString(), fingerprint)
        }
    }

    private val keys = mutableListOf<java.security.KeyPair>()
}
