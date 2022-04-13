package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.common.currencyItem
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel

class FrozenTokensBottomSheet(
    context: Context,
    private val payload: AssetModel
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = context.getString(R.string.wallet_balance_frozen_template, payload.token.configuration.symbol)
        setTitle(title)

        currencyItem(R.string.wallet_balance_locked, payload.formatTokenAmount(payload.locked), payload.getAsFiatWithCurrency(payload.locked))
        // todo uncomment when bonded data receiving will be completed in this module
//        currencyItem(R.string.wallet_balance_bonded, payload.bonded)
        currencyItem(R.string.wallet_balance_reserved, payload.formatTokenAmount(payload.reserved), payload.getAsFiatWithCurrency(payload.reserved))
//        currencyItem(R.string.wallet_balance_redeemable, payload.redeemable)
//        currencyItem(R.string.wallet_balance_unbonding_v1_9_0, payload.unbonding)
    }
}
