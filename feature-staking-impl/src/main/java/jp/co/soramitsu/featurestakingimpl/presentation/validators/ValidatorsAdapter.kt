package jp.co.soramitsu.featurestakingimpl.presentation.validators

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import jp.co.soramitsu.featurestakingimpl.presentation.validators.change.ValidatorModel

class ValidatorsAdapter(
    private val itemHandler: ItemHandler,
    initialMode: Mode = Mode.VIEW
) : ListAdapter<ValidatorModel, ValidatorViewHolder>(ValidatorDiffCallback) {

    private var mode = initialMode

    interface ItemHandler {

        fun validatorInfoClicked(validatorModel: ValidatorModel)

        fun validatorClicked(validatorModel: ValidatorModel) {
            // default empty
        }

        fun removeClicked(validatorModel: ValidatorModel) {
            // default empty
        }
    }

    enum class Mode {
        VIEW, EDIT
    }

    fun modeChanged(newMode: Mode) {
        mode = newMode

        notifyItemRangeChanged(0, itemCount, mode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValidatorViewHolder {
        val view = parent.inflateChild(R.layout.item_validator)

        return ValidatorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ValidatorViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler, mode)
    }

    override fun onBindViewHolder(holder: ValidatorViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)

        resolvePayload(
            holder,
            position,
            payloads,
            onUnknownPayload = { holder.bindIcon(mode, item, itemHandler) },
            onDiffCheck = {
                when (it) {
                    ValidatorModel::isChecked -> holder.bindIcon(mode, item, itemHandler)
                    ValidatorModel::scoring -> holder.bindScoring(item)
                }
            }
        )
    }
}

class ValidatorViewHolder(private val containerView: View) : RecyclerView.ViewHolder(containerView) {

    fun bind(
        validator: ValidatorModel,
        itemHandler: ValidatorsAdapter.ItemHandler,
        mode: ValidatorsAdapter.Mode
    ) = with(containerView) {
        findViewById<TextView>(R.id.itemValidatorName).text = validator.title
        findViewById<ImageView>(R.id.itemValidatorIcon).setImageDrawable(validator.image)

        findViewById<ImageView>(R.id.itemValidatorInfo).setOnClickListener {
            itemHandler.validatorInfoClicked(validator)
        }

        setOnClickListener {
            itemHandler.validatorClicked(validator)
        }

        bindIcon(mode, validator, itemHandler)

        bindScoring(validator)
    }

    fun bindIcon(
        mode: ValidatorsAdapter.Mode,
        validatorModel: ValidatorModel,
        handler: ValidatorsAdapter.ItemHandler
    ) = with(containerView) {
        val icon = findViewById<ImageView>(R.id.itemValidatorActionIcon)

        when {
            mode == ValidatorsAdapter.Mode.EDIT -> {
                icon.setImageResource(R.drawable.ic_delete_symbol)
                icon.makeVisible()

                icon.setOnClickListener { handler.removeClicked(validatorModel) }
            }
            validatorModel.isChecked == null -> {
                icon.makeGone()
            }
            else -> {
                icon.setOnClickListener(null)

                icon.setImageResource(R.drawable.ic_checkmark_white_24)
                icon.setVisible(validatorModel.isChecked, falseState = View.INVISIBLE)
            }
        }
    }

    fun bindScoring(validatorModel: ValidatorModel) = with(containerView) {
        val scoringPrimary = findViewById<TextView>(R.id.itemValidatorScoringPrimary)
        val scoringSecondary = findViewById<TextView>(R.id.itemValidatorScoringSecondary)

        when (val scoring = validatorModel.scoring) {
            null -> {
                scoringPrimary.makeGone()
                scoringSecondary.makeGone()
            }

            is ValidatorModel.Scoring.OneField -> {
                scoringPrimary.makeVisible()
                scoringSecondary.makeGone()
                scoringPrimary.text = scoring.field
            }

            is ValidatorModel.Scoring.TwoFields -> {
                scoringPrimary.makeVisible()
                scoringSecondary.makeVisible()
                scoringPrimary.text = scoring.primary
                scoringSecondary.text = scoring.secondary
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
    ValidatorModel::isChecked,
    ValidatorModel::scoring
)
