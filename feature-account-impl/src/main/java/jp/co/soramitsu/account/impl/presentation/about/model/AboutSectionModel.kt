package jp.co.soramitsu.account.impl.presentation.about.model

import androidx.annotation.StringRes

data class AboutSectionModel(
    @StringRes val titleResId: Int
) : AboutItemListModel()
