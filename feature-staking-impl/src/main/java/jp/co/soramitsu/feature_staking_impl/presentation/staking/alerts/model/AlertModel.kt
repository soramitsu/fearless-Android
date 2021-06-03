package jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

class AlertModel(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val extraMessage: Int,
    val type: Type
) {
    sealed class Type {
        object Warning : Type()

        class CallToAction(val action: (View) -> Unit) : Type()
    }
}
