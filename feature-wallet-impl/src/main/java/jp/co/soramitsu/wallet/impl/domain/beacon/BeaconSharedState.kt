package jp.co.soramitsu.wallet.impl.domain.beacon

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.state.SingleAssetSharedState

private const val BEACON_SHARED_STATE = "BEACON_SHARED_STATE"

class BeaconSharedState(
    chainRegistry: ChainRegistry,
    preferences: Preferences
) : SingleAssetSharedState(
    preferences = preferences,
    chainRegistry = chainRegistry,
    filter = { _, chainAsset -> chainAsset.staking != Asset.StakingType.UNSUPPORTED },
    preferencesKey = BEACON_SHARED_STATE
)
