package jp.co.soramitsu.feature_account_impl.presentation.account.edit

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.account.LightMetaAccountDiffCallback
import jp.co.soramitsu.feature_account_impl.presentation.account.model.LightMetaAccountUi
import kotlinx.android.synthetic.main.item_edit_account.view.accountDelete
import kotlinx.android.synthetic.main.item_edit_account.view.accountDrag
import kotlinx.android.synthetic.main.item_edit_account.view.accountIcon
import kotlinx.android.synthetic.main.item_edit_account.view.accountTitle

class EditAccountsAdapter(
    private val accountItemHandler: EditAccountItemHandler,
    private val dragHelper: ItemTouchHelper
) : ListAdapter<LightMetaAccountUi, EditAccountHolder>(LightMetaAccountDiffCallback) {

    interface EditAccountItemHandler {

        fun deleteClicked(accountModel: LightMetaAccountUi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditAccountHolder {
        return EditAccountHolder(parent.inflateChild(R.layout.item_edit_account), dragHelper)
    }

    override fun onBindViewHolder(holder: EditAccountHolder, position: Int) {
        holder.bind(getItem(position), accountItemHandler)
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
        accountModel: LightMetaAccountUi,
        handler: EditAccountsAdapter.EditAccountItemHandler
    ) {
        with(containerView) {
            accountTitle.text = accountModel.name
            accountIcon.setImageDrawable(accountModel.picture.value)

            val iconRes = if (accountModel.isSelected) R.drawable.ic_checkmark_white_24 else R.drawable.ic_delete_symbol
            accountDelete.setImageResource(iconRes)

            accountDelete.setOnClickListener { handler.deleteClicked(accountModel) }
        }
    }
}
