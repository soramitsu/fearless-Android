package jp.co.soramitsu.feature_account_impl.presentation.node

import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.FearlessException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.feature_account_impl.domain.errors.UnsupportedNetworkException

abstract class NodeDetailsRootViewModel(
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    protected open fun handleNodeException(throwable: Throwable) {
        when (throwable) {
            is NodeAlreadyExistsException -> showError(resourceManager.getString(R.string.connection_add_already_exists_error))
            is UnsupportedNetworkException -> showError(getUnsupportedNodeError())
            is FearlessException -> {
                if (FearlessException.Kind.NETWORK == throwable.kind) {
                    showError(resourceManager.getString(R.string.connection_add_invalid_error))
                } else {
                    throwable.message?.let(::showError)
                }
            }
            else -> throwable.message?.let(::showError)
        }
    }

    protected open fun getUnsupportedNodeError(): String {
        val supportedNodes = Node.NetworkType.values().joinToString(", ") { it.readableName }
        val unsupportedNodeErrorMsg = resourceManager.getString(R.string.connection_add_unsupported_error)
        return unsupportedNodeErrorMsg.format(supportedNodes)
    }
}