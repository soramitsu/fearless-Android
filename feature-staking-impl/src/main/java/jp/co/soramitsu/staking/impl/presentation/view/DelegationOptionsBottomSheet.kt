package jp.co.soramitsu.staking.impl.presentation.view

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item

class DelegationOptionsBottomSheet(
    context: Context,
    private val onStakingBalance: () -> Unit,
    private val onYourCollator: (() -> Unit)?
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.staking_manage_title)

        item(R.drawable.ic_basic_layers_24, R.string.staking_balance_title) {
            onStakingBalance()
        }

        onYourCollator?.let { action ->
            item(R.drawable.ic_security_shield_ok_24, R.string.your_collator) {
                action()
            }
        }
    }
}
