package jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel

import android.os.Parcelable
import java.math.BigInteger
import kotlinx.android.parcel.Parcelize

@Parcelize
class NominatorParcelModel(
    val who: ByteArray,
    val value: BigInteger
) : Parcelable
