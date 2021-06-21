package jp.co.soramitsu.feature_staking_impl.presentation.validators

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.list.PayloadGenerator
import jp.co.soramitsu.common.list.resolvePayload
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended.model.ValidatorModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorCheck
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorIcon
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorInfo
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorName
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorScoring
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorScoringPrimary
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorScoringSecondary

class ValidatorsAdapter(
    private val itemHandler: ItemAssetHandler
) : ListAdapter<ValidatorModel, ValidatorViewHolder>(ValidatorDiffCallback) {

    interface ItemAssetHandler {

        fun validatorInfoClicked(validatorModel: ValidatorModel)

        fun validatorClicked(validatorModel: ValidatorModel) {
            // default empty
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValidatorViewHolder {
        val view = parent.inflateChild(R.layout.item_validator)

        return ValidatorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ValidatorViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }

    override fun onBindViewHolder(holder: ValidatorViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)

        resolvePayload(holder, position, payloads) {
            when (it) {
                ValidatorModel::isChecked -> holder.bindIsChecked(item)
                ValidatorModel::scoring -> holder.bindScoring(item)
            }
        }
    }
}

class ValidatorViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    fun bind(validator: ValidatorModel, itemHandler: ValidatorsAdapter.ItemAssetHandler) = with(containerView) {
        itemValidatorName.text = validator.title
        itemValidatorIcon.setImageDrawable(validator.image)

        itemValidatorInfo.setOnClickListener {
            itemHandler.validatorInfoClicked(validator)
        }

        setOnClickListener {
            itemHandler.validatorClicked(validator)
        }

        bindIsChecked(validator)

        bindScoring(validator)
    }

    fun bindIsChecked(validatorModel: ValidatorModel) = with(containerView) {
        when (val isChecked = validatorModel.isChecked) {
            null -> itemValidatorCheck.makeGone()
            else -> itemValidatorCheck.setVisible(isChecked, falseState = View.INVISIBLE)
        }
    }

    fun bindScoring(validatorModel: ValidatorModel) = with(containerView) {
        when (val scoring = validatorModel.scoring) {
            null -> itemValidatorScoring.makeGone()

            is ValidatorModel.Scoring.OneField -> {
                itemValidatorScoringPrimary.makeVisible()
                itemValidatorScoringSecondary.makeGone()
                itemValidatorScoringPrimary.text = scoring.field
            }

            is ValidatorModel.Scoring.TwoFields -> {
                itemValidatorScoringPrimary.makeVisible()
                itemValidatorScoringSecondary.makeVisible()
                itemValidatorScoringPrimary.text = scoring.primary
                itemValidatorScoringSecondary.text = scoring.secondary
            }
        }
    }
}

object ValidatorDiffCallback : DiffUtil.ItemCallback<ValidatorModel>() {

    override fun areItemsTheSame(oldItem: ValidatorModel, newItem: ValidatorModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(oldItem: ValidatorModel, newItem: ValidatorModel): Boolean {
        return oldItem.scoring == newItem.scoring && oldItem.title == newItem.title && oldItem.isChecked == newItem.isChecked
    }

    override fun getChangePayload(oldItem: ValidatorModel, newItem: ValidatorModel): Any? {
        return ValidatorPayloadGenerator.diff(oldItem, newItem)
    }
}

private object ValidatorPayloadGenerator : PayloadGenerator<ValidatorModel>(
    ValidatorModel::isChecked, ValidatorModel::scoring
)
