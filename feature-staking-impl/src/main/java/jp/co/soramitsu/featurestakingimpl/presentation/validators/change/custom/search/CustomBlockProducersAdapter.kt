package jp.co.soramitsu.featurestakingimpl.presentation.validators.change.custom.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.list.PayloadGenerator
import jp.co.soramitsu.feature_staking_impl.databinding.ItemBlockProducerSearchBinding

class CustomBlockProducersAdapter(
    private val itemHandler: ItemHandler
) : ListAdapter<SearchBlockProducerModel, BlockProducerViewHolder>(BlockProducerDiffCallback) {

    interface ItemHandler {
        fun blockProducerInfoClicked(blockProducerModel: SearchBlockProducerModel)
        fun blockProducerClicked(blockProducerModel: SearchBlockProducerModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockProducerViewHolder {
        val binding = ItemBlockProducerSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BlockProducerViewHolder(binding, { itemHandler.blockProducerInfoClicked(getItem(it)) }, { itemHandler.blockProducerClicked(getItem(it)) })
    }

    override fun onBindViewHolder(holder: BlockProducerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class BlockProducerViewHolder(private val binding: ItemBlockProducerSearchBinding, infoClicked: (Int) -> Unit, selected: (Int) -> Unit) :
    RecyclerView.ViewHolder(binding.root) {
    init {
        binding.info.setOnClickListener { infoClicked(bindingAdapterPosition) }
        binding.root.setOnClickListener { selected(bindingAdapterPosition) }
    }

    fun bind(item: SearchBlockProducerModel) {
        binding.apply {
            icon.setImageDrawable(item.image)
            name.text = item.name
            selectedIndicator.isVisible = item.selected
            scoring.text = item.rewardsPercent
        }
    }
}

object BlockProducerDiffCallback : DiffUtil.ItemCallback<SearchBlockProducerModel>() {

    override fun areItemsTheSame(
        oldItem: SearchBlockProducerModel,
        newItem: SearchBlockProducerModel
    ): Boolean {
        return oldItem.address == newItem.address
    }

    override fun areContentsTheSame(
        oldItem: SearchBlockProducerModel,
        newItem: SearchBlockProducerModel
    ): Boolean {
        return oldItem.name == newItem.name && oldItem.rewardsPercent == newItem.rewardsPercent && oldItem.selected == newItem.selected
    }

    override fun getChangePayload(
        oldItem: SearchBlockProducerModel,
        newItem: SearchBlockProducerModel
    ): Any {
        return BlockProducerPayloadGenerator.diff(oldItem, newItem)
    }

    private object BlockProducerPayloadGenerator : PayloadGenerator<SearchBlockProducerModel>(
        SearchBlockProducerModel::selected,
        SearchBlockProducerModel::rewardsPercent
    )
}
