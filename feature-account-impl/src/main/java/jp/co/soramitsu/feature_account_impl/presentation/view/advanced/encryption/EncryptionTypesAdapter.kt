package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeListAdapter.EncryptionItemHandler
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import kotlinx.android.synthetic.main.item_encryption_type.view.encryptionTv
import kotlinx.android.synthetic.main.item_encryption_type.view.rightIcon

class EncryptionTypeListAdapter(
    private val handler: EncryptionItemHandler,
    private val selectedType: CryptoTypeModel
) : ListAdapter<CryptoTypeModel, EncryptionTypeViewHolder>(DiffCallback2) {

    interface EncryptionItemHandler {
        fun encryptionClicked(type: CryptoTypeModel)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): EncryptionTypeViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_encryption_type, viewGroup, false)
        return EncryptionTypeViewHolder(view, selectedType)
    }

    override fun onBindViewHolder(viewHolder: EncryptionTypeViewHolder, position: Int) {
        viewHolder.bind(getItem(position), handler)
    }
}

class EncryptionTypeViewHolder(
    itemView: View,
    private val selectedType: CryptoTypeModel
) : RecyclerView.ViewHolder(itemView) {

    fun bind(encryptionTypeModel: CryptoTypeModel, handler: EncryptionItemHandler) {
        with(itemView) {
            if (encryptionTypeModel == selectedType) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            encryptionTv.text = encryptionTypeModel.name

            setOnClickListener {
                handler.encryptionClicked(encryptionTypeModel)
            }
        }
    }
}

object DiffCallback2 : DiffUtil.ItemCallback<CryptoTypeModel>() {
    override fun areItemsTheSame(oldItem: CryptoTypeModel, newItem: CryptoTypeModel): Boolean {
        return oldItem.cryptoType == newItem.cryptoType
    }

    override fun areContentsTheSame(oldItem: CryptoTypeModel, newItem: CryptoTypeModel): Boolean {
        return oldItem == newItem
    }
}