package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.changeAccount.AccountChooserBottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.util.formatAsCurrency
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListActions
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListAssets
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListAvatar
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListContent
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListTotalAmount
import kotlinx.android.synthetic.main.fragment_balance_list.transfersContainer
import kotlinx.android.synthetic.main.fragment_balance_list.walletContainer

class BalanceListFragment : BaseFragment<BalanceListViewModel>(), BalanceListAdapter.ItemAssetHandler {

    private lateinit var adapter: BalanceListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_balance_list, container, false)
    }

    override fun initViews() {
        adapter = BalanceListAdapter(this)
        balanceListAssets.adapter = adapter

        transfersContainer.initializeBehavior(anchorView = balanceListContent)

        transfersContainer.setScrollingListener(viewModel::scrolled)

        transfersContainer.setSlidingStateListener(this::setRefreshEnabled)

        transfersContainer.setTransactionClickListener(viewModel::transactionClicked)

        walletContainer.setOnRefreshListener {
            viewModel.refresh()
        }

        balanceListActions.send.setOnClickListener {
            viewModel.sendClicked()
        }

        balanceListActions.receive.setOnClickListener {
            viewModel.receiveClicked()
        }

        balanceListActions.buy.setOnClickListener {
            viewModel.buyClicked()
        }

        balanceListAvatar.setOnClickListener {
            viewModel.avatarClicked()
        }
    }

    private fun setRefreshEnabled(bottomSheetState: Int) {
        val bottomSheetCollapsed = BottomSheetBehavior.STATE_COLLAPSED == bottomSheetState
        walletContainer.isEnabled = bottomSheetCollapsed
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .balanceListComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: BalanceListViewModel) {
        viewModel.syncAssetsRates()
        viewModel.syncFirstTransactionsPage()

        viewModel.transactionsLiveData.observe(transfersContainer::showTransactions)

        viewModel.buyShownLiveData.observe(balanceListActions.buy::setEnabled)

        viewModel.balanceLiveData.observe {
            adapter.submitList(it.assetModels)

            balanceListTotalAmount.text = it.totalBalance.formatAsCurrency()
        }

        viewModel.currentAddressModelLiveData.observe {
            balanceListAvatar.setImageDrawable(it.image)
        }

        viewModel.hideRefreshEvent.observeEvent {
            walletContainer.isRefreshing = false
        }

        viewModel.showAccountChooser.observeEvent {
            AccountChooserBottomSheetDialog(requireActivity(), it, viewModel::accountSelected).show()
        }
    }

    override fun assetClicked(asset: AssetModel) {
        viewModel.assetClicked(asset)
    }
}