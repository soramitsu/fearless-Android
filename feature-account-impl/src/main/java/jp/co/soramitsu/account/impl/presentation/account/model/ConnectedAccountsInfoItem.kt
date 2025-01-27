package jp.co.soramitsu.account.impl.presentation.account.model

import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType

class ConnectedAccountsInfoItem(
    val accountType: ImportAccountType,
    val title: String,
    val amount: Int,
)