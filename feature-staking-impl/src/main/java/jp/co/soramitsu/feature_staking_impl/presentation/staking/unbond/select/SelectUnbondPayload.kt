package jp.co.soramitsu.feature_staking_impl.presentation.staking.unbond.select

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class SelectUnbondPayload(
    val collatorAddress: String?,
    val oneScreenConfirmation: Boolean = false
) : Parcelable
