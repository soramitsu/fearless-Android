package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings

import android.os.Parcelable
import androidx.annotation.StringRes
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingsSchema(
    val filters: List<Filter>,
    val sortings: List<Sorting>
) : Parcelable {

    @Parcelize
    data class Filter(
        @StringRes val title: Int,
        val checked: Boolean,
        val filter: Filters,
        val unit: String? = null
    ) : Parcelable

    @Parcelize
    data class Sorting(
        @StringRes val title: Int,
        val checked: Boolean,
        val sorting: jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
    ) : Parcelable
}
