package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.common.data.network.scale.byte
import jp.co.soramitsu.common.data.network.scale.compactInt
import jp.co.soramitsu.common.data.network.scale.pair
import jp.co.soramitsu.common.data.network.scale.schema
import jp.co.soramitsu.common.data.network.scale.sizedByteArray
import jp.co.soramitsu.common.data.network.scale.uint32
import jp.co.soramitsu.common.data.network.scale.uint8

private val VERSION = "84".toUByte(radix = 16)
private const val ERA = 0.toByte()
private val TIP = 0.toBigInteger()

object SubmittableExtrinsic : Schema<SubmittableExtrinsic>() {
    val byteLength by compactInt()

    val signedExtrinsic by schema(SignedExtrinsic)
}

object SignedExtrinsic : Schema<SignedExtrinsic>() {
    val version by uint8(default = VERSION)

    val accountId by sizedByteArray(32)

    val signatureVersion by uint8()
    val signature by sizedByteArray(64)

    val era by byte(default = ERA)

    val nonce by compactInt()

    val tip by compactInt(default = TIP)

    val call by schema(Call)
}

object Call : Schema<Call>() {
    val callIndex by pair(uint8, uint8)

    val args by schema(TransferArgs)
}

@Suppress("EXPERIMENTAL_API_USAGE")
enum class SupportedCall(val index: Pair<UByte, UByte>) {
    TRANSFER(0.toUByte() to 4.toUByte());

    companion object {
        fun from(callIndex: Pair<UByte, UByte>) : SupportedCall? {
            return values().firstOrNull { it.index == callIndex }
        }
    }
}

object TransferArgs : Schema<TransferArgs>() {
    val recipientId by sizedByteArray(32)

    val amount by compactInt()
}

object ExtrinsicPayloadValue : Schema<ExtrinsicPayloadValue>() {
    val call by schema(Call)

    val era by byte(default = ERA)

    val nonce by compactInt()

    val tip by compactInt(default = TIP)

    val specVersion by uint32()
    val transactionVersion by uint32()

    val genesis by sizedByteArray(32)
    val blockHash by sizedByteArray(32)
}