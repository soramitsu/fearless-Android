package jp.co.soramitsu.nft.impl.presentation

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.cachedOrNew
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

@Singleton
class CoroutinesStore @Inject constructor(
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter
) {

    val uiScope: CoroutineScope by Delegates.cachedOrNew(isCorrupted = ::isScopeCanceled) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler(::handleException))
    }

    val ioScope: CoroutineScope by Delegates.cachedOrNew(::isScopeCanceled) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler(::handleException))
    }

    private fun isScopeCanceled(scope: CoroutineScope): Boolean {
        return scope.coroutineContext[Job]?.isActive != true
    }

    @Suppress("UnusedParameter")
    private fun handleException(coroutineContext: CoroutineContext, throwable: Throwable?) {
        internalNFTRouter.openErrorsScreen(
            title = resourceManager.getString(R.string.common_error_general_title),
            message = resourceManager.getString(R.string.common_error_network)
        )
    }
}
