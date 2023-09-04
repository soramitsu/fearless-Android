package jp.co.soramitsu.wallet.impl.presentation.model

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import kotlinx.parcelize.Parcelize

@Parcelize
class RewardDetailsPayload(
    val operation: OperationParcelizeModel.Reward,
    val chainId: ChainId
) : Parcelable
