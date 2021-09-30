package jp.co.soramitsu.feature_account_impl.presentation.account.details

import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.common.list.headers.TextHeaderHolder
import jp.co.soramitsu.common.utils.castOrNull
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.item_chain_acount.view.chainAccountAccountAddress
import kotlinx.android.synthetic.main.item_chain_acount.view.chainAccountAccountIcon
import kotlinx.android.synthetic.main.item_chain_acount.view.chainAccountChainIcon
import kotlinx.android.synthetic.main.item_chain_acount.view.chainAccountChainName

class ChainAccountsAdapter(
    private val handler: Handler,
    private val imageLoader: ImageLoader
) : GroupedListAdapter<TextHeader, AccountInChainUi>(DiffCallback) {

    interface Handler {

        fun chainAccountClicked(item: AccountInChainUi)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return TextHeaderHolder(parent)
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return ChainAccountHolder(parent.inflateChild(R.layout.item_chain_acount))
    }

    override fun bindGroup(holder: GroupedListHolder, group: TextHeader) {
        holder.castOrNull<TextHeaderHolder>()?.bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: AccountInChainUi) {
        holder.castOrNull<ChainAccountHolder>()?.bind(child, handler, imageLoader)
    }

}

class ChainAccountHolder(view: View) : GroupedListHolder(view) {

    fun bind(
        item: AccountInChainUi,
        handler: ChainAccountsAdapter.Handler,
        imageLoader: ImageLoader
    ) = with(containerView) {
        chainAccountChainIcon.load(item.chainIcon, imageLoader)
        chainAccountChainName.text = item.chainName

        chainAccountAccountIcon.setImageDrawable(item.accountIcon)
        chainAccountAccountAddress.text = item.address

        setOnClickListener { handler.chainAccountClicked(item) }
    }
}

private object DiffCallback : BaseGroupedDiffCallback<TextHeader, AccountInChainUi>(TextHeader::class.java) {

    override fun areGroupItemsTheSame(oldItem: TextHeader, newItem: TextHeader): Boolean {
        return TextHeader.DIFF_CALLBACK.areItemsTheSame(oldItem, newItem)
    }

    override fun areGroupContentsTheSame(oldItem: TextHeader, newItem: TextHeader): Boolean {
        return TextHeader.DIFF_CALLBACK.areContentsTheSame(oldItem, newItem)
    }

    override fun areChildItemsTheSame(oldItem: AccountInChainUi, newItem: AccountInChainUi): Boolean {
        return oldItem.chainName == newItem.chainName
    }

    override fun areChildContentsTheSame(oldItem: AccountInChainUi, newItem: AccountInChainUi): Boolean {
        return oldItem.chainName == newItem.chainName
            && oldItem.chainIcon == newItem.chainIcon
            && oldItem.address == newItem.address
    }
}
