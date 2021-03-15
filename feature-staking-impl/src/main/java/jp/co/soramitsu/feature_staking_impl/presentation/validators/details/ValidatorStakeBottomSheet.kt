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

        val nominatorsStakeItem = ValidatorInfoItemView(context).apply {
            setTitle(payload.nominatorsTitle)
            setBody(payload.nominatorsStake)
            showTextOrHideExtra(this, payload.nominatorsStakeFiat)
        }

        val totalStakeItem = ValidatorInfoItemView(context).apply {
            setTitle(payload.totalStakeTitle)
            setBody(payload.totalStake)
            showTextOrHideExtra(this, payload.totalStakeFiat)
        }

        item(ValidatorInfoItemView(context)) {
            it.setTitle(payload.ownStakeTitle)
            it.setBody(payload.ownStake)
            showTextOrHideExtra(it, payload.ownStakeFiat)
        }


//        item(nominatorsStakeItem)
//        item(totalStakeItem)
    }

    private fun showTextOrHideExtra(view: ValidatorInfoItemView, text: String?) {
        if (text == null) {
            view.hideExtra()
        } else {
            view.setExtra(text)
            view.showExtra()
        }
    }
}
