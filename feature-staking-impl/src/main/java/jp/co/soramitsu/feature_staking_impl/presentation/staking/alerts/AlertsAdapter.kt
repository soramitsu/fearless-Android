package jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import kotlinx.android.extensions.LayoutContainer

class AlertsAdapter : ListAdapter<AlertModel, AlertsAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = parent.inflateChild(R.layout.item_alert)

        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val isLast = position == itemCount - 1

        val item = getItem(position)

        holder.bind(item, isLast)
    }

    class AlertViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(alert: AlertModel, isLast: Boolean) = with(containerView) {
            findViewById<ImageView>(R.id.imageView).setImageResource(alert.icon)
            findViewById<TextView>(R.id.alertItemTitle).text = alert.title
            findViewById<TextView>(R.id.alertItemMessage).text = alert.extraMessage

            if (alert.type is AlertModel.Type.CallToAction) {
                findViewById<ImageView>(R.id.alertItemGoToFlowIcon).makeVisible()

                setOnClickListener {
                    alert.type.action()
                }
            } else {
                findViewById<ImageView>(R.id.alertItemGoToFlowIcon).makeGone()
            }

            findViewById<View>(R.id.alertItemDivider).setVisible(isLast.not())
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
