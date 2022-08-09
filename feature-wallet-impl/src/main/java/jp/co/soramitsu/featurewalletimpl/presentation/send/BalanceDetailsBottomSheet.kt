package jp.co.soramitsu.featurewalletimpl.presentation.send

import android.content.Context
import android.os.Bundle
import java.math.BigDecimal
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.featurewalletimpl.presentation.common.currencyItem
import jp.co.soramitsu.featurewalletimpl.presentation.model.AssetModel

class BalanceDetailsBottomSheet(
    context: Context,
    private val payload: Payload
) : FixedListBottomSheet(context) {

    class Payload(
        val assetModel: AssetModel,
        val transferDraft: TransferDraft,
        val existentialDeposit: BigDecimal
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.wallet_balance_details_title)

        with(payload) {
            currencyItem(
                R.string.choose_amount_available_balance,
                assetModel.formatTokenAmount(assetModel.available),
                assetModel.getAsFiatWithCurrency(assetModel.available)
            )
            currencyItem(
                R.string.wallet_balance_details_total,
                assetModel.formatTokenAmount(assetModel.total),
                assetModel.getAsFiatWithCurrency(assetModel.total)
            )
            currencyItem(
                R.string.wallet_balance_details_total_after,
                assetModel.formatTokenAmount(transferDraft.totalAfterTransfer(assetModel.total.orZero())),
                assetModel.getAsFiatWithCurrency(transferDraft.totalAfterTransfer(assetModel.total.orZero()))
            )
            currencyItem(
                R.string.wallet_send_balance_minimal,
                assetModel.formatTokenAmount(existentialDeposit),
                assetModel.getAsFiatWithCurrency(existentialDeposit)
            )
        }
    }
}
