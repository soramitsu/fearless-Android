package jp.co.soramitsu.staking.impl.presentation.staking.controller.confirm

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@Parcelize
class ConfirmSetControllerPayload(
    val fee: BigDecimal,
    val stashAddress: String,
    val controllerAddress: String,
    val transferable: BigDecimal,
    val chainId: ChainId? = null
) : Parcelable
