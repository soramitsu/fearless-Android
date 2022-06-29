package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.ClickHandler
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListSheetAdapter
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.HolderCreator
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel

class EncryptionTypeChooserBottomSheetDialog(
    context: Context,
    payload: Payload<CryptoTypeModel>,
    onClicked: ClickHandler<CryptoTypeModel>
) : DynamicListBottomSheet<CryptoTypeModel>(context, payload, CryptoModelCallback, onClicked) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.common_crypto_type)
    }

    override fun holderCreator(): HolderCreator<CryptoTypeModel> = {
        EncryptionTypeViewHolder(it.inflateChild(R.layout.item_encryption_type))
    }
}

class EncryptionTypeViewHolder(
    itemView: View
) : DynamicListSheetAdapter.Holder<CryptoTypeModel>(itemView) {

    override fun bind(item: CryptoTypeModel, isSelected: Boolean, handler: DynamicListSheetAdapter.Handler<CryptoTypeModel>) {
        super.bind(item, isSelected, handler)

        with(itemView) {
            if (isSelected) {
                findViewById<ImageView>(R.id.rightIcon).makeVisible()
            } else {
                findViewById<ImageView>(R.id.rightIcon).makeInvisible()
            }

            findViewById<TextView>(R.id.encryptionTv).text = item.name
        }
    }
}

private object CryptoModelCallback : DiffUtil.ItemCallback<CryptoTypeModel>() {
    override fun areItemsTheSame(oldItem: CryptoTypeModel, newItem: CryptoTypeModel): Boolean {
        return oldItem.cryptoType == newItem.cryptoType
    }

    override fun areContentsTheSame(oldItem: CryptoTypeModel, newItem: CryptoTypeModel): Boolean {
        return oldItem == newItem
    }
}
