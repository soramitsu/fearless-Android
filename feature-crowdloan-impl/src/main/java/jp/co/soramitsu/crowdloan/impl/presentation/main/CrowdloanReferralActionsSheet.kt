package jp.co.soramitsu.crowdloan.impl.presentation.main

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.account.api.presentation.actions.CopyCallback
import jp.co.soramitsu.feature_crowdloan_impl.R

typealias ShareCallback = (String) -> Unit

class CrowdloanReferralActionsSheet(
    context: Context,
    private val code: String,
    val onCopy: CopyCallback,
    val onShare: ShareCallback
) : FixedListBottomSheet(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(code)

        item(R.drawable.ic_copy_24, R.string.copy_referral_code) {
            onCopy(code)
        }

        item(R.drawable.ic_share_arrow_white_24, R.string.share_referral_code) {
            onShare(code)
        }
    }
}
