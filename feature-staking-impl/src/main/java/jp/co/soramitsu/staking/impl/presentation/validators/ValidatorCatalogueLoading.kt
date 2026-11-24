package jp.co.soramitsu.staking.impl.presentation.validators

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The validator catalogue behind a validator choice screen. [loading] keeps the choices disabled while a load runs.
 * A failed load is shown with Retry and enables the choices again: choosing one loads the catalogue before acting, so
 * neither a dismissed dialog nor a recreated screen leaves the screen stuck, and no choice opens on a failed catalogue.
 * Only the screen's own cancellation ends a load silently; a loader that times out has failed.
 */
internal class ValidatorCatalogue(
    private val viewModel: BaseViewModel,
    private val resourceManager: ResourceManager,
    private val load: suspend () -> Unit
) {
    private val loadingState = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = loadingState.asStateFlow()
    private var loaded = false

    init {
        start {}
    }

    /** Runs [action] once the catalogue has loaded; after a failed load it loads the catalogue again first. */
    fun whenLoaded(action: () -> Unit) {
        when {
            loaded -> action()
            !loadingState.value -> start(action)
        }
    }

    private fun start(then: () -> Unit) {
        loadingState.value = true
        viewModel.launch {
            val failure = runCatching { load() }.exceptionOrNull()
            currentCoroutineContext().ensureActive()
            loadingState.value = false
            if (failure == null) {
                loaded = true
                then()
            } else {
                viewModel.showError(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = failure.takeUnless { it is CancellationException }?.message
                        ?: resourceManager.getString(R.string.common_error_general_message),
                    positiveButtonText = resourceManager.getString(R.string.common_retry),
                    positiveClick = { whenLoaded {} }
                )
            }
        }
    }
}
