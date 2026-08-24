package com.example.dsh.relay

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps Relay pairing secrets with Android Keystore AES-GCM. SQLite never stores tokens. */
internal class DshRelaySecrets(context: Context) {
    private val root = File(context.filesDir, "relay-secrets").apply { mkdirs() }
    private val alias = "dsh.relay.secret-store"

    fun save(
        masterKeyB64: String,
        clientToken: String,
        hostId: String,
        hostName: String,
        relayOrigin: String,
    ) {
        write("master.bin", masterKeyB64.toByteArray())
        write("client.bin", clientToken.toByteArray())
        write("host.bin", hostId.toByteArray())
        write("name.bin", hostName.toByteArray())
        write("origin.bin", relayOrigin.toByteArray())
    }

    fun masterKey(): String? = read("master.bin")?.toString(Charsets.UTF_8)
    fun clientToken(): String? = read("client.bin")?.toString(Charsets.UTF_8)
    fun hostId(): String? = read("host.bin")?.toString(Charsets.UTF_8)
    fun hostName(): String? = read("name.bin")?.toString(Charsets.UTF_8)
    fun relayOrigin(): String? = read("origin.bin")?.toString(Charsets.UTF_8)
    fun hasPairing(): Boolean = masterKey()?.isNotBlank() == true && clientToken()?.isNotBlank() == true

    fun clear() {
        listOf("master.bin", "client.bin", "host.bin", "name.bin", "origin.bin").forEach { File(root, it).delete() }
    }

    private fun write(name: String, bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        File(root, name).outputStream().use { output ->
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            output.write(cipher.doFinal(bytes))
        }
        bytes.fill(0)
    }

    private fun read(name: String): ByteArray? {
        val file = File(root, name)
        if (!file.isFile) return null
        return file.inputStream().use { input ->
            val ivLength = input.read()
            require(ivLength in 12..32)
            val iv = ByteArray(ivLength)
            input.readFully(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(input.readBytes())
        }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }
}

private fun InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val count = read(buffer, offset, buffer.size - offset)
        require(count >= 0)
        offset += count
    }
}
