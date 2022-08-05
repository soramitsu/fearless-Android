package jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets

import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.dragAndDropItemTouchHelper
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.scrollToTopWhenItemsShuffled
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.AddAccountBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentManageAssetsBinding
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import javax.inject.Inject

@AndroidEntryPoint
class ManageAssetsFragment : BaseFragment<ManageAssetsViewModel>(R.layout.fragment_manage_assets), ManageAssetsAdapter.Handler {

    @Inject
    lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentManageAssetsBinding::bind)

    override val viewModel: ManageAssetsViewModel by viewModels()

    private val dragHelper by lazy { dragAndDropItemTouchHelper(viewModel) }

    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        ManageAssetsAdapter(this, imageLoader)
    }

    override fun initViews() {
        with(binding) {
            assetsList.adapter = adapter
            assetsList.scrollToTopWhenItemsShuffled(viewLifecycleOwner)
            dragHelper.attachToRecyclerView(assetsList)
            assetsSearchField.onTextChanged { viewModel.searchQueryChanged(it) }
            manageAssetsToolbar.setHomeButtonListener { viewModel.backClicked() }
            applyButton.setOnClickListener { viewModel.onApply() }
        }
    }

    override fun subscribe(viewModel: ManageAssetsViewModel) {
        viewModel.unsyncedItemsFlow.observe(adapter::submitList)
        viewModel.canApply.observe {
            val state = if (it) ButtonState.NORMAL else ButtonState.DISABLED
            binding.applyButton.setState(state)
        }
        viewModel.showAddAccountChooser.observeEvent(::showAddAccountChooser)
    }

    override fun switch(item: ManageAssetModel) {
        viewModel.toggleEnabled(item)
    }

    override fun addAccount(chainId: ChainId, chainName: String, symbol: String, markedAsNotNeed: Boolean) {
        viewModel.onAddAccountClick(chainId, chainName, symbol, markedAsNotNeed)
    }

    override fun startDrag(viewHolder: RecyclerView.ViewHolder) {
        dragHelper.startDrag(viewHolder)
    }

    private fun showAddAccountChooser(payload: AddAccountBottomSheet.Payload) {
        AddAccountBottomSheet(
            requireContext(),
            payload = payload,
            onCreate = viewModel::createAccount,
            onImport = viewModel::importAccount,
            onNoNeed = viewModel::noNeedAccount
        ).show()
    }
}
