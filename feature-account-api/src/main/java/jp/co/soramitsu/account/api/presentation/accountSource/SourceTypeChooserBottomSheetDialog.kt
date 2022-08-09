package jp.co.soramitsu.account.api.presentation.accountSource

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.feature_account_api.R

class SourceTypeChooserBottomSheetDialog<T : AccountSource>(
    @StringRes private val titleRes: Int = R.string.recovery_source_type,
    context: Context,
    payload: Payload<T>,
    onClicked: ClickHandler<T>
) : DynamicListBottomSheet<T>(context, payload, AccountSourceDiffCallback<T>(), onClicked) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(titleRes)
    }

    override fun holderCreator(): HolderCreator<T> = {
        SourceTypeHolder(it.inflateChild(R.layout.item_source))
    }
}

class SourceTypeHolder<T : AccountSource>(itemView: View) : DynamicListSheetAdapter.Holder<T>(itemView) {

    override fun bind(item: T, isSelected: Boolean, handler: DynamicListSheetAdapter.Handler<T>) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            when {
                item.isExport -> {
                    findViewById<ImageView>(R.id.rightIcon).makeGone()
                    findViewById<ImageView>(R.id.rightArrow).makeVisible()
                }
                isSelected -> {
                    findViewById<ImageView>(R.id.rightIcon).makeVisible()
                    findViewById<ImageView>(R.id.rightArrow).makeGone()
                }
                else -> {
                    findViewById<ImageView>(R.id.rightIcon).makeInvisible()
                    findViewById<ImageView>(R.id.rightArrow).makeGone()
                }
            }

            findViewById<TextView>(R.id.sourceTv).setText(item.nameRes)
            findViewById<TextView>(R.id.sourceHintTv).setText(item.hintRes)
            findViewById<ImageView>(R.id.sourceIcon).setImageResource(item.iconRes)
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
