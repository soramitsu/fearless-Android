package co.jp.soramitsu.tonconnect.model

import android.net.Uri
import org.json.JSONObject

data class AppConnectEntity(
    val accountId: String,
    val testnet: Boolean,
    val clientId: String,
    val type: Type,
    val appUrl: Uri,
    val keyPair: CryptoBox.KeyPair,
    val proofSignature: String?,
    val proofPayload: String?,
    val timestamp: Long = (System.currentTimeMillis() / 1000L),
    val pushEnabled: Boolean,
) {

    enum class Type(val value: Int) {
        Internal(1), External(2)
    }

    companion object {

//        private fun decryptMessage(
         fun decryptMessage(
            remotePublicKey: ByteArray,
            localPrivateKey: ByteArray,
            body: ByteArray
        ): ByteArray {
            val nonce = body.sliceArray(0 until Sodium.cryptoBoxNonceBytes())
            val cipher = body.sliceArray(Sodium.cryptoBoxNonceBytes() until body.size)
            val message = ByteArray(cipher.size - Sodium.cryptoBoxMacBytes())
            Sodium.cryptoBoxOpenEasy(message, cipher, cipher.size, nonce, remotePublicKey, localPrivateKey)
            return message
        }
    }
}