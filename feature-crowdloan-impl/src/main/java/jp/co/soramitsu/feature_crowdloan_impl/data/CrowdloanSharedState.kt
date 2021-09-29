package jp.co.soramitsu.feature_crowdloan_impl.data

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.ext.isUtilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.state.SingleAssetSharedState

private const val CROWDLOAN_SHARED_STATE = "CROWDLOAN_SHARED_STATE"

class CrowdloanSharedState(
    chainRegistry: ChainRegistry,
    preferences: Preferences,
) : SingleAssetSharedState(
    preferences = preferences,
    chainRegistry = chainRegistry,
    filter = { chain, chainAsset -> chain.hasCrowdloans and chainAsset.isUtilityAsset },
    preferencesKey = CROWDLOAN_SHARED_STATE
)
