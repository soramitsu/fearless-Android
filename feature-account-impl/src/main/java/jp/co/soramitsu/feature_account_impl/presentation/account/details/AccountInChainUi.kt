package jp.co.soramitsu.feature_account_impl.presentation.account.details

import android.graphics.drawable.Drawable
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class AccountInChainUi(
    val chainId: ChainId,
    val chainName: String,
    val chainIcon: String,
    val address: String,
    val accountIcon: Drawable
)
