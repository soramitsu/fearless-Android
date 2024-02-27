package jp.co.soramitsu.nft.impl.presentation

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoroutinesStore @Inject constructor(
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter
) {

    val uiScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + createExceptionHandler()
    )

    private fun createExceptionHandler() = CoroutineExceptionHandler { _, _ ->
        internalNFTRouter.openErrorsScreen(
            title = resourceManager.getString(
                R.string.common_error_general_title
            ),
            message = resourceManager.getString(
                R.string.common_error_network
            )
        )
    }
}
