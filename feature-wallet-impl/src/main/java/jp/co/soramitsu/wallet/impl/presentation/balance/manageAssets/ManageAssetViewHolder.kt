package jp.co.soramitsu.wallet.impl.presentation.balance.manageAssets

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.co.soramitsu.common.view.BadgeView
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

@SuppressLint("ClickableViewAccessibility")
class ManageAssetViewHolder(
    itemView: View,
    private val imageLoader: ImageLoader,
    private val listener: Listener
) : RecyclerView.ViewHolder(itemView) {

    interface Listener {
        fun switch(itemPosition: Int, checked: Boolean)
        fun addAccount(chainId: ChainId, chainName: String, assetId: String, markedAsNotNeed: Boolean)
        fun startDrag(viewHolder: RecyclerView.ViewHolder)
    }

    init {
        itemView.findViewById<SwitchMaterial>(R.id.manageAssetsItemSwitch).setOnCheckedChangeListener { _, checked ->
            listener.switch(adapterPosition, checked)
        }

        itemView.findViewById<ImageView>(R.id.manageAssetsItemDragView).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                listener.startDrag(this)
            }
            false
        }
    }

    fun bind(item: ManageAssetModel) =
        with(itemView) {
            findViewById<ImageView>(R.id.manageAssetsItemIcon).load(item.iconUrl, imageLoader)
            findViewById<TextView>(R.id.manageAssetsItemName).text = item.name

            findViewById<Group>(R.id.manageAssetsAccountExistStateGroup).isVisible = item.hasAccount
            findViewById<Group>(R.id.manageAssetsMissingAccountStateGroup).isVisible = !item.hasAccount
            if (item.hasAccount) {
                findViewById<TextView>(R.id.manageAssetsItemName).setTextColor(context.getColor(R.color.white))
                findViewById<TextView>(R.id.manageAssetsItemAmount).text = item.amount
                findViewById<SwitchMaterial>(R.id.manageAssetsItemSwitch).isChecked = item.enabled
                setupNetworkBadge(item.network)
                findViewById<BadgeView>(R.id.testnetBadge).isVisible = item.isTestNet
            } else {
                findViewById<TextView>(R.id.manageAssetsItemName).setTextColor(context.getColor(R.color.black2))
                findViewById<BadgeView>(R.id.manageAssetsBadge).isVisible = false
                findViewById<BadgeView>(R.id.testnetBadge).isVisible = false
            }

            if (item.markedAsNotNeed) {
                findViewById<TextView>(R.id.manageAssetsMissingAccountMessage).setCompoundDrawables(null, null, null, null)
            } else {
                findViewById<TextView>(R.id.manageAssetsMissingAccountMessage).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning_filled, 0, 0, 0)
            }

            findViewById<TextView>(R.id.manageAssetsAddAccount).setOnClickListener {
                listener.addAccount(
                    chainId = item.chainId,
                    chainName = item.name,
                    assetId = item.assetId,
                    markedAsNotNeed = item.markedAsNotNeed
                )
            }
        }

    private fun setupNetworkBadge(model: ManageAssetModel.Network?) = itemView.apply {
        findViewById<BadgeView>(R.id.manageAssetsBadge).isVisible = model?.let {
            findViewById<BadgeView>(R.id.manageAssetsBadge).setIcon(it.iconUrl, imageLoader)
            findViewById<BadgeView>(R.id.manageAssetsBadge).setText(stringText = it.name)
            true
        } ?: false
    }
}
