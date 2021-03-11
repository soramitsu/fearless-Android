package jp.co.soramitsu.feature_wallet_impl.presentation.send

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.common.currencyItem
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel

class BalanceDetailsBottomSheet(
    context: Context,
    private val assetModel: AssetModel,
    private val transferDraft: TransferDraft
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.wallet_balance_details_title)

        currencyItem(R.string.choose_amount_available_balance, assetModel.available)
        currencyItem(R.string.wallet_balance_details_total, assetModel.total)
        currencyItem(R.string.wallet_balance_details_total_after, transferDraft.totalAfterTransfer(assetModel.total))
        currencyItem(R.string.wallet_balance_details_existential_deposit, assetModel.token.type.networkType.runtimeConfiguration.existentialDeposit)
    }
}