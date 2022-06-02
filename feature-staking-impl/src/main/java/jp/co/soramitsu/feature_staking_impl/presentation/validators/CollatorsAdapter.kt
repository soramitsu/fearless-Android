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
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorActionIcon
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorIcon
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorInfo
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorName
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorScoringPrimary
import kotlinx.android.synthetic.main.item_validator.view.itemValidatorScoringSecondary

class CollatorsAdapter(
    private val itemHandler: ItemHandler,
    initialMode: Mode = Mode.VIEW
) : ListAdapter<CollatorModel, CollatorViewHolder>(CollatorDiffCallback) {

    private var mode = initialMode

    interface ItemHandler {

        fun collatorInfoClicked(collatorModel: CollatorModel)

        fun collatorClicked(collatorModel: CollatorModel) {
            // default empty
        }

        fun removeClicked(collatorModel: CollatorModel) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CollatorViewHolder {
        val view = parent.inflateChild(R.layout.item_validator)

        return CollatorViewHolder(view)
    }

    override fun onBindViewHolder(holder: CollatorViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler, mode)
    }

    override fun onBindViewHolder(holder: CollatorViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)

        resolvePayload(
            holder, position, payloads,
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

class CollatorViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    fun bind(
        collator: CollatorModel,
        itemHandler: CollatorsAdapter.ItemHandler,
        mode: CollatorsAdapter.Mode
    ) = with(containerView) {
        itemValidatorName.text = collator.title
        itemValidatorIcon.setImageDrawable(collator.image)

        itemValidatorInfo.setOnClickListener {
            itemHandler.collatorInfoClicked(collator)
        }

        setOnClickListener {
            itemHandler.collatorClicked(collator)
        }

        bindIcon(mode, collator, itemHandler)

        bindScoring(collator)
    }

    fun bindIcon(
        mode: CollatorsAdapter.Mode,
        collatorModel: CollatorModel,
        handler: CollatorsAdapter.ItemHandler
    ) = with(containerView) {
        when {
            mode == CollatorsAdapter.Mode.EDIT -> {
                itemValidatorActionIcon.setImageResource(R.drawable.ic_delete_symbol)
                itemValidatorActionIcon.makeVisible()

                itemValidatorActionIcon.setOnClickListener { handler.removeClicked(collatorModel) }
            }
            collatorModel.isChecked == null -> {
                itemValidatorActionIcon.makeGone()
            }
            else -> {
                itemValidatorActionIcon.setOnClickListener(null)

                itemValidatorActionIcon.setImageResource(R.drawable.ic_checkmark_white_24)
                itemValidatorActionIcon.setVisible(collatorModel.isChecked, falseState = View.INVISIBLE)
            }
        }
    }

    fun bindScoring(collatorModel: CollatorModel) = with(containerView) {
        when (val scoring = collatorModel.scoring) {
            null -> {
                itemValidatorScoringPrimary.makeGone()
                itemValidatorScoringSecondary.makeGone()
            }

            is CollatorModel.Scoring.OneField -> {
                itemValidatorScoringPrimary.makeVisible()
                itemValidatorScoringSecondary.makeGone()
                itemValidatorScoringPrimary.text = scoring.field
            }

            is CollatorModel.Scoring.TwoFields -> {
                itemValidatorScoringPrimary.makeVisible()
                itemValidatorScoringSecondary.makeVisible()
                itemValidatorScoringPrimary.text = scoring.primary
                itemValidatorScoringSecondary.text = scoring.secondary
            }
        }
    }
}

object CollatorDiffCallback : DiffUtil.ItemCallback<CollatorModel>() {

    override fun areItemsTheSame(oldItem: CollatorModel, newItem: CollatorModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(oldItem: CollatorModel, newItem: CollatorModel): Boolean {
        return oldItem.scoring == newItem.scoring && oldItem.title == newItem.title && oldItem.isChecked == newItem.isChecked
    }

    override fun getChangePayload(oldItem: CollatorModel, newItem: CollatorModel): Any? {
        return CollatorPayloadGenerator.diff(oldItem, newItem)
    }
}

private object CollatorPayloadGenerator : PayloadGenerator<CollatorModel>(
    CollatorModel::isChecked, CollatorModel::scoring
)