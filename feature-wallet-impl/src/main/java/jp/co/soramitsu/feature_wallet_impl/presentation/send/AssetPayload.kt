package jp.co.soramitsu.feature_wallet_impl.presentation.send

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class AssetPayload(val chainId: ChainId, val chainAssetId: Int)
