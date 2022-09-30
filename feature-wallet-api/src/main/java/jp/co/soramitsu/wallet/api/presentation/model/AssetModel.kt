package jp.co.soramitsu.wallet.api.presentation.model

import androidx.recyclerview.widget.DiffUtil

data class AssetModel(
    val chainId: String,
    val chainAssetId: String,
    val imageUrl: String,
    val tokenName: String,
    val assetBalance: String
) {

    companion object {

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AssetModel>() {

            override fun areItemsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
                return oldItem.chainId == newItem.chainId && oldItem.chainAssetId == newItem.chainAssetId
            }

            override fun areContentsTheSame(oldItem: AssetModel, newItem: AssetModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}
