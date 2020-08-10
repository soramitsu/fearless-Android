package jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.model.CryptoTypeModel
import kotlinx.android.synthetic.main.item_encryption_type.view.encryptionTv
import kotlinx.android.synthetic.main.item_encryption_type.view.rightIcon

class EncryptionTypeListAdapter(
    private val itemClickListener: (CryptoType) -> Unit
) : ListAdapter<CryptoTypeModel, EncryptionTypeViewHolder>(DiffCallback2) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): EncryptionTypeViewHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_encryption_type, viewGroup, false)
        return EncryptionTypeViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: EncryptionTypeViewHolder, position: Int) {
        viewHolder.bind(getItem(position), itemClickListener)
    }
}

class EncryptionTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(encryptionTypeModel: CryptoTypeModel, itemClickListener: (CryptoType) -> Unit) {
        with(itemView) {
            if (encryptionTypeModel.isSelected) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            encryptionTv.text = encryptionTypeModel.name

            setOnClickListener {
                itemClickListener(encryptionTypeModel.cryptoType)
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