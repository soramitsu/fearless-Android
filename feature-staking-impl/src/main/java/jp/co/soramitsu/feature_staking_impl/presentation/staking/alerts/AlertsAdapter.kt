package jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_alert.view.*

class AlertsAdapter : ListAdapter<AlertModel, AlertsAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = parent.inflateChild(R.layout.item_alert)

        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item)
    }

    class AlertViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(alert: AlertModel) = with(containerView) {
            imageView.setImageResource(alert.icon)
            alertItemTitle.setText(alert.title)
            alertItemMessage.setText(alert.extraMessage)

            if (alert.type is AlertModel.Type.CallToAction) {
                alertItemGoToFlowIcon.makeVisible()
                setOnClickListener(alert.type.action)
            }
        }
    }
}

private class AlertDiffCallback : DiffUtil.ItemCallback<AlertModel>() {
    override fun areItemsTheSame(oldItem: AlertModel, newItem: AlertModel): Boolean {
        return oldItem.title == newItem.title && oldItem.extraMessage == newItem.extraMessage
    }

    override fun areContentsTheSame(oldItem: AlertModel, newItem: AlertModel): Boolean {
        return true
    }
}
