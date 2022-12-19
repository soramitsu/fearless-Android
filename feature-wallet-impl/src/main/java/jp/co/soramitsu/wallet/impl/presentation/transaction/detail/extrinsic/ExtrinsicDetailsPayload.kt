package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic

import android.os.Parcelable
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

@Parcelize
class ExtrinsicDetailsPayload(
    val operation: OperationParcelizeModel.Extrinsic,
    val chainId: ChainId
) : Parcelable
