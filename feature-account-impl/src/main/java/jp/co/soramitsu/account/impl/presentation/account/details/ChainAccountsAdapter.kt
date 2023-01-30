package jp.co.soramitsu.account.impl.presentation.account.details

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.isVisible
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.common.list.headers.TextHeaderHolder
import jp.co.soramitsu.common.utils.castOrNull
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.view.shape.getCutLeftBottomCornerDrawableFromColors
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain

class ChainAccountsAdapter(
    private val handler: Handler,
    private val imageLoader: ImageLoader
) : GroupedListAdapter<TextHeader, AccountInChainUi>(DiffCallback) {

    interface Handler {

        fun chainAccountClicked(item: AccountInChainUi)
        fun chainAccountOptionsClicked(item: AccountInChainUi)
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
        val interactionAllowed = item.enabled && item.isSupported

        findViewById<ImageView>(R.id.chainAccountChainIcon).load(item.chainIcon, imageLoader)
        findViewById<TextView>(R.id.chainAccountChainName).text = item.chainName

        findViewById<TextView>(R.id.chainAccountAccountAddress).text = if (item.isSupported) item.address else resources.getString(R.string.common_unsupported)

        findViewById<ImageView>(R.id.labeledTextAction).isVisible = interactionAllowed
        if (interactionAllowed) {
            findViewById<ImageView>(R.id.labeledTextAction).setOnClickListener {
                handler.chainAccountOptionsClicked(item)
            }

            setOnClickListener { handler.chainAccountClicked(item) }
        }

        findViewById<TextView>(R.id.chainAccountNameBadge).apply {
            isVisible = !item.accountName.isNullOrEmpty()
            text = item.accountName.orEmpty()
        }
        when (item.accountFrom) {
            AccountInChain.From.CHAIN_ACCOUNT -> context.getCutLeftBottomCornerDrawableFromColors()
            AccountInChain.From.META_ACCOUNT -> context.getCutLeftBottomCornerDrawableFromColors(context.getColor(R.color.white_50))
            else -> null
        }?.let(findViewById<TextView>(R.id.chainAccountNameBadge)::setBackground)

        if (item.isSupported) {
            findViewById<ImageView>(R.id.chainAccountAccountIcon).isVisible = item.accountIcon != null
            findViewById<ImageView>(R.id.chainAccountAccountIcon).setImageDrawable(item.accountIcon)
        } else {
            (this as ViewGroup).children.forEach {
                it.alpha = 0.4f
            }
            setOnClickListener { handler.chainAccountClicked(item) }
            findViewById<ImageView>(R.id.chainAccountAccountIcon).setImageResource(R.drawable.ic_warning_filled)
        }

        val chainNameColorId = when {
            item.hasAccount -> R.color.white
            else -> R.color.black2
        }
        findViewById<TextView>(R.id.chainAccountChainName).setTextColor(context.getColor(chainNameColorId))
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
        return oldItem.chainName == newItem.chainName &&
            oldItem.chainIcon == newItem.chainIcon &&
            oldItem.address == newItem.address &&
            oldItem.markedAsNotNeed == newItem.markedAsNotNeed
    }
}
