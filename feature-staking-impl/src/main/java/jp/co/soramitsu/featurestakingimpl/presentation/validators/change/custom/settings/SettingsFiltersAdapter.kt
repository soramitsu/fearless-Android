package jp.co.soramitsu.featurestakingimpl.presentation.validators.change.custom.settings

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_staking_impl.R

private object SettingsFilterDiffCallback : DiffUtil.ItemCallback<SettingsSchema.Filter>() {
    override fun areItemsTheSame(oldItem: SettingsSchema.Filter, newItem: SettingsSchema.Filter): Boolean {
        return oldItem.title == newItem.title && oldItem.checked == newItem.checked
    }

    override fun areContentsTheSame(oldItem: SettingsSchema.Filter, newItem: SettingsSchema.Filter): Boolean {
        return oldItem == newItem
    }
}

class SettingsFiltersAdapter(private val onCheckListener: (SettingsSchema.Filter) -> Unit) :
    ListAdapter<SettingsSchema.Filter, SettingsFilterViewHolder>(SettingsFilterDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsFilterViewHolder {
        return SettingsFilterViewHolder(parent.inflateChild(R.layout.item_settings_filter)) {
            val item = getItem(it)
            onCheckListener(item)
        }
    }

    override fun onBindViewHolder(holder: SettingsFilterViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
}

class SettingsFilterViewHolder(itemView: View, private val onCheckListener: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
    init {
        itemView.findViewById<SwitchMaterial>(R.id.settingsFilterItem).setOnClickListener { onCheckListener(adapterPosition) }
    }

    fun bind(item: SettingsSchema.Filter) {
        itemView.apply {
            findViewById<SwitchMaterial>(R.id.settingsFilterItem).setText(item.title)
            findViewById<SwitchMaterial>(R.id.settingsFilterItem).isChecked = item.checked
        }
    }
}
