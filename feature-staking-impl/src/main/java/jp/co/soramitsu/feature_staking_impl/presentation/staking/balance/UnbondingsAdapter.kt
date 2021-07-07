package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import android.os.CountDownTimer
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.UnbondingModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.formatTime
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.getDays
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementDescriptionLeft
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementDescriptionRight
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementTitleLeft
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementTitleRight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

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

class UnbondingsHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    fun bind(unbonding: UnbondingModel) = with(containerView) {
        with(unbonding) {

            val timer = object : CountDownTimer(timeLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val days = millisUntilFinished.getDays()

                    itemListElementDescriptionLeft.text = if(days > 0)
                        resources.getQuantityString(R.plurals.staking_payouts_days_left, days, days)
                    else
                        millisUntilFinished.formatTime()
                }

                override fun onFinish() {
                    itemListElementDescriptionLeft.text = 0L.formatTime()

                    cancel()
                }
            }

            timer.start()
            itemListElementTitleLeft.text = context.getString(R.string.staking_unbond)
            itemListElementTitleRight.text = unbonding.amountModel.token
            itemListElementDescriptionRight.text = unbonding.amountModel.fiat
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
