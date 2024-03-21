package jp.co.soramitsu.staking.impl.presentation.validators.parcel

import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.parcelize.Parcelize

@Parcelize
class ValidatorPrefsParcelModel(
    val commission: BigDecimal,
    val isBlocked: Boolean
) : Parcelable