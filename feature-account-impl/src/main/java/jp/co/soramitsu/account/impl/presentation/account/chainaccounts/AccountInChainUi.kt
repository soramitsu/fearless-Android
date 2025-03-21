package jp.co.soramitsu.account.impl.presentation.account.chainaccounts

import android.graphics.drawable.Drawable
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class AccountInChainUi(
    val chainId: ChainId,
    val chainName: String,
    val chainIcon: String,
    val address: String,
    val accountIcon: Drawable?,
    val enabled: Boolean = true,
    val accountName: String?,
    val accountFrom: AccountInChain.From?,
    val isSupported: Boolean = true,
    val hasAccount: Boolean,
    val markedAsNotNeed: Boolean
)
