package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog

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

class EncryptionTypeListAdapter(
    var selectedEncryptionType: CryptoType,
    private val itemClickListener: (CryptoType) -> Unit
) : ListAdapter<CryptoType, EncryptionTypeViewHolder>(DiffCallback2) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): EncryptionTypeViewHolder {
        return EncryptionTypeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_chooser_list, viewGroup, false))
    }

    override fun onBindViewHolder(encryptionTypeViewHolder: EncryptionTypeViewHolder, position: Int) {
        encryptionTypeViewHolder.bind(getItem(position), selectedEncryptionType, itemClickListener)
    }
}

class EncryptionTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val encryptionTypeText: TextView = itemView.findViewById(R.id.chooserText)
    private val selectedPinIcon: ImageView = itemView.findViewById(R.id.rightIcon)

    fun bind(encryptionType: CryptoType, selectedEncryptionType: CryptoType, itemClickListener: (CryptoType) -> Unit) {
        with(itemView) {
            if (encryptionType == selectedEncryptionType) {
                selectedPinIcon.makeVisible()
            } else {
                selectedPinIcon.makeInvisible()
            }

            encryptionTypeText.text = when (encryptionType) {
                CryptoType.SR25519 -> "Schnorrkel | sr25519 (recommended) "
                CryptoType.ED25519 -> "Edwards | ed25519 (alternative)"
                CryptoType.ECDSA -> "ECDSA | (BTC/ETH compatible)"
            }

            setOnClickListener {
                itemClickListener(encryptionType)
            }
        }
    }
}

object DiffCallback2 : DiffUtil.ItemCallback<CryptoType>() {
    override fun areItemsTheSame(oldItem: CryptoType, newItem: CryptoType): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: CryptoType, newItem: CryptoType): Boolean {
        return oldItem == newItem
    }
}