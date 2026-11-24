package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class EncryptionPayloadRoundTripTest {
    private val key = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun shouldWriteRandomAuthenticatedCiphertextsAndRoundTripUnicode() {
        val util = EncryptionUtil(mock<Context>())
        val clear = "wallet 日本語 😀\nline two"
        val first = util.encrypt(key, clear)
        val second = util.encrypt(key, clear)
        assertTrue(util.isModernCiphertext(first))
        assertNotEquals(first, second)
        assertEquals(clear, util.decrypt(key, first))
        assertEquals(clear, util.decrypt(key, second))
        assertEquals("", util.decrypt(key, util.encrypt(key, "")))
    }

    @Test
    fun shouldRejectTamperingWrongKeysAndTruncatedPayloads() {
        val util = EncryptionUtil(mock<Context>())
        val ciphertext = util.encrypt(key, "secret")
        val bytes = Base64.decode(ciphertext.removePrefix("v2:"))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertEquals("", util.decrypt(key, "v2:" + Base64.toBase64String(bytes)))
        assertEquals("", util.decrypt(ByteArray(32) { 42 }, ciphertext))
        listOf("", "?invalid", "v2:?invalid", "v2:AA==", "AA==", "v2:" + "A".repeat(2_097_152)).forEach {
            assertEquals("", util.decrypt(key, it))
        }
    }

    @Test
    fun shouldContinueReadingLegacyCbcWithoutCallingItModern() {
        val util = EncryptionUtil(mock<Context>())
        val iv = ByteArray(16) { 7 }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ciphertext = Base64.toBase64String(iv + cipher.doFinal("legacy wallet".toByteArray()))
        assertFalse(util.isModernCiphertext(ciphertext))
        assertFalse(util.isModernCiphertext(null))
        assertFalse(util.isModernCiphertext("V2:AA=="))
        assertEquals("legacy wallet", util.decrypt(key, ciphertext))
    }

    @Test
    fun shouldUseThePreferenceKeyForOrdinaryEncryptionAndDecryption() {
        val util = spy(EncryptionUtil(mock<Context>()))
        doReturn(SecretKeySpec(key, "AES")).whenever(util).getPrerenceAesKey()
        assertEquals("", util.encrypt(null))
        assertEquals("", util.encrypt(""))
        assertEquals("preferences", util.decrypt(util.encrypt("preferences")))
        assertEquals("", util.decrypt("not base64"))
    }

    @Test
    fun shouldDistinguishCorruptBlocksFromUnavailableProviders() {
        val ciphertext = EncryptionUtil(mock<Context>()).encrypt(key, "payload")
        val brokenBlock = EncryptionUtil(mock<Context>(), WalletPayloadDecryptor { _, _, _, _, _ ->
            throw IllegalBlockSizeException("invalid block")
        })
        assertEquals("", brokenBlock.decrypt(key, ciphertext))

        val unavailable = EncryptionUtil(mock<Context>(), WalletPayloadDecryptor { _, _, _, _, _ ->
            throw ProviderException("provider offline")
        })
        val failure = assertThrows(WalletSecureStorageUnavailableException::class.java) {
            unavailable.decrypt(key, ciphertext)
        }
        assertTrue(failure.cause is ProviderException)
    }
}
