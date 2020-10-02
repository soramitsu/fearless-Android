package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.groupedList.BaseGroupedDiffCallback
import jp.co.soramitsu.common.groupedList.GroupedListAdapter
import jp.co.soramitsu.common.groupedList.GroupedListHolder
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactModel
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.model.ContactsHeader
import kotlinx.android.synthetic.main.item_contact.view.itemContactAddress
import kotlinx.android.synthetic.main.item_contact.view.itemContactIcon
import kotlinx.android.synthetic.main.item_contact_group.view.contactGroupTitle

class ChooseRecipientAdapter(
    private val nodeItemHandler: NodeItemHandler
) : GroupedListAdapter<ContactsHeader, ContactModel>(NodesDiffCallback) {

    interface NodeItemHandler {

        fun contactClicked(contactModel: ContactModel)
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

    override fun bindChild(holder: GroupedListHolder, child: ContactModel) {
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
        contactModel: ContactModel,
        handler: ChooseRecipientAdapter.NodeItemHandler
    ) {
        with(containerView) {
            itemContactAddress.text = contactModel.address
            itemContactIcon.setImageDrawable(contactModel.image)

            setOnClickListener { handler.contactClicked(contactModel) }
        }
    }
}

private object NodesDiffCallback : BaseGroupedDiffCallback<ContactsHeader, ContactModel>(ContactsHeader::class.java) {
    override fun areGroupItemsTheSame(oldItem: ContactsHeader, newItem: ContactsHeader): Boolean {
        return oldItem.title == newItem.title
    }

    override fun areGroupContentsTheSame(oldItem: ContactsHeader, newItem: ContactsHeader): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: ContactModel, newItem: ContactModel): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areChildContentsTheSame(oldItem: ContactModel, newItem: ContactModel): Boolean {
       return true
    }
}