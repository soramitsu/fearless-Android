package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.item_settings_sorting.view.settingsSortingItem

private object SettingsSortingDiffCallback : DiffUtil.ItemCallback<SettingsSchema.Sorting>() {
    override fun areItemsTheSame(oldItem: SettingsSchema.Sorting, newItem: SettingsSchema.Sorting): Boolean {
        return oldItem.title == newItem.title && oldItem.checked == newItem.checked
    }

    override fun areContentsTheSame(oldItem: SettingsSchema.Sorting, newItem: SettingsSchema.Sorting): Boolean {
        return oldItem == newItem
    }
}

class SettingsSortingAdapter(private val onCheckListener: (SettingsSchema.Sorting) -> Unit) :
    ListAdapter<SettingsSchema.Sorting, SettingsSortingViewHolder>(SettingsSortingDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsSortingViewHolder {
        return SettingsSortingViewHolder(parent.inflateChild(R.layout.item_settings_sorting)) {
            val item = getItem(it)
            onCheckListener(item)
        }
    }

    override fun onBindViewHolder(holder: SettingsSortingViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
}

class SettingsSortingViewHolder(itemView: View, private val onCheckListener: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
    init {
        itemView.settingsSortingItem.setOnClickListener { onCheckListener(adapterPosition) }
    }

    fun bind(item: SettingsSchema.Sorting) {
        itemView.apply {
            settingsSortingItem.setText(item.title)
            settingsSortingItem.isChecked = item.checked
        }
    }
}
