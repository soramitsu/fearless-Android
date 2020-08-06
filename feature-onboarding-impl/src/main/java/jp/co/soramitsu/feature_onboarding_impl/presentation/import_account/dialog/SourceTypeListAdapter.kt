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
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_onboarding_impl.R

class SourceTypeListAdapter(
    var selectedSourceTypeItem: SourceType,
    private val itemClickListener: (SourceType) -> Unit
) : ListAdapter<SourceType, SourceTypeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SourceTypeViewHolder {
        return SourceTypeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_chooser_list, viewGroup, false))
    }

    override fun onBindViewHolder(sourceTypeViewHolder: SourceTypeViewHolder, position: Int) {
        sourceTypeViewHolder.bind(getItem(position), selectedSourceTypeItem, itemClickListener)
    }
}

class SourceTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val sourceTypeText: TextView = itemView.findViewById(R.id.chooserText)
    private val selectedPinIcon: ImageView = itemView.findViewById(R.id.rightIcon)

    fun bind(sourceType: SourceType, selectedSourceType: SourceType, itemClickListener: (SourceType) -> Unit) {
        with(itemView) {
            if (sourceType == selectedSourceType) {
                selectedPinIcon.makeVisible()
            } else {
                selectedPinIcon.makeInvisible()
            }

            sourceTypeText.text = when(sourceType) {
                SourceType.MNEMONIC_PASSPHRASE -> "Mnemonic passphrase"
                SourceType.RAW_SEED -> "Raw seed"
                SourceType.KEYSTORE -> "Keystore"
            }

            setOnClickListener {
                itemClickListener(sourceType)
            }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<SourceType>() {
    override fun areItemsTheSame(oldItem: SourceType, newItem: SourceType): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: SourceType, newItem: SourceType): Boolean {
        return oldItem == newItem
    }
}