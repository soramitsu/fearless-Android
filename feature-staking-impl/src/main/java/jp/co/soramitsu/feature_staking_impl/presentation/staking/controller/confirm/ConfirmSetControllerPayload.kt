package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm

import android.os.Parcelable
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmSetControllerPayload(
    val fee: BigDecimal,
    val stashAddress: String,
    val controllerAddress: String,
    val transferable: BigDecimal
) : Parcelable
