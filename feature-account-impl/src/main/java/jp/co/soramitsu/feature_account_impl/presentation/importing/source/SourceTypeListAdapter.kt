package jp.co.soramitsu.feature_account_impl.presentation.importing.source

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.SourceTypeModel
import kotlinx.android.synthetic.main.item_source.view.rightIcon
import kotlinx.android.synthetic.main.item_source.view.sourceTv

class SourceTypeListAdapter(
    private val itemClickListener: (SourceType) -> Unit
) : ListAdapter<SourceTypeModel, SourceTypeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SourceTypeViewHolder {
        return SourceTypeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_source, viewGroup, false))
    }

    override fun onBindViewHolder(sourceTypeViewHolder: SourceTypeViewHolder, position: Int) {
        sourceTypeViewHolder.bind(getItem(position), itemClickListener)
    }
}

class SourceTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(sourceTypeModel: SourceTypeModel, itemClickListener: (SourceType) -> Unit) {
        with(itemView) {
            if (sourceTypeModel.isSelected) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            sourceTv.text = sourceTypeModel.name

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