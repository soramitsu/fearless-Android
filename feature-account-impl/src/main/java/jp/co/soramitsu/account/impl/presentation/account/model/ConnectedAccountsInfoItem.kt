package jp.co.soramitsu.account.impl.presentation.account.model

import jp.co.soramitsu.common.model.ImportAccountType

class ConnectedAccountsInfoItem(
    val accountType: ImportAccountType,
    val title: String,
    val amount: Int,
)