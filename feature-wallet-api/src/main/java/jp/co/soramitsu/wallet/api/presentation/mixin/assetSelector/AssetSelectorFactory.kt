package jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.domain.AssetUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class AssetSelectorFactory(
    private val assetUseCase: AssetUseCase,
    private val singleAssetSharedState: SingleAssetSharedState,
    private val resourceManager: ResourceManager
) : AssetSelectorMixin.Presentation.Factory {

    override fun create(scope: CoroutineScope): AssetSelectorMixin.Presentation {
        return AssetSelectorProvider(assetUseCase, resourceManager, singleAssetSharedState, scope)
    }
}

private class AssetSelectorProvider(
    private val assetUseCase: AssetUseCase,
    private val resourceManager: ResourceManager,
    private val singleAssetSharedState: SingleAssetSharedState,
    private val scope: CoroutineScope
) : AssetSelectorMixin.Presentation, CoroutineScope by scope {

    override val showAssetChooser = MutableLiveData<Event<DynamicListBottomSheet.Payload<AssetModel>>>()

    override val selectedAssetFlow: Flow<Asset> = assetUseCase.currentAssetFlow()
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    override val selectedAssetModelFlow: Flow<AssetModel> = selectedAssetFlow
        .map {
            mapAssetToAssetModel(it, resourceManager, patternId = null)
        }
        .shareIn(this, SharingStarted.Eagerly, replay = 1)

    override fun assetSelectorClicked() {
        launch {
            val availableToSelect = assetUseCase.availableAssetsToSelect()

            val models = availableToSelect.map { mapAssetToAssetModel(it, resourceManager, patternId = null) }

            val selectedChainAsset = selectedAssetFlow.first().token.configuration

            val selectedModel = models.firstOrNull {
                it.chainAssetId == selectedChainAsset.id && it.chainId == selectedChainAsset.chainId
            } ?: models.firstOrNull()

            showAssetChooser.value = Event(DynamicListBottomSheet.Payload(models, selectedModel))
        }
    }

    override fun assetChosen(assetModel: AssetModel) {
        singleAssetSharedState.update(assetModel.chainId, assetModel.chainAssetId)
    }
}
