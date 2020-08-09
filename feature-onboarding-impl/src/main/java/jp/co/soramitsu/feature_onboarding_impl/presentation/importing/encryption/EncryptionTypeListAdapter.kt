package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.encryption

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.encryption.model.CryptoTypeModel

class EncryptionTypeListAdapter(
    private val itemClickListener: (CryptoType) -> Unit
) : ListAdapter<CryptoTypeModel, EncryptionTypeViewHolder>(DiffCallback2) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): EncryptionTypeViewHolder {
        return EncryptionTypeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_chooser_list, viewGroup, false))
    }

    override fun onBindViewHolder(encryptionTypeViewHolder: EncryptionTypeViewHolder, position: Int) {
        encryptionTypeViewHolder.bind(getItem(position), itemClickListener)
    }
}

class EncryptionTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val encryptionTypeText: TextView = itemView.findViewById(R.id.chooserText)
    private val selectedPinIcon: ImageView = itemView.findViewById(R.id.rightIcon)

    fun bind(encryptionTypeModel: CryptoTypeModel, itemClickListener: (CryptoType) -> Unit) {
        with(itemView) {
            if (encryptionTypeModel.isSelected) {
                selectedPinIcon.makeVisible()
            } else {
                selectedPinIcon.makeInvisible()
            }

            encryptionTypeText.text = encryptionTypeModel.name

            setOnClickListener {
                itemClickListener(encryptionTypeModel.cryptoType)
            }
        }
    }
}

object DiffCallback2 : DiffUtil.ItemCallback<CryptoTypeModel>() {
    override fun areItemsTheSame(oldItem: CryptoTypeModel, newItem: CryptoTypeModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: CryptoTypeModel, newItem: CryptoTypeModel): Boolean {
        return oldItem == newItem
    }
}