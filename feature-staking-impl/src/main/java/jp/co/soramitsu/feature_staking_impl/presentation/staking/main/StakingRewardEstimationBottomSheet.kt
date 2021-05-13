package jp.co.soramitsu.feature_staking_impl.presentation.staking.main

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.RewardEstimation
import kotlinx.android.synthetic.main.item_sheet_staking_estimate_percentage.view.*

class StakingRewardEstimationBottomSheet(
    context: Context,
    private val payload: Payload,
) : FixedListBottomSheet(context) {
    class Payload(val apr: RewardEstimation, val apy: RewardEstimation)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.staking_reward_estimation_bottom_sheet_title)

        addItem(payload.apr.gain, R.string.staking_maximum_apy) // TODO here is the wrong mocked value
        addItem(payload.apy.gain, R.string.staking_average_apy)
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
