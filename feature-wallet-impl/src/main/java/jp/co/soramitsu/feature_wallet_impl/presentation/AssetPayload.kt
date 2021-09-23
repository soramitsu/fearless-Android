package jp.co.soramitsu.feature_wallet_impl.presentation

import android.os.Parcelable
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class AssetPayload(val chainId: ChainId, val chainAssetId: Int): Parcelable {

    companion object {

        fun stub() = AssetPayload(
            chainId = Node.NetworkType.POLKADOT.chainId,
            chainAssetId = 0
        )
    }
}
