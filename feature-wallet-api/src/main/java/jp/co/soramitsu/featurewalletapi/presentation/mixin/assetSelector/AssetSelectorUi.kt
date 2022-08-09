package jp.co.soramitsu.featurewalletapi.presentation.mixin.assetSelector

import coil.ImageLoader
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.featurewalletapi.presentation.view.AssetSelectorBottomSheet
import jp.co.soramitsu.featurewalletapi.presentation.view.AssetSelectorView

interface WithAssetSelector {

    val assetSelectorMixin: AssetSelectorMixin
}

fun <V> BaseFragment<V>.setupAssetSelector(
    view: AssetSelectorView,
    viewModel: V,
    imageLoader: ImageLoader
) where V : BaseViewModel, V : WithAssetSelector {
    view.onClick {
        viewModel.assetSelectorMixin.assetSelectorClicked()
    }

    viewModel.assetSelectorMixin.selectedAssetModelFlow.observe {
        view.setState(imageLoader, it)
    }

    viewModel.assetSelectorMixin.showAssetChooser.observeEvent {
        AssetSelectorBottomSheet(
            imageLoader = imageLoader,
            context = requireContext(),
            payload = it,
            onClicked = viewModel.assetSelectorMixin::assetChosen
        ).show()
    }
}
