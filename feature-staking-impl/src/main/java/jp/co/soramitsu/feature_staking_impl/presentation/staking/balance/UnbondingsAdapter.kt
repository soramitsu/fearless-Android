package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.view.startTimer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.UnbondingModel

class UnbondingsAdapter : ListAdapter<UnbondingModel, UnbondingsHolder>(UnbondingModelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnbondingsHolder {
        val view = parent.inflateChild(R.layout.item_list_default)

        return UnbondingsHolder(view)
    }

    override fun onBindViewHolder(holder: UnbondingsHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item)
    }
}

class UnbondingsHolder(val containerView: View) : RecyclerView.ViewHolder(containerView) {

    fun bind(unbonding: UnbondingModel) = with(containerView) {
        with(unbonding) {
            findViewById<TextView>(R.id.itemListElementDescriptionLeft).startTimer(timeLeft, calculatedAt)

            findViewById<TextView>(R.id.itemListElementTitleLeft).text = unbonding.amountModel.titleResId?.let { context.getString(it) }
            findViewById<TextView>(R.id.itemListElementTitleRight).text = unbonding.amountModel.token
            findViewById<TextView>(R.id.itemListElementDescriptionRight).text = unbonding.amountModel.fiat
        }
    }
}

private class UnbondingModelDiffCallback : DiffUtil.ItemCallback<UnbondingModel>() {

    override fun areItemsTheSame(oldItem: UnbondingModel, newItem: UnbondingModel): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: UnbondingModel, newItem: UnbondingModel): Boolean {
        return true
    }
}
