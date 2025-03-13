package jp.co.soramitsu.runtime.multiNetwork.chain.ton

import io.ktor.util.hex
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.crypto.Ed25519
import org.ton.crypto.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.math.ceil

object CommentEncryption {
    private const val ROOT_CELL_BYTE_LENGTH = 35 + 4
    private const val CELL_BYTE_LENGTH = 127

    fun decryptComment(
        publicKey: PublicKeyEd25519,
        privateKey: PrivateKeyEd25519,
        cipherText: String,
        senderAddress: String
    ): String {
        val address = AddrStd(senderAddress).toString(bounceable = true, urlSafe = true)

        val decryptedData = decryptData(
            hex(cipherText),
            publicKey.key.toByteArray(),
            privateKey.key.toByteArray(),
            address.toByteArray()
        )

        return decryptedData.decodeToString()
    }
    fun encryptComment(
        comment: String,
        myPublicKey: PublicKeyEd25519,
        theirPublicKey: PublicKeyEd25519,
        myPrivateKey: PrivateKeyEd25519,
        senderAddress: String
    ): Cell {
        val myPublicKeyBytes = myPublicKey.key.toByteArray()
        val theirPublicKeyBytes = theirPublicKey.key.toByteArray()
        val myPrivateKeyBytes = myPrivateKey.key.toByteArray()
        return encryptComment(comment, myPublicKeyBytes, theirPublicKeyBytes, myPrivateKeyBytes, senderAddress)
    }

    fun encryptComment(
        comment: String,
        myPublicKey: ByteArray,
        theirPublicKey: ByteArray,
        myPrivateKey: ByteArray,
        senderAddress: String
    ): Cell {
        if (comment.isEmpty()) throw IllegalArgumentException("empty comment")

        val privateKey = if (myPrivateKey.size == 64) {
            myPrivateKey.sliceArray(0 until 32)
        } else {
            myPrivateKey
        }

        val commentBytes = comment.toByteArray()

        val address = AddrStd(senderAddress).toString(bounceable = true, urlSafe = true)
        val salt = address.toByteArray()

        val encryptedBytes =
            encryptData(commentBytes, myPublicKey, theirPublicKey, privateKey, salt)

        val payload = ByteArray(encryptedBytes.size + 4)
        payload[0] = 0x21 // encrypted text prefix
        payload[1] = 0x67
        payload[2] = 0xda.toByte()
        payload[3] = 0x4b
        encryptedBytes.copyInto(payload, 4)

        val rootCellBuilder = CellBuilder.beginCell()
            .storeBytes(payload.sliceArray(0 until minOf(payload.size, ROOT_CELL_BYTE_LENGTH)))

        val cellCount =
            ceil((payload.size - ROOT_CELL_BYTE_LENGTH).toDouble() / CELL_BYTE_LENGTH).toInt()
        if (cellCount > 16) {
            throw IllegalArgumentException("Text too long")
        }

        rootCellBuilder.storeRef(storeDeepRef(payload.sliceArray(ROOT_CELL_BYTE_LENGTH until payload.size)))

        return rootCellBuilder.endCell()
    }

    private fun makeSnakeCells(bytes: ByteArray): Cell {
        val rootCellBuilder = CellBuilder.beginCell()
            .storeBytes(bytes.sliceArray(0 until minOf(bytes.size, ROOT_CELL_BYTE_LENGTH)))

        val cellCount =
            ceil((bytes.size - ROOT_CELL_BYTE_LENGTH).toDouble() / CELL_BYTE_LENGTH).toInt()
        if (cellCount > 16) {
            throw IllegalArgumentException("Text too long")
        }

        rootCellBuilder.storeRef(storeDeepRef(bytes.sliceArray(ROOT_CELL_BYTE_LENGTH until bytes.size)))

        return rootCellBuilder.endCell()
    }

    private fun storeDeepRef(bytes: ByteArray): Cell {
        return if (bytes.size <= CELL_BYTE_LENGTH) {
            CellBuilder.beginCell().storeBytes(bytes.sliceArray(0 until bytes.size)).endCell()
        } else {
            CellBuilder.beginCell().storeBytes(bytes.sliceArray(0 until CELL_BYTE_LENGTH))
                .storeRef(storeDeepRef(bytes.sliceArray(CELL_BYTE_LENGTH until bytes.size)))
                .endCell()
        }
    }

    private fun encryptData(
        data: ByteArray,
        myPublicKey: ByteArray,
        theirPublicKey: ByteArray,
        privateKey: ByteArray,
        salt: ByteArray
    ): ByteArray {
        val sharedSecret = Ed25519.sharedKey(privateKey, theirPublicKey)

        val encrypted = encryptDataImpl(data, sharedSecret, salt)
        val prefixedEncrypted = ByteArray(myPublicKey.size + encrypted.size)

        for (i in myPublicKey.indices) {
            prefixedEncrypted[i] = (theirPublicKey[i] xor myPublicKey[i]).toByte()
        }

        encrypted.copyInto(prefixedEncrypted, myPublicKey.size)

        return prefixedEncrypted
    }

