package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.rebond

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.feature_staking_impl.R

class ChooseRebondKindBottomSheet(
    context: Context,
    private val actionListener: (RebondKind) -> Unit,
    private val allowedRebondKinds: Set<RebondKind> = RebondKind.values().toSet()
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.wallet_balance_unbonding_v1_9_0)

        allowedRebondKinds.forEach {
            when (it) {
                RebondKind.ALL -> item(R.drawable.ic_stacking_24, R.string.staking_rebond_all) {
                    actionListener(RebondKind.ALL)
                }
                RebondKind.LAST -> item(R.drawable.ic_stacking_24, R.string.staking_rebond_last) {
                    actionListener(RebondKind.LAST)
                }
                RebondKind.CUSTOM -> item(R.drawable.ic_stacking_24, R.string.staking_rebond) {
                    actionListener(RebondKind.CUSTOM)
                }
            }
        }
    }
}
