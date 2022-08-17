package jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmRebondPayload(
    val amount: BigDecimal,
    val collatorAddress: String?
) : Parcelable
