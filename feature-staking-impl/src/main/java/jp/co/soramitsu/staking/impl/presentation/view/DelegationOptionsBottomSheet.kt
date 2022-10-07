package jp.co.soramitsu.staking.impl.presentation.view

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.item
import jp.co.soramitsu.staking.impl.presentation.staking.main.DelegatorViewState

class DelegationOptionsBottomSheet(
    context: Context,
    private val model: DelegatorViewState.CollatorDelegationModel,
    private val onStakingBalance: (DelegatorViewState.CollatorDelegationModel) -> Unit,
    private val onYourCollator: (DelegatorViewState.CollatorDelegationModel) -> Unit
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.staking_manage_title)

        item(R.drawable.ic_basic_layers_24, R.string.staking_balance_title) {
            onStakingBalance(model)
        }

        item(R.drawable.ic_security_shield_ok_24, R.string.your_collator) {
            onYourCollator(model)
        }
    }
}
