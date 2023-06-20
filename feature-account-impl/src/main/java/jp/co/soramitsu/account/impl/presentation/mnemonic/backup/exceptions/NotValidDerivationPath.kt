package jp.co.soramitsu.account.impl.presentation.mnemonic.backup.exceptions

import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.R

class NotValidDerivationPath(
    resourceManager: ResourceManager
) : TitledException(
    title = resourceManager.getString(R.string.common_error_general_title),
    message = resourceManager.getString(R.string.common_invalid_hard_soft_numeric_password_message)
)
