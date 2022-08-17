package jp.co.soramitsu.featurewalletimpl.presentation.send.recipient

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.featurewalletimpl.presentation.send.recipient.model.ContactsHeader

class ChooseRecipientAdapter(
    private val itemHandler: RecipientItemHandler
) : GroupedListAdapter<ContactsHeader, AddressModel>(RecipientsDiffCallback) {

    interface RecipientItemHandler {

        fun contactClicked(address: String)
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
        (holder as RecipientHolder).bind(child, itemHandler)
    }
}

class RecipientGroupHolder(view: View) : GroupedListHolder(view) {
    fun bind(header: ContactsHeader) = with(containerView) {
        findViewById<TextView>(R.id.contactGroupTitle).text = header.title
    }
}

class RecipientHolder(view: View) : GroupedListHolder(view) {
    fun bind(
        addressModel: AddressModel,
        handler: ChooseRecipientAdapter.RecipientItemHandler
    ) {
        with(containerView) {
            if (addressModel.name == null) {
                findViewById<TextView>(R.id.itemContactTitle).text = addressModel.address
                findViewById<TextView>(R.id.itemContactBody).text = ""
                findViewById<TextView>(R.id.itemContactBody).makeGone()
            } else {
                findViewById<TextView>(R.id.itemContactTitle).text = addressModel.name
                findViewById<TextView>(R.id.itemContactBody).text = addressModel.address
                findViewById<TextView>(R.id.itemContactBody).makeVisible()
            }
            findViewById<ImageView>(R.id.itemContactIcon).setImageDrawable(addressModel.image)

            setOnClickListener { handler.contactClicked(addressModel.address) }
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
