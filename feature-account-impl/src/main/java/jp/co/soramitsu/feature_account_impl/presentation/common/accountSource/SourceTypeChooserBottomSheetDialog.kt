package jp.co.soramitsu.feature_account_impl.presentation.common.accountSource

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.item_source.view.rightIcon
import kotlinx.android.synthetic.main.item_source.view.sourceTv

class SourceTypeChooserBottomSheetDialog<T : AccountSource>(
    context: Context,
    payload: Payload<T>,
    onClicked: ClickHandler<T>
) : DynamicListBottomSheet<T>(context, payload, AccountSourceDiffCallback<T>(), onClicked) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.recovery_source_type)
    }

    override fun holderCreator(): HolderCreator<T> = {
        SourceTypeHolder(it.inflateChild(R.layout.item_source))
    }
}

class SourceTypeHolder<T : AccountSource>(itemView: View) : DynamicListSheetAdapter.Holder<T>(itemView) {

    override fun bind(item: T, isSelected: Boolean, handler: DynamicListSheetAdapter.Handler<T>) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            if (isSelected) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            sourceTv.setText(item.nameRes)
        }
    }
}

private class AccountSourceDiffCallback<T : AccountSource> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem.nameRes == newItem.nameRes
    }

    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return true
    }
}