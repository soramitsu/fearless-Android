package jp.co.soramitsu.account.impl.presentation.account.edit

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.account.impl.presentation.account.LightMetaAccountDiffCallback
import jp.co.soramitsu.account.impl.presentation.account.model.LightMetaAccountUi

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
        containerView.findViewById<ImageView>(R.id.accountDrag).setOnTouchListener { _, event ->
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
            findViewById<TextView>(R.id.accountTitle).text = accountModel.name
            findViewById<ImageView>(R.id.accountIcon).setImageDrawable(accountModel.picture.value)

            val iconRes = if (accountModel.isSelected) R.drawable.ic_checkmark_white_24 else R.drawable.ic_delete_symbol
            findViewById<ImageView>(R.id.accountDelete).setImageResource(iconRes)

            findViewById<ImageView>(R.id.accountDelete).setOnClickListener { handler.deleteClicked(accountModel) }
        }
    }
}
