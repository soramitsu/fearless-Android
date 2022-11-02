package jp.co.soramitsu.common

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class AlertViewState(
    val title: String,
    val message: String,
    val buttonText: String,
    @DrawableRes val iconRes: Int
) : Parcelable
