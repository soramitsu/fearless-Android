package jp.co.soramitsu.feature_wallet_api.presentation.mixin.assetSelector

import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.presentation.model.AssetModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface AssetSelectorMixin {

    val showAssetChooser: Flow<DynamicListBottomSheet.Payload<AssetModel>>

    fun assetSelectorClicked()

    fun assetChosen(assetModel: AssetModel)

    val selectedAssetModelFlow: Flow<AssetModel>

    interface Presentation : AssetSelectorMixin {

        interface Factory {

            fun create(scope: CoroutineScope): Presentation
        }

        val selectedAssetFlow: Flow<Asset>
    }
}
