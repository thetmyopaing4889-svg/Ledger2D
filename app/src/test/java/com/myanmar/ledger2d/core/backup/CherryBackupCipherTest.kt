package com.myanmar.ledger2d.core.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CherryBackupCipherTest {
    @Test fun encryptedBackupRoundTripAndRandomNonce() {
        val original = ByteArray(128_000) { (it * 17).toByte() }
        fun save(): ByteArray = ByteArrayOutputStream().use {
            CherryBackupCipher.encrypt(ByteArrayInputStream(original),it,
                "StrongPass#2026".toCharArray())
            it.toByteArray()
        }
        val first=save()
        val second=save()
        assertFalse(original.contentEquals(first))
        assertFalse(first.contentEquals(second))
        val plain=ByteArrayOutputStream()
        CherryBackupCipher.decrypt(ByteArrayInputStream(first),plain,
            "StrongPass#2026".toCharArray())
        assertArrayEquals(original, plain.toByteArray())
    }

    @Test fun wrongPasswordOrModifiedArchiveIsRejected() {
        val encoded=ByteArrayOutputStream().use {
            CherryBackupCipher.encrypt(ByteArrayInputStream("hello".toByteArray()),
                it,"CorrectPassword".toCharArray())
            it.toByteArray()
        }
        assertThrows(Exception::class.java) {
            CherryBackupCipher.decrypt(ByteArrayInputStream(encoded),
                ByteArrayOutputStream(),"WrongPassword".toCharArray())
        }
        encoded[encoded.size-3] = (encoded[encoded.size-3].toInt() xor 0x40).toByte()
        assertThrows(Exception::class.java) {
            CherryBackupCipher.decrypt(ByteArrayInputStream(encoded),
                ByteArrayOutputStream(),"CorrectPassword".toCharArray())
        }
    }

    @Test fun shortPasswordAndTruncatedHeaderRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CherryBackupCipher.encrypt(ByteArrayInputStream(byteArrayOf(1)),
                ByteArrayOutputStream(),"short".toCharArray())
        }
        assertThrows(Exception::class.java) {
            CherryBackupCipher.decrypt(ByteArrayInputStream(byteArrayOf(1,2)),
                ByteArrayOutputStream(),"SomePassword".toCharArray())
        }
    }
}
