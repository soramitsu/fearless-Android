package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_impl.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.ActiveEraInfo
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.repository.sumStaking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class StakingLedgerUpdater(
    accountRepository: AccountRepository,
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache
) : AccountUpdater(accountRepository) {

    override suspend fun listenForUpdates(account: Account): Flow<Updater.SideEffect> {
        return substrateSource.listenStakingLedger(account.address)
            .onEach { stakingLedger ->
                val era = substrateSource.getActiveEra()

                updateAssetStaking(account, stakingLedger, era)
            }.noSideAffects()
    }

    private suspend fun updateAssetStaking(
        account: Account,
        stakingLedger: EncodableStruct<StakingLedger>,
        era: EncodableStruct<ActiveEraInfo>
    ) {
        return assetCache.updateAsset(account) { cached ->
            val eraIndex = era[ActiveEraInfo.index].toLong()

            val redeemable = stakingLedger.sumStaking { it <= eraIndex }
            val unbonding = stakingLedger.sumStaking { it > eraIndex }

            cached.copy(
                redeemableInPlanks = redeemable,
                unbondingInPlanks = unbonding,
                bondedInPlanks = stakingLedger[StakingLedger.active]
            )
        }
    }
}