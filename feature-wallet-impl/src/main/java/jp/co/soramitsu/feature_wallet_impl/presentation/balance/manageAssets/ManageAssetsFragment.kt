package jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.dragAndDropItemTouchHelper
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_manage_assets.applyButton
import kotlinx.android.synthetic.main.fragment_manage_assets.assetsList
import kotlinx.android.synthetic.main.fragment_manage_assets.assetsSearchField
import kotlinx.android.synthetic.main.fragment_manage_assets.manageAssetsToolbar

class ManageAssetsFragment : BaseFragment<ManageAssetsViewModel>(), ManageAssetsAdapter.Handler {

    @Inject
    lateinit var imageLoader: ImageLoader

    private val dragHelper by lazy { dragAndDropItemTouchHelper(viewModel) }

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        ManageAssetsAdapter(this, imageLoader)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_manage_assets, container, false)

    override fun initViews() {
        assetsList.adapter = adapter
        dragHelper.attachToRecyclerView(assetsList)
        assetsSearchField.onTextChanged { viewModel.searchQueryChanged(it) }
        manageAssetsToolbar.setHomeButtonListener { viewModel.backClicked() }
        applyButton.setOnClickListener { viewModel.onApply() }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .manageAssetsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ManageAssetsViewModel) {
        viewModel.unsyncedItemsFlow.observe(adapter::submitList)
        viewModel.canApply.observe {
            val state = if (it) ButtonState.NORMAL else ButtonState.DISABLED
            applyButton.setState(state)
        }
    }

    override fun switch(item: ManageAssetModel) {
        viewModel.toggleEnabled(item)
    }

    override fun addAccount() {
        viewModel.addAccount()
    }

    override fun startDrag(viewHolder: RecyclerView.ViewHolder) {
        dragHelper.startDrag(viewHolder)
    }
}
