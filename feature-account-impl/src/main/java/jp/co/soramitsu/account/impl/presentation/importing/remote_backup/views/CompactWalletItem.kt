package jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import kotlin.random.Random

internal fun CompactWalletItemViewState(title: String): WalletItemViewState {
    return WalletItemViewState(
        id = Random.nextLong(),
        balance = null,
        assetSymbol = null,
        changeBalanceViewState = null,
        title = title,
        walletIcon = R.drawable.ic_wallet,
        isSelected = false,
        additionalMetadata = ""
    )
}

internal fun CompactWalletNameItemViewState(title: String): WalletNameItemViewState {
    return WalletNameItemViewState(
        id = Random.nextLong(),
        title = title,
        walletIcon = R.drawable.ic_wallet,
        isSelected = false
    )
}
