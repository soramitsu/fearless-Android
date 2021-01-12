package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.setupBuyIntegration
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.icon
import jp.co.soramitsu.feature_wallet_impl.util.format
import jp.co.soramitsu.feature_wallet_impl.util.formatAsChange
import jp.co.soramitsu.feature_wallet_impl.util.formatAsCurrency
import jp.co.soramitsu.feature_wallet_impl.util.formatAsToken
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetaiActions
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailAvailableAmount
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailBack
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContainer
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContent
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailDollarAmount
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailDollarGroup
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailFrozenAmount
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailFrozenTitle
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailRate
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailRateChange
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailTokenIcon
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailTokenName
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailTotal
import kotlinx.android.synthetic.main.fragment_balance_detail.transfersContainer

private const val KEY_TOKEN = "KEY_TOKEN"

class BalanceDetailFragment : BaseFragment<BalanceDetailViewModel>() {

    companion object {
        fun getBundle(type: Token.Type): Bundle {
            return Bundle().apply {
                putSerializable(KEY_TOKEN, type)
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
        transfersContainer.initializeBehavior(anchorView = balanceDetailContent)

        transfersContainer.setScrollingListener(viewModel::scrolled)

        transfersContainer.setSlidingStateListener(::setRefreshEnabled)

        transfersContainer.setTransactionClickListener(viewModel::transactionClicked)

        balanceDetailContainer.setOnRefreshListener {
            viewModel.refresh()
        }

        balanceDetailBack.setOnClickListener { viewModel.backClicked() }

        balanceDetaiActions.send.setOnClickListener {
            viewModel.sendClicked()
        }

        balanceDetaiActions.receive.setOnClickListener {
            viewModel.receiveClicked()
        }

        balanceDetaiActions.buy.setOnClickListener {
            viewModel.buyClicked()
        }

        balanceDetailFrozenTitle.setOnClickListener {
            viewModel.frozenInfoClicked()
        }
    }

    override fun inject() {
        val token = arguments!![KEY_TOKEN] as Token.Type

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .balanceDetailComponentFactory()
            .create(this, token)
            .inject(this)
    }

    override fun subscribe(viewModel: BalanceDetailViewModel) {
        viewModel.syncAssetRates()
        viewModel.syncFirstTransactionsPage()

        viewModel.transactionsLiveData.observe(transfersContainer::showTransactions)

        setupBuyIntegration(viewModel)

        viewModel.assetLiveData.observe { asset ->
            balanceDetailTokenIcon.setImageResource(asset.token.type.icon)
            balanceDetailTokenName.text = asset.token.type.networkType.readableName

            asset.token.dollarRate?.let {
                balanceDetailDollarGroup.visibility = View.VISIBLE

                balanceDetailRate.text = it.formatAsCurrency()
            }

            asset.token.recentRateChange?.let {
                balanceDetailRateChange.setTextColorRes(asset.token.rateChangeColorRes!!)
                balanceDetailRateChange.text = it.formatAsChange()
            }

            asset.dollarAmount?.let { balanceDetailDollarAmount.text = it.formatAsCurrency() }

            balanceDetailTotal.text = asset.total.formatAsToken(asset.token.type)

            balanceDetailFrozenAmount.text = asset.frozen.format()
            balanceDetailAvailableAmount.text = asset.available.format()
        }

        viewModel.hideRefreshEvent.observeEvent {
            balanceDetailContainer.isRefreshing = false
        }

        viewModel.showFrozenDetailsEvent.observeEvent(::showFrozenDetails)

        balanceDetaiActions.buy.isEnabled = viewModel.buyEnabled
    }

    private fun setRefreshEnabled(bottomSheetState: Int) {
        val bottomSheetCollapsed = BottomSheetBehavior.STATE_COLLAPSED == bottomSheetState
        balanceDetailContainer.isEnabled = bottomSheetCollapsed
    }

    private fun showFrozenDetails(model: AssetModel) {
        FrozenTokensBottomSheet(requireContext(), model).show()
    }
}