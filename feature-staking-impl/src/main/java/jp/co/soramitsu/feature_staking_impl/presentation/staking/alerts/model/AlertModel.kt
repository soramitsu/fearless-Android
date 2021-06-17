package jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model

import androidx.annotation.DrawableRes

class AlertModel(
    @DrawableRes val icon: Int,
    val title: String,
    val extraMessage: String,
    val type: Type
) {
    sealed class Type {
        object Info : Type()

        class CallToAction(val action: () -> Unit) : Type()
    }
}
