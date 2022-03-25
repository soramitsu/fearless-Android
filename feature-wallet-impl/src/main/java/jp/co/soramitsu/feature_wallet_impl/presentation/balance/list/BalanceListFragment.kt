package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import javax.inject.Inject
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListAssets
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListAvatar
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListContent
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListTotalAmount
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListTotalAmountEmptyShimmer
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListTotalAmountShimmer
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListTotalAmountShimmerInner
import kotlinx.android.synthetic.main.fragment_balance_list.balanceListTotalTitle
import kotlinx.android.synthetic.main.fragment_balance_list.manageAssets
import kotlinx.android.synthetic.main.fragment_balance_list.walletContainer

class BalanceListFragment : BaseFragment<BalanceListViewModel>(), BalanceListAdapter.ItemAssetHandler {

    @Inject
    protected lateinit var imageLoader: ImageLoader

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

        adapter = BalanceListAdapter(imageLoader, this)
        balanceListAssets.adapter = adapter

        walletContainer.setOnRefreshListener {
            viewModel.sync()
        }

        balanceListAvatar.setOnClickListener {
            viewModel.avatarClicked()
        }

        manageAssets.setWholeClickListener {
            viewModel.manageAssetsClicked()
        }

        balanceListTotalAmount.setOnClickListener { viewModel.onBalanceClicked() }
        balanceListTotalAmountShimmer.setOnClickListener { viewModel.onBalanceClicked() }
        balanceListTotalAmountEmptyShimmer.setOnClickListener { viewModel.onBalanceClicked() }
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

            balanceListTotalAmount.text = it.totalBalance?.formatAsCurrency(it.fiatSymbol)
            balanceListTotalAmountShimmerInner.text = it.totalBalance?.formatAsCurrency(it.fiatSymbol)
            balanceListTotalAmountShimmer.setVisible(it.isUpdating && it.totalBalance != null && it.isTokensUpdated, View.INVISIBLE)
            balanceListTotalAmountEmptyShimmer.setVisible(it.isUpdating && (it.totalBalance == null || !it.isTokensUpdated))
            balanceListTotalAmount.setVisible(!it.isUpdating, View.INVISIBLE)
        }

        viewModel.currentAddressModelLiveData.observe {
            balanceListTotalTitle.text = it.nameOrAddress
            balanceListAvatar.setImageDrawable(it.image)
        }

        viewModel.hideRefreshEvent.observeEvent {
            walletContainer.isRefreshing = false
        }

        viewModel.showFiatChooser.observeEvent(::showFiatChooser)

        viewModel.showUnsupportedChainAlert.observeEvent { showUnsupportedChainAlert() }
        viewModel.openPlayMarket.observeEvent { openPlayMarket() }
    }

    private fun showFiatChooser(payload: DynamicListBottomSheet.Payload<FiatCurrency>) {
        FiatCurrenciesChooserBottomSheetDialog(requireContext(), imageLoader, payload, viewModel::onFiatSelected).show()
    }

    private fun showUnsupportedChainAlert() {
        AlertBottomSheet.Builder(requireContext())
            .setTitle(R.string.common_update_needed)
            .setMessage(R.string.unsupported_chain_alert_message)
            .setButtonText(R.string.common_update)
            .callback { viewModel.updateAppClicked() }
            .build()
            .show()
    }

    private fun openPlayMarket() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_APP_URI)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_BROWSER_URI)))
        }
    }

    override fun assetClicked(asset: AssetModel) {
        viewModel.assetClicked(asset)
    }
}
