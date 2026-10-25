package jp.co.soramitsu.app.root.presentation

import android.content.Context
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet

/** Network-cache failures leave local wallets usable while the user chooses when to retry. */
internal fun createStartupConnectionAlert(
    context: Context,
    step: InitializationStep,
    onRetry: (InitializationStep) -> Unit
): AlertBottomSheet {
    lateinit var alert: AlertBottomSheet
    val builder = AlertBottomSheet.Builder(context)
        .setTitle(R.string.common_warning)
        .setMessage(if (step == InitializationStep.AppConfig) R.string.startup_settings_refresh_unavailable else R.string.startup_network_refresh_unavailable)
        .setButtonText(R.string.common_retry)
        .setCancelable(true)
        .callback { onRetry(step) }
    builder.setSecondaryButton(R.string.common_continue) { alert.dismiss() }
    alert = builder.build()
    return alert
}

/** Update guidance must preserve authenticated access to local recovery material. */
internal fun createStartupVersionAlert(context: Context, onUpdate: () -> Unit): AlertBottomSheet {
    lateinit var alert: AlertBottomSheet
    alert = AlertBottomSheet.Builder(context)
        .setTitle(R.string.update_needed_text)
        .setMessage(R.string.startup_app_update_notice)
        .setButtonText(R.string.common_update)
        .setCancelable(true)
        .callback(onUpdate)
        .setSecondaryButton(R.string.common_continue) { alert.dismiss() }
        .build()
    return alert
}
