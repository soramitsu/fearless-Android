package jp.co.soramitsu.wallet.impl.presentation

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

@Parcelize
data class AssetPayload(
    val chainId: ChainId,
    val chainAssetId: String
) : Parcelable
