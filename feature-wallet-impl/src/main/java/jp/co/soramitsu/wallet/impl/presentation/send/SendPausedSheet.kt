package jp.co.soramitsu.wallet.impl.presentation.send

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.SendPausedException

/**
 * The paused-send sheet (design 11.8), shown only under a remote Bitcoin or Solana veto: why
 * sending is paused, that the balance is unaffected, and Receive as the working next step.
 */
internal fun BaseViewModel.showSendPausedSheet(
    resourceManager: ResourceManager,
    error: SendPausedException,
    onReceive: () -> Unit,
    onClose: () -> Unit = emptyClick
) {
    showError(
        title = error.title,
        message = error.message.orEmpty(),
        positiveButtonText = resourceManager.getString(R.string.common_action_receive),
        negativeButtonText = resourceManager.getString(R.string.common_close),
        positiveClick = onReceive,
        negativeClick = onClose
    )
}
