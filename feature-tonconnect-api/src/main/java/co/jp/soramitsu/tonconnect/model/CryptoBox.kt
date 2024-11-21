package co.jp.soramitsu.tonconnect.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

object CryptoBox {

    @Parcelize
    data class KeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    ): Parcelable {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as KeyPair

            if (!publicKey.contentEquals(other.publicKey)) return false
            if (!privateKey.contentEquals(other.privateKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + privateKey.contentHashCode()
            return result
        }
    }

    fun nonce(): ByteArray {
        return Security.randomBytes(Sodium.cryptoBoxNonceBytes())
    }

}