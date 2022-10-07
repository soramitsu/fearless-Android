package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward

import android.os.Parcelable
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.android.parcel.Parcelize

@Parcelize
class RewardDetailsPayload(
    val operation: OperationParcelizeModel.Reward,
    val chainId: ChainId
) : Parcelable
