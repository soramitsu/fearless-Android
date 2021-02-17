package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.dataType.uint32
import jp.co.soramitsu.fearless_utils.scale.sizedByteArray
import jp.co.soramitsu.fearless_utils.scale.vector

object StakingLedger : Schema<StakingLedger>() {
    val stash by sizedByteArray(32)

    val total by compactInt()
    val active by compactInt()

    val unlocking by vector(UnlockChunk)

    val claimedRewards by vector(uint32)
}

object UnlockChunk : Schema<UnlockChunk>() {
    val value by compactInt()
    val era by compactInt()
}

object AccountId : Schema<AccountId>() {
    val id by sizedByteArray(32)
}