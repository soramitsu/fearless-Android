package jp.co.soramitsu.common.compose.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.common.R

/** Accessible action labels shared by toolbar and navigation controls. */
@StringRes
fun toolbarActionLabel(@DrawableRes icon: Int): Int = when (icon) {
    R.drawable.ic_arrow_left_24, R.drawable.ic_arrow_back_24dp -> R.string.ux_back
    R.drawable.ic_close, R.drawable.ic_cross_24, R.drawable.ic_cross_32 -> R.string.ux_close
    R.drawable.ic_search -> R.string.ux_search
    R.drawable.ic_scan -> R.string.ux_scan
    R.drawable.ic_wallet -> R.string.ux_switch_wallet
    R.drawable.ic_settings, R.drawable.ic_settings_swap -> R.string.profile_settings_title
    else -> R.string.ux_more
}
