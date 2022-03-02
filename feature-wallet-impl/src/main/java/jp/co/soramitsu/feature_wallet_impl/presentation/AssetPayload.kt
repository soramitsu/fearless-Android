package jp.co.soramitsu.feature_wallet_impl.presentation

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class AssetPayload(
    val chainId: ChainId,
    val chainAssetId: String
) : Parcelable
