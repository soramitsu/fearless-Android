package jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmUnbondPayload(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val collatorAddress: String?
) : Parcelable
