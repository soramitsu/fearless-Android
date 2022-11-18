package jp.co.soramitsu.common

import android.os.Parcelable
import androidx.annotation.DrawableRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class AlertViewState(
    val title: String,
    val message: String,
    val buttonText: String,
    val textSize: Int = 16,
    @DrawableRes val iconRes: Int
) : Parcelable
