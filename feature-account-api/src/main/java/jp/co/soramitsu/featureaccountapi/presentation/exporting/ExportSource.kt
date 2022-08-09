package jp.co.soramitsu.featureaccountapi.presentation.exporting

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_account_api.R
import jp.co.soramitsu.featureaccountapi.presentation.accountSource.AccountSource

sealed class ExportSource(@StringRes nameRes: Int, @StringRes hintRes: Int, @DrawableRes iconRes: Int) : AccountSource(nameRes, hintRes, iconRes, true) {
    object Json : ExportSource(R.string.recovery_json, R.string.recovery_json_hint, R.drawable.ic_save_type_json)

    object Mnemonic : ExportSource(R.string.recovery_mnemonic, R.string.recovery_mnemonic_hint, R.drawable.ic_save_type_mnemonic)

    object Seed : ExportSource(R.string.recovery_raw_seed, R.string.recovery_raw_seed_hint, R.drawable.ic_save_type_seed)

    val sort: Int
        get() = when (this) {
            Mnemonic -> 1
            Seed -> 2
            Json -> 3
        }
}
