package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailBack
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContainer
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContent
import kotlinx.android.synthetic.main.fragment_balance_detail.transfersContainer

private const val KEY_TOKEN = "KEY_TOKEN"

class BalanceDetailFragment : BaseFragment<BalanceDetailViewModel>() {

    companion object {
        fun getBundle(token: Asset.Token): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TOKEN, token)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_balance_detail, container, false)
    }

    override fun initViews() {
        transfersContainer.anchorTo(balanceDetailContent)

        transfersContainer.setPageLoadListener {
            viewModel.shouldLoadPage()
        }

        transfersContainer.setSlidingStateListener {
            val bottomSheetExpanded = BottomSheetBehavior.STATE_EXPANDED == it
            balanceDetailContainer.isEnabled = !bottomSheetExpanded
        }

        balanceDetailContainer.setOnRefreshListener {
            viewModel.refresh()
        }

        balanceDetailBack.setOnClickListener { viewModel.backClicked() }
    }

    override fun inject() {
        val token = arguments!![KEY_TOKEN] as Asset.Token

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .balanceDetailComponentFactory()
            .create(this, token)
            .inject(this)
    }

    override fun subscribe(viewModel: BalanceDetailViewModel) {
        viewModel.syncAssets()
        viewModel.syncFirstTransactionsPage()

        viewModel.transactionsLiveData.observe(transfersContainer::showTransactions)

//        viewModel.balanceLiveData.observe {
//            adapter.submitList(it.assetModels)
//
//            balanceListTotalAmount.text = it.totalBalance.formatAsCurrency()
//        }
//
//        viewModel.userIconLiveData.observe {
//            balanceListAvatar.setImageDrawable(it)
//        }

        viewModel.hideRefreshEvent.observeEvent {
            balanceDetailContainer.isRefreshing = false
        }
    }
}