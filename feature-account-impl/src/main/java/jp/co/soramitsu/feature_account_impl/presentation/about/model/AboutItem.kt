package jp.co.soramitsu.feature_account_impl.presentation.about.model

import androidx.annotation.DrawableRes
import jp.co.soramitsu.feature_account_impl.R

data class AboutItem(
    @DrawableRes val iconResId: Int = R.drawable.ic_info_primary_24,
    val title: String,
    val text: String,
)
