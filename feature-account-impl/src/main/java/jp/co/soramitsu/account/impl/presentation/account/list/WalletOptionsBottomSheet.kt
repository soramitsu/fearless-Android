package jp.co.soramitsu.account.impl.presentation.account.list

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item

class WalletOptionsBottomSheet(
    context: Context,
    private val metaAccountId: Long,
    private val onViewWallet: (Long) -> Unit,
    private val onExportWallet: (Long) -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.wallet_settings)

        item(R.drawable.ic_wallet_view_24, R.string.view_wallet) {
            onViewWallet(metaAccountId)
        }

        item(R.drawable.ic_share_arrow_white_24, R.string.export_wallet) {
            onExportWallet(metaAccountId)
        }
    }
}
