package jp.co.soramitsu.feature_staking_impl.presentation.validators

import android.content.Context
import android.os.Bundle
import android.widget.TextView
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
        val ownStakeTitle: String,
        val ownStake: String,
        val ownStakeFiat: String?,
        val nominatorsTitle: String,
        val nominatorsStake: String,
        val nominatorsStakeFiat: String?,
        val totalStakeTitle: String,
        val totalStake: String,
        val totalStakeFiat: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.staking_validator_total_stake)

        item(R.layout.view_validator_total_stake_item) {
            it.updatePadding(top = 8.dp(context), bottom = 8.dp(context), start = 16.dp(context), end = 16.dp(context))
            it.validatorTotalStakeItemTitle.text = payload.ownStakeTitle
            it.validatorTotalStakeItemAmount.text = payload.ownStake
            showTextOrHide(it.validatorTotalStakeItemAmountFiat, payload.ownStakeFiat)
        }

        item(R.layout.view_validator_total_stake_item) {
            it.updatePadding(top = 8.dp(context), bottom = 8.dp(context), start = 16.dp(context), end = 16.dp(context))
            it.validatorTotalStakeItemTitle.text = payload.nominatorsTitle
            it.validatorTotalStakeItemAmount.text = payload.nominatorsStake
            showTextOrHide(it.validatorTotalStakeItemAmountFiat, payload.nominatorsStakeFiat)
        }

        item(R.layout.view_validator_total_stake_item) {
            it.updatePadding(top = 8.dp(context), bottom = 8.dp(context), start = 16.dp(context), end = 16.dp(context))
            it.validatorTotalStakeItemTitle.text = payload.totalStakeTitle
            it.validatorTotalStakeItemAmount.text = payload.totalStake
            showTextOrHide(it.validatorTotalStakeItemAmountFiat, payload.totalStakeFiat)
        }
    }

    private fun showTextOrHide(view: TextView, text: String?) {
        if (text == null) {
            view.validatorTotalStakeItemAmountFiat.text = ""
            view.validatorTotalStakeItemAmountFiat.makeGone()
        } else {
            view.validatorTotalStakeItemAmountFiat.text = payload.totalStakeFiat
            view.validatorTotalStakeItemAmountFiat.makeVisible()
        }
    }
}
