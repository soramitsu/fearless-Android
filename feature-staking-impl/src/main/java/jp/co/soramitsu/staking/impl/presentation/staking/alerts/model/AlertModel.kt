package jp.co.soramitsu.staking.impl.presentation.staking.alerts.model

import androidx.annotation.DrawableRes

class AlertModel(
    @DrawableRes val icon: Int,
    val title: String,
    val extraMessage: String,
    val type: Type,
    val sourceIsCurrent: () -> Boolean = { true }
) {
    sealed class Type {
        object Info : Type()

        class CallToAction(private val guardedAction: (isCurrent: () -> Boolean) -> Unit) : Type() {
            val action: () -> Unit = { invokeIfCurrent { true } }

            fun invokeIfCurrent(isCurrent: () -> Boolean) {
                if (isCurrent()) guardedAction(isCurrent)
            }
        }
    }
}
