package jp.co.soramitsu.account.impl.presentation.account.model

import jp.co.soramitsu.common.model.WalletEcosystem

class ConnectedAccountsInfoItem(
    val accountType: WalletEcosystem,
    val title: String,
    val amount: Int,
)