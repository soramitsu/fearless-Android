package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.BaseFixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.common.addCurrencyItem

class BalanceDetailsBottomSheet(
    context: Context,
    private val payload: TransferDraft
) : BaseFixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.wallet_balance_details_title)

        addCurrencyItem(R.string.choose_amount_available_balance, payload.available)
        addCurrencyItem(R.string.wallet_balance_details_total, payload.totalBalance)
        addCurrencyItem(R.string.wallet_balance_details_total_after, payload.totalBalanceAfterTransfer)
        addCurrencyItem(R.string.wallet_balance_details_existential_deposit, payload.token.networkType.existentialDeposit)
    }
}