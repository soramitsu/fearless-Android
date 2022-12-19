package jp.co.soramitsu.staking.impl.presentation.staking.unbond.select

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class SelectUnbondPayload(
    val collatorAddress: String?,
    val oneScreenConfirmation: Boolean = false
) : Parcelable
