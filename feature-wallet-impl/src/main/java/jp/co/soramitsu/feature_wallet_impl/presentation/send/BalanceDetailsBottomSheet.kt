package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.FixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.common.currencyItem

class BalanceDetailsBottomSheet(
    context: Context,
    private val payload: TransferDraft
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.wallet_balance_details_title)

        currencyItem(R.string.choose_amount_available_balance, payload.available)
        currencyItem(R.string.wallet_balance_details_total, payload.totalBalance)
        currencyItem(R.string.wallet_balance_details_total_after, payload.totalBalanceAfterTransfer)
        currencyItem(R.string.wallet_balance_details_existential_deposit, payload.token.networkType.existentialDeposit)
    }
}