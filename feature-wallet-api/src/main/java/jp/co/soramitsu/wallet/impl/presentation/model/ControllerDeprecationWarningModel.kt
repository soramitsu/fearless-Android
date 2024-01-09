package jp.co.soramitsu.wallet.impl.presentation.model

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning

data class ControllerDeprecationWarningModel(
    val title: String,
    val message: String,
    val buttonText: String,
    val action: Action,
    val chainId: ChainId
) {
    enum class Action { ChangeController, ImportStash }
}

fun ControllerDeprecationWarning.toModel(resourceManager: ResourceManager): ControllerDeprecationWarningModel {
    return when (this) {
        is ControllerDeprecationWarning.ChangeController ->
            ControllerDeprecationWarningModel(
                title = resourceManager.getString(R.string.common_important),
                message = resourceManager.getString(R.string.controller_account_issue_message, chainName),
                buttonText = resourceManager.getString(R.string.controller_account_issue_action),
                action = ControllerDeprecationWarningModel.Action.ChangeController,
                chainId = chainId
            )
        is ControllerDeprecationWarning.ImportStash ->
            ControllerDeprecationWarningModel(
                title = resourceManager.getString(R.string.common_important),
                message = resourceManager.getString(R.string.stash_account_issue_message, stashAddress.shortenAddress(8)),
                buttonText = resourceManager.getString(R.string.stash_account_issue_action),
                action = ControllerDeprecationWarningModel.Action.ImportStash,
                chainId = chainId
            )
    }
}
