package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListAssets
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListAvatar
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListContent
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListTotalAmount
import kotlinx.android.synthetic.main.fragment_balance_list.walletContainer

class BalanceListFragment : BaseFragment<BalanceListViewModel>(), BalanceListAdapter.ItemAssetHandler {

    private lateinit var adapter: BalanceListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_balance_list, container, false)
    }

    override fun initViews() {
        balanceListContent.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        hideKeyboard()

        adapter = BalanceListAdapter(this)
        balanceListAssets.adapter = adapter

        walletContainer.setOnRefreshListener {
            viewModel.sync()
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
        viewModel.sync()

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
    }

    override fun assetClicked(asset: AssetModel) {
        viewModel.assetClicked(asset)
    }
}
