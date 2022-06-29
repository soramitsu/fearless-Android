package jp.co.soramitsu.feature_staking_impl.presentation.validators.current

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorStatusModel

class CurrentValidatorsAdapter(
    private val handler: Handler,
) : GroupedListAdapter<NominatedValidatorStatusModel, NominatedValidatorModel>(CurrentValidatorsDiffCallback) {

    interface Handler {

        fun infoClicked(validatorModel: NominatedValidatorModel)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return CurrentValidatorsGroupHolder(parent.inflateChild(R.layout.item_current_validator_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return CurrentValidatorsChildHolder(parent.inflateChild(R.layout.item_current_validator))
    }

    override fun bindGroup(holder: GroupedListHolder, group: NominatedValidatorStatusModel) {
        (holder as CurrentValidatorsGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: NominatedValidatorModel) {
        (holder as CurrentValidatorsChildHolder).bind(child, handler)
    }
}

private class CurrentValidatorsGroupHolder(view: View) : GroupedListHolder(view) {

    fun bind(group: NominatedValidatorStatusModel) = with(containerView) {
        findViewById<TextView>(R.id.itemCurrentValidatorGroupStatus).setTextOrHide(group.titleConfig?.text)

        group.titleConfig?.let {
            findViewById<TextView>(R.id.itemCurrentValidatorGroupStatus).setTextColorRes(it.colorRes)

            findViewById<TextView>(R.id.itemCurrentValidatorGroupStatus).setCompoundDrawableTint(it.colorRes)
        }

        findViewById<TextView>(R.id.itemCurrentValidatorGroupDescription).text = group.description
    }
}

private class CurrentValidatorsChildHolder(view: View) : GroupedListHolder(view) {

    fun bind(child: NominatedValidatorModel, handler: CurrentValidatorsAdapter.Handler) = with(containerView) {
        findViewById<ImageView>(R.id.itemCurrentValidatorIcon).setImageDrawable(child.addressModel.image)
        findViewById<TextView>(R.id.itemCurrentValidatorName).text = child.addressModel.nameOrAddress
        findViewById<TextView>(R.id.itemCurrentValidatorNominated).setTextOrHide(child.nominated)

        findViewById<ImageView>(R.id.itemCurrentValidatorInfo).setOnClickListener { handler.infoClicked(child) }

        findViewById<ImageView>(R.id.itemCurrentValidatorBadge).setVisible(child.isOversubscribed)
        findViewById<ImageView>(R.id.currentValidatorSlashedIcon).setVisible(child.isSlashed)
    }
}

private object CurrentValidatorsDiffCallback :
    BaseGroupedDiffCallback<NominatedValidatorStatusModel, NominatedValidatorModel>(NominatedValidatorStatusModel::class.java) {

    override fun areGroupItemsTheSame(oldItem: NominatedValidatorStatusModel, newItem: NominatedValidatorStatusModel): Boolean {
        return oldItem == newItem
    }

    override fun areGroupContentsTheSame(oldItem: NominatedValidatorStatusModel, newItem: NominatedValidatorStatusModel): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: NominatedValidatorModel, newItem: NominatedValidatorModel): Boolean {
        return oldItem.addressModel.address == newItem.addressModel.address
    }

    override fun areChildContentsTheSame(oldItem: NominatedValidatorModel, newItem: NominatedValidatorModel): Boolean {
        return oldItem.nominated == newItem.nominated
    }
}
