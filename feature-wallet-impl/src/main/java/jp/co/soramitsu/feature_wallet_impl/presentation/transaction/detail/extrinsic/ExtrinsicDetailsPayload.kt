package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic

import android.os.Parcelable
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class ExtrinsicDetailsPayload(
    val operation: OperationParcelizeModel.Extrinsic,
    val chainId: ChainId
) : Parcelable
