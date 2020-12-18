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

        val title = context.getString(R.string.wallet_balance_frozen_template, payload.token.type.displayName)
        setTitle(title)

        currencyItem(R.string.wallet_balance_locked, payload.locked)
        currencyItem(R.string.wallet_balance_bonded, payload.bonded)
        currencyItem(R.string.wallet_balance_reserved, payload.reserved)
        currencyItem(R.string.wallet_balance_redeemable, payload.redeemable)
        currencyItem(R.string.wallet_balance_unbonding, payload.unbonding)
    }
}