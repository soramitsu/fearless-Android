package jp.co.soramitsu.staking.impl.presentation.validators

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Waits until [load] succeeds. A failed load is shown with a retry and the caller keeps its validator choices disabled
 * meanwhile, so a node that cannot be read never crashes the screen or opens one that would read it again.
 */
internal suspend fun BaseViewModel.awaitValidatorCatalogue(resourceManager: ResourceManager, load: suspend () -> Unit) {
    while (true) {
        val failure = runCatching { load() }.exceptionOrNull() ?: return
        if (failure is CancellationException) throw failure
        val retry = CompletableDeferred<Unit>()
        showError(
            title = resourceManager.getString(R.string.common_error_general_title),
            message = failure.message ?: resourceManager.getString(R.string.common_error_general_message),
            positiveButtonText = resourceManager.getString(R.string.common_retry),
            positiveClick = { retry.complete(Unit) }
        )
        retry.await()
    }
}
