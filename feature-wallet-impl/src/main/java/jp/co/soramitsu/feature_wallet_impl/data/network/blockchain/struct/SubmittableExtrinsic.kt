package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.custom
import jp.co.soramitsu.fearless_utils.scale.dataType.uint8
import jp.co.soramitsu.fearless_utils.scale.pair
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.sizedByteArray
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.scale.uint8
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.jcajce.provider.digest.BCMessageDigest

private val VERSION = "84".toUByte(radix = 16)
private val TIP = 0.toBigInteger()

object SubmittableExtrinsic : Schema<SubmittableExtrinsic>() {
    val byteLength by compactInt()

    val signedExtrinsic by schema(SignedExtrinsic)
}

object SignedExtrinsic : Schema<SignedExtrinsic>() {
    val version by uint8(default = VERSION)

    val accountId by sizedByteArray(32)

    val signature by custom(SignatureType)

    val era by custom(EraType, default = Era.Immortal)

    val nonce by compactInt()

    val tip by compactInt(default = TIP)

    val call by schema(Call)
}

object Call : Schema<Call>() {
    val callIndex by pair(uint8, uint8)

    val args by schema(TransferArgs)
}

object TransferArgs : Schema<TransferArgs>() {
    val recipientId by sizedByteArray(32)

    val amount by compactInt()
}

object ExtrinsicPayloadValue : Schema<ExtrinsicPayloadValue>() {
    val call by schema(Call)

    val era by custom(EraType, default = Era.Immortal)

    val nonce by compactInt()

    val tip by compactInt(default = TIP)

    val specVersion by uint32()
    val transactionVersion by uint32()

    val genesis by sizedByteArray(32)
    val blockHash by sizedByteArray(32)
}

object Blake2b256 : BCMessageDigest(Blake2bDigest(256))

fun EncodableStruct<SubmittableExtrinsic>.hash(): String {
    val bytes = Blake2b256.digest(SubmittableExtrinsic.toByteArray(this))

    return bytes.toHexString(withPrefix = true)
}