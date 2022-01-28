package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward

import android.os.Parcelable
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class RewardDetailsPayload(
    val operation: OperationParcelizeModel.Reward,
    val chainId: ChainId
) : Parcelable
