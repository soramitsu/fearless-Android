package jp.co.soramitsu.feature_staking_impl.presentation.validators

import android.content.Context
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_validator_total_stake_item.view.validatorTotalStakeItemAmount
import kotlinx.android.synthetic.main.view_validator_total_stake_item.view.validatorTotalStakeItemAmountFiat
import kotlinx.android.synthetic.main.view_validator_total_stake_item.view.validatorTotalStakeItemTitle

class ValidatorStakeBottomSheet(
    context: Context,
    private val payload: Payload
) : FixedListBottomSheet(context) {

    class Payload(
        val ownStake: String,
        val ownStakeFiat: String?,
        val nominatorsStake: String,
        val nominatorsStakeFiat: String?,
        val totalStake: String,
        val totalStakeFiat: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.staking_validator_total_stake)

        item(R.layout.view_validator_total_stake_item, ) {
            it.updatePadding(top = 8.dp(context), bottom = 8.dp(context), start = 16.dp(context), end = 16.dp(context))
            it.validatorTotalStakeItemTitle.text = "Own"
            it.validatorTotalStakeItemAmount.text = payload.ownStake
            if (payload.ownStakeFiat == null) {
                it.validatorTotalStakeItemAmountFiat.text = ""
                it.validatorTotalStakeItemAmountFiat.makeGone()
            } else {
                it.validatorTotalStakeItemAmountFiat.text = payload.ownStakeFiat
                it.validatorTotalStakeItemAmountFiat.makeVisible()
            }
        }

        item(R.layout.view_validator_total_stake_item) {
            it.updatePadding(top = 8.dp(context), bottom = 8.dp(context), start = 16.dp(context), end = 16.dp(context))
            it.validatorTotalStakeItemTitle.text = "Nominators"
            it.validatorTotalStakeItemAmount.text = payload.nominatorsStake
            if (payload.nominatorsStakeFiat == null) {
                it.validatorTotalStakeItemAmountFiat.text = ""
                it.validatorTotalStakeItemAmountFiat.makeGone()
            } else {
                it.validatorTotalStakeItemAmountFiat.text = payload.nominatorsStakeFiat
                it.validatorTotalStakeItemAmountFiat.makeVisible()
            }
        }

        item(R.layout.view_validator_total_stake_item) {
            it.updatePadding(top = 8.dp(context), bottom = 8.dp(context), start = 16.dp(context), end = 16.dp(context))
            it.validatorTotalStakeItemTitle.text = "Total"
            it.validatorTotalStakeItemAmount.text = payload.totalStake
            if (payload.totalStakeFiat == null) {
                it.validatorTotalStakeItemAmountFiat.text = ""
                it.validatorTotalStakeItemAmountFiat.makeGone()
            } else {
                it.validatorTotalStakeItemAmountFiat.text = payload.totalStakeFiat
                it.validatorTotalStakeItemAmountFiat.makeVisible()
            }
        }
    }
}
