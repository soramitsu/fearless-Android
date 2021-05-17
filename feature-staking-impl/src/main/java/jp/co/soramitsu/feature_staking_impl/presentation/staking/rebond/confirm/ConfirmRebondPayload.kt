package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmRebondPayload(
    val amount: BigDecimal
) : Parcelable
