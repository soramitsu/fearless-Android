package jp.co.soramitsu.crowdloan.impl.data

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.state.SingleAssetSharedState

private const val CROWDLOAN_SHARED_STATE = "CROWDLOAN_SHARED_STATE"

class CrowdloanSharedState(
    chainRegistry: ChainRegistry,
    preferences: Preferences
) : SingleAssetSharedState(
    preferences = preferences,
    chainRegistry = chainRegistry,
    filter = { chain, chainAsset -> chain.hasCrowdloans and (chain.utilityAsset.id == chainAsset.id) },
    preferencesKey = CROWDLOAN_SHARED_STATE
)
