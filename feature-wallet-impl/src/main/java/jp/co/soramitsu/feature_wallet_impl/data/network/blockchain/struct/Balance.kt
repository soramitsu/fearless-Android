package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.compactInt
import jp.co.soramitsu.fearless_utils.scale.dataType.uint32
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.sizedByteArray
import jp.co.soramitsu.fearless_utils.scale.uint128
import jp.co.soramitsu.fearless_utils.scale.uint32
import jp.co.soramitsu.fearless_utils.scale.vector

object AccountData : Schema<AccountData>() {
    val free by uint128()
    val reserved by uint128()
    val miscFrozen by uint128()
    val feeFrozen by uint128()
}

object AccountInfo : Schema<AccountInfo>() {
    val nonce by uint32()

    val refCount by uint32()

    val data by schema(AccountData)
}

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

object ActiveEraInfo : Schema<ActiveEraInfo>() {
    val index by uint32()
}

object AccountId : Schema<AccountId>() {
    val id by sizedByteArray(32)
}