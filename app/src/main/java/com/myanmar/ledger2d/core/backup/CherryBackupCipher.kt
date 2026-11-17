package com.myanmar.ledger2d.core.backup

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated, versioned encrypted stream; no plaintext leaves private cache. */
internal object CherryBackupCipher {
    private val magic = "CHERRY2D-BK1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val NONCE = 12
    private const val ITERATIONS = 210_000
    private const val MIN_PASSWORD = 8

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return SecretKeySpec(encoded, "AES").also { encoded.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plaintext: InputStream, target: OutputStream, password: CharArray) {
        require(password.size >= MIN_PASSWORD) { "Password must contain at least 8 characters" }
        val salt = ByteArray(SALT).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE).also(SecureRandom()::nextBytes)
        target.write(magic)
        target.write(salt)
        target.write(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(magic + salt + nonce)
        CipherOutputStream(target, cipher).use { encoded -> plaintext.copyTo(encoded) }
    }

    fun decrypt(source: InputStream, plaintext: OutputStream, password: CharArray) {
        val head = readExactly(source, magic.size)
        if (!head.contentEquals(magic)) throw IOException("Not a Cherry 2D backup file")
        val salt = readExactly(source, SALT)
        val nonce = readExactly(source, NONCE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(magic + salt + nonce)
        // Reading until EOF verifies the complete GCM authentication tag.
        CipherInputStream(source, cipher).use { input -> input.copyTo(plaintext) }
        plaintext.flush()
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw IOException("Truncated Cherry 2D backup")
            offset += count
        }
        return bytes
    }
}
