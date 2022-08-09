package jp.co.soramitsu.featurestakingimpl.presentation.staking.unbond.select

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class SelectUnbondPayload(
    val collatorAddress: String?,
    val oneScreenConfirmation: Boolean = false
) : Parcelable
