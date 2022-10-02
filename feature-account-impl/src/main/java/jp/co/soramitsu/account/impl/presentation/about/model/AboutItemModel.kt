package jp.co.soramitsu.account.impl.presentation.about.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class AboutItemModel(
    @DrawableRes val iconResId: Int,
    @StringRes val titleResId: Int,
    val text: String? = null,
    val showDivider: Boolean = true,
    val onClick: () -> Unit = {}
) : AboutItemListModel()
