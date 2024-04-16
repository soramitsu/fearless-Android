package jp.co.soramitsu.staking.impl.presentation.common

import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class StakingAssetSelector(
    private val stakingSharedState: StakingSharedState,
    private val scope: CoroutineScope
) : CoroutineScope by scope {

    val showAssetChooser = MutableLiveData<Event<DynamicListBottomSheet.Payload<StakingAssetSelectorModel>>>()

    val selectedAssetModelFlow: SharedFlow<StakingAssetSelectorModel> = combine(
        stakingSharedState.selectionItem,
        stakingSharedState.currentAssetFlow()
    ) { selectedItem, asset ->
        val assetBalance = if (selectedItem.type == StakingType.POOL) {
            asset.transferable
        } else {
            asset.availableForStaking
        }
        StakingAssetSelectorModel(
            selectedItem,
            asset.token.configuration.iconUrl,
            asset.token.configuration.chainName,
            assetBalance.formatCrypto(asset.token.configuration.symbol)
        )
    }
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    fun assetSelectorClicked() {
        launch {
            val availableToSelect = stakingSharedState.availableToSelect()
            val availableAssetsToSelect = stakingSharedState.availableAssetsToSelect()

            val models = availableToSelect.mapNotNull { selection ->
                val asset = availableAssetsToSelect.firstOrNull { it.token.configuration.id == selection.chainAssetId } ?: return@mapNotNull null
                val assetBalance = if (selection.type == StakingType.POOL) {
                    asset.transferable
                } else {
                    asset.availableForStaking
                }
                StakingAssetSelectorModel(
                    selection,
                    asset.token.configuration.iconUrl,
                    asset.token.configuration.chainName,
                    assetBalance.formatCrypto(asset.token.configuration.symbol)
                )
            }

            val selectedItem = stakingSharedState.selectionItem.first()

            val selectedModel = models.firstOrNull {
                it.selectionItem.chainAssetId == selectedItem.chainAssetId &&
                    it.selectionItem.chainId == selectedItem.chainId &&
                    it.selectionItem.type == selectedItem.type
            } ?: models.firstOrNull()

            showAssetChooser.value = Event(DynamicListBottomSheet.Payload(models, selectedModel))
        }
    }

    fun assetChosen(model: StakingAssetSelectorModel) {
        stakingSharedState.update(model.selectionItem)
    }

    data class StakingAssetSelectorModel(
        val selectionItem: StakingAssetSelection,
        val imageUrl: String,
        val chainName: String,
        val assetBalance: String
    ) {
        companion object {

            val DIFF_CALLBACK = object : DiffUtil.ItemCallback<StakingAssetSelectorModel>() {

                override fun areItemsTheSame(
                    oldItem: StakingAssetSelectorModel,
                    newItem: StakingAssetSelectorModel
                ): Boolean {
                    return oldItem.selectionItem.chainId == newItem.selectionItem.chainId &&
                        oldItem.selectionItem.chainAssetId == newItem.selectionItem.chainAssetId &&
                        oldItem.selectionItem.type == newItem.selectionItem.type
                }

                override fun areContentsTheSame(
                    oldItem: StakingAssetSelectorModel,
                    newItem: StakingAssetSelectorModel
                ): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }
}
