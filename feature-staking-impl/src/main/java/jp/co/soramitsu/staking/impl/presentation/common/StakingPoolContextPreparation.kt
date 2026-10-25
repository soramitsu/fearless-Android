package jp.co.soramitsu.staking.impl.presentation.common

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import kotlinx.coroutines.flow.first

/** Reads one coherent pool context; a wallet transition during any suspended read invalidates it. */
internal suspend fun prepareStakingPoolContext(
    stakingSharedState: StakingSharedState,
    interactor: StakingInteractor,
    walletEpoch: Long? = runCatching { WalletSelectionEpoch.snapshot() }.getOrNull(),
    selection: jp.co.soramitsu.staking.api.data.StakingSelectionRevision.Snapshot = stakingSharedState.selectionRevision.capture(),
    readAmount: suspend () -> BigDecimal?
): StakingPoolState? {
    if (walletEpoch == null || !WalletSelectionEpoch.isCurrent(walletEpoch)) return null
    val asset = stakingSharedState.currentAssetFlow().first()
    val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
    val meta = interactor.getCurrentMetaAccount()
    val address = meta.address(chain) ?: return null
    val amount = readAmount()

    if (!WalletSelectionEpoch.isCurrent(walletEpoch) ||
        !stakingSharedState.selectionRevision.matches(selection, chain.id, chainAsset.id) || asset.metaId != meta.id ||
        asset.token.configuration.chainId != chain.id || asset.token.configuration.id != chainAsset.id ||
        !asset.accountId.contentEquals(meta.accountId(chain))) return null

    return StakingPoolState(asset = asset, chain = chain, chainAsset = chainAsset, address = address,
        amount = amount, walletSelectionEpoch = walletEpoch)
}
