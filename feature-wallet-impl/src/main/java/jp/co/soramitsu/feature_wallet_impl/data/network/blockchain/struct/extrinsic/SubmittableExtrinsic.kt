package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.custom
import jp.co.soramitsu.fearless_utils.scale.dataType.DataType
import jp.co.soramitsu.fearless_utils.scale.dataType.uint8
import jp.co.soramitsu.fearless_utils.scale.pair
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.sizedByteArray
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.scale.uint8
import jp.co.soramitsu.fearless_utils.scale.utils.directWrite
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.Era
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.EraType
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.SignatureType

private val VERSION = "84".toUByte(radix = 16)
private val TIP = 0.toBigInteger()

object OpaqueCall : DataType<ByteArray>() {
    override fun conformsType(value: Any?): Boolean {
        return value is ByteArray
    }

    override fun read(reader: ScaleCodecReader): ByteArray {
        throw NotImplementedError("Cannot decode opaque call without runtime metadata")
    }

    override fun write(writer: ScaleCodecWriter, value: ByteArray) {
        writer.directWrite(value)
    }
}

object SubmittableExtrinsicV27 : Schema<SubmittableExtrinsicV27>() {
    val byteLength by compactInt()

    val signedExtrinsic by schema(SignedExtrinsicV27)
}

object SignedExtrinsicV27 : Schema<SignedExtrinsicV27>() {
    val version by uint8(default = VERSION)

    val accountId by sizedByteArray(32)

    val signature by custom(SignatureType)

    val era by custom(EraType, default = Era.Immortal)

    val nonce by compactInt()

    val tip by compactInt(default = TIP)

    val call by schema(TransferCallV27)
}

object TransferCallV27 : Schema<TransferCallV27>() {
    val callIndex by pair(uint8, uint8)

    val args by schema(TransferArgsV27)
}

object TransferArgsV27 : Schema<TransferArgsV27>() {
    val recipientId by sizedByteArray(32)

    val amount by compactInt()
}

object ExtrinsicPayloadValue : Schema<ExtrinsicPayloadValue>() {
    val call by custom(OpaqueCall)

    val era by custom(EraType, default = Era.Immortal)

    val nonce by compactInt()

    val tip by compactInt(default = TIP)

    val specVersion by uint32()
    val transactionVersion by uint32()

    val genesis by sizedByteArray(32)
    val blockHash by sizedByteArray(32)
}