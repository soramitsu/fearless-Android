package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.item_sheet_staking_reward_estimation.view.*

class StakingRewardEstimationBottomSheet(
    context: Context,
    private val payload: Payload,
) : FixedListBottomSheet(context) {
    class Payload(val apr: String, val apy: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.staking_reward_info_title)

        addItem(payload.apr, R.string.staking_reward_info_max)
        addItem(payload.apy, R.string.staking_reward_info_avg)
    }

    private fun addItem(
        percentage: String,
        @StringRes titleRes: Int,
    ) {
        item(R.layout.item_sheet_staking_reward_estimation) {
            it.itemSheetStakingEstimateTitle.setText(titleRes)
            it.itemSheetStakingEstimateValue.text = percentage
        }
    }
}
