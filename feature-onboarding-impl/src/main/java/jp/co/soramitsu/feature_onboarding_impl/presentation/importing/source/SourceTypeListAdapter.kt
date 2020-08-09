package jp.co.soramitsu.feature_onboarding_impl.presentation.importing.source

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
import jp.co.soramitsu.feature_onboarding_impl.presentation.importing.source.model.SourceTypeModel

class SourceTypeListAdapter(
    private val itemClickListener: (SourceType) -> Unit
) : ListAdapter<SourceTypeModel, SourceTypeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SourceTypeViewHolder {
        return SourceTypeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_chooser_list, viewGroup, false))
    }

    override fun onBindViewHolder(sourceTypeViewHolder: SourceTypeViewHolder, position: Int) {
        sourceTypeViewHolder.bind(getItem(position), itemClickListener)
    }
}

class SourceTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val sourceTypeText: TextView = itemView.findViewById(R.id.chooserText)
    private val selectedPinIcon: ImageView = itemView.findViewById(R.id.rightIcon)

    fun bind(sourceTypeModel: SourceTypeModel, itemClickListener: (SourceType) -> Unit) {
        with(itemView) {
            if (sourceTypeModel.isSelected) {
                selectedPinIcon.makeVisible()
            } else {
                selectedPinIcon.makeInvisible()
            }

            sourceTypeText.text = sourceTypeModel.name

            setOnClickListener {
                itemClickListener(sourceTypeModel.sourceType)
            }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<SourceTypeModel>() {
    override fun areItemsTheSame(oldItem: SourceTypeModel, newItem: SourceTypeModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: SourceTypeModel, newItem: SourceTypeModel): Boolean {
        return oldItem == newItem
    }
}