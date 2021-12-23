package jp.co.soramitsu.feature_staking_impl.data

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState

private const val STAKING_SHARED_STATE = "STAKING_SHARED_STATE"
private const val localTestPolkadotChainId = "111979a679f11fc283d8968b657855f4afab1cff6905c7cb0451bdf2c50df47c"//dot

class StakingSharedState(
    chainRegistry: ChainRegistry,
    preferences: Preferences,
) : SingleAssetSharedState(
    preferences = preferences,
    chainRegistry = chainRegistry,
    filter = { _, chainAsset -> chainAsset.staking != Chain.Asset.StakingType.UNSUPPORTED && chainAsset.chainId != localTestPolkadotChainId},//todo remove local test net from  staking
    preferencesKey = STAKING_SHARED_STATE
)
