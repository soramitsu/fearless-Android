package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.common.data.network.scale.byte
import jp.co.soramitsu.common.data.network.scale.compactInt
import jp.co.soramitsu.common.data.network.scale.pair
import jp.co.soramitsu.common.data.network.scale.schema
import jp.co.soramitsu.common.data.network.scale.sizedByteArray
import jp.co.soramitsu.common.data.network.scale.uint8

object ExtrinsicStub : Schema<ExtrinsicStub>() {
    val byteLength by compactInt()

    val signedExtrinsic by schema(SignedExtrinsic)
}

object SignedExtrinsicStub : Schema<SignedExtrinsicStub>() {
    val version by uint8()

    val accountId by sizedByteArray(32)

    val signatureVersion by uint8()
    val signature by sizedByteArray(64)

    val era by byte()

    val nonce by compactInt()

    val tip by compactInt()

    val call by schema(Call)
}

object CallStub : Schema<CallStub>() {
    val callIndex by pair(uint8, uint8)
}