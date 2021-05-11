package jp.co.soramitsu.feature_staking_impl.presentation.validators.current

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorStatusModel
import kotlinx.android.synthetic.main.item_current_validator.view.itemCurrentValidatorIcon
import kotlinx.android.synthetic.main.item_current_validator.view.itemCurrentValidatorName
import kotlinx.android.synthetic.main.item_current_validator.view.itemCurrentValidatorNominated
import kotlinx.android.synthetic.main.item_current_validator_group.view.itemCurrentValidatorGroupDescription
import kotlinx.android.synthetic.main.item_current_validator_group.view.itemCurrentValidatorGroupStatus

class CurrentValidatorsAdapter : GroupedListAdapter<NominatedValidatorStatusModel, NominatedValidatorModel>(CurrentValidatorsDiffCallback) {

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
        (holder as CurrentValidatorsChildHolder).bind(child)
    }
}

private class CurrentValidatorsGroupHolder(view: View) : GroupedListHolder(view) {

    fun bind(group: NominatedValidatorStatusModel) = with(containerView) {
        itemCurrentValidatorGroupStatus.setTextOrHide(group.titleConfig?.text)

        group.titleConfig?.let {
            itemCurrentValidatorGroupStatus.setTextColorRes(it.colorRes)

            itemCurrentValidatorGroupStatus.setCompoundDrawableTint(it.colorRes)
        }

        itemCurrentValidatorGroupDescription.text = group.description
    }
}

private class CurrentValidatorsChildHolder(view: View) : GroupedListHolder(view) {

    fun bind(child: NominatedValidatorModel) = with(containerView) {
        itemCurrentValidatorIcon.setImageDrawable(child.addressModel.image)
        itemCurrentValidatorName.text = child.addressModel.nameOrAddress
        itemCurrentValidatorNominated.setTextOrHide(child.nominated)
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
