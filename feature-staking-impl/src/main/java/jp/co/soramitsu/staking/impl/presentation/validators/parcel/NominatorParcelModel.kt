package jp.co.soramitsu.staking.impl.presentation.validators.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigInteger

@Parcelize
class NominatorParcelModel(
    val who: ByteArray,
    val value: BigInteger
) : Parcelable