    private fun encryptDataImpl(
        data: ByteArray,
        sharedSecret: ByteArray,
        salt: ByteArray
    ): ByteArray {
        val prefix = getRandomPrefix(data.size, 16)
        val combined = ByteArray(prefix.size + data.size)
        System.arraycopy(prefix, 0, combined, 0, prefix.size)
        System.arraycopy(data, 0, combined, prefix.size, data.size)
        return encryptDataWithPrefix(combined, sharedSecret, salt)
    }

    private fun encryptDataWithPrefix(
        data: ByteArray,
        sharedSecret: ByteArray,
        salt: ByteArray
    ): ByteArray {
        if (data.size % 16 != 0) throw IllegalArgumentException("Data length is not divisible by 16")
        val dataHash = hmacSha512(salt, data)
        val msgKey = dataHash.sliceArray(0 until 16)
        val cbcStateSecret = hmacSha512(sharedSecret, msgKey)
        val encrypted = AesCbcState(cbcStateSecret).encrypt(data)
        return msgKey + encrypted
    }

    private fun getRandomPrefix(dataLength: Int, minPadding: Int): ByteArray {
        val prefixLength = ((minPadding + 15 + dataLength) and -16) - dataLength
        val prefix = SecureRandom.nextBytes(prefixLength)
        prefix[0] = prefixLength.toByte()
        if ((prefixLength + dataLength) % 16 != 0) throw IllegalArgumentException("Prefix length is invalid")
        return prefix
    }

    private fun decryptData(
        data: ByteArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
        salt: ByteArray
    ): ByteArray {
        val theirPublicKey = ByteArray(publicKey.size)
        for (i in publicKey.indices) {
            theirPublicKey[i] = data[i] xor publicKey[i]
        }
        val sharedSecret = Ed25519.sharedKey(privateKey, theirPublicKey)
        return decryptDataImpl(data.sliceArray(publicKey.size until data.size), sharedSecret, salt)
    }

    private fun decryptDataImpl(
        encryptedData: ByteArray,
        sharedSecret: ByteArray,
        salt: ByteArray
    ): ByteArray {
        if (encryptedData.size < 16) throw IllegalArgumentException("Failed to decrypt: data is too small")
        if (encryptedData.size % 16 != 0) throw IllegalArgumentException("Failed to decrypt: data size is not divisible by 16")

        val msgKey = encryptedData.sliceArray(0 until 16)
        val data = encryptedData.sliceArray(16 until encryptedData.size)
        val cbcStateSecret = hmacSha512(sharedSecret, msgKey)

        return doDecrypt(cbcStateSecret, msgKey, data, salt)
    }

    private fun doDecrypt(
        cbcStateSecret: ByteArray,
        msgKey: ByteArray,
        encryptedData: ByteArray,
        salt: ByteArray
    ): ByteArray {
        val decryptedData = AesCbcState(cbcStateSecret).decrypt(encryptedData)
        val dataHash = hmacSha512(salt, decryptedData)

        val gotMsgKey = dataHash.sliceArray(0 until 16)

        if (!msgKey.contentEquals(gotMsgKey)) {
            throw IllegalArgumentException("Failed to decrypt: hash mismatch")
        }

        val prefixLength = decryptedData[0].toInt()
        if (prefixLength > decryptedData.size || prefixLength < 16) {
            throw IllegalArgumentException("Failed to decrypt: invalid prefix size")
        }

        return decryptedData.sliceArray(prefixLength until decryptedData.size)
    }
    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        val keySpec = SecretKeySpec(key, "HmacSHA512")
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    data class AesCbcState(
        val key: ByteArray,
        val iv: ByteArray
    ) {

        companion object {

            private fun initCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
                val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                val keySpec = SecretKeySpec(key, "AES")
                val ivSpec = IvParameterSpec(iv)
                cipher.init(mode, keySpec, ivSpec)
                return cipher
            }
        }

        constructor(hash: ByteArray) : this(
            key = hash.sliceArray(0 until 32),
            iv = hash.sliceArray(32 until 48)
        )

        fun decrypt(encryptedData: ByteArray): ByteArray {
            val cipher = initCipher(Cipher.DECRYPT_MODE, key, iv)
            return cipher.doFinal(encryptedData)
        }

        fun encrypt(data: ByteArray): ByteArray {
            val cipher = initCipher(Cipher.ENCRYPT_MODE, key, iv)
            return cipher.doFinal(data)
        }
    }
}
