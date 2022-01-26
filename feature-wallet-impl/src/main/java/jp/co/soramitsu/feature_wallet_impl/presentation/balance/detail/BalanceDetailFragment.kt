package jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import coil.ImageLoader
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.AssetPayload
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.assetActions.buy.setupBuyIntegration
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.showState
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetaiActions
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailBack
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContainer
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailContent
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailRate
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailRateChange
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailTokenIcon
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailTokenName
import kotlinx.android.synthetic.main.fragment_balance_detail.balanceDetailsInfo
import kotlinx.android.synthetic.main.fragment_balance_detail.chainAssetName
import kotlinx.android.synthetic.main.fragment_balance_detail.chainBadgeIcon
import kotlinx.android.synthetic.main.fragment_balance_detail.transfersContainer
import javax.inject.Inject

private const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"

class BalanceDetailFragment : BaseFragment<BalanceDetailViewModel>() {

    companion object {
        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_balance_detail, container, false)
    }

    override fun initViews() {
        hideKeyboard()

        transfersContainer.initializeBehavior(anchorView = balanceDetailContent)

        transfersContainer.setScrollingListener(viewModel::transactionsScrolled)

        transfersContainer.setSlidingStateListener(::setRefreshEnabled)

        transfersContainer.setTransactionClickListener(viewModel::transactionClicked)

        transfersContainer.setFilterClickListener { viewModel.filterClicked() }

        balanceDetailContainer.setOnRefreshListener {
            viewModel.sync()
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

        balanceDetailsInfo.lockedTitle.setOnClickListener {
            viewModel.frozenInfoClicked()
        }
    }

    override fun inject() {
        val token = arguments!![KEY_ASSET_PAYLOAD] as AssetPayload

        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .balanceDetailComponentFactory()
            .create(this, token)
            .inject(this)
    }

    override fun subscribe(viewModel: BalanceDetailViewModel) {
        viewModel.sync()

        viewModel.state.observe(transfersContainer::showState)

        setupBuyIntegration(viewModel)

        viewModel.assetLiveData.observe { asset ->
            balanceDetailTokenIcon.load(asset.token.configuration.iconUrl, imageLoader)
            chainBadgeIcon.load(asset.token.configuration.chainIcon, imageLoader)

            balanceDetailTokenName.text = asset.token.configuration.symbol
            chainAssetName.text = asset.token.configuration.chainName

            balanceDetailRate.text = asset.token.dollarRate?.formatAsCurrency() ?: ""

            asset.token.recentRateChange?.let {
                balanceDetailRateChange.setTextColorRes(asset.token.rateChangeColorRes)
                balanceDetailRateChange.text = it.formatAsChange()
            }

            balanceDetailsInfo.total.text = asset.total.formatTokenAmount(asset.token.configuration)
            balanceDetailsInfo.totalFiat.setTextOrHide(asset.totalFiat?.formatAsCurrency())

            balanceDetailsInfo.transferable.text = asset.available.formatTokenAmount(asset.token.configuration)
            balanceDetailsInfo.transferableFiat.setTextOrHide(asset.availableFiat?.formatAsCurrency())

            balanceDetailsInfo.locked.text = asset.frozen.formatTokenAmount(asset.token.configuration)
            balanceDetailsInfo.lockedFiat.setTextOrHide(asset.frozenFiat?.formatAsCurrency())
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
