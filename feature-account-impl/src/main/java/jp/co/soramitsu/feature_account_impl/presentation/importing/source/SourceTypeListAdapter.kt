package jp.co.soramitsu.feature_account_impl.presentation.importing.source

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.SourceTypeListAdapter.*
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import kotlinx.android.synthetic.main.item_source.view.rightIcon
import kotlinx.android.synthetic.main.item_source.view.sourceTv

class SourceTypeListAdapter(
    private val selectedSource: ImportSource,
    private val itemClickListener: SourceItemHandler
) : ListAdapter<ImportSource, SourceTypeViewHolder>(DiffCallback) {
    interface SourceItemHandler {
        fun onSourceSelected(source: ImportSource)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SourceTypeViewHolder {
        return SourceTypeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.item_source, viewGroup, false))
    }

    override fun onBindViewHolder(sourceTypeViewHolder: SourceTypeViewHolder, position: Int) {
        val currentSourceType = getItem(position)

        val isSelected = selectedSource.nameRes == currentSourceType.nameRes

        sourceTypeViewHolder.bind(currentSourceType, itemClickListener, isSelected)
    }
}

class SourceTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(sourceType: ImportSource, typeClickListener: SourceItemHandler, isSelected: Boolean) {
        with(itemView) {
            if (isSelected) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            sourceTv.setText(sourceType.nameRes)

            setOnClickListener {
                typeClickListener.onSourceSelected(sourceType)
            }
        }
    }
}

object DiffCallback : DiffUtil.ItemCallback<ImportSource>() {
    override fun areItemsTheSame(oldItem: ImportSource, newItem: ImportSource): Boolean {
        return oldItem.nameRes == newItem.nameRes
    }

    override fun areContentsTheSame(oldItem: ImportSource, newItem: ImportSource): Boolean {
        return true
    }
}