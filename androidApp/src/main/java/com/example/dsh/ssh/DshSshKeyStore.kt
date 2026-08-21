package com.example.dsh.ssh

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import org.apache.sshd.common.NamedResource
import org.apache.sshd.common.config.keys.FilePasswordProvider
import org.apache.sshd.common.util.security.SecurityUtils
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores imported private keys encrypted with an Android Keystore AES key. */
internal class DshSshKeyStore(context: Context) {
    private val root = File(context.filesDir, "ssh-keys").apply { mkdirs() }
    private val keyAlias = "dsh.ssh.key-store"

    fun importKey(source: File): String {
        val keyId = "key-${System.currentTimeMillis()}-${source.name.hashCode().toUInt().toString(16)}"
        val bytes = source.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val target = File(root, "$keyId.bin")
        FileOutputStream(target).use { output ->
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            output.write(cipher.doFinal(bytes))
        }
        return keyId
    }

    fun importBytes(name: String, bytes: ByteArray): String {
        val temp = File.createTempFile("ssh-import-", ".key", root)
        return try {
            temp.writeBytes(bytes)
            importKey(temp)
        } finally {
            temp.delete()
        }
    }

    fun delete(keyId: String) {
        File(root, "$keyId.bin").delete()
    }

    fun exists(keyId: String): Boolean = keyId.isNotBlank() && File(root, "$keyId.bin").isFile

    fun withKeyFile(keyId: String, block: (File) -> Unit) {
        require(exists(keyId)) { "SSH private key is missing" }
        val encrypted = File(root, "$keyId.bin")
        val bytes = FileInputStream(encrypted).use { input ->
            val ivLength = input.read()
            require(ivLength in 12..32) { "Invalid encrypted SSH key" }
            val iv = ByteArray(ivLength)
            input.readFully(iv)
            val ciphertext = input.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        }
        val plaintext = File.createTempFile("ssh-key-", ".pem", root)
        try {
            plaintext.writeBytes(bytes)
            block(plaintext)
        } finally {
            plaintext.delete()
            bytes.fill(0)
        }
    }

    fun readKeyBytes(keyId: String): ByteArray {
        var result: ByteArray? = null
        withKeyFile(keyId) { result = it.readBytes() }
        return result ?: error("SSH private key could not be read")
    }

    fun validateKey(keyId: String): Boolean {
        if (!exists(keyId)) return false
        return runCatching {
            withKeyFile(keyId) { file ->
                SecurityUtils.loadKeyPairIdentities(
                    null,
                    NamedResource.ofName(file.name),
                    file.inputStream(),
                    FilePasswordProvider.EMPTY,
                )?.toList()?.ifEmpty { error("SSH 私钥中没有可用的 KeyPair") }
                    ?: error("SSH 私钥中没有可用的 KeyPair")
            }
        }.isSuccess
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(keyAlias, null) as? SecretKey ?: createSecretKey()
    }

    private fun createSecretKey(): SecretKey {
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            keyAlias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        return KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        ).apply { init(spec) }.generateKey()
    }
}

private fun java.io.InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val count = read(buffer, offset, buffer.size - offset)
        require(count >= 0) { "Truncated encrypted SSH key" }
        offset += count
    }
}
