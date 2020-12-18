package jp.co.soramitsu.feature_account_impl.presentation.account.edit

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountGroupHolder
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import jp.co.soramitsu.feature_account_impl.presentation.account.AccountsDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListing
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.item_edit_account.view.accountAddress
import kotlinx.android.synthetic.main.item_edit_account.view.accountDelete
import kotlinx.android.synthetic.main.item_edit_account.view.accountDrag
import kotlinx.android.synthetic.main.item_edit_account.view.accountIcon
import kotlinx.android.synthetic.main.item_edit_account.view.accountTitle

class EditAccountsAdapter(
    private val accountItemHandler: EditAccountItemHandler,
    private val dragHelper: ItemTouchHelper
) : GroupedListAdapter<NetworkModel, AccountModel>(AccountsDiffCallback) {
    private var selectedItem: AccountModel? = null

    interface EditAccountItemHandler {
        fun deleteClicked(accountModel: AccountModel)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return AccountGroupHolder(parent)
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        val view = parent.inflateChild(R.layout.item_edit_account)

        return EditAccountHolder(view, dragHelper)
    }

    override fun bindGroup(holder: GroupedListHolder, group: NetworkModel) {
        (holder as AccountGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: AccountModel) {
        val isChecked = child.address == selectedItem!!.address

        (holder as EditAccountHolder).bind(child, accountItemHandler, isChecked)
    }

    fun unsyncedSwap(payload: UnsyncedSwapPayload) {
        submitList(payload.newState)
    }

    fun submitListing(accountListing: AccountListing) {
        selectedItem = accountListing.selectedAccount

        submitList(accountListing.groupedAccounts)
    }
}

@SuppressLint("ClickableViewAccessibility")
class EditAccountHolder(view: View, dragHelper: ItemTouchHelper) : GroupedListHolder(view) {
    init {
        containerView.accountDrag.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                dragHelper.startDrag(this)
            }

            false
        }
    }

    fun bind(
        accountModel: AccountModel,
        handler: EditAccountsAdapter.EditAccountItemHandler,
        isChecked: Boolean
    ) {
        with(containerView) {
            accountTitle.text = accountModel.name ?: ""
            accountAddress.text = accountModel.address
            accountIcon.setImageDrawable(accountModel.image)

            val iconRes = if (isChecked) R.drawable.ic_checkmark_white_24 else R.drawable.ic_delete_symbol
            accountDelete.setImageResource(iconRes)

            accountDelete.setOnClickListener { handler.deleteClicked(accountModel) }
        }
    }
}