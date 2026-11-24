package jp.co.soramitsu.tonconnect.api.model

import android.os.Parcel
import android.os.Parcelable
import jp.co.soramitsu.fearless_utils.encrypt.ton.TonEd25519
import org.ton.block.AddrStd
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object TONProof {

    private const val tonProofPrefix = "ton-proof-item-v2/"
    private const val tonConnectPrefix = "ton-connect"
    private const val MILLIS_PER_SECOND = 1000L
    private val prefixMessage = byteArrayOf(0xff.toByte(), 0xff.toByte()) + tonConnectPrefix.toByteArray()
    private val prefixItem = tonProofPrefix.toByteArray()

    /** Signs a TON Connect ton_proof with the wallet's 32-byte Ed25519 private key. */
    @OptIn(ExperimentalEncodingApi::class)
    fun sign(
        address: AddrStd,
        privateKey: ByteArray,
        payload: String,
        domain: String,
        timestamp: Long = System.currentTimeMillis() / MILLIS_PER_SECOND,
    ): Result {
        val request = Request(
            timestamp = timestamp,
            payload = payload,
            domain = Domain(domain),
            address = Address(address)
        )

        val body = sha256(prefixMessage + request.signatureMessage)
        val signature = TonEd25519.sign(privateKey, body)

        return Result(
            timestamp = request.timestamp,
            domain = request.domain,
            payload = request.payload,
            signature = Base64.encode(signature)
        )
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private interface Serializable {
        fun toByteArray(): ByteArray
    }

    data class Request(
        @Suppress("MagicNumber")
        val timestamp: Long = System.currentTimeMillis() / 1000L,
        val payload: String,
        val domain: Domain,
        val address: Address
    ) {

        val message by lazy {
            prefixItem + address.toByteArray() + domain.toByteArray() + timestamp.toByteArray() + payload.toByteArray()
        }

        val signatureMessage by lazy {
            sha256(message)
        }
    }

    @Suppress("SerialVersionUIDInSerializableClass")
    data class Address(val value: AddrStd) : Serializable {

        override fun toByteArray(): ByteArray {
            val addressWorkchainBuffer = value.workchainId.toByteArray()
            val addressHashBuffer = value.address.toByteArray()
            return addressWorkchainBuffer + addressHashBuffer
        }
    }

    @Suppress("SerialVersionUIDInSerializableClass")
    data class Domain(val value: String) : Serializable {

        private val lengthBytes: Int = value.toByteArray().size

        override fun toByteArray(): ByteArray {
            return lengthBytes.toByteArray() + value.toByteArray()
        }
    }

    data class Result(
        val timestamp: Long,
        val domain: Domain,
        val payload: String?,
        val signature: String
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readLong(),
            Domain(parcel.readString()!!),
            parcel.readString(),
            parcel.readString()!!
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(timestamp)
            parcel.writeString(domain.value)
            parcel.writeString(payload)
            parcel.writeString(signature)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Result> {
            override fun createFromParcel(parcel: Parcel): Result {
                return Result(parcel)
            }

            override fun newArray(size: Int): Array<Result?> {
                return arrayOfNulls(size)
            }
        }
    }
}
