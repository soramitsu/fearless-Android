package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import android.content.Context
import android.os.Bundle
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view.ValidatorInfoItemView

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

        item(ValidatorInfoItemView(context)) {
            it.setTitle(payload.ownStakeTitle)
            it.setBody(payload.ownStake)
            showTextOrHideExtra(it, payload.ownStakeFiat)
        }

        item(ValidatorInfoItemView(context)) {
            it.setTitle(payload.nominatorsTitle)
            it.setBody(payload.nominatorsStake)
            showTextOrHideExtra(it, payload.nominatorsStakeFiat)
        }

        item(ValidatorInfoItemView(context)) {
            it.setTitle(payload.totalStakeTitle)
            it.setBody(payload.totalStake)
            showTextOrHideExtra(it, payload.totalStakeFiat)
        }
    }

    private fun showTextOrHideExtra(view: ValidatorInfoItemView, text: String?) {
        if (text == null) {
            view.hideExtra()
        } else {
            view.setExtraOrHide(text)
            view.showExtra()
        }
    }
}
