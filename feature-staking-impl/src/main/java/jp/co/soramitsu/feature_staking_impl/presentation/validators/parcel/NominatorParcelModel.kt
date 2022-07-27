package jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigInteger

@Parcelize
class NominatorParcelModel(
    val who: ByteArray,
    val value: BigInteger
) : Parcelable
