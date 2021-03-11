package jp.co.soramitsu.feature_staking_impl.presentation.validators

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorDiffCallback
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorApy
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorIcon
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorInfo
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorName

class ValidatorsAdapter(
    private val itemHandler: ItemAssetHandler
) : ListAdapter<ValidatorModel, ValidatorViewHolder>(ValidatorDiffCallback) {

    interface ItemAssetHandler {
        fun validatorInfoClicked(validatorModel: ValidatorModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValidatorViewHolder {
        val view = parent.inflateChild(R.layout.item_validator)

        return ValidatorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ValidatorViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }
}

class ValidatorViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    fun bind(validator: ValidatorModel, itemHandler: ValidatorsAdapter.ItemAssetHandler) = with(containerView) {
        itemValidatorApy.text = validator.apy
        itemValidatorName.text = validator.title
        itemValidatorIcon.setImageDrawable(validator.image)

        itemValidatorInfo.setOnClickListener {
            itemHandler.validatorInfoClicked(validator)
        }
    }
}
