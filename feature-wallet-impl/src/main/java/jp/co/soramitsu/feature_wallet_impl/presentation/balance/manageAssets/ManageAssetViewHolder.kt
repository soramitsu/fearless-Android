package jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.load
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsAccountExistStateGroup
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsAddAccountButton
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsBadge
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsItemAmount
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsItemDragView
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsItemIcon
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsItemName
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsItemSwitch
import kotlinx.android.synthetic.main.item_manage_asset.view.manageAssetsMissingAccountStateGroup
import kotlinx.android.synthetic.main.item_manage_asset.view.testnetBadge

@SuppressLint("ClickableViewAccessibility")
class ManageAssetViewHolder(
    itemView: View,
    private val imageLoader: ImageLoader,
    private val listener: Listener
) : RecyclerView.ViewHolder(itemView) {

    interface Listener {
        fun switch(itemPosition: Int, checked: Boolean)
        fun addAccount()
        fun startDrag(viewHolder: RecyclerView.ViewHolder)
    }

    init {
        itemView.manageAssetsItemSwitch.setOnCheckedChangeListener { _, checked ->
            listener.switch(adapterPosition, checked)
        }

        itemView.manageAssetsAddAccountButton.setOnClickListener {
            listener.addAccount()
        }

        itemView.manageAssetsItemDragView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                listener.startDrag(this)
            }
            false
        }
    }

    fun bind(item: ManageAssetModel) =
        with(itemView) {
            manageAssetsItemIcon.load(item.iconUrl, imageLoader)
            manageAssetsItemName.text = item.name

            manageAssetsAccountExistStateGroup.isVisible = item.amount?.let {
                manageAssetsItemAmount.text = item.amount
                manageAssetsItemSwitch.isChecked = item.enabled
                setupNetworkBadge(item.network)
                testnetBadge.isVisible = item.isTestNet
                true
            } ?: false

            manageAssetsMissingAccountStateGroup.isVisible = item.amount?.let { false }
                ?: kotlin.run {
                    manageAssetsBadge.isVisible = false
                    testnetBadge.isVisible = false
                    true
                }
        }

    private fun setupNetworkBadge(model: ManageAssetModel.Network?) = itemView.apply {
        manageAssetsBadge.isVisible = model?.let {
            manageAssetsBadge.setIcon(it.iconUrl, imageLoader)
            manageAssetsBadge.setText(stringText = it.name)
            true
        } ?: false
    }
}
