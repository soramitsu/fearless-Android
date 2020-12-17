package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactsHeader
import kotlinx.android.synthetic.main.item_contact.view.itemContactAddress
import kotlinx.android.synthetic.main.item_contact.view.itemContactIcon
import kotlinx.android.synthetic.main.item_contact_group.view.contactGroupTitle

class ChooseRecipientAdapter(
    private val nodeItemHandler: NodeItemHandler
) : GroupedListAdapter<ContactsHeader, AddressModel>(NodesDiffCallback) {

    interface NodeItemHandler {

        fun contactClicked(addressModel: AddressModel)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return RecipientGroupHolder(parent.inflateChild(R.layout.item_contact_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return RecipientHolder(parent.inflateChild(R.layout.item_contact))
    }

    override fun bindGroup(holder: GroupedListHolder, group: ContactsHeader) {
        (holder as RecipientGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: AddressModel) {
        (holder as RecipientHolder).bind(child, nodeItemHandler)
    }
}

class RecipientGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(header: ContactsHeader) = with(containerView) {
        contactGroupTitle.text = header.title
    }
}

class RecipientHolder(view: View) : GroupedListHolder(view) {

    fun bind(
        addressModel: AddressModel,
        handler: ChooseRecipientAdapter.NodeItemHandler
    ) {
        with(containerView) {
            itemContactAddress.text = addressModel.address
            itemContactIcon.setImageDrawable(addressModel.image)

            setOnClickListener { handler.contactClicked(addressModel) }
        }
    }
}

private object NodesDiffCallback : BaseGroupedDiffCallback<ContactsHeader, AddressModel>(ContactsHeader::class.java) {
    override fun areGroupItemsTheSame(oldItem: ContactsHeader, newItem: ContactsHeader): Boolean {
        return oldItem.title == newItem.title
    }

    override fun areGroupContentsTheSame(oldItem: ContactsHeader, newItem: ContactsHeader): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: AddressModel, newItem: AddressModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areChildContentsTheSame(oldItem: AddressModel, newItem: AddressModel): Boolean {
        return true
    }
}