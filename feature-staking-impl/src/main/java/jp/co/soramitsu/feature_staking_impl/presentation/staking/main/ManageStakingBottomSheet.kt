package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.item_sheet_staking_action.view.itemSheetStakingActionImage
import kotlinx.android.synthetic.main.item_sheet_staking_action.view.itemSheetStakingActionText

class ManageStakingBottomSheet(
    context: Context,
    private val payload: Payload,
    private val onItemChosen: (ManageStakeAction) -> Unit,
) : FixedListBottomSheet(context) {

    class Payload(val availableActions: Set<ManageStakeAction>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.staking_manage)

        manageItem(R.drawable.ic_basic_layers_24, R.string.staking_balance, ManageStakeAction.BALANCE)
//        manageItem(R.drawable.ic_stop_circle_24, R.string.staking_pause_staking)
//        manageItem(R.drawable.ic_send, R.string.staking_unstake)
//        manageItem(R.drawable.ic_dotted_list_24, R.string.staking_unstaking_requests)
        manageItem(R.drawable.ic_basic_circle_star_24, R.string.staking_reward_payouts, ManageStakeAction.PAYOUTS)
//        manageItem(R.drawable.ic_finance_wallet_24, R.string.staking_confirm_reward_destination)
        manageItem(R.drawable.ic_maps_compas_24, R.string.staking_your_validators, ManageStakeAction.VALIDATORS)

        manageItem(R.drawable.ic_profile_24, R.string.staking_controller_account, ManageStakeAction.CONTROLLER)
    }

    private inline fun manageItem(
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        action: ManageStakeAction = ManageStakeAction.STUB,
        crossinline extraBuilder: (View) -> Unit = {},
    ) {
        if (action in payload.availableActions) {
            item(R.layout.item_sheet_staking_action) {
                it.itemSheetStakingActionImage.setImageResource(iconRes)
                it.itemSheetStakingActionText.setText(titleRes)

                extraBuilder(it)

                it.setDismissingClickListener {
                    onItemChosen(action)
                }
            }
        }
    }
}
