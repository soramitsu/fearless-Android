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
import kotlinx.android.synthetic.main.item_contact_account.view.itemContactAccountAddress
import kotlinx.android.synthetic.main.item_contact_account.view.itemContactAccountIcon
import kotlinx.android.synthetic.main.item_contact_account.view.itemContactAccountName
import kotlinx.android.synthetic.main.item_contact_address.view.itemContactAddressIcon
import kotlinx.android.synthetic.main.item_contact_address.view.itemContactAddressName
import kotlinx.android.synthetic.main.item_contact_group.view.contactGroupTitle

class ChooseRecipientAdapter(
    private val itemHandler: RecipientItemHandler
) : GroupedListAdapter<ContactsHeader, AddressModel>(RecipientsDiffCallback) {

    interface RecipientItemHandler {

        fun contactClicked(address: String)
    }

    override fun getChildItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item is AddressModel) {
            if (item.name == null) R.layout.item_contact_address else R.layout.item_contact_account
        } else {
            super.getChildItemViewType(position)
        }
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return RecipientGroupHolder(parent.inflateChild(R.layout.item_contact_group))
    }

    override fun createChildViewHolder(parent: ViewGroup, viewType: Int): GroupedListHolder {
        return when (viewType) {
            R.layout.item_contact_account -> RecipientHolder.MyAccountViewHolder(parent.inflateChild(R.layout.item_contact_account))
            R.layout.item_contact_address -> RecipientHolder.AddressViewHolder(parent.inflateChild(R.layout.item_contact_address))
            else -> super.createViewHolder(parent, viewType)
        }
    }

    override fun bindGroup(holder: GroupedListHolder, group: ContactsHeader) {
        (holder as RecipientGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: AddressModel) {
        when (holder) {
            is RecipientHolder.MyAccountViewHolder -> holder.bind(child, itemHandler)
            is RecipientHolder.AddressViewHolder -> holder.bind(child, itemHandler)
        }
    }
}

class RecipientGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(header: ContactsHeader) = with(containerView) {
        contactGroupTitle.text = header.title
    }
}

sealed class RecipientHolder(view: View) : GroupedListHolder(view) {

    class MyAccountViewHolder(view: View) : RecipientHolder(view) {
        fun bind(
            addressModel: AddressModel,
            handler: ChooseRecipientAdapter.RecipientItemHandler
        ) {
            with(containerView) {
                itemContactAccountName.text = addressModel.name
                itemContactAccountAddress.text = addressModel.address

                itemContactAccountIcon.setImageDrawable(addressModel.image)

                setOnClickListener { handler.contactClicked(addressModel.address) }
            }
        }
    }

    class AddressViewHolder(view: View) : RecipientHolder(view) {
        fun bind(
            addressModel: AddressModel,
            handler: ChooseRecipientAdapter.RecipientItemHandler
        ) {
            with(containerView) {
                itemContactAddressName.text = addressModel.address
                itemContactAddressIcon.setImageDrawable(addressModel.image)

                setOnClickListener { handler.contactClicked(addressModel.address) }
            }
        }
    }
}

private object RecipientsDiffCallback : BaseGroupedDiffCallback<ContactsHeader, AddressModel>(ContactsHeader::class.java) {
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