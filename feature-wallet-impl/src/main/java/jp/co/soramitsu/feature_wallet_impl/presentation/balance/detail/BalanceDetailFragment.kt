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
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.icon
import jp.co.soramitsu.feature_wallet_impl.util.format
import jp.co.soramitsu.feature_wallet_impl.util.formatAsChange
import jp.co.soramitsu.feature_wallet_impl.util.formatAsCurrency
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailAmount
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailAvailableAmount
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailBack
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailBondedAmount
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContainer
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContent
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailDollarAmount
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailDollarGroup
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailRate
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailRateChange
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailSend
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailTokenIcon
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailTokenName
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

        balanceDetailSend.setOnClickListener {
            viewModel.sendClicked()
        }
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
        viewModel.syncAsset()
        viewModel.syncFirstTransactionsPage()

        viewModel.transactionsLiveData.observe(transfersContainer::showTransactions)

        viewModel.assetLiveData.observe { asset ->
            balanceDetailTokenIcon.setImageResource(asset.token.icon)
            balanceDetailTokenName.text = asset.token.networkType.readableName

            asset.dollarRate?.let {
                balanceDetailDollarGroup.visibility = View.VISIBLE

                balanceDetailRate.text = it.formatAsCurrency()
            }

            asset.recentRateChange?.let {
                balanceDetailRateChange.setTextColorRes(asset.rateChangeColorRes!!)
                balanceDetailRateChange.text = it.formatAsChange()
            }

            asset.dollarAmount?.let { balanceDetailDollarAmount.text = it.formatAsCurrency() }

            balanceDetailAmount.text = asset.formattedAmount

            balanceDetailBondedAmount.text = asset.bonded.format()
            balanceDetailAvailableAmount.text = asset.available.format()
        }

        viewModel.hideRefreshEvent.observeEvent {
            balanceDetailContainer.isRefreshing = false
        }
    }
}