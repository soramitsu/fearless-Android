package jp.co.soramitsu.staking.impl.domain.model

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Immutable public identity, denomination and bonded input used to calculate a relay summary. */
data class StakeSummarySource(
    val metaId: Long,
    val chainId: String,
    val assetId: String,
    val accountIdHex: String,
    val precision: Int,
    val symbol: String,
    val stakingType: ChainAsset.StakingType,
    val bondedInPlanks: BigInteger?
) {
    fun matches(asset: Asset): Boolean = this == from(asset)

    companion object {
        fun from(asset: Asset): StakeSummarySource = with(asset.token.configuration) {
            StakeSummarySource(asset.metaId, chainId, id, asset.accountId.toHexString(), precision, symbol, staking, asset.bondedInPlanks)
        }
    }
}
